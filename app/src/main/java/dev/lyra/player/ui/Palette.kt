package dev.lyra.player.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

suspend fun paletteColor(context: Context, artwork: String): Color? = withContext(Dispatchers.IO) {
    val result = runCatching {
        context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(artwork)
                .size(64)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .build(),
        )
    }.getOrNull()
    val bitmap = (result as? SuccessResult)?.drawable
        ?.let { drawable ->
            if (drawable is android.graphics.drawable.BitmapDrawable) drawable.bitmap else null
        } ?: return@withContext null
    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
    val swatches = listOfNotNull(
        palette.vibrantSwatch,
        palette.mutedSwatch,
        palette.darkVibrantSwatch,
        palette.lightMutedSwatch,
        palette.dominantSwatch,
    )
    swatches.firstOrNull { usable(Color(it.rgb)) }?.rgb?.let(::Color)
}

private fun usable(color: Color): Boolean {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    val maxChannel = max(color.red, max(color.green, color.blue))
    val minChannel = minOf(color.red, color.green, color.blue)
    return luminance in 0.22f..0.78f && (maxChannel - minChannel) > 0.08f
}
