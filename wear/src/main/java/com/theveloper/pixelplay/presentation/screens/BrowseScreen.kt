package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Root browse screen showing library categories.
 * Categories are hardcoded (no network request needed) — the user navigates
 * deeper to load actual library content from the phone.
 */
@Composable
fun BrowseScreen(
    onCategoryClick: (browseType: String, title: String) -> Unit,
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
            .background(background),
        columnState = columnState,
    ) {
        item {
            Text(
                text = "Library",
                style = MaterialTheme.typography.title2,
                color = Color(0xFFF4EEFF),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }

        item {
            BrowseCategoryChip(
                label = "Favorites",
                icon = Icons.Rounded.Favorite,
                iconTint = Color(0xFFF1608E),
                onClick = { onCategoryClick("favorites", "Favorites") },
            )
        }

        item {
            BrowseCategoryChip(
                label = "Playlists",
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                iconTint = Color(0xFF44CDC4),
                onClick = { onCategoryClick("playlists", "Playlists") },
            )
        }

        item {
            BrowseCategoryChip(
                label = "Albums",
                icon = Icons.Rounded.Album,
                iconTint = Color(0xFF70A6FF),
                onClick = { onCategoryClick("albums", "Albums") },
            )
        }

        item {
            BrowseCategoryChip(
                label = "Artists",
                icon = Icons.Rounded.Person,
                iconTint = Color(0xFFFFB74D),
                onClick = { onCategoryClick("artists", "Artists") },
            )
        }

        item {
            BrowseCategoryChip(
                label = "All Songs",
                icon = Icons.Rounded.MusicNote,
                iconTint = Color(0xFFE1D5FF),
                onClick = { onCategoryClick("all_songs", "All Songs") },
            )
        }
    }
}

@Composable
private fun BrowseCategoryChip(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Chip(
        label = {
            Text(
                text = label,
                color = Color(0xFFF4EEFF),
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(0xFFD8CEF3).copy(alpha = 0.18f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
