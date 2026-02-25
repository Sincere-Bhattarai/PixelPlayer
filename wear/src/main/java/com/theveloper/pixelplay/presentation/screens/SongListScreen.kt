package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.theveloper.pixelplay.presentation.viewmodel.BrowseUiState
import com.theveloper.pixelplay.presentation.viewmodel.WearBrowseViewModel
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.radialBackgroundBrush
import com.theveloper.pixelplay.shared.WearBrowseRequest
import com.theveloper.pixelplay.shared.WearLibraryItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import com.theveloper.pixelplay.data.TransferState
import com.theveloper.pixelplay.presentation.viewmodel.WearDownloadsViewModel
import com.theveloper.pixelplay.shared.WearTransferProgress

/**
 * Screen showing songs within a specific context (album, artist, playlist, favorites, all songs).
 * Tapping a song triggers playback on the phone with the full context queue.
 */
@Composable
fun SongListScreen(
    browseType: String,
    contextId: String?,
    title: String,
    onSongPlayed: () -> Unit = {},
    viewModel: WearBrowseViewModel = hiltViewModel(),
    downloadsViewModel: WearDownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedIds by downloadsViewModel.downloadedSongIds.collectAsState()
    val activeTransfers by downloadsViewModel.activeTransfers.collectAsState()
    val palette = LocalWearPalette.current
    var selectedSongForMenu by remember { mutableStateOf<WearLibraryItem?>(null) }
    var selectedSongForTransferConfirmation by remember { mutableStateOf<WearLibraryItem?>(null) }

    // Determine the context type for playback (maps browseType to context)
    val contextType = when (browseType) {
        WearBrowseRequest.ALBUM_SONGS -> "album"
        WearBrowseRequest.ARTIST_SONGS -> "artist"
        WearBrowseRequest.PLAYLIST_SONGS -> "playlist"
        WearBrowseRequest.FAVORITES -> "favorites"
        WearBrowseRequest.ALL_SONGS -> "all_songs"
        else -> browseType
    }

    // The actual context ID for playback (null for favorites/all_songs)
    val playbackContextId = when (browseType) {
        WearBrowseRequest.FAVORITES, WearBrowseRequest.ALL_SONGS -> null
        else -> contextId?.takeIf { it != "none" }
    }

    LaunchedEffect(browseType, contextId) {
        viewModel.loadItems(browseType, contextId?.takeIf { it != "none" })
    }

    val background = palette.radialBackgroundBrush()

    when (val state = uiState) {
        is BrowseUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    indicatorColor = palette.textSecondary,
                )

                WearTopTimeText(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(5f),
                    color = palette.textPrimary,
                )
            }
        }

        is BrowseUiState.Error -> {
            val columnState = rememberResponsiveColumnState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    columnState = columnState,
                ) {
                    item { Spacer(modifier = Modifier.height(18.dp)) }

                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.title3,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.body2,
                            color = palette.textError,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    item {
                        Chip(
                            label = { Text("Retry", color = palette.textPrimary) },
                            icon = {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Retry",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = { viewModel.refresh() },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = palette.chipContainer,
                                contentColor = palette.chipContent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }

                AlwaysOnScalingPositionIndicator(
                    listState = columnState.state,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    color = palette.textPrimary,
                )

                WearTopTimeText(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(5f),
                    color = palette.textPrimary,
                )
            }
        }

        is BrowseUiState.Success -> {
            val columnState = rememberResponsiveColumnState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    columnState = columnState,
                ) {
                    item { Spacer(modifier = Modifier.height(18.dp)) }

                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.title3,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                        )
                    }

                    if (state.items.isEmpty()) {
                        item {
                            Text(
                                text = "No songs",
                                style = MaterialTheme.typography.body2,
                                color = palette.textSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    } else {
                        items(state.items.size) { index ->
                            val song = state.items[index]
                            val isDownloaded = downloadedIds.contains(song.id)
                            val transfer = activeTransfers.values.firstOrNull { it.songId == song.id }
                            SongChip(
                                song = song,
                                isDownloaded = isDownloaded,
                                transfer = transfer,
                                onClick = {
                                    viewModel.playFromContext(
                                        songId = song.id,
                                        contextType = contextType,
                                        contextId = playbackContextId,
                                    )
                                    onSongPlayed()
                                },
                                onMenuClick = {
                                    selectedSongForMenu = song
                                },
                            )
                        }
                    }
                }

                AlwaysOnScalingPositionIndicator(
                    listState = columnState.state,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    color = palette.textPrimary,
                )

                WearTopTimeText(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(5f),
                    color = palette.textPrimary,
                )

                val menuSong = selectedSongForMenu
                if (menuSong != null) {
                    val menuSongIsDownloaded = downloadedIds.contains(menuSong.id)
                    val menuSongTransfer = activeTransfers.values.firstOrNull { it.songId == menuSong.id }
                    val menuSongIsTransferring = menuSongTransfer != null &&
                        menuSongTransfer.status == WearTransferProgress.STATUS_TRANSFERRING
                    val menuSongCanSaveToWatch = menuSong.canSaveToWatch

                    SongActionScreen(
                        song = menuSong,
                        isDownloaded = menuSongIsDownloaded,
                        isTransferring = menuSongIsTransferring,
                        canSaveToWatch = menuSongCanSaveToWatch,
                        onDismiss = { selectedSongForMenu = null },
                        onPlayNow = {
                            viewModel.playFromContext(
                                songId = menuSong.id,
                                contextType = contextType,
                                contextId = playbackContextId,
                            )
                            onSongPlayed()
                            selectedSongForMenu = null
                        },
                        onPlayNext = {
                            viewModel.playNextFromContext(
                                songId = menuSong.id,
                                contextType = contextType,
                                contextId = playbackContextId,
                            )
                            selectedSongForMenu = null
                        },
                        onAddToQueue = {
                            viewModel.addToQueueFromContext(
                                songId = menuSong.id,
                                contextType = contextType,
                                contextId = playbackContextId,
                            )
                            selectedSongForMenu = null
                        },
                        onSaveToWatch = {
                            if (menuSongCanSaveToWatch) {
                                selectedSongForMenu = null
                                selectedSongForTransferConfirmation = menuSong
                            } else {
                                selectedSongForMenu = null
                            }
                        },
                    )
                }

                val confirmSong = selectedSongForTransferConfirmation
                if (confirmSong != null) {
                    ConfirmSaveToWatchScreen(
                        song = confirmSong,
                        onDismiss = { selectedSongForTransferConfirmation = null },
                        onConfirm = {
                            downloadsViewModel.requestDownload(confirmSong.id)
                            selectedSongForTransferConfirmation = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SongChip(
    song: WearLibraryItem,
    isDownloaded: Boolean = false,
    transfer: TransferState? = null,
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {},
) {
    val palette = LocalWearPalette.current
    val isTransferring = transfer != null &&
        transfer.status == WearTransferProgress.STATUS_TRANSFERRING

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            label = {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textPrimary,
                )
            },
            secondaryLabel = if (song.subtitle.isNotEmpty()) {
                {
                    Text(
                        text = if (isTransferring) {
                            "${(transfer!!.progress * 100).toInt()}%"
                        } else {
                            song.subtitle
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isTransferring) {
                            palette.shuffleActive.copy(alpha = 0.9f)
                        } else {
                            palette.textSecondary.copy(alpha = 0.78f)
                        },
                    )
                }
            } else null,
            icon = {
                when {
                    isDownloaded -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = palette.shuffleActive,
                        modifier = Modifier.size(18.dp),
                    )
                    isTransferring -> CircularProgressIndicator(
                        indicatorColor = palette.shuffleActive,
                        trackColor = palette.chipContainer,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            onClick = onClick,
            colors = ChipDefaults.chipColors(
                backgroundColor = palette.chipContainer,
                contentColor = palette.chipContent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 40.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .background(
                    color = palette.controlContainer.copy(alpha = 0.36f),
                    shape = CircleShape,
                )
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options",
                tint = palette.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SongActionScreen(
    song: WearLibraryItem,
    isDownloaded: Boolean,
    isTransferring: Boolean,
    canSaveToWatch: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveToWatch: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    val playNowColor = palette.shuffleActive.copy(alpha = 0.38f)
    val playNextColor = palette.repeatActive.copy(alpha = 0.38f)
    val addToQueueColor = palette.controlContainer.copy(alpha = 0.56f)
    val saveToWatchColor = if (!canSaveToWatch || isDownloaded || isTransferring) {
        palette.controlDisabledContainer
    } else {
        palette.favoriteActive.copy(alpha = 0.40f)
    }
    val cancelColor = palette.chipContainer.copy(alpha = 0.50f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.radialBackgroundBrush())
            .zIndex(12f),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = song.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.title3,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            if (song.subtitle.isNotEmpty()) {
                item {
                    Text(
                        text = song.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption2,
                        color = palette.textSecondary.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                    )
                }
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play now",
                    backgroundColor = playNowColor,
                    onClick = onPlayNow,
                )
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.SkipNext,
                    label = "Play next",
                    backgroundColor = playNextColor,
                    onClick = onPlayNext,
                )
            }

            item {
                SongActionChip(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Add to queue",
                    backgroundColor = addToQueueColor,
                    onClick = onAddToQueue,
                )
            }

            if (canSaveToWatch || isDownloaded || isTransferring) {
                item {
                    SongActionChip(
                        icon = Icons.Rounded.Download,
                        label = when {
                            isDownloaded -> "Saved on watch"
                            isTransferring -> "Saving..."
                            else -> "Save to watch"
                        },
                        backgroundColor = saveToWatchColor,
                        enabled = canSaveToWatch && !isDownloaded && !isTransferring,
                        onClick = onSaveToWatch,
                    )
                }
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.Close,
                    label = "Back",
                    backgroundColor = cancelColor,
                    onClick = onDismiss,
                )
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun ConfirmSaveToWatchScreen(
    song: WearLibraryItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    val confirmColor = palette.favoriteActive.copy(alpha = 0.42f)
    val cancelColor = palette.chipContainer.copy(alpha = 0.50f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.radialBackgroundBrush())
            .zIndex(14f),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = song.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                Text(
                    text = "Save this song on watch?",
                    color = palette.textSecondary.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.Download,
                    label = "Confirm save",
                    backgroundColor = confirmColor,
                    onClick = onConfirm,
                )
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.Close,
                    label = "Cancel",
                    backgroundColor = cancelColor,
                    onClick = onDismiss,
                )
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun SongActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val contentColor = if (enabled) {
        if (backgroundColor.luminance() > 0.46f) Color.Black.copy(alpha = 0.86f) else palette.textPrimary
    } else {
        palette.controlDisabledContent
    }

    Chip(
        label = {
            Text(
                text = label,
                color = contentColor,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        enabled = enabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (enabled) backgroundColor
            else palette.controlDisabledContainer,
            contentColor = contentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
