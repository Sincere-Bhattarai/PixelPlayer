package com.theveloper.pixelplay.presentation.screens

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.ButtonDefaults as M3ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.HorizontalPageIndicator as M3HorizontalPageIndicator
import androidx.wear.compose.material3.Icon as M3Icon
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.shapes.RoundedStarShape
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.radialBackgroundBrush
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import com.theveloper.pixelplay.shared.WearPlayerState
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun PlayerScreen(
    onBrowseClick: () -> Unit = {},
    onVolumeClick: () -> Unit = {},
    onOutputClick: () -> Unit = {},
    onQueueClick: () -> Unit = onBrowseClick,
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsState()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsState()
    val isWatchOutputSelected by viewModel.isWatchOutputSelected.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()

    PlayerContent(
        state = state,
        albumArt = albumArt,
        isPhoneConnected = isPhoneConnected,
        isWatchOutputSelected = isWatchOutputSelected,
        onTogglePlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        onBrowseClick = onBrowseClick,
        onVolumeClick = onVolumeClick,
        onOutputClick = onOutputClick,
        onQueueClick = onQueueClick,
    )
}

@Composable
private fun PlayerContent(
    state: WearPlayerState,
    albumArt: Bitmap?,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onBrowseClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onOutputClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val background = palette.radialBackgroundBrush()

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var mainPageQueueReveal by remember { mutableFloatStateOf(0f) }
    val hidePageIndicator = pagerState.currentPage == 1 && mainPageQueueReveal > 0.05f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> {
                    AlbumArtPage(
                        state = state,
                        albumArt = albumArt,
                        onTap = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                    )
                }

                1 -> {
                    MainPlayerPage(
                        state = state,
                        isPhoneConnected = isPhoneConnected,
                        isWatchOutputSelected = isWatchOutputSelected,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleFavorite = onToggleFavorite,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onQueueClick = onQueueClick,
                        onQueueShortcutRevealChanged = { mainPageQueueReveal = it },
                    )
                }

                else -> {
                    UtilityPage(
                        enabled = true,
                        onBrowseClick = onBrowseClick,
                        onVolumeClick = onVolumeClick,
                        onOutputClick = onOutputClick,
                    )
                }
            }
        }

        if (!hidePageIndicator) {
            M3HorizontalPageIndicator(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .zIndex(6f),
                selectedColor = palette.textPrimary,
                unselectedColor = palette.textPrimary.copy(alpha = 0.52f),
                backgroundColor = Color.Transparent,
            )
        }

        if (pagerState.currentPage != 0) {
            WearTopTimeText(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(5f),
                color = palette.textPrimary,
            )
        }
    }
}

