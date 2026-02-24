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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh

/**
 * Generic list screen for albums, artists, or playlists.
 * Loads items from the phone via [WearBrowseViewModel] and displays them.
 * Clicking an item navigates to [SongListScreen] to show the songs within.
 */
@Composable
fun LibraryListScreen(
    browseType: String,
    title: String,
    onItemClick: (item: WearLibraryItem, subBrowseType: String, itemTitle: String) -> Unit,
    viewModel: WearBrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(browseType) {
        viewModel.loadItems(browseType)
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
                            text = "No items",
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
                        val item = state.items[index]
                        LibraryItemChip(
                            item = item,
                            browseType = browseType,
                            onClick = {
                                val subBrowseType = when (browseType) {
                                    WearBrowseRequest.ALBUMS -> WearBrowseRequest.ALBUM_SONGS
                                    WearBrowseRequest.ARTISTS -> WearBrowseRequest.ARTIST_SONGS
                                    WearBrowseRequest.PLAYLISTS -> WearBrowseRequest.PLAYLIST_SONGS
                                    else -> browseType
                                }
                                onItemClick(item, subBrowseType, item.title)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItemChip(
    item: WearLibraryItem,
    browseType: String,
    onClick: () -> Unit,
) {
    val icon = when (browseType) {
        WearBrowseRequest.ALBUMS -> Icons.Rounded.Album
        WearBrowseRequest.ARTISTS -> Icons.Rounded.Person
        WearBrowseRequest.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> Icons.Rounded.Album
    }

    val iconTint = when (browseType) {
        WearBrowseRequest.ALBUMS -> Color(0xFF70A6FF)
        WearBrowseRequest.ARTISTS -> Color(0xFFFFB74D)
        WearBrowseRequest.PLAYLISTS -> Color(0xFF44CDC4)
        else -> Color(0xFFE1D5FF)
    }

    Chip(
        label = {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFF4EEFF),
            )
        },
        secondaryLabel = if (item.subtitle.isNotEmpty()) {
            {
                Text(
                    text = item.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFE1D5FF).copy(alpha = 0.7f),
                )
            }
        } else null,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(0xFFD8CEF3).copy(alpha = 0.14f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
