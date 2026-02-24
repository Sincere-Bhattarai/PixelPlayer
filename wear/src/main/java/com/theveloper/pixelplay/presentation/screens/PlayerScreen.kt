package com.theveloper.pixelplay.presentation.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.presentation.shapes.RoundedStarShape
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import com.theveloper.pixelplay.shared.WearPlayerState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBrowseClick: () -> Unit = {},
    onVolumeClick: () -> Unit = {},
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsState()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsState()
    val clock = rememberClockLabel()

    PlayerContent(
        clock = clock,
        state = state,
        isPhoneConnected = isPhoneConnected,
        onTogglePlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        onBrowseClick = onBrowseClick,
        onVolumeClick = onVolumeClick,
    )
}

@Composable
private fun PlayerContent(
    clock: String,
    state: WearPlayerState,
    isPhoneConnected: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onBrowseClick: () -> Unit,
    onVolumeClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val background = Brush.radialGradient(
        colors = listOf(
            palette.gradientTop,
            palette.gradientMiddle,
            palette.gradientBottom,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { 2 })
    val pageIndicatorState: PageIndicatorState = remember(pagerState) {
        object : PageIndicatorState {
            override val pageOffset: Float
                get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int
                get() = pagerState.currentPage
            override val pageCount: Int
                get() = pagerState.pageCount
        }
    }

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
                    MainPlayerPage(
                        clock = clock,
                        state = state,
                        isPhoneConnected = isPhoneConnected,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleFavorite = onToggleFavorite,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                    )
                }

                else -> {
                    UtilityPage(
                        enabled = true,
                        onBrowseClick = onBrowseClick,
                        onVolumeClick = onVolumeClick,
                    )
                }
            }
        }

        HorizontalPageIndicator(
            pageIndicatorState = pageIndicatorState,
            selectedColor = palette.textPrimary,
            unselectedColor = palette.textPrimary.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun MainPlayerPage(
    clock: String,
    state: WearPlayerState,
    isPhoneConnected: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
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

    val trackProgressTarget = if (state.totalDurationMs > 0L) {
        (state.currentPositionMs.toFloat() / state.totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackProgress by animateFloatAsState(
        targetValue = trackProgressTarget,
        animationSpec = tween(durationMillis = 280),
        label = "trackProgress",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            columnState = columnState,
        ) {
            item {
                Text(
                    text = clock,
                    style = MaterialTheme.typography.body1.copy(fontSize = 20.sp),
                    color = palette.textPrimary,
                )
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }

            item {
                HeaderBlock(
                    state = state,
                    isPhoneConnected = isPhoneConnected,
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                MainControlsRow(
                    isPlaying = state.isPlaying,
                    isEmpty = state.isEmpty,
                    enabled = isPhoneConnected,
                    trackProgress = trackProgress,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

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

            if (!isPhoneConnected) {
                item {
                    Text(
                        text = "Phone disconnected",
                        style = MaterialTheme.typography.body2,
                        color = palette.textError,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        }

        PositionIndicator(
            scalingLazyListState = columnState.state,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun HeaderBlock(
    state: WearPlayerState,
    isPhoneConnected: Boolean,
) {
    val palette = LocalWearPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.songTitle.ifEmpty { "Song name" },
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = when {
                !isPhoneConnected -> "No phone"
                state.artistName.isNotEmpty() -> state.artistName
                state.isEmpty -> "Waiting playback"
                else -> "Artist name"
            },
            style = MaterialTheme.typography.body1,
            color = if (isPhoneConnected) palette.textSecondary else palette.textError,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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

        Spacer(modifier = Modifier.width(4.dp))

        CenterPlayButton(
            isPlaying = isPlaying,
            enabled = enabled && !isEmpty,
            trackProgress = trackProgress,
            onClick = onTogglePlayPause,
        )

        Spacer(modifier = Modifier.width(4.dp))

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
                color = palette.controlContainer.copy(alpha = if (enabled) 1f else 0.45f),
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
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryActionButton(
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            enabled = enabled,
            active = isFavorite,
            activeColor = favoriteActiveColor,
            onClick = onToggleFavorite,
            contentDescription = "Like",
        )
        SecondaryActionButton(
            icon = Icons.Rounded.Shuffle,
            enabled = enabled,
            active = isShuffleEnabled,
            activeColor = shuffleActiveColor,
            onClick = onToggleShuffle,
            contentDescription = "Shuffle",
        )
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
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> palette.controlDisabledContainer.copy(alpha = 0.42f)
            active -> activeColor.copy(alpha = 0.36f)
            else -> palette.chipContainer
        },
        animationSpec = spring(),
        label = "secondaryContainer",
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> palette.controlDisabledContent
            active -> palette.textPrimary
            else -> palette.chipContent
        },
        animationSpec = spring(),
        label = "secondaryTint",
    )

    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 42.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun UtilityPage(
    enabled: Boolean,
    onBrowseClick: () -> Unit,
    onVolumeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UtilityPillButton(
            icon = Icons.Rounded.LibraryMusic,
            label = "Library",
            enabled = enabled,
            onClick = onBrowseClick,
        )

        Spacer(modifier = Modifier.height(10.dp))

        UtilityPillButton(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            label = "Volume",
            enabled = enabled,
            onClick = onVolumeClick,
        )
    }
}

@Composable
private fun UtilityPillButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val container = if (enabled) palette.chipContainer else palette.controlDisabledContainer.copy(alpha = 0.35f)
    val tint = if (enabled) palette.chipContent else palette.controlDisabledContent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 190.dp)
            .height(50.dp)
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

@Composable
private fun rememberClockLabel(): String {
    val formatter = remember { DateTimeFormatter.ofPattern("H:mm") }
    var value by remember { mutableStateOf(LocalTime.now().format(formatter)) }
    LaunchedEffect(Unit) {
        while (true) {
            value = LocalTime.now().format(formatter)
            delay(15_000L)
        }
    }
    return value
}
