package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ExpressiveScrollBar(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    minHeight: Dp = 48.dp,
    thickness: Dp = 8.dp,
    indicatorExpandedWidth: Dp = 24.dp,
    paddingEnd: Dp = 4.dp,
    trackGap: Dp = 8.dp
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var pendingScrollIndex by remember { mutableIntStateOf(-1) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.secondaryContainer
    val innerIcon = Icons.Rounded.UnfoldMore

    val isInteracting = isPressed || isDragging
    
    val animatedWidth by animateDpAsState(
        targetValue = if (isInteracting) indicatorExpandedWidth else thickness,
        animationSpec = tween(durationMillis = 200),
        label = "WidthAnimation"
    )
    
    val iconAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "IconAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(indicatorExpandedWidth + paddingEnd)
    ) {
        val density = LocalDensity.current
        val constraintsMaxWidth = maxWidth
        val constraintsMaxHeight = maxHeight

        val canScrollForward by remember { derivedStateOf { listState?.canScrollForward ?: gridState?.canScrollForward ?: false } }
        val canScrollBackward by remember { derivedStateOf { listState?.canScrollBackward ?: gridState?.canScrollBackward ?: false } }
        
        if (!canScrollForward && !canScrollBackward) return@BoxWithConstraints

        fun getScrollStats(): Triple<Float, Int, Float> {
            val totalItemsCount: Int
            val firstVisibleItemIndex: Int
            val visibleCount: Int

            if (listState != null) {
                val layoutInfo = listState.layoutInfo
                totalItemsCount = layoutInfo.totalItemsCount
                firstVisibleItemIndex = listState.firstVisibleItemIndex
                visibleCount = layoutInfo.visibleItemsInfo.size
            } else if (gridState != null) {
                val layoutInfo = gridState.layoutInfo
                totalItemsCount = layoutInfo.totalItemsCount
                firstVisibleItemIndex = gridState.firstVisibleItemIndex
                visibleCount = layoutInfo.visibleItemsInfo.size
            } else {
                return Triple(0f, 0, 1f)
            }

            if (totalItemsCount == 0) return Triple(0f, 0, 1f)

            val maxScrollIndex = (totalItemsCount - visibleCount).coerceAtLeast(1)
            
            val forward = listState?.canScrollForward ?: gridState?.canScrollForward ?: false
            val backward = listState?.canScrollBackward ?: gridState?.canScrollBackward ?: false
            
            val realProgress = if (!forward && totalItemsCount > 0) 1f
            else if (!backward) 0f
            else (firstVisibleItemIndex.toFloat() / maxScrollIndex.toFloat()).coerceIn(0f, 0.99f)

            val availableHeight = with(density) { constraintsMaxHeight.toPx() }
            val handleHeightPx = with(density) { minHeight.toPx() }
            val scrollableHeight = (availableHeight - handleHeightPx).coerceAtLeast(1f)

            return Triple(realProgress, totalItemsCount, scrollableHeight)
        }

        fun updateProgressFromTouch(touchY: Float, grabOffset: Float) {
            val stats = getScrollStats()
            val scrollableHeight = stats.third
            val totalItemsCount = stats.second
            
            val targetHandleTop = touchY - grabOffset
            val newProgress = (targetHandleTop / scrollableHeight).coerceIn(0f, 1f)
            
            dragProgress = newProgress
            pendingScrollIndex = (newProgress * totalItemsCount).toInt()
        }

        // Throttled scroll: coalesces rapid drag updates into one scroll per frame
        LaunchedEffect(Unit) {
            snapshotFlow { pendingScrollIndex }
                .collect { index ->
                    if (index >= 0) {
                        listState?.scrollToItem(index)
                        gridState?.scrollToItem(index)
                    }
                }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var grabOffset = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            
                            val stats = getScrollStats()
                            val realProgress = stats.first
                            val scrollableHeight = stats.third
                            val handleHeightPx = with(density) { minHeight.toPx() }
                            
                            val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else realProgress
                            val handleY = displayProgress * scrollableHeight

                            val isTouchOnHandle = offset.y >= handleY && offset.y <= (handleY + handleHeightPx)
                            
                            if (isTouchOnHandle) {
                                grabOffset = offset.y - handleY
                                dragProgress = realProgress 
                            } else {
                                grabOffset = handleHeightPx / 2f
                                updateProgressFromTouch(offset.y, grabOffset)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragProgress = -1f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragProgress = -1f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateProgressFromTouch(change.position.y, grabOffset)
                        }
                    )
                }
        ) {
            val rightAnchorX = with(density) { (constraintsMaxWidth - paddingEnd).toPx() }
            val trackX = rightAnchorX - with(density) { thickness.toPx() / 2 }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val stats = getScrollStats()
                val realProgress = stats.first
                val scrollableHeight = stats.third
                
                val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else realProgress
                val handleY = displayProgress * scrollableHeight
                val handleHeightPx = minHeight.toPx()

                val trackStrokeWidth = thickness.toPx()
                val indicatorWidthPx = animatedWidth.toPx()
                val gapPx = trackGap.toPx()
                val indicatorCornerRadius = indicatorWidthPx / 2

                val currentIndicatorX = rightAnchorX - indicatorWidthPx

                if (handleY > gapPx) {
                    drawLine(
                        color = surfaceVariantColor,
                        start = Offset(trackX, 0f),
                        end = Offset(trackX, handleY - gapPx),
                        strokeWidth = trackStrokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                if (handleY + handleHeightPx + gapPx < size.height) {
                    drawLine(
                        color = surfaceVariantColor,
                        start = Offset(trackX, handleY + handleHeightPx + gapPx),
                        end = Offset(trackX, size.height),
                        strokeWidth = trackStrokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(currentIndicatorX, handleY),
                    size = Size(indicatorWidthPx, handleHeightPx),
                    cornerRadius = CornerRadius(indicatorCornerRadius, indicatorCornerRadius)
                )
            }
            
            if (iconAlpha > 0f) {
               Box(
                   modifier = Modifier
                       .offset {
                           val stats = getScrollStats()
                           val realProgress = stats.first
                           val scrollableHeight = stats.third
                           val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else realProgress
                           val handleY = displayProgress * scrollableHeight
                           val handleHeightPx = with(density) { minHeight.toPx() }
                           
                           val iconSizePx = with(density) { 24.dp.toPx() }
                           val paddingEndPx = with(density) { paddingEnd.toPx() }
                           val animatedWidthPx = with(density) { animatedWidth.toPx() }
                           val maxWidthPx = with(density) { constraintsMaxWidth.toPx() }
                           
                           val x = maxWidthPx - paddingEndPx - (animatedWidthPx / 2) - (iconSizePx / 2)
                           val y = handleY + (handleHeightPx / 2) - (iconSizePx / 2)
                           
                           androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
                       }
                       .size(24.dp)
                       .graphicsLayer { 
                           alpha = iconAlpha 
                           scaleX = iconAlpha
                           scaleY = iconAlpha
                       }
               ) {
                   Icon(
                       imageVector = innerIcon,
                       contentDescription = null,
                       tint = MaterialTheme.colorScheme.onPrimary,
                       modifier = Modifier.fillMaxSize()
                   )
               }
            }
        }
    }
}
