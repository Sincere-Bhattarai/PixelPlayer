package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.presentation.viewmodel.BrowseUiState
import com.theveloper.pixelplay.presentation.viewmodel.WearBrowseViewModel
import com.theveloper.pixelplay.shared.WearBrowseRequest
import com.theveloper.pixelplay.shared.WearLibraryItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh

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
) {
    val uiState by viewModel.uiState.collectAsState()

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

    val background = Brush.radialGradient(
        colors = listOf(
            Color(0xFF6C3AD8),
            Color(0xFF2C1858),
            Color(0xFF130B23),
        ),
    )

    when (val state = uiState) {
        is BrowseUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    indicatorColor = Color(0xFFE1D5FF),
                )
            }
        }

        is BrowseUiState.Error -> {
            val columnState = rememberResponsiveColumnState()
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
                columnState = columnState,
            ) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.title3,
                        color = Color(0xFFF4EEFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.body2,
                        color = Color(0xFFFFB7C5),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                item {
                    Chip(
                        label = { Text("Retry", color = Color(0xFFF4EEFF)) },
                        icon = {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Retry",
                                tint = Color(0xFFE1D5FF),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { viewModel.refresh() },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = Color(0xFFD8CEF3).copy(alpha = 0.18f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }

        is BrowseUiState.Success -> {
            val columnState = rememberResponsiveColumnState()
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
                columnState = columnState,
            ) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.title3,
                        color = Color(0xFFF4EEFF),
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
                            color = Color(0xFFE1D5FF).copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                } else {
                    items(state.items.size) { index ->
                        val song = state.items[index]
                        SongChip(
                            song = song,
                            onClick = {
                                viewModel.playFromContext(
                                    songId = song.id,
                                    contextType = contextType,
                                    contextId = playbackContextId,
                                )
                                onSongPlayed()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongChip(
    song: WearLibraryItem,
    onClick: () -> Unit,
) {
    Chip(
        label = {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFF4EEFF),
            )
        },
        secondaryLabel = if (song.subtitle.isNotEmpty()) {
            {
                Text(
                    text = song.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFE1D5FF).copy(alpha = 0.7f),
                )
            }
        } else null,
        icon = {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFFE1D5FF),
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(0xFFD8CEF3).copy(alpha = 0.14f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