@Composable
private fun AlbumArtPage(
    state: WearPlayerState,
    albumArt: Bitmap?,
    onTap: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val textColors = remember(albumArt, palette.textPrimary, palette.textSecondary) {
        deriveAlbumOverlayTextColors(
            albumArt = albumArt,
            fallbackPrimary = palette.textPrimary,
            fallbackSecondary = palette.textSecondary,
        )
    }
    val contrastOverlay = remember(albumArt, textColors) {
        deriveAlbumContrastOverlay(
            albumArt = albumArt,
            textColors = textColors,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.radialBackgroundBrush()),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.78f to Color.Transparent,
                            0.94f to Color.Black.copy(alpha = 0.52f),
                            1f to Color.Black.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.32f),
                            0.14f to Color.Black.copy(alpha = 0.08f),
                            0.24f to Color.Transparent,
                            0.68f to Color.Transparent,
                            0.86f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to contrastOverlay.topColor.copy(alpha = contrastOverlay.topAlpha),
                            contrastOverlay.topFadeEnd to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            contrastOverlay.bottomFadeStart to Color.Transparent,
                            1f to contrastOverlay.bottomColor.copy(alpha = contrastOverlay.bottomAlpha),
                        ),
                    ),
                ),
        )

        LargeAlbumClockText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .zIndex(5f),
            color = textColors.clock,
            shadow = textColors.clockShadow,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.songTitle.ifEmpty { "No song playing" },
                style = MaterialTheme.typography.title2.copy(
                    shadow = textColors.bottomShadow,
                ),
                fontWeight = FontWeight.Bold,
                color = textColors.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.artistName.ifEmpty { "Connect phone playback" },
                style = MaterialTheme.typography.body1.copy(
                    shadow = textColors.bottomShadow,
                ),
                color = textColors.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun LargeAlbumClockText(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    shadow: Shadow? = null,
) {
    val displayTime by produceState(initialValue = "--:--") {
        val formatter = DateTimeFormatter.ofPattern("H:mm")
        while (true) {
            value = LocalTime.now().format(formatter)
            delay(1000L)
        }
    }
    val gSansFlex = remember {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(650),
                    FontVariation.width(146f),
                    FontVariation.Setting("ROND", 56f),
                    FontVariation.Setting("XTRA", 520f),
                    FontVariation.Setting("YOPQ", 90f),
                    FontVariation.Setting("YTLC", 505f),
                ),
            ),
        )
    }

    Text(
        text = displayTime,
        color = color,
        fontFamily = gSansFlex,
        fontWeight = FontWeight(760),
        fontSize = 26.sp,
        lineHeight = 26.sp,
        style = if (shadow != null) TextStyle(shadow = shadow) else TextStyle.Default,
        modifier = modifier,
    )
}

private data class AlbumOverlayTextColors(
    val clock: Color,
    val title: Color,
    val artist: Color,
    val clockShadow: Shadow,
    val bottomShadow: Shadow,
)

private data class AlbumContrastOverlay(
    val topColor: Color,
    val topAlpha: Float,
    val topFadeEnd: Float,
    val bottomColor: Color,
    val bottomAlpha: Float,
    val bottomFadeStart: Float,
)

private fun deriveAlbumOverlayTextColors(
    albumArt: Bitmap?,
    fallbackPrimary: Color,
    fallbackSecondary: Color,
): AlbumOverlayTextColors {
    if (albumArt == null || albumArt.width <= 0 || albumArt.height <= 0) {
        val defaultShadow = Shadow(
            color = Color.Black.copy(alpha = 0.56f),
            offset = Offset(0f, 1.6f),
            blurRadius = 5f,
        )
        return AlbumOverlayTextColors(
            clock = fallbackPrimary,
            title = fallbackPrimary,
            artist = fallbackSecondary.copy(alpha = 0.92f),
            clockShadow = defaultShadow,
            bottomShadow = defaultShadow,
        )
    }

    val topBg = sampleRegionAverageColor(albumArt, startYFraction = 0f, endYFraction = 0.24f)
    val bottomBg = sampleRegionAverageColor(albumArt, startYFraction = 0.66f, endYFraction = 1f)

    val preferredClock = deriveClockTintFromAlbumArt(albumArt, fallbackPrimary)
    val clockColor = deriveReadableTintedColor(
        preferred = preferredClock,
        background = topBg,
        minContrast = 4.8,
        tintStrength = 0.34f,
    )
    val titleColor = deriveReadableTintedColor(
        preferred = fallbackPrimary.copy(alpha = 0.99f),
        background = bottomBg,
        minContrast = 5.2,
        tintStrength = 0.18f,
    )
    val artistColor = deriveReadableTintedColor(
        preferred = fallbackSecondary.copy(alpha = 0.96f),
        background = bottomBg,
        minContrast = 4.4,
        tintStrength = 0.22f,
    ).copy(alpha = 0.97f)

    val clockShadow = if (clockColor.luminance() < 0.5f) {
        Shadow(
            color = Color.White.copy(alpha = 0.36f),
            offset = Offset(0f, 1.2f),
            blurRadius = 4f,
        )
    } else {
        Shadow(
            color = Color.Black.copy(alpha = 0.58f),
            offset = Offset(0f, 1.6f),
            blurRadius = 5f,
        )
    }
    val bottomShadow = if (titleColor.luminance() < 0.5f) {
        Shadow(
            color = Color.White.copy(alpha = 0.30f),
            offset = Offset(0f, 1.2f),
            blurRadius = 4f,
        )
    } else {
        Shadow(
            color = Color.Black.copy(alpha = 0.55f),
            offset = Offset(0f, 1.6f),
            blurRadius = 5f,
        )
    }

    return AlbumOverlayTextColors(
        clock = clockColor,
        title = titleColor,
        artist = artistColor,
        clockShadow = clockShadow,
        bottomShadow = bottomShadow,
    )
}

