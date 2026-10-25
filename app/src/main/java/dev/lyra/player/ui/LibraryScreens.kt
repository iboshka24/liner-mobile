package dev.lyra.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lyra.player.model.AlbumGroup
import dev.lyra.player.model.ArtistGroup
import dev.lyra.player.model.MusicIndex
import dev.lyra.player.model.Playlist
import dev.lyra.player.model.Track

@Composable
fun LibraryScreen(
    index: MusicIndex,
    granted: Boolean,
    playlists: List<Playlist>,
    activeId: String?,
    onAllow: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onOpenAlbum: (AlbumGroup) -> Unit,
    onOpenArtist: (ArtistGroup) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String, List<Track>) -> Unit,
    onAddToPlaylist: (String, Track) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf<Track?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    Column(modifier.fillMaxSize()) {
        Text("Library", style = LyraType.display, modifier = Modifier.padding(start = 22.dp, top = 18.dp, end = 22.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            listOf("Songs", "Albums", "Artists", "Playlists").forEachIndexed { i, label ->
                val selected = i == section
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { section = i }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = LyraType.meta, color = if (selected) LyraColors.ivory else LyraColors.ash)
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.45f)
                            .background(if (selected) LyraColors.brass else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(top = 1.dp),
                    )
                }
            }
        }
        if (!granted && section != 3) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The library reads songs stored on this device.", style = LyraType.body, color = LyraColors.ash)
                QuietButton("Allow access", onAllow)
            }
            return
        }
        when (section) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                items(index.tracks, key = { it.id }) { track ->
                    TrackLine(track, track.id == activeId, { onPlay(index.tracks, index.tracks.indexOf(track)) }, onPlayNext) {
                        adding = track
                    }
                }
            }
            1 -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(index.albums, key = { it.key }) { album ->
                    Column(Modifier.clickable { onOpenAlbum(album) }) {
                        Cover(album.artworkUri, album.title, 160.dp, Modifier.fillMaxWidth())
                        Text(album.title, style = LyraType.album, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(album.artist, style = LyraType.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            2 -> LazyColumn(Modifier.fillMaxSize()) {
                items(index.artists, key = { it.name }) { artist ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArtist(artist) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Text(artist.name, style = LyraType.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${artist.tracks.size} songs", style = LyraType.meta)
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuietButton("New playlist", onClick = { adding = Track(id = "", title = "", artist = "", uri = "") })
                        QuietButton("Import M3U", onClick = {
                            importer.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                        })
                    }
                }
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            "Playlists stay on this phone.",
                            style = LyraType.body,
                            color = LyraColors.ash,
                            modifier = Modifier.padding(horizontal = 22.dp),
                        )
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(playlist) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Text(playlist.name, style = LyraType.album)
                        Text("${playlist.tracks.size} songs", style = LyraType.meta)
                    }
                }
            }
        }
    }
    val pending = adding
    if (pending != null) {
        if (pending.id.isEmpty()) {
            NameDialog(
                title = "New playlist",
                confirm = "Create",
                onDismiss = { adding = null },
                onConfirm = {
                    onCreatePlaylist(it, emptyList())
                    adding = null
                },
            )
        } else {
            PlaylistPicker(
                playlists = playlists,
                onDismiss = { adding = null },
                onPick = {
                    onAddToPlaylist(it, pending)
                    adding = null
                },
                onCreate = {
                    onCreatePlaylist(it, listOf(pending))
                    adding = null
                },
            )
        }
    }
}

@Composable
private fun TrackLine(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAdd: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TrackRow(track, active, onClick, trailing = {
        Box {
            Text(
                "···",
                style = LyraType.meta,
                modifier = Modifier
                    .clickable { open = true }
                    .padding(8.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = LyraColors.paperRaised) {
                DropdownMenuItem(
                    text = { Text("Play next", color = LyraColors.ivory) },
                    onClick = { open = false; onPlayNext(track) },
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist", color = LyraColors.ivory) },
                    onClick = { open = false; onAdd() },
                )
            }
        }
    })
}

@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    activeId: String?,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Text("Close", style = LyraType.meta, modifier = Modifier.clickable(onClick = onBack))
                Text(title, style = LyraType.title, modifier = Modifier.padding(top = 12.dp))
                Text(subtitle, style = LyraType.meta, modifier = Modifier.padding(top = 4.dp))
                QuietButton("Play", { onPlay(tracks, 0) }, Modifier.padding(top = 16.dp))
            }
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(track, track.id == activeId, { onPlay(tracks, tracks.indexOf(track).coerceAtLeast(0)) })
        }
    }
}

@Composable
fun NameDialog(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LyraColors.paperRaised,
        title = { Text(title, style = LyraType.album) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                singleLine = true,
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirm, color = LyraColors.brass)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LyraColors.ash) }
        },
    )
}

@Composable
fun PlaylistPicker(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    if (creating) {
        NameDialog("New playlist", "Create", onDismiss) { onCreate(it) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LyraColors.paperRaised,
        title = { Text("Add to playlist", style = LyraType.album) },
        text = {
            Column {
                playlists.forEach { playlist ->
                    Text(
                        playlist.name,
                        style = LyraType.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist.id) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { creating = true }) { Text("New", color = LyraColors.brass) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LyraColors.ash) }
        },
    )
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LyraColors.brass,
    unfocusedBorderColor = LyraColors.line,
    cursorColor = LyraColors.brass,
    focusedTextColor = LyraColors.ivory,
    unfocusedTextColor = LyraColors.ivory,
)
