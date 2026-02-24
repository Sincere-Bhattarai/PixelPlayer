package com.theveloper.pixelplay.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.theveloper.pixelplay.presentation.screens.BrowseScreen
import com.theveloper.pixelplay.presentation.screens.LibraryListScreen
import com.theveloper.pixelplay.presentation.screens.PlayerScreen
import com.theveloper.pixelplay.presentation.screens.SongListScreen
import com.theveloper.pixelplay.presentation.screens.VolumeScreen
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Navigation host for the Wear OS app.
 * Routes:
 * - PLAYER: Main player controls (start destination)
 * - VOLUME: Volume control
 * - BROWSE: Library category picker
 * - LIBRARY_LIST: List of albums/artists/playlists
 * - SONG_LIST: Songs within a context (album/artist/playlist/favorites/all)
 */
object WearScreens {
    const val PLAYER = "player"
    const val VOLUME = "volume"
    const val BROWSE = "browse"
    const val LIBRARY_LIST = "library_list/{browseType}/{title}"
    const val SONG_LIST = "song_list/{browseType}/{contextId}/{title}"

    fun libraryListRoute(browseType: String, title: String): String {
        return "library_list/$browseType/${URLEncoder.encode(title, "UTF-8")}"
    }

    fun songListRoute(browseType: String, contextId: String, title: String): String {
        return "song_list/$browseType/$contextId/${URLEncoder.encode(title, "UTF-8")}"
    }
}

@Composable
fun WearNavigation() {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearScreens.PLAYER,
    ) {
        composable(WearScreens.PLAYER) {
            PlayerScreen(
                onBrowseClick = {
                    navController.navigate(WearScreens.BROWSE)
                },
            )
        }

        composable(WearScreens.VOLUME) {
            VolumeScreen()
        }

        composable(WearScreens.BROWSE) {
            BrowseScreen(
                onCategoryClick = { browseType, title ->
                    // Favorites and All Songs go directly to songs list
                    if (browseType == "favorites" || browseType == "all_songs") {
                        navController.navigate(
                            WearScreens.songListRoute(browseType, "none", title)
                        )
                    } else {
                        navController.navigate(
                            WearScreens.libraryListRoute(browseType, title)
                        )
                    }
                },
            )
        }

        composable(
            route = WearScreens.LIBRARY_LIST,
            arguments = listOf(
                navArgument("browseType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val browseType = backStackEntry.arguments?.getString("browseType") ?: ""
            val title = URLDecoder.decode(
                backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
            )
            LibraryListScreen(
                browseType = browseType,
                title = title,
                onItemClick = { item, subBrowseType, itemTitle ->
                    navController.navigate(
                        WearScreens.songListRoute(subBrowseType, item.id, itemTitle)
                    )
                },
            )
        }

        composable(
            route = WearScreens.SONG_LIST,
            arguments = listOf(
                navArgument("browseType") { type = NavType.StringType },
                navArgument("contextId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val browseType = backStackEntry.arguments?.getString("browseType") ?: ""
            val contextId = backStackEntry.arguments?.getString("contextId")
            val title = URLDecoder.decode(
                backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
            )
            SongListScreen(
                browseType = browseType,
                contextId = contextId,
                title = title,
                onSongPlayed = {
                    // Navigate back to player when a song is played
                    navController.popBackStack(WearScreens.PLAYER, inclusive = false)
                },
            )
        }
    }
}
