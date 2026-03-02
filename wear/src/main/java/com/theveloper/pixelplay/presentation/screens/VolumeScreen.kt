package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.screenBackgroundColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerHighColor
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun VolumeScreen(
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val palette = LocalWearPalette.current
    val volumeState by viewModel.activeVolumeState.collectAsState()
    val volumePercent by viewModel.activeVolumePercent.collectAsState()
    val activeDeviceName by viewModel.activeVolumeDeviceName.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshActiveVolumeState()
            delay(350L)
        }
    }
    val progressTarget = if (volumeState.max > 0) {
        (volumeState.level.toFloat() / volumeState.max.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = spring(),
        label = "volumeProgress",
    )
    val background = palette.screenBackgroundColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        CurvedVolumeIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 30.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            VolumeStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Volume up",
                onClick = viewModel::volumeUp,
            )

            VolumeValuePill(
                level = volumeState.level,
                percent = volumePercent,
                deviceName = activeDeviceName,
            )

            VolumeStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Volume down",
                onClick = viewModel::volumeDown,
            )
        }

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun CurvedVolumeIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "curvedVolumeIndicator",
    )
    val trackColor = palette.surfaceContainerColor().copy(alpha = 0.58f)
    val progressColor = palette.controlContainer

    Canvas(modifier = modifier) {
        val strokeWidth = 6.dp.toPx()
        val inset = strokeWidth / 2f + 9.dp.toPx()
        val diameter = size.minDimension - (inset * 2f)
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f,
        )
        val bottomAngle = 132f
        val topAngle = 228f
        val totalSweep = topAngle - bottomAngle

        drawArc(
            color = trackColor,
            startAngle = bottomAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = progressColor,
            startAngle = bottomAngle,
            sweepAngle = totalSweep * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun VolumeValuePill(
    level: Int,
    percent: Int,
    deviceName: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val container = palette.chipContent
    val icon = if (level <= 0) {
        Icons.AutoMirrored.Rounded.VolumeOff
    } else {
        Icons.AutoMirrored.Rounded.VolumeUp
    }

    Row(
        modifier = modifier
            .width(150.dp)
            .height(64.dp)
            .background(container, RoundedCornerShape(32.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.controlContent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
//            Text(
//                text = "$percent%",
//                color = palette.controlContent,
//                style = MaterialTheme.typography.title3,
//                fontWeight = FontWeight.SemiBold,
//            )
            Text(
                text = deviceName,
                color = palette.controlContent,
                style = MaterialTheme.typography.title3,
                //fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VolumeStepButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val container by animateColorAsState(
        targetValue = palette.surfaceContainerColor().copy(alpha = 0.98f),
        animationSpec = spring(),
        label = "volumeStepContainer",
    )

    Box(
        modifier = modifier
            .width(92.dp)
            .height(42.dp)
            .background(container, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.chipContent,
            modifier = Modifier.size(22.dp),
        )
    }
}
