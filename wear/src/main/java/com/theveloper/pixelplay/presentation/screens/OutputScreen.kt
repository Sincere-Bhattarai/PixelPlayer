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
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.theveloper.pixelplay.data.WearOutputTarget
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.radialBackgroundBrush
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel

@Composable
fun OutputScreen(
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val outputTarget by viewModel.outputTarget.collectAsState()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsState()
    val isLocalPlaybackActive by viewModel.isLocalPlaybackActive.collectAsState()
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
                    text = "Device",
                    style = MaterialTheme.typography.title2,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            item {
                OutputTargetChip(
                    label = "Phone",
                    subtitle = when {
                        outputTarget == WearOutputTarget.PHONE && isPhoneConnected -> "Controlling now"
                        outputTarget == WearOutputTarget.PHONE -> "Selected (disconnected)"
                        isPhoneConnected -> "Control phone playback"
                        else -> "Phone disconnected"
                    },
                    icon = Icons.Rounded.PhoneAndroid,
                    selected = outputTarget == WearOutputTarget.PHONE,
                    enabled = true,
                    onClick = { viewModel.selectOutput(WearOutputTarget.PHONE) },
                )
            }

            item {
                OutputTargetChip(
                    label = "Watch",
                    subtitle = when {
                        outputTarget == WearOutputTarget.WATCH && isLocalPlaybackActive -> "Controlling now"
                        isLocalPlaybackActive -> "Control local watch playback"
                        else -> "Play a local song first"
                    },
                    icon = Icons.Rounded.Watch,
                    selected = outputTarget == WearOutputTarget.WATCH,
                    enabled = isLocalPlaybackActive,
                    onClick = { viewModel.selectOutput(WearOutputTarget.WATCH) },
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
private fun OutputTargetChip(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val containerColor = when {
        !enabled -> palette.controlDisabledContainer
        selected -> palette.controlContainer.copy(alpha = 0.95f)
        else -> palette.chipContainer
    }
    val contentColor = when {
        !enabled -> palette.controlDisabledContent
        selected -> palette.controlContent
        else -> palette.textPrimary
    }
    val secondaryColor = when {
        !enabled -> palette.controlDisabledContent.copy(alpha = 0.90f)
        selected -> palette.controlContent.copy(alpha = 0.76f)
        else -> palette.textSecondary.copy(alpha = 0.80f)
    }

    Chip(
        label = {
            Text(
                text = label,
                color = contentColor,
            )
        },
        secondaryLabel = {
            Text(
                text = subtitle,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        enabled = enabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
