package dev.lyra.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lyra.player.model.AlbumGroup
import dev.lyra.player.model.MusicIndex
import dev.lyra.player.model.SavedSession
import dev.lyra.player.model.Track
import java.time.LocalTime

@Composable
fun HomeScreen(
    index: MusicIndex,
    granted: Boolean,
    trending: List<Track>,
    resume: SavedSession?,
    activeId: String?,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onAbout: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenAlbum: (AlbumGroup) -> Unit,
    onResume: (SavedSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp)) {
                Text("LYRA", style = LyraType.wordmark, modifier = Modifier.clickable(onClick = onAbout))
                Text(greeting, style = LyraType.display, modifier = Modifier.padding(top = 14.dp))
            }
        }
        if (!granted) {
            item {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Allow access to the music stored on this device. Streaming still works without it.",
                        style = LyraType.body,
                        color = LyraColors.ash,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuietButton("Allow access", onAllow)
                        QuietButton("Settings", onOpenSettings)
                    }
                }
            }
        }
        val session = resume
        if (session != null && session.tracks.isNotEmpty() && activeId == null) {
            item {
                SectionLabel("Continue")
                TrackRow(
                    track = session.tracks[session.index.coerceIn(0, session.tracks.lastIndex)],
                    active = false,
                    onClick = { onResume(session) },
                )
            }
        }
        if (index.recentAlbums.isNotEmpty()) {
            item { SectionLabel("On this phone") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(index.recentAlbums, key = { it.key }) { album ->
                        Column(
                            Modifier
                                .width(132.dp)
                                .clickable { onOpenAlbum(album) },
                        ) {
                            Cover(album.artworkUri, album.title, 132.dp)
                            Text(
                                album.title,
                                style = LyraType.album,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                album.artist,
                                style = LyraType.meta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (index.recent.isNotEmpty()) {
            item { SectionLabel("Recently added") }
            items(index.recent.take(25), key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    active = track.id == activeId,
                    onClick = { onPlay(index.recent, index.recent.indexOf(track).coerceAtLeast(0)) },
                )
            }
        } else if (granted) {
            item {
                Text(
                    "No music files yet. Add songs to this device, or search Audius and the Internet Archive.",
                    style = LyraType.body,
                    color = LyraColors.ash,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
        if (trending.isNotEmpty()) {
            item { SectionLabel("Free on Audius") }
            items(trending, key = { "trend-${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    active = track.id == activeId,
                    onClick = { onPlay(trending, trending.indexOf(track).coerceAtLeast(0)) },
                )
            }
        }
    }
}
