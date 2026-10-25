package dev.lyra.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.lyra.player.model.AlbumGroup
import dev.lyra.player.model.Track

@Composable
fun SearchBody(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    note: String?,
    tracks: List<Track>,
    albums: List<AlbumGroup>,
    audius: List<Track>,
    archive: List<Track>,
    activeId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenAlbum: (AlbumGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                Text("Search", style = LyraType.display)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    textStyle = LyraType.body,
                    cursorBrush = SolidColor(LyraColors.brass),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.padding(top = 18.dp),
                    decorationBox = { inner ->
                        Column {
                            Box {
                                if (query.isEmpty()) {
                                    Text("Songs, albums, people", style = LyraType.body, color = LyraColors.faint)
                                }
                                inner()
                            }
                            Hairline(Modifier.padding(top = 8.dp))
                        }
                    },
                )
                if (searching) {
                    Text("Searching…", style = LyraType.meta, modifier = Modifier.padding(top = 12.dp))
                }
                if (note != null) {
                    Text(note, style = LyraType.body, color = LyraColors.ivory, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        val typed = query.trim().length >= 2
        if (typed && tracks.isEmpty() && albums.isEmpty() && audius.isEmpty() && archive.isEmpty() && !searching && note == null) {
            item {
                Text(
                    "Nothing matched.",
                    style = LyraType.body,
                    color = LyraColors.ash,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionLabel("Albums") }
            items(albums, key = { "album-${it.key}" }) { album ->
                TrackRow(track = album.asRow(), active = false, onClick = { onOpenAlbum(album) })
            }
        }
        if (tracks.isNotEmpty()) {
            item { SectionLabel("On this phone") }
            items(tracks, key = { it.id }) { track ->
                TrackRow(track, track.id == activeId, onClick = { onPlay(tracks, tracks.indexOf(track).coerceAtLeast(0)) })
            }
        }
        if (audius.isNotEmpty()) {
            item { SectionLabel("Audius") }
            items(audius, key = { it.id }) { track ->
                TrackRow(track, track.id == activeId, onClick = { onPlay(audius, audius.indexOf(track).coerceAtLeast(0)) })
            }
        }
        if (archive.isNotEmpty()) {
            item { SectionLabel("Internet Archive") }
            items(archive, key = { it.id }) { track ->
                TrackRow(track, track.id == activeId, onClick = { onPlay(listOf(track), 0) })
            }
        }
    }
}

private fun AlbumGroup.asRow() = Track(
    id = "album-row-$key",
    title = title,
    artist = artist,
    artworkUri = artworkUri,
    uri = "",
)
