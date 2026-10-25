package dev.lyra.player.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.lyra.player.AudioAccess
import dev.lyra.player.LibraryViewModel
import dev.lyra.player.LyraApp
import dev.lyra.player.R
import dev.lyra.player.SearchViewModel
import dev.lyra.player.model.AlbumGroup
import dev.lyra.player.model.ArtistGroup
import dev.lyra.player.model.Playlist
import dev.lyra.player.player.PlayerViewModel
import dev.lyra.player.player.StreamCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Main : Screen
    data class Album(val key: String) : Screen
    data class Artist(val name: String) : Screen
    data class PlaylistDetail(val id: String) : Screen
    data object NowPlaying : Screen
    data object About : Screen
}

@Composable
fun LyraRoot(
    player: PlayerViewModel,
    library: LibraryViewModel,
    search: SearchViewModel,
) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Screen>(Screen.Main) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = stack.last() != Screen.Main) {
        stack.removeAt(stack.lastIndex)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) library.refreshPermission()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        library.refreshPermission()
    }
    fun allow() {
        val needed = mutableListOf(AudioAccess.permission())
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(needed.toTypedArray())
    }
    fun settings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        context.startActivity(intent)
    }
    val screen = stack.last()
    val openNow: () -> Unit = { stack.add(Screen.NowPlaying) }
    val play: (List<dev.lyra.player.model.Track>, Int) -> Unit = { tracks, index ->
        player.play(tracks, index)
        if (stack.last() != Screen.NowPlaying) stack.add(Screen.NowPlaying)
    }
    when (screen) {
        Screen.NowPlaying -> NowPlayingScreen(
            player = player,
            onClose = { stack.removeAt(stack.lastIndex) },
            onSaveQueue = { name -> library.createPlaylist(name, player.queue) },
        )
        Screen.About -> AboutScreen(
            onBack = { stack.removeAt(stack.lastIndex) },
            onClearAudio = { StreamCache.clear(context) },
            onClearLyrics = { (context.applicationContext as LyraApp).graph.lyrics.clearCache() },
        )
        is Screen.Album -> {
            val album = library.index.albums.find { it.key == screen.key }
            if (album == null) {
                Missing("That album is no longer on this device.") { stack.removeAt(stack.lastIndex) }
            } else {
                CollectionScreen(album.title, album.artist, album.tracks, player.track?.id, { stack.removeAt(stack.lastIndex) }, play)
            }
        }
        is Screen.Artist -> {
            val artist = library.index.artists.find { it.name == screen.name }
            if (artist == null) {
                Missing("That artist is no longer on this device.") { stack.removeAt(stack.lastIndex) }
            } else {
                CollectionScreen(artist.name, "${artist.tracks.size} songs", artist.tracks, player.track?.id, { stack.removeAt(stack.lastIndex) }, play)
            }
        }
        is Screen.PlaylistDetail -> {
            val playlist = library.playlists.find { it.id == screen.id }
            if (playlist == null) {
                Missing("That playlist was removed.") { stack.removeAt(stack.lastIndex) }
            } else {
                PlaylistDetailScreen(
                    playlist = playlist,
                    activeId = player.track?.id,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onPlay = play,
                    onRemove = { library.removeFromPlaylist(playlist.id, it) },
                    onDelete = {
                        library.deletePlaylist(playlist.id)
                        stack.removeAt(stack.lastIndex)
                    },
                )
            }
        }
        Screen.Main -> MainScaffold(
            tab = tab,
            onTab = { tab = it },
            player = player,
            message = library.message ?: player.error,
            onOpenNow = openNow,
            onRetry = player::retry,
        ) { padding ->
            Box(Modifier.padding(padding).windowInsetsPadding(WindowInsets.statusBars)) {
                when (tab) {
                    0 -> HomeScreen(
                        index = library.index,
                        granted = library.granted,
                        trending = library.trending,
                        resume = player.resume,
                        activeId = player.track?.id,
                        onAllow = ::allow,
                        onOpenSettings = ::settings,
                        onAbout = { stack.add(Screen.About) },
                        onPlay = play,
                        onOpenAlbum = { stack.add(Screen.Album(it.key)) },
                        onResume = { session ->
                            player.play(session.tracks, session.index, session.positionMs)
                            stack.add(Screen.NowPlaying)
                        },
                    )
                    1 -> LibraryScreen(
                        index = library.index,
                        granted = library.granted,
                        playlists = library.playlists,
                        activeId = player.track?.id,
                        onAllow = ::allow,
                        onPlay = play,
                        onPlayNext = player::playNext,
                        onOpenAlbum = { stack.add(Screen.Album(it.key)) },
                        onOpenArtist = { stack.add(Screen.Artist(it.name)) },
                        onOpenPlaylist = { stack.add(Screen.PlaylistDetail(it.id)) },
                        onCreatePlaylist = { name, tracks -> library.createPlaylist(name, tracks) },
                        onAddToPlaylist = { id, track -> library.addToPlaylist(id, listOf(track)) },
                        onImport = library::importM3u,
                    )
                    else -> SearchBody(
                        query = search.query,
                        onQuery = { search.update(it, library.index) },
                        searching = search.searching,
                        note = search.note,
                        tracks = search.tracks,
                        albums = search.albums,
                        audius = search.audius,
                        archive = search.archive,
                        activeId = player.track?.id,
                        onPlay = play,
                        onOpenAlbum = { stack.add(Screen.Album(it.key)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    tab: Int,
    onTab: (Int) -> Unit,
    player: PlayerViewModel,
    message: String?,
    onOpenNow: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = LyraColors.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                Modifier
                    .background(LyraColors.paper)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                if (message != null) {
                    MessageLine(message, Modifier.clickable(onClick = onRetry))
                }
                val track = player.track
                if (track != null) {
                    Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenNow)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(track.artworkUri, track.title, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(track.title, style = LyraType.bodyMedium, maxLines = 1)
                            Text(track.artist, style = LyraType.meta, maxLines = 1)
                        }
                        IconButton(onClick = player::toggle) {
                            Icon(
                                painterResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = if (player.isPlaying) "Pause" else "Play",
                                tint = LyraColors.ivory,
                            )
                        }
                    }
                }
                Hairline()
                Row(Modifier.fillMaxWidth()) {
                    listOf("Listen", "Library", "Search").forEachIndexed { index, label ->
                        val selected = tab == index
                        Box(
                            Modifier
                                .weight(1f)
                                .clickable { onTab(index) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = LyraType.meta, color = if (selected) LyraColors.brass else LyraColors.ash)
                        }
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    activeId: String?,
    onBack: () -> Unit,
    onPlay: (List<dev.lyra.player.model.Track>, Int) -> Unit,
    onRemove: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text("Close", style = LyraType.meta, modifier = Modifier.clickable(onClick = onBack))
                    Text("Delete", style = LyraType.meta, color = LyraColors.danger, modifier = Modifier.clickable { confirm = true })
                }
                Text(playlist.name, style = LyraType.title, modifier = Modifier.padding(top = 12.dp))
                Text("${playlist.tracks.size} songs", style = LyraType.meta, modifier = Modifier.padding(top = 4.dp))
                if (playlist.tracks.isNotEmpty()) {
                    QuietButton("Play", { onPlay(playlist.tracks, 0) }, Modifier.padding(top = 16.dp))
                }
            }
        }
        items(playlist.tracks.size) { index ->
            val track = playlist.tracks[index]
            TrackRow(track, track.id == activeId, { onPlay(playlist.tracks, index) }, trailing = {
                Text(
                    "Remove",
                    style = LyraType.meta,
                    modifier = Modifier.clickable { onRemove(track.id) }.padding(8.dp),
                )
            })
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = LyraColors.paperRaised,
            title = { Text("Delete ${playlist.name}?", style = LyraType.album) },
            text = { Text("The songs stay on this device.", style = LyraType.body) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onDelete) {
                    Text("Delete", color = LyraColors.danger)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false }) {
                    Text("Cancel", color = LyraColors.ash)
                }
            },
        )
    }
    }
}

