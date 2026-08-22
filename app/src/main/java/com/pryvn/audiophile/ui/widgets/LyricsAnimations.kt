package com.pryvn.audiophile.ui.widgets

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.exp

const val BUBBLE_RADIUS = 5
fun bubbleAmplitudeDp(distance: Int): Float =
    (8f * exp(-0.7f * (distance - 1).toFloat())).toFloat()

const val GAP_ROW_ANIM_MS = 220L

const val GAP_DOTS_REVEAL_MS = 520L
const val GAP_DOTS_HIDE_MS = 380L
const val GAP_DOTS_SLIDE_DP = 28f

const val PULL_LINE_RANGE = 14
const val PULL_STRENGTH = 0.65f
const val PULL_STAGGER_MS = 14L
const val PULL_HOLD_MS = 130L
const val PULL_RELEASE_MS = 6L

const val HIGHLIGHT_LEAD_MS = 60L


const val LYRIC_ANCHOR_FRACTION = 0.22f


fun lyricAnchorOffsetPx(viewportHeight: Int): Int =
    (viewportHeight * LYRIC_ANCHOR_FRACTION).toInt()

suspend fun LazyListState.animateCurrentLineToAnchor(
    currentIndex: Int,
    viewportHeight: Int,
    targetOffset: Int = lyricAnchorOffsetPx(viewportHeight),
) {
    val targetItem = layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
    if (targetItem != null) {
        animateScrollBy(
            targetItem.offset - targetOffset.toFloat(),
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = 150f,
                visibilityThreshold = 0.01f,
            ),
        )
    } else {
        animateScrollToItem(
            index = currentIndex.coerceAtLeast(0),
            scrollOffset = -targetOffset,
        )
    }
}

suspend fun LazyListState.pullCurrentLineToAnchorIfAbove(
    currentIndex: Int,
    viewportHeight: Int,
    targetOffset: Int = lyricAnchorOffsetPx(viewportHeight),
) {
    val currentItem = layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
    if (currentItem != null && currentItem.offset < targetOffset) {
        animateScrollBy(
            currentItem.offset - targetOffset.toFloat(),
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = 180f,
                visibilityThreshold = 0.01f,
            ),
        )
    }
}