private fun deriveAlbumContrastOverlay(
    albumArt: Bitmap?,
    textColors: AlbumOverlayTextColors,
): AlbumContrastOverlay {
    if (albumArt == null || albumArt.width <= 0 || albumArt.height <= 0) {
        return AlbumContrastOverlay(
            topColor = Color.Black,
            topAlpha = 0.30f,
            topFadeEnd = 0.30f,
            bottomColor = Color.Black,
            bottomAlpha = 0.36f,
            bottomFadeStart = 0.64f,
        )
    }

    val topBg = sampleRegionAverageColor(albumArt, startYFraction = 0f, endYFraction = 0.24f)
    val bottomBg = sampleRegionAverageColor(albumArt, startYFraction = 0.62f, endYFraction = 1f)

    val topBase = if (textColors.clock.luminance() > 0.5f) Color.Black else Color.White
    val topAlpha = solveScrimAlphaForContrast(
        textColor = textColors.clock,
        backgroundColor = topBg,
        scrimBaseColor = topBase,
        minContrast = 6.0,
        extraHeadroom = 0.10f,
    )

    val bottomBase = if (textColors.title.luminance() > 0.5f) Color.Black else Color.White
    val bottomTitleAlpha = solveScrimAlphaForContrast(
        textColor = textColors.title,
        backgroundColor = bottomBg,
        scrimBaseColor = bottomBase,
        minContrast = 6.4,
        extraHeadroom = 0.10f,
    )
    val bottomArtistAlpha = solveScrimAlphaForContrast(
        textColor = textColors.artist,
        backgroundColor = bottomBg,
        scrimBaseColor = bottomBase,
        minContrast = 5.1,
        extraHeadroom = 0.08f,
    )

    return AlbumContrastOverlay(
        topColor = topBase,
        topAlpha = topAlpha,
        topFadeEnd = 0.33f,
        bottomColor = bottomBase,
        bottomAlpha = max(bottomTitleAlpha, bottomArtistAlpha),
        bottomFadeStart = 0.56f,
    )
}

private fun solveScrimAlphaForContrast(
    textColor: Color,
    backgroundColor: Color,
    scrimBaseColor: Color,
    minContrast: Double,
    extraHeadroom: Float,
): Float {
    val opaqueBg = toOpaqueArgb(backgroundColor)
    val textArgb = textColor.toArgb()
    val currentContrast = ColorUtils.calculateContrast(textArgb, opaqueBg)
    if (currentContrast >= minContrast) {
        return 0f
    }

    var alpha = 0f
    while (alpha <= 0.84f) {
        val scrimArgb = ColorUtils.setAlphaComponent(
            scrimBaseColor.toArgb(),
            (alpha * 255f).toInt().coerceIn(0, 255),
        )
        val compositedBg = ColorUtils.compositeColors(scrimArgb, opaqueBg)
        val contrast = ColorUtils.calculateContrast(textArgb, compositedBg)
        if (contrast >= minContrast) {
            return (alpha + extraHeadroom).coerceIn(0f, 0.84f)
        }
        alpha += 0.03f
    }

    return 0.84f
}

private fun toOpaqueArgb(color: Color): Int {
    val argb = color.toArgb()
    return if (android.graphics.Color.alpha(argb) >= 255) {
        argb
    } else {
        ColorUtils.compositeColors(argb, Color.Black.toArgb())
    }
}

