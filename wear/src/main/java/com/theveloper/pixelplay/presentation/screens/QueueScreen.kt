package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.radialBackgroundBrush
import com.theveloper.pixelplay.presentation.viewmodel.BrowseUiState
import com.theveloper.pixelplay.presentation.viewmodel.WearBrowseViewModel
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.WearSleepTimerMode
import com.theveloper.pixelplay.shared.WearBrowseRequest
import com.theveloper.pixelplay.shared.WearLibraryItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.MusicNote

@Composable
fun QueueScreen(
    onTimerClick: () -> Unit,
    viewModel: WearPlayerViewModel = hiltViewModel(),
    browseViewModel: WearBrowseViewModel = hiltViewModel(),
) {
    val palette = LocalWearPalette.current
    val playerState by viewModel.playerState.collectAsState()
    val uiState by browseViewModel.uiState.collectAsState()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsState()
    val isWatchOutputSelected by viewModel.isWatchOutputSelected.collectAsState()
    val timerState by viewModel.sleepTimerUiState.collectAsState()

    val controlsEnabled = isPhoneConnected && !isWatchOutputSelected
    val columnState = rememberResponsiveColumnState(
        contentPadding = {
            PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 6.dp,
                bottom = 20.dp,
            )
        },
    )

    LaunchedEffect(controlsEnabled) {
        if (controlsEnabled) {
            browseViewModel.loadItems(WearBrowseRequest.QUEUE)
        }
    }
    LaunchedEffect(playerState.songId, controlsEnabled) {
        if (controlsEnabled) {
            browseViewModel.loadItems(WearBrowseRequest.QUEUE)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.radialBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            QueueShortcutsRow(
                shuffleEnabled = playerState.isShuffleEnabled,
                repeatMode = playerState.repeatMode,
                timerEnabled = timerState.mode != WearSleepTimerMode.OFF,
                enabled = controlsEnabled,
                onShuffleClick = {
                    viewModel.toggleShuffle()
                    browseViewModel.refresh()
                },
                onTimerClick = onTimerClick,
                onRepeatClick = {
                    viewModel.cycleRepeat()
                    browseViewModel.refresh()
                },
            )

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ScalingLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    columnState = columnState,
                ) {
                    if (!controlsEnabled) {
                        item {
                            Text(
                                text = if (!isPhoneConnected) {
                                    "Connect your phone"
                                } else {
                                    "Switch output to Phone"
                                },
                                style = MaterialTheme.typography.body2,
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                            )
                        }
                        item { Spacer(modifier = Modifier.height(2.dp)) }
                    } else {
                        when (val state = uiState) {
                            is BrowseUiState.Loading -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(indicatorColor = palette.textSecondary)
                                    }
                                }
                            }

                            is BrowseUiState.Error -> {
                                item {
                                    Text(
                                        text = "Queue unavailable",
                                        style = MaterialTheme.typography.title3,
                                        color = palette.textPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                    )
                                }
                                item {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.caption2,
                                        color = palette.textError,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    )
                                }
                            }

                            is BrowseUiState.Success -> {
                                if (state.items.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Queue is empty",
                                            style = MaterialTheme.typography.body2,
                                            color = palette.textSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 20.dp),
                                        )
                                    }
                                } else {
                                    items(state.items.size) { index ->
                                        val item = state.items[index]
                                        QueueSongChip(
                                            song = item,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                item.id.toIntOrNull()?.let { queueIndex ->
                                                    browseViewModel.playQueueIndex(queueIndex)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }

                AlwaysOnScalingPositionIndicator(
                    listState = columnState.state,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = (-36).dp),
                    color = palette.textPrimary,
                )
            }
        }

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun QueueShortcutsRow(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    timerEnabled: Boolean,
    enabled: Boolean,
    onShuffleClick: () -> Unit,
    onTimerClick: () -> Unit,
    onRepeatClick: () -> Unit,
) {
    val palette = LocalWearPalette.current

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
        QueueShortcutSlot(lower = true) {
            QueueShortcutButton(
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                active = shuffleEnabled,
                enabled = enabled,
                activeColor = palette.shuffleActive,
                onClick = onShuffleClick,
            )
        }
        QueueShortcutSlot(lower = false) {
            QueueShortcutButton(
                icon = Icons.Rounded.Timer,
                contentDescription = "Timer",
                active = timerEnabled,
                enabled = enabled,
                activeColor = palette.favoriteActive,
                onClick = onTimerClick,
            )
        }
        QueueShortcutSlot(lower = true) {
            QueueShortcutButton(
                icon = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                active = repeatMode != 0,
                enabled = enabled,
                activeColor = palette.repeatActive,
                onClick = onRepeatClick,
            )
        }
    }
}

@Composable
private fun QueueShortcutButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    enabled: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> palette.controlDisabledContainer
            active -> activeColor.copy(alpha = 0.86f)
            else -> palette.chipContainer
        },
        animationSpec = spring(),
        label = "queueShortcutContainer",
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> palette.controlDisabledContent
            active -> if (activeColor.luminance() > 0.52f) Color.Black else Color.White
            else -> palette.chipContent
        },
        animationSpec = spring(),
        label = "queueShortcutTint",
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
private fun QueueShortcutSlot(
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
private fun QueueSongChip(
    song: WearLibraryItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val isPlayingItem = song.subtitle.startsWith("Playing")

    Chip(
        label = {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = palette.textPrimary,
            )
        },
        secondaryLabel = {
            Text(
                text = song.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = palette.textSecondary.copy(alpha = 0.78f),
            )
        },
        icon = {
            Icon(
                imageVector = if (isPlayingItem) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = if (isPlayingItem) palette.shuffleActive else palette.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        enabled = enabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (isPlayingItem) {
                palette.controlContainer.copy(alpha = 0.30f)
            } else {
                palette.chipContainer
            },
            contentColor = palette.chipContent,
            secondaryContentColor = palette.textSecondary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .background(Color.Transparent, RoundedCornerShape(14.dp)),
    )
}
