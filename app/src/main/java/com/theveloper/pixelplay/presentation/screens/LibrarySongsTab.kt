package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Songs tab for the library screen with multi-selection support.
 *
 * @param songs The list of songs to display
 * @param isLoading Whether the songs are currently loading
 * @param stablePlayerState Current player state for highlighting playing song
 * @param playerViewModel ViewModel for playback actions
 * @param bottomBarHeight Height of the bottom bar for padding
 * @param onMoreOptionsClick Callback when more options is clicked on a song
 * @param isRefreshing Whether pull-to-refresh is active
 * @param onRefresh Callback for pull-to-refresh
 * @param isSelectionMode Whether multi-selection mode is active
 * @param selectedSongIds Set of currently selected song IDs
 * @param onSongLongPress Callback when a song is long-pressed (activates selection)
 * @param onSongSelectionToggle Callback to toggle selection of a song
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibrarySongsTab(
    songs: ImmutableList<Song>,
    isLoading: Boolean,
    stablePlayerState: StablePlayerState,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    // Multi-selection parameters
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
    val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
    val currentSongId = stablePlayerState.currentSong?.id
    val currentSongListIndex = remember(songs, currentSongId) {
        currentSongId?.let { songId -> songs.indexOfFirst { it.id == songId } } ?: -1
    }
    val locateCurrentSongAction: (() -> Unit)? = remember(currentSongListIndex, listState) {
        if (currentSongListIndex < 0) {
            null
        } else {
            {
                coroutineScope.launch {
                    listState.animateScrollToItem(currentSongListIndex)
                }
            }
        }
    }

    LaunchedEffect(locateCurrentSongAction) {
        registerActionCallback(locateCurrentSongAction)
    }

    LaunchedEffect(currentSongListIndex, songs, isLoading, listState) {
        if (currentSongListIndex < 0 || songs.isEmpty() || isLoading) {
            visibilityCallback(false)
            return@LaunchedEffect
        }

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                false
            } else {
                currentSongListIndex in visibleItems.first().index..visibleItems.last().index
            }
        }
            .distinctUntilChanged()
            .collect { isVisible ->
                visibilityCallback(!isVisible)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            visibilityCallback(false)
            registerActionCallback(null)
        }
    }

    // Handle different loading states
    when {
        isLoading && songs.isEmpty() -> {
            // Initial loading - show skeleton placeholders
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 24.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    )
                    .fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                items(12) { // Show 12 skeleton items
                    EnhancedSongListItem(
                        song = Song.emptySong(),
                        isPlaying = false,
                        isLoading = true,
                        isCurrentSong = false,
                        onMoreOptionsClick = {},
                        onClick = {}
                    )
                }
            }
        }
        !isLoading && songs.isEmpty() -> {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_off_24),
                        contentDescription = "No songs found",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No songs found in your library.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Try rescanning your library in settings if you have music on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        else -> {
            // Songs loaded
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp, bottom = 6.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 26.dp,
                                        topEnd = 26.dp,
                                        bottomStart = PlayerSheetCollapsedCornerRadius,
                                        bottomEnd = PlayerSheetCollapsedCornerRadius
                                    )
                                ),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                        ) {
                            //item(key = "songs_top_spacer") { Spacer(Modifier.height(0.dp)) }

                            items(
                                items = songs,
                                contentType = { "song" }
                            ) { song ->
                                val isPlayingThisSong = song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying
                                val isSelected = selectedSongIds.contains(song.id)
                                
                                val rememberedOnMoreOptionsClick: (Song) -> Unit = remember(onMoreOptionsClick) {
                                    { songFromListItem -> onMoreOptionsClick(songFromListItem) }
                                }
                                
                                // In selection mode, click toggles selection instead of playing
                                val rememberedOnClick: () -> Unit = remember(song, isSelectionMode) {
                                    if (isSelectionMode) {
                                        { onSongSelectionToggle(song) }
                                    } else {
                                        { playerViewModel.showAndPlaySong(song, songs, "Library") }
                                    }
                                }
                                
                                val rememberedOnLongPress: () -> Unit = remember(song) {
                                    { onSongLongPress(song) }
                                }

                                EnhancedSongListItem(
                                    song = song,
                                    isPlaying = isPlayingThisSong,
                                    isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                    isLoading = false,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = rememberedOnLongPress,
                                    onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                    onClick = rememberedOnClick
                                )
                            }
                        }
                        
                        // ScrollBar Overlay
                        val bottomPadding = if (stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong()) 
                            bottomBarHeight + MiniPlayerHeight + 16.dp 
                        else 
                            bottomBarHeight + 16.dp

                        com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            listState = listState
                        )
                    }
                }
                // Top gradient fade effect
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(10.dp)
//                        .background(
//                            brush = Brush.verticalGradient(
//                                colors = listOf(
//                                    MaterialTheme.colorScheme.surface, Color.Transparent
//                                )
//                            )
//                        )
//                )
            }
        }
    }
}