private fun deriveReadableTintedColor(
    preferred: Color,
    background: Color,
    minContrast: Double,
    tintStrength: Float,
): Color {
    val lightBase = Color(0xFFF7F7F7)
    val darkBase = Color(0xFF111111)

    val lightContrast = ColorUtils.calculateContrast(lightBase.toArgb(), background.toArgb())
    val darkContrast = ColorUtils.calculateContrast(darkBase.toArgb(), background.toArgb())
    val highContrastBase = if (lightContrast >= darkContrast) lightBase else darkBase

    val preferredContrast = ColorUtils.calculateContrast(preferred.toArgb(), background.toArgb())
    if (preferredContrast >= minContrast) return preferred

    val clampedTintStrength = tintStrength.coerceIn(0f, 0.5f)
    val tintSteps = floatArrayOf(
        clampedTintStrength,
        clampedTintStrength * 0.72f,
        clampedTintStrength * 0.46f,
        clampedTintStrength * 0.24f,
        0f,
    )

    tintSteps.forEach { blend ->
        val candidate = androidx.compose.ui.graphics.lerp(highContrastBase, preferred, blend)
        val contrast = ColorUtils.calculateContrast(candidate.toArgb(), background.toArgb())
        if (contrast >= minContrast) {
            return candidate
        }
    }

    return highContrastBase
}

private fun sampleRegionAverageColor(
    albumArt: Bitmap,
    startYFraction: Float,
    endYFraction: Float,
): Color {
    val width = albumArt.width
    val height = albumArt.height
    val yStart = (height * startYFraction).toInt().coerceIn(0, height - 1)
    val yEnd = (height * endYFraction).toInt().coerceIn(yStart + 1, height)
    val step = (min(width, (yEnd - yStart).coerceAtLeast(1)) / 24).coerceAtLeast(1)

    var redSum = 0L
    var greenSum = 0L
    var blueSum = 0L
    var count = 0L

    var y = yStart
    while (y < yEnd) {
        var x = 0
        while (x < width) {
            val pixel = albumArt.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha >= 28) {
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r + g + b > 30) {
                    redSum += r
                    greenSum += g
                    blueSum += b
                    count++
                }
            }
            x += step
        }
        y += step
    }

    if (count == 0L) return Color.Black
    return Color(
        android.graphics.Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    )
}

private fun deriveClockTintFromAlbumArt(albumArt: Bitmap?, fallback: Color): Color {
    if (albumArt == null || albumArt.width <= 0 || albumArt.height <= 0) return fallback

    val width = albumArt.width
    val height = albumArt.height
    val step = (min(width, height) / 28).coerceAtLeast(1)

    var redSum = 0L
    var greenSum = 0L
    var blueSum = 0L
    var count = 0L

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = albumArt.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha >= 28) {
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r + g + b > 30) {
                    redSum += r
                    greenSum += g
                    blueSum += b
                    count++
                }
            }
            x += step
        }
        y += step
    }

    if (count == 0L) return fallback

    val avgColor = android.graphics.Color.rgb(
        (redSum / count).toInt(),
        (greenSum / count).toInt(),
        (blueSum / count).toInt(),
    )
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(avgColor, hsl)
    hsl[1] = max(0.42f, hsl[1]).coerceAtMost(0.92f)
    hsl[2] = max(0.68f, hsl[2]).coerceAtMost(0.90f)

    var tinted = Color(ColorUtils.HSLToColor(hsl))
    val lum = tinted.luminance()
    tinted = when {
        lum < 0.60f -> androidx.compose.ui.graphics.lerp(tinted, Color.White, 0.32f)
        lum > 0.92f -> androidx.compose.ui.graphics.lerp(tinted, Color.White, 0.08f)
        else -> tinted
    }
    return tinted.copy(alpha = 0.98f)
}

