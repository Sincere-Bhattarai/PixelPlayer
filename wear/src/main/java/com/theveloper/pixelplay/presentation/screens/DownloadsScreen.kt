package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.radialBackgroundBrush
import com.theveloper.pixelplay.presentation.viewmodel.WearDownloadsViewModel

/**
 * Screen showing songs stored locally on the watch.
 * Tapping a song starts local ExoPlayer playback.
 */
@Composable
fun DownloadsScreen(
    onSongClick: (songId: String) -> Unit = {},
    viewModel: WearDownloadsViewModel = hiltViewModel(),
) {
    val localSongs by viewModel.localSongs.collectAsState()
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    val background = palette.radialBackgroundBrush()

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
                    text = "On Watch",
                    style = MaterialTheme.typography.title3,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            if (localSongs.isEmpty()) {
                item {
                    Text(
                        text = "No songs on watch",
                        style = MaterialTheme.typography.body2,
                        color = palette.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            } else {
                items(localSongs.size) { index ->
                    val song = localSongs[index]
                    Chip(
                        label = {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = if (song.artist.isNotEmpty()) {
                            {
                                Text(
                                    text = song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = palette.textSecondary.copy(alpha = 0.78f),
                                )
                            }
                        } else null,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            viewModel.playLocalSong(song.songId)
                            onSongClick(song.songId)
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = palette.chipContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
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
