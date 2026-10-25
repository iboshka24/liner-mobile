package dev.lyra.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.lyra.player.model.formatClock
import dev.lyra.player.model.Track
import dev.lyra.player.model.TrackSource

private val ArtShape = RoundedCornerShape(4.dp)

fun Track.sourceLabel(): String = when (source) {
    TrackSource.LOCAL -> "On device"
    TrackSource.AUDIUS -> "Audius"
    TrackSource.ARCHIVE -> "Internet Archive"
}

@Composable
fun Cover(
    artwork: String?,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var failed by remember(artwork) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(ArtShape)
            .background(LyraColors.paperRaised)
            .border(1.dp, LyraColors.line, ArtShape),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork.isNullOrBlank() || failed) {
            Text(
                text = title.trim().take(1).uppercase().ifBlank { "·" },
                style = LyraType.album,
                color = LyraColors.brass,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artwork)
                    .size(size.toPxApprox())
                    .crossfade(false)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
            )
        }
    }
}

private fun Dp.toPxApprox(): Int = (value * 3).toInt().coerceIn(48, 720)

@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(track.artworkUri, track.title, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = LyraType.bodyMedium,
                color = if (active) LyraColors.brass else LyraColors.ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = LyraType.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0) {
            Text(formatClock(track.durationMs), style = LyraType.meta)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = LyraType.label,
        modifier = modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LyraType.bodyMedium,
        color = LyraColors.ivory,
        modifier = modifier
            .border(1.dp, LyraColors.brass, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LyraColors.line))
}

@Composable
fun MessageLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LyraType.meta,
        color = LyraColors.ivory,
        modifier = modifier
            .fillMaxWidth()
            .background(LyraColors.paperRaised)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}