@Composable
private fun MainPlayerPage(
    state: WearPlayerState,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onQueueClick: () -> Unit,
    onQueueShortcutRevealChanged: (Float) -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState(
        contentPadding = {
            PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 2.dp,
                bottom = 20.dp,
            )
        },
    )

    val livePositionMs by rememberLivePositionMs(state)
    val trackProgressTarget = if (state.totalDurationMs > 0L) {
        (livePositionMs.toFloat() / state.totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackProgress by animateFloatAsState(
        targetValue = trackProgressTarget,
        animationSpec = tween(durationMillis = 280),
        label = "trackProgress",
    )
    val queueShortcutRevealTarget by remember(columnState.state) {
        derivedStateOf {
            val layoutInfo = columnState.state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf 0f
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0f

            val firstVisibleIndex = visibleItems.minOf { it.index }
            val lastVisibleIndex = visibleItems.maxOf { it.index }
            val visibleCount = (lastVisibleIndex - firstVisibleIndex + 1).coerceAtLeast(1)
            val maxFirstVisibleIndex = (totalItems - visibleCount).coerceAtLeast(0)
            if (maxFirstVisibleIndex == 0) return@derivedStateOf 0f

            if (lastVisibleIndex >= totalItems - 1) {
                1f
            } else {
                (firstVisibleIndex.toFloat() / maxFirstVisibleIndex.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val queueShortcutReveal by animateFloatAsState(
        targetValue = queueShortcutRevealTarget,
        animationSpec = tween(durationMillis = 220),
        label = "queueShortcutReveal",
    )
    LaunchedEffect(queueShortcutReveal) {
        onQueueShortcutRevealChanged(queueShortcutReveal)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                HeaderBlock(
                    state = state,
                    isPhoneConnected = isPhoneConnected,
                    isWatchOutputSelected = isWatchOutputSelected,
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                MainControlsRow(
                    isPlaying = state.isPlaying,
                    isEmpty = state.isEmpty,
                    enabled = if (isWatchOutputSelected) !state.isEmpty else isPhoneConnected,
                    trackProgress = trackProgress,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
            }

            if (!isWatchOutputSelected) {
                item {
                    SecondaryControlsRow(
                        isFavorite = state.isFavorite,
                        isShuffleEnabled = state.isShuffleEnabled,
                        repeatMode = state.repeatMode,
                        enabled = isPhoneConnected && !state.isEmpty,
                        onToggleFavorite = onToggleFavorite,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        favoriteActiveColor = palette.favoriteActive,
                        shuffleActiveColor = palette.shuffleActive,
                        repeatActiveColor = palette.repeatActive,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(50.dp)) }
        }

        BottomQueueShortcut(
            revealProgress = queueShortcutReveal,
            enabled = isPhoneConnected,
            onClick = onQueueClick,
            modifier = Modifier
                .align(Alignment.BottomCenter),
        )

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun rememberLivePositionMs(state: WearPlayerState): androidx.compose.runtime.State<Long> {
    val safeDuration = state.totalDurationMs.coerceAtLeast(0L)
    val safeAnchorPosition = state.currentPositionMs.coerceIn(0L, safeDuration)
    val positionKey = remember(
        state.songId,
        safeAnchorPosition,
        safeDuration,
        state.isPlaying,
    ) {
        "${state.songId}|$safeAnchorPosition|$safeDuration|${state.isPlaying}"
    }
    return produceState(
        initialValue = safeAnchorPosition,
        key1 = positionKey,
    ) {
        value = safeAnchorPosition
        if (!state.isPlaying || safeDuration <= 0L) {
            return@produceState
        }

        val startElapsedRealtime = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startElapsedRealtime
            val next = (safeAnchorPosition + elapsed).coerceIn(0L, safeDuration)
            value = next
            if (next >= safeDuration) break
            delay(250L)
        }
    }
}

@Composable
private fun HeaderBlock(
    state: WearPlayerState,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean = false,
) {
    val palette = LocalWearPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.songTitle.ifEmpty { "Song name" },
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = when {
                isWatchOutputSelected -> {
                    val artistAlbum = buildList {
                        if (state.artistName.isNotBlank()) add(state.artistName)
                        if (state.albumName.isNotBlank()) add(state.albumName)
                    }.joinToString(" · ")
                    if (artistAlbum.isNotBlank()) artistAlbum else "On watch"
                }
                !isPhoneConnected -> "No phone"
                state.artistName.isNotEmpty() -> state.artistName
                state.isEmpty -> "Waiting playback"
                else -> "Artist name"
            },
            style = MaterialTheme.typography.body1,
            color = when {
                isWatchOutputSelected -> palette.textSecondary
                !isPhoneConnected -> palette.textError
                else -> palette.textSecondary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isWatchOutputSelected) {
            Text(
                text = "On watch",
                style = MaterialTheme.typography.caption3,
                color = palette.shuffleActive.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MainControlsRow(
    isPlaying: Boolean,
    isEmpty: Boolean,
    enabled: Boolean,
    trackProgress: Float,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlattenedControlButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            enabled = enabled,
            onClick = onPrevious,
            width = 44.dp,
            height = 54.dp,
        )

        Spacer(modifier = Modifier.width(10.dp))

        CenterPlayButton(
            isPlaying = isPlaying,
            enabled = enabled && !isEmpty,
            trackProgress = trackProgress,
            onClick = onTogglePlayPause,
        )

        Spacer(modifier = Modifier.width(10.dp))

        FlattenedControlButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            enabled = enabled,
            onClick = onNext,
            width = 44.dp,
            height = 54.dp,
        )
    }
}

@Composable
private fun FlattenedControlButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
) {
    val palette = LocalWearPalette.current
    val container = if (enabled) palette.controlContainer else palette.controlDisabledContainer
    val tint = if (enabled) palette.controlContent else palette.controlDisabledContent

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun CenterPlayButton(
    isPlaying: Boolean,
    enabled: Boolean,
    trackProgress: Float,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current

    val animatedCurve by animateFloatAsState(
        targetValue = if (isPlaying) 0.08f else 0.00f,
        animationSpec = spring(),
        label = "playStarCurve",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "playStarSpin")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 13800,
                easing = LinearEasing,
            ),
        ),
        label = "playStarRotationInfinite",
    )
    val animatedRotation = if (isPlaying) spinningRotation else 0f
    val animatedSize by animateDpAsState(
        targetValue = if (isPlaying) 60.dp else 56.dp,
        animationSpec = spring(),
        label = "playButtonSize",
    )
    val container by animateColorAsState(
        targetValue = if (enabled) palette.controlContainer else palette.controlDisabledContainer,
        animationSpec = spring(),
        label = "playContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (enabled) palette.controlContent else palette.controlDisabledContent,
        animationSpec = spring(),
        label = "playTint",
    )

    val ringProgress = trackProgress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier.size(animatedSize + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val arcTopLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = palette.chipContainer.copy(alpha = 0.62f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = palette.controlContainer.copy(alpha = if (enabled) 1f else 0.95f),
                startAngle = -90f,
                sweepAngle = 360f * ringProgress,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Box(
            modifier = Modifier
                .size(animatedSize)
                .clip(
                    RoundedStarShape(
                        sides = 8,
                        curve = animatedCurve.toDouble(),
                        rotation = animatedRotation,
                    )
                )
                .background(container)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = tint,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun SecondaryControlsRow(
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    enabled: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    favoriteActiveColor: Color,
    shuffleActiveColor: Color,
    repeatActiveColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        SecondaryActionSlot(lower = false) {
            SecondaryActionButton(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                enabled = enabled,
                active = isFavorite,
                activeColor = favoriteActiveColor,
                onClick = onToggleFavorite,
                contentDescription = "Like",
            )
        }
        SecondaryActionSlot(lower = true) {
            SecondaryActionButton(
                icon = Icons.Rounded.Shuffle,
                enabled = enabled,
                active = isShuffleEnabled,
                activeColor = shuffleActiveColor,
                onClick = onToggleShuffle,
                contentDescription = "Shuffle",
            )
        }
        SecondaryActionSlot(lower = false) {
            SecondaryActionButton(
                icon = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                enabled = enabled,
                active = repeatMode != 0,
                activeColor = repeatActiveColor,
                onClick = onCycleRepeat,
                contentDescription = "Repeat",
            )
        }
    }
}

@Composable
private fun SecondaryActionButton(
    icon: ImageVector,
    enabled: Boolean,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    contentDescription: String,
) {
    val palette = LocalWearPalette.current
    val activeContainerColor = activeColor.copy(alpha = 0.84f)
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> palette.controlDisabledContainer
            active -> activeContainerColor
            else -> palette.chipContainer
        },
        animationSpec = spring(),
        label = "secondaryContainer",
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> palette.controlDisabledContent
            active -> if (activeContainerColor.luminance() > 0.52f) Color.Black else Color.White
            else -> palette.chipContent
        },
        animationSpec = spring(),
        label = "secondaryTint",
    )

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun SecondaryActionSlot(
    lower: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(width = 48.dp, height = 48.dp),
        contentAlignment = if (lower) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        content()
    }
}

@Composable
private fun BottomQueueShortcut(
    revealProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val clampedProgress = revealProgress.coerceIn(0f, 1f)
    if (clampedProgress <= 0.01f) return

    val containerColor by animateColorAsState(
        targetValue = if (enabled) palette.controlContainer else palette.controlDisabledContainer,
        animationSpec = spring(),
        label = "queueShortcutContainer",
    )
    val iconColor by animateColorAsState(
        targetValue = if (enabled) palette.controlContent else palette.controlDisabledContent,
        animationSpec = spring(),
        label = "queueShortcutIcon",
    )

    val edgeHeight = lerp(16.dp, 66.dp, clampedProgress)
    val iconSize = lerp(14.dp, 24.dp, clampedProgress)
    val containerAlpha = (clampedProgress * 1.1f).coerceIn(0f, 1f)

    EdgeButton(
        onClick = onClick,
        enabled = enabled && clampedProgress > 0.65f,
        buttonSize = EdgeButtonSize.Small,
        colors = M3ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = iconColor,
            disabledContainerColor = palette.controlDisabledContainer,
            disabledContentColor = palette.controlDisabledContent,
        ),
        modifier = modifier
            //.height(edgeHeight)
            .graphicsLayer {
                alpha = containerAlpha
                scaleY = 0.55f + (0.45f * clampedProgress)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        ,
    ) {
        M3Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "Queue",
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun UtilityPage(
    enabled: Boolean,
    onBrowseClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onOutputClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val maxSafeWidth = maxWidth - 10.dp
        val middleWidth = (maxWidth * 0.84f).let { width ->
            if (width > maxSafeWidth) maxSafeWidth else width
        }
        val sideWidth = (middleWidth * 0.82f).let { width ->
            if (width < 128.dp) 128.dp else width
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 30.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UtilityPillButton(
                icon = Icons.Rounded.PhoneAndroid,
                label = "Device",
                enabled = enabled,
                width = sideWidth,
                onClick = onOutputClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            UtilityPillButton(
                icon = Icons.Rounded.LibraryMusic,
                label = "Library",
                enabled = enabled,
                width = middleWidth,
                onClick = onBrowseClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            UtilityPillButton(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = "Volume",
                enabled = enabled,
                width = sideWidth,
                onClick = onVolumeClick,
            )
        }
    }
}

@Composable
private fun UtilityPillButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val container = if (enabled) palette.chipContainer else palette.controlDisabledContainer
    val tint = if (enabled) palette.chipContent else palette.controlDisabledContent

    Row(
        modifier = Modifier
            .width(width)
            .height(46.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.button,
            maxLines = 1,
        )
    }
}
