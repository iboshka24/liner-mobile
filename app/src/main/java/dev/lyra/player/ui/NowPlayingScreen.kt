package dev.lyra.player.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.media3.common.Player
import dev.lyra.player.LyraApp
import dev.lyra.player.R
import dev.lyra.player.data.userMessage
import dev.lyra.player.model.formatClock
import dev.lyra.player.lyrics.LyricLine
import dev.lyra.player.lyrics.LyricResult
import dev.lyra.player.lyrics.LyricsSync
import dev.lyra.player.model.Track
import dev.lyra.player.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

@Composable
fun NowPlayingScreen(
    player: PlayerViewModel,
    onClose: () -> Unit,
    onSaveQueue: (String) -> Unit,
) {
    val track = player.track
    val accent by animateColorAsState(player.accent, tween(450), label = "accent")
    var lyricsOpen by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    if (track == null) {
        Box(Modifier.fillMaxSize().background(LyraColors.paper), contentAlignment = Alignment.Center) {
            Text("Nothing playing", style = LyraType.body, color = LyraColors.ash)
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(LyraColors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp),
    ) {
        ShareRow(track, onClose)
        if (lyricsOpen) {
            CompactHeader(track, accent)
            LyricsPanel(track, player, accent, Modifier.weight(1f).padding(top = 8.dp))
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroArt(track)
                Text(track.title, style = LyraType.title, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
                Text(track.artist, style = LyraType.body, color = LyraColors.ash, modifier = Modifier.padding(top = 4.dp))
                if (track.album.isNotBlank()) {
                    Text(track.album, style = LyraType.meta, modifier = Modifier.padding(top = 2.dp))
                }
                QueuePreview(player)
            }
        }
        Seek(player, accent, Modifier.padding(top = 8.dp))
        Transport(player, accent)
        ModeRow(
            lyricsOpen = lyricsOpen,
            shuffle = player.shuffle,
            repeatMode = player.repeatMode,
            accent = accent,
            onLyrics = { lyricsOpen = !lyricsOpen },
            onShuffle = player::toggleShuffle,
            onRepeat = player::cycleRepeat,
            onSave = { naming = true },
        )
    }
    if (naming) {
        NameDialog("Save queue", "Save", { naming = false }) {
            onSaveQueue(it)
            naming = false
        }
    }
}

@Composable
private fun ShareRow(track: Track, onClose: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_down), contentDescription = "Close", tint = LyraColors.ivory)
        }
        Text(track.sourceLabel().uppercase(), style = LyraType.label)
        Text(
            "Share",
            style = LyraType.meta,
            color = LyraColors.ivory,
            modifier = Modifier
                .clickable {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
                .padding(8.dp),
        )
    }
}

@Composable
private fun HeroArt(track: Track) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth, 360.dp)
        Cover(track.artworkUri, track.title, edge)
    }
}

@Composable
private fun CompactHeader(track: Track, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(track.artworkUri, track.title, 64.dp)
        Column(Modifier.weight(1f)) {
            Text(track.title, style = LyraType.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = accent)
            Text(track.artist, style = LyraType.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Seek(player: PlayerViewModel, accent: Color, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0f) }
    var shown by remember { mutableLongStateOf(0L) }
    val draggingState = rememberUpdatedState(dragging)
    val playingState = rememberUpdatedState(player.isPlaying)
    LaunchedEffect(player.track?.id) {
        while (isActive) {
            if (!draggingState.value) shown = player.position()
            if (playingState.value && !draggingState.value) {
                withFrameMillis { }
            } else {
                delay(120)
            }
        }
    }
    val duration = player.durationMs.coerceAtLeast(0L)
    val value = if (dragging) drag else shown.toFloat()
    Column(modifier) {
        Slider(
            value = if (duration <= 0L) 0f else value.coerceIn(0f, duration.toFloat()),
            onValueChange = {
                dragging = true
                drag = it
            },
            onValueChangeFinished = {
                player.seek(drag.toLong())
                dragging = false
            },
            enabled = duration > 0L,
            valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
            colors = SliderDefaults.colors(
                thumbColor = LyraColors.ivory,
                activeTrackColor = accent,
                inactiveTrackColor = LyraColors.line,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(if (dragging) drag.toLong() else shown), style = LyraType.meta)
            Text(formatClock(duration), style = LyraType.meta)
        }
    }
}

@Composable
private fun Transport(player: PlayerViewModel, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = player::previous) {
            Icon(painterResource(R.drawable.ic_prev), contentDescription = "Previous", tint = LyraColors.ivory)
        }
        IconButton(onClick = player::toggle, modifier = Modifier.size(72.dp)) {
            Icon(
                painterResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (player.isPlaying) "Pause" else "Play",
                tint = accent,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = player::next) {
            Icon(painterResource(R.drawable.ic_next), contentDescription = "Next", tint = LyraColors.ivory)
        }
    }
}

@Composable
private fun ModeRow(
    lyricsOpen: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    accent: Color,
    onLyrics: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSave: () -> Unit,
) {
    val repeat = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> "Repeat all"
        Player.REPEAT_MODE_ONE -> "Repeat one"
        else -> "Repeat"
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ModeText("Shuffle", shuffle, accent, onShuffle)
        ModeText(if (lyricsOpen) "Hide lyrics" else "Lyrics", lyricsOpen, accent, onLyrics)
        ModeText(repeat, repeatMode != Player.REPEAT_MODE_OFF, accent, onRepeat)
        ModeText("Save", false, accent, onSave)
    }
}

