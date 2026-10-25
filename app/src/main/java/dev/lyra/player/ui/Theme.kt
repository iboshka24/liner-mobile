package dev.lyra.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.lyra.player.R

object LyraColors {
    val paper = Color(0xFF12110E)
    val paperRaised = Color(0xFF1C1A16)
    val ivory = Color(0xFFF4F0E6)
    val ash = Color(0xFF9A9488)
    val faint = Color(0xFF5C574E)
    val line = Color(0xFF2E2B26)
    val brass = Color(0xFFD7A441)
    val danger = Color(0xFFD4654F)
}

private val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

private val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

object LyraType {
    val wordmark = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 3.2.sp,
        color = LyraColors.ivory,
    )
    val display = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
        color = LyraColors.ivory,
    )
    val title = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
        color = LyraColors.ivory,
    )
    val album = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = LyraColors.ivory,
    )
    val body = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = LyraColors.ivory,
    )
    val bodyMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = LyraColors.ivory,
    )
    val meta = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = LyraColors.ash,
    )
    val label = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = LyraColors.ash,
    )
    val lyricActive = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = LyraColors.ivory,
    )
    val lyricNear = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
    val lyricFar = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
}

private val Scheme = darkColorScheme(
    primary = LyraColors.brass,
    onPrimary = LyraColors.paper,
    secondary = LyraColors.ivory,
    onSecondary = LyraColors.paper,
    background = LyraColors.paper,
    onBackground = LyraColors.ivory,
    surface = LyraColors.paperRaised,
    onSurface = LyraColors.ivory,
    surfaceVariant = LyraColors.paperRaised,
    onSurfaceVariant = LyraColors.ash,
    outline = LyraColors.line,
    error = LyraColors.danger,
    onError = LyraColors.ivory,
)

private val Typography = Typography(
    headlineLarge = LyraType.display,
    headlineMedium = LyraType.title,
    titleLarge = LyraType.album,
    titleMedium = LyraType.bodyMedium,
    bodyLarge = LyraType.body,
    bodyMedium = LyraType.meta,
    labelSmall = LyraType.label,
)

@Composable
fun LyraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