@Composable
private fun Missing(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text("Close", style = LyraType.meta, modifier = Modifier.clickable(onClick = onBack))
        Text(text, style = LyraType.body, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit, onClearAudio: () -> Unit, onClearLyrics: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audio by remember { mutableStateOf("…") }
    var lyrics by remember { mutableStateOf("…") }
    fun refresh() {
        scope.launch {
            val graph = (context.applicationContext as LyraApp).graph
            val sizes = withContext(Dispatchers.IO) {
                StreamCache.bytes(context) to graph.lyrics.cacheBytes()
            }
            audio = formatBytes(sizes.first)
            lyrics = formatBytes(sizes.second)
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(22.dp)) {
        Text("Close", style = LyraType.meta, modifier = Modifier.clickable(onClick = onBack))
        Text("Lyra", style = LyraType.display, modifier = Modifier.padding(top = 18.dp))
        Text("Version ${dev.lyra.player.BuildConfig.VERSION_NAME}", style = LyraType.meta, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Plays music stored on this phone. Search also streams tracks artists upload to Audius, and Creative Commons recordings from the Internet Archive.",
            style = LyraType.body,
            color = LyraColors.ash,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            "Synced lyrics come from LRCLIB when a match exists, or from an .lrc file you import. Audius audio is cached only when the artist allows downloads.",
            style = LyraType.body,
            color = LyraColors.ash,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text("Audio cache $audio", style = LyraType.meta, modifier = Modifier.padding(top = 22.dp))
        Text("Lyrics cache $lyrics", style = LyraType.meta, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 16.dp)) {
            QuietButton("Clear audio cache", onClick = {
                onClearAudio()
                refresh()
            })
        }
        Row(Modifier.padding(top = 10.dp)) {
            QuietButton("Clear lyrics cache", onClick = {
                onClearLyrics()
                refresh()
            })
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}
