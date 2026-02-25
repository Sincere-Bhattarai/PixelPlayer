package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                                onDownloadClick = {
                                    downloadsViewModel.requestDownload(song.id)
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
    onDownloadClick: () -> Unit = {},
) {
    val palette = LocalWearPalette.current
    val isTransferring = transfer != null &&
        transfer.status == WearTransferProgress.STATUS_TRANSFERRING

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
        onClick = {
            if (!isDownloaded && !isTransferring) {
                onDownloadClick()
            }
            onClick()
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = palette.chipContainer,
            contentColor = palette.chipContent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
