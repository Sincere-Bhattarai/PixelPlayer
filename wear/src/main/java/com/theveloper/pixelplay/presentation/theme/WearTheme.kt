package com.theveloper.pixelplay.presentation.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors
import kotlin.math.max
import kotlin.math.min

@Immutable
data class WearPalette(
    val gradientTop: Color,
    val gradientMiddle: Color,
    val gradientBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textError: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val controlDisabledContainer: Color,
    val controlDisabledContent: Color,
    val chipContainer: Color,
    val chipContent: Color,
    val favoriteActive: Color,
    val shuffleActive: Color,
    val repeatActive: Color,
)

private val DefaultWearPalette = WearPalette(
    gradientTop = Color(0xFF6C3AD8),
    gradientMiddle = Color(0xFF2C1858),
    gradientBottom = Color(0xFF130B23),
    textPrimary = Color(0xFFF4EEFF),
    textSecondary = Color(0xFFE1D5FF),
    textError = Color(0xFFFFB7C5),
    controlContainer = Color(0xFFE6DBFF).copy(alpha = 0.95f),
    controlContent = Color(0xFF2C0C62),
    controlDisabledContainer = Color(0xFF9185AC),
    controlDisabledContent = Color(0xFF615C70),
    chipContainer = Color(0xFFD8CEF3).copy(alpha = 0.22f),
    chipContent = Color(0xFFE8E0FF),
    favoriteActive = Color(0xFFF1608E),
    shuffleActive = Color(0xFF44CDC4),
    repeatActive = Color(0xFF70A6FF),
)

val LocalWearPalette = staticCompositionLocalOf { DefaultWearPalette }

fun WearPalette.radialBackgroundBrush(): Brush = Brush.radialGradient(
    colorStops = arrayOf(
        0.0f to gradientTop,
        0.56f to gradientMiddle,
        0.82f to gradientBottom,
        1.0f to Color.Black,
    ),
)

@Composable
fun WearPixelPlayTheme(
    albumArt: Bitmap? = null,
    seedColorArgb: Int? = null,
    content: @Composable () -> Unit,
) {
    val palette = remember(albumArt, seedColorArgb) {
        when {
            albumArt != null -> buildPaletteFromAlbumArt(albumArt)
            seedColorArgb != null -> buildPaletteFromSeedColor(Color(seedColorArgb))
            else -> DefaultWearPalette
        }
    }
    val wearColors = remember(palette) {
        Colors(
            primary = palette.controlContainer,
            primaryVariant = lerp(palette.controlContainer, Color.Black, 0.18f),
            secondary = palette.chipContainer,
            secondaryVariant = lerp(palette.chipContainer, Color.Black, 0.24f),
            error = palette.textError,
            onPrimary = palette.controlContent,
            onSecondary = palette.textPrimary,
            onError = Color.Black,
        )
    }

    CompositionLocalProvider(LocalWearPalette provides palette) {
        MaterialTheme(
            colors = wearColors,
            content = content,
        )
    }
}

private fun buildPaletteFromAlbumArt(bitmap: Bitmap): WearPalette {
    val seed = extractSeedColor(bitmap)
    return buildPaletteFromSeedColor(seed)
}

private fun buildPaletteFromSeedColor(seed: Color): WearPalette {
    val seedArgb = seed.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedArgb, hsl)
    hsl[1] = (hsl[1] * 1.18f).coerceIn(0.30f, 0.82f)
    hsl[2] = hsl[2].coerceIn(0.32f, 0.56f)
    val tunedSeed = Color(ColorUtils.HSLToColor(hsl))

    val top = lerp(tunedSeed, Color.Black, 0.30f)
    val middle = lerp(tunedSeed, Color.Black, 0.57f)
    val bottom = lerp(tunedSeed, Color.Black, 0.84f)
    val controlContainer = lerp(tunedSeed, Color.White, 0.80f)
    val controlContent = lerp(tunedSeed, Color.Black, 0.82f)
    val chipContainer = lerp(tunedSeed, Color.White, 0.34f).copy(alpha = 0.24f)

    return WearPalette(
        gradientTop = top,
        gradientMiddle = middle,
        gradientBottom = bottom,
        textPrimary = Color(0xFFF7F2FF),
        textSecondary = Color(0xFFE8DEF8),
        textError = Color(0xFFFFB8C7),
        controlContainer = controlContainer.copy(alpha = 0.96f),
        controlContent = controlContent,
        controlDisabledContainer = lerp(controlContainer, Color.Black, 0.35f).copy(alpha = 0.86f),
        controlDisabledContent = lerp(controlContent, Color.White, 0.32f),
        chipContainer = chipContainer,
        chipContent = Color(0xFFF2EBFF),
        favoriteActive = buildAccentFromSeed(seedArgb, hueShift = 34f),
        shuffleActive = buildAccentFromSeed(seedArgb, hueShift = -72f),
        repeatActive = buildAccentFromSeed(seedArgb, hueShift = -22f),
    )
}

private fun buildAccentFromSeed(seedColor: Int, hueShift: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor, hsl)
    hsl[0] = (hsl[0] + hueShift + 360f) % 360f
    hsl[1] = (hsl[1] * 1.22f).coerceIn(0.46f, 0.92f)
    hsl[2] = (hsl[2] + 0.08f).coerceIn(0.40f, 0.72f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun extractSeedColor(bitmap: Bitmap): Color {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return DefaultWearPalette.gradientTop

    val step = max(1, min(width, height) / 24)
    var redSum = 0L
    var greenSum = 0L
    var blueSum = 0L
    var count = 0L

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel)
            if (alpha >= 28) {
                val red = AndroidColor.red(pixel)
                val green = AndroidColor.green(pixel)
                val blue = AndroidColor.blue(pixel)
                if (red + green + blue > 36) {
                    redSum += red
                    greenSum += green
                    blueSum += blue
                    count++
                }
            }
            x += step
        }
        y += step
    }

    if (count == 0L) return DefaultWearPalette.gradientTop
    val avgColor = AndroidColor.rgb(
        (redSum / count).toInt(),
        (greenSum / count).toInt(),
        (blueSum / count).toInt(),
    )
    return Color(avgColor)
}
