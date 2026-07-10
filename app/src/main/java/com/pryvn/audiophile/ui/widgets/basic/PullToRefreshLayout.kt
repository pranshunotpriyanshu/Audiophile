package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

@Composable
fun PullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var hasTriggeredRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && hasTriggeredRefresh) {
            val anim = Animatable(offsetY)
            anim.animateTo(0f, spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )) { offsetY = value }
            hasTriggeredRefresh = false
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            offsetY = thresholdPx
        }
    }

    val connection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isRefreshing || !enabled || source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

                if (available.y < 0 && atTop) {
                    val dragAmount = -available.y
                    val newOffset = (offsetY + dragAmount).coerceAtMost(thresholdPx * 2)
                    val consumed = offsetY - newOffset
                    offsetY = newOffset
                    return Offset(0f, consumed)
                }
                if (available.y > 0 && offsetY > 0) {
                    val pushAmount = available.y
                    val newOffset = (offsetY - pushAmount).coerceAtLeast(0f)
                    val consumed = offsetY - newOffset
                    offsetY = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (isRefreshing || !enabled || source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

                if (available.y < 0 && atTop) {
                    val dragAmount = -available.y
                    val newOffset = (offsetY + dragAmount).coerceAtMost(thresholdPx * 2)
                    offsetY = newOffset
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isRefreshing || !enabled) return Velocity.Zero

                if (offsetY >= thresholdPx && !hasTriggeredRefresh) {
                    hasTriggeredRefresh = true
                    onRefresh()
                    val anim = Animatable(offsetY)
                    anim.animateTo(thresholdPx, spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )) { offsetY = value }
                } else if (offsetY > 0f) {
                    val anim = Animatable(offsetY)
                    anim.animateTo(0f, spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )) { offsetY = value }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .nestedScroll(connection)
            .clipToBounds()
            .graphicsLayer {
                translationY = offsetY
            }
    ) {
        content()

        if (offsetY > 0f || isRefreshing) {
            val progress = if (isRefreshing) 1f else (offsetY / thresholdPx).coerceIn(0f, 1f)
            val indicatorSize = 24.dp * progress.coerceAtLeast(0.3f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp)
                    .alpha(progress),
                contentAlignment = Alignment.TopCenter
            ) {
                AppleLoadingSpinner(
                    modifier = Modifier.size(indicatorSize),
                    size = indicatorSize
                )
            }
        }
    }
}
