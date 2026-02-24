package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import com.theveloper.pixelplay.shared.WearPlayerState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
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

@Composable
fun PlayerScreen(
    onBrowseClick: () -> Unit = {},
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
) {
    val columnState = rememberResponsiveColumnState()
    val background = Brush.radialGradient(
        colors = listOf(
            Color(0xFF6C3AD8),
            Color(0xFF2C1858),
            Color(0xFF130B23),
        ),
    )

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 6.dp),
        columnState = columnState,
    ) {
        item {
            Text(
                text = clock,
                style = MaterialTheme.typography.body1,
                color = Color(0xFFF4EEFF),
                modifier = Modifier.padding(top = 0.dp),
            )
        }

        item {
            HeaderBlock(
                state = state,
                isPhoneConnected = isPhoneConnected,
            )
        }

        item { Spacer(modifier = Modifier.height(2.dp)) }

        item {
            MainControlsRow(
                isPlaying = state.isPlaying,
                isEmpty = state.isEmpty,
                enabled = isPhoneConnected,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )
        }

        item { Spacer(modifier = Modifier.height(6.dp)) }

        item {
            SecondaryControlsRow(
                isFavorite = state.isFavorite,
                isShuffleEnabled = state.isShuffleEnabled,
                repeatMode = state.repeatMode,
                enabled = isPhoneConnected && !state.isEmpty,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
        }

        item { Spacer(modifier = Modifier.height(2.dp)) }

        item {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 42.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFD8CEF3).copy(alpha = 0.25f))
                    .clickable(onClick = onBrowseClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = "Library",
                    tint = Color(0xFFE8E0FF),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (!isPhoneConnected) {
            item {
                Text(
                    text = "Phone disconnected",
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFFFFB7C5),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock(
    state: WearPlayerState,
    isPhoneConnected: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.songTitle.ifEmpty { "Song name" },
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF4ECFF),
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
            color = if (isPhoneConnected) Color(0xFFE1D5FF) else Color(0xFFFFC2CF),
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
            width = 48.dp,
            height = 40.dp,
        )

        Spacer(modifier = Modifier.width(4.dp))

        CenterPlayButton(
            isPlaying = isPlaying,
            enabled = enabled && !isEmpty,
            onClick = onTogglePlayPause,
        )

        Spacer(modifier = Modifier.width(4.dp))

        FlattenedControlButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            enabled = enabled,
            onClick = onNext,
            width = 48.dp,
            height = 40.dp,
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
    val container = if (enabled) Color(0xFFE6DBFF).copy(alpha = 0.95f) else Color(0xFF9185AC)
    val tint = if (enabled) Color(0xFF2C0C62) else Color(0xFF615C70)
    val shape = CircleShape

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
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
private fun CenterPlayButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val animatedCorner by animateDpAsState(
        targetValue = if (isPlaying) 18.dp else 32.dp,
        animationSpec = spring(),
        label = "playCorner",
    )
    val animatedWidth by animateDpAsState(
        targetValue = if (isPlaying) 60.dp else 56.dp,
        animationSpec = spring(),
        label = "playWidth",
    )
    val animatedHeight by animateDpAsState(
        targetValue = if (isPlaying) 50.dp else 56.dp,
        animationSpec = spring(),
        label = "playHeight",
    )
    val container by animateColorAsState(
        targetValue = if (enabled) Color(0xFFF2EAFF) else Color(0xFFA69ABF),
        animationSpec = spring(),
        label = "playContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (enabled) Color(0xFF320E69) else Color(0xFF635D74),
        animationSpec = spring(),
        label = "playTint",
    )

    Box(
        modifier = Modifier
            .size(width = animatedWidth, height = animatedHeight)
            .clip(RoundedCornerShape(animatedCorner))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
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
            activeColor = Color(0xFFF1608E),
            onClick = onToggleFavorite,
            contentDescription = "Like",
        )
        SecondaryActionButton(
            icon = Icons.Rounded.Shuffle,
            enabled = enabled,
            active = isShuffleEnabled,
            activeColor = Color(0xFF44CDC4),
            onClick = onToggleShuffle,
            contentDescription = "Shuffle",
        )
        SecondaryActionButton(
            icon = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            enabled = enabled,
            active = repeatMode != 0,
            activeColor = Color(0xFF70A6FF),
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
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF8F84A9).copy(alpha = 0.45f)
            active -> activeColor.copy(alpha = 0.36f)
            else -> Color(0xFFD8CEF3).copy(alpha = 0.25f)
        },
        animationSpec = spring(),
        label = "secondaryContainer",
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF6A647C)
            active -> Color(0xFFF5F0FF)
            else -> Color(0xFFE8E0FF)
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