@Composable
private fun ModeText(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        style = LyraType.meta,
        color = if (selected) accent else LyraColors.ash,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

@Composable
private fun QueuePreview(player: PlayerViewModel) {
    val queue = player.queue
    if (queue.size < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(if (player.shuffle) "SHUFFLE IS ON" else "UP NEXT", style = LyraType.label)
        val upcoming = if (player.shuffle) queue.take(6) else queue.drop(player.index + 1).take(6)
        upcoming.forEach { item ->
            val at = queue.indexOfFirst { it.id == item.id }
            Text(
                "${item.title}  ·  ${item.artist}",
                style = LyraType.meta,
                color = LyraColors.ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (at >= 0) player.seekQueue(at) }
                    .padding(vertical = 8.dp),
            )
        }
        if (!player.shuffle && queue.size - player.index - 1 > 6) {
            Text("${queue.size - player.index - 1 - 6} more", style = LyraType.meta)
        }
    }
}

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object Instrumental : LyricsUi
    data class Synced(val lines: List<LyricLine>) : LyricsUi
    data class Plain(val text: String) : LyricsUi
    data object Missing : LyricsUi
    data class Failed(val message: String) : LyricsUi
}

@Composable
private fun LyricsPanel(track: Track, player: PlayerViewModel, accent: Color, modifier: Modifier) {
    val context = LocalContext.current
    val graph = (context.applicationContext as LyraApp).graph
    var attempt by remember(track.id) { mutableIntStateOf(0) }
    var state by remember(track.id) { mutableStateOf<LyricsUi>(LyricsUi.Loading) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (total < 200_000) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, minOf(read, 200_000 - total))
                total += read
            }
            out.toString(Charsets.UTF_8.name())
        }
        if (!text.isNullOrBlank()) {
            graph.lyrics.saveImport(track, text)
            attempt += 1
        }
    }
    LaunchedEffect(track.id, attempt) {
        state = LyricsUi.Loading
        state = try {
            val result = withContext(Dispatchers.IO) { graph.lyrics.load(track) }
            when (result) {
                LyricResult.Instrumental -> LyricsUi.Instrumental
                is LyricResult.Synced -> LyricsUi.Synced(result.lines)
                is LyricResult.Plain -> LyricsUi.Plain(result.text)
                LyricResult.Missing -> LyricsUi.Missing
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LyricsUi.Failed(error.userMessage())
        }
    }
    Column(modifier.fillMaxSize()) {
        when (val current = state) {
            LyricsUi.Loading -> Text("Looking up lyrics…", style = LyraType.meta, modifier = Modifier.padding(top = 12.dp))
            LyricsUi.Instrumental -> Text("This recording is instrumental.", style = LyraType.body, color = LyraColors.ash)
            is LyricsUi.Plain -> Text(current.text, style = LyraType.lyricNear, color = LyraColors.ivory)
            LyricsUi.Missing -> Text("No lyrics matched this recording.", style = LyraType.body, color = LyraColors.ash)
            is LyricsUi.Failed -> Text(current.message, style = LyraType.body, color = LyraColors.ivory)
            is LyricsUi.Synced -> SyncedLyrics(
                lines = current.lines,
                position = player::position,
                playing = player.isPlaying,
                accent = accent,
                onSeek = player::seek,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Import .lrc",
                style = LyraType.meta,
                color = LyraColors.ivory,
                modifier = Modifier.clickable { picker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
            )
            if (state is LyricsUi.Failed) {
                Text("Retry", style = LyraType.meta, color = accent, modifier = Modifier.clickable { attempt += 1 })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    position: () -> Long,
    playing: Boolean,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var active by remember(lines) { mutableIntStateOf(-1) }
    val playingState = rememberUpdatedState(playing)
    LaunchedEffect(lines) {
        var last = Int.MIN_VALUE
        while (isActive) {
            val index = LyricsSync.lineIndex(lines, position())
            if (index != last) {
                active = index
                last = index
                if (index >= 0 && !listState.isScrollInProgress) {
                    listState.animateScrollToItem((index - 2).coerceAtLeast(0))
                }
            }
            if (playingState.value) delay(48) else delay(160)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index-${line.startMs}" }) { index, line ->
            val distance = if (active < 0) 2 else abs(index - active)
            if (index == active) {
                ActiveLine(line, position, playing, accent, Modifier.clickable { onSeek(line.startMs) })
            } else {
                Text(
                    line.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.startMs) }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = if (distance <= 1) LyraType.lyricNear else LyraType.lyricFar,
                    color = if (distance <= 1) LyraColors.ash else LyraColors.faint,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveLine(
    line: LyricLine,
    position: () -> Long,
    playing: Boolean,
    accent: Color,
    modifier: Modifier,
) {
    var pos by remember { mutableLongStateOf(position()) }
    val playingState = rememberUpdatedState(playing)
    LaunchedEffect(line) {
        while (isActive) {
            pos = position()
            if (playingState.value) {
                withFrameMillis { }
            } else {
                delay(80)
            }
        }
    }
    val state = LyricsSync.wordState(line, pos)
    val words = line.words.ifEmpty { listOf(dev.lyra.player.lyrics.LyricWord(line.text, line.startMs, line.endMs)) }
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        words.forEachIndexed { index, word ->
            val color = when {
                index < state.index -> LyraColors.ivory.copy(alpha = 0.78f)
                index == state.index -> accent
                else -> LyraColors.faint
            }
            Text(
                word.text,
                color = color,
                style = LyraType.lyricActive,
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .drawBehind {
                        if (index == state.index) {
                            val y = size.height - 1.dp.toPx()
                            drawLine(
                                color = accent,
                                start = Offset(0f, y),
                                end = Offset(size.width * state.progress.coerceIn(0f, 1f), y),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                    },
            )
        }
    }
}
