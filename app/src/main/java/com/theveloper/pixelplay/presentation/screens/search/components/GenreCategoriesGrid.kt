package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.utils.GenreIconProvider
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(UnstableApi::class)
@Composable
fun GenreCategoriesGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No genres available.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val systemNavBarHeight = getNavigationBarHeight()
    val customGenreIcons = playerViewModel.customGenreIcons.collectAsState(
        initial = emptyMap(),
        context = kotlin.coroutines.EmptyCoroutineContext
    ).value

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .clip(AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp,
                smoothnessAsPercentTR = 70,
                cornerRadiusTL = 24.dp,
                smoothnessAsPercentTL = 70,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 70,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 70
            )),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + NavBarContentHeight + MiniPlayerHeight + systemNavBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Browse by genre",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
            )
        }
        items(genres, key = { it.id }) { genre ->
            // CORREGIDO: Obtener las URIs de manera más robusta
            GenreCard(
                genre = genre,
                customIcons = customGenreIcons,
                onClick = { onGenreClick(genre) }
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    customIcons: Map<String, Int>,
    onClick: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val themeColor = remember(genre, isDark) {
        com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(genre.id, isDark)
    }
    val backgroundColor = themeColor.container
    val onBackgroundColor = themeColor.onContainer

    Card(
        modifier = Modifier
            .aspectRatio(1.2f)
            .clip(AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp,
                smoothnessAsPercentTL = 70,
                cornerRadiusTL = 24.dp,
                smoothnessAsPercentTR = 70,
                cornerRadiusBR = 24.dp,
                smoothnessAsPercentBL = 70,
                cornerRadiusBL = 24.dp,
                smoothnessAsPercentBR = 70
            ))
            .clickable(onClick = onClick),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentTL = 70,
            cornerRadiusTL = 24.dp,
            smoothnessAsPercentTR = 70,
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentBL = 70,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentBR = 70
        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
        ) {
            // Imagen del género en esquina inferior derecha
            Box(
                modifier = Modifier
                    .size(90.dp) // Reduced size from 108.dp
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 16.dp) // Adjusted offset
            ) {
                SmartImage(
                    model = GenreIconProvider.getGenreImageResource(genre.name, customIcons), // Use genre.name for icon lookup
                    contentDescription = "Genre illustration",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                    colorFilter = ColorFilter.tint(onBackgroundColor),
                    contentScale = ContentScale.Crop
                )
            }

            // Nombre del género en esquina superior izquierda
            Text(
                text = genre.name,
                style = GenreTypography.getGenreStyle(genre.id).copy(
                    lineHeight = 24.sp // Enforce comfortable line height
                ),
                color = onBackgroundColor,
                softWrap = true,
                minLines = 1,
                maxLines = 3, // Allow up to 3 lines for very long names/large fonts
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.7f) // Give slightly more room (70%)
                    .padding(start = 14.dp, top = 14.dp, end = 0.dp) // Removed Right padding effectively by limiting width
            )
        }
    }
}