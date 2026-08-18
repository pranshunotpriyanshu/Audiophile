package com.pryvn.audiophile.ui.widgets

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.exp

// Bubble-bounce: how far the wobble reaches around the current line and how
// the kick amplitude decays with distance from it (dp at distance 1). Shared
// by the line-synced (YosLyricView) and word-synced (LyricsV2) renderers.
const val BUBBLE_RADIUS = 5
fun bubbleAmplitudeDp(distance: Int): Float =
    (8f * exp(-0.7f * (distance - 1).toFloat())).toFloat()

// Gap-dots rows (empty lines, instrumental rows, between-lines dots) animate
// their height with a fixed tween while they expand/collapse, and only reserve
// space while active so the lyric list stays tight. The auto-scrolls wait this
// long (+ a small margin) before measuring, so the layout is final and the
// anchored line can never overshoot its set level.
const val GAP_ROW_ANIM_MS = 220L

// Between-lines gap dots reveal/hide: when the gap begins the dots slide out of
// the line above fast and ease into place (FastOutSlowIn), and when the next
// line starts they linger at the anchor then accelerate into it (LinearOutSlowIn)
// while fading — Apple Music style. Shared by both lyric renderers.
const val GAP_DOTS_REVEAL_MS = 520L
const val GAP_DOTS_HIDE_MS = 380L
const val GAP_DOTS_SLIDE_DP = 28f

// The "pull" (spacing animation) that fires when the current line advances:
// the spacers below the current line stretch like a rubber band — the pull
// grows with distance, so the lower lyrics feel dragged by inertia — and then
// release in a wave from the top down, gliding the block onto the anchor.
const val PULL_LINE_RANGE = 14
const val PULL_STRENGTH = 0.65f
const val PULL_STAGGER_MS = 14L
const val PULL_HOLD_MS = 130L
const val PULL_RELEASE_MS = 6L

// The next line lights up first and the list scrolls almost together with the
// switch — priority goes to switching the highlight, and the scroll follows
// almost immediately so there is no perceived lag.
const val HIGHLIGHT_LEAD_MS = 60L

// ── Shared lyric anchor logic ──
// Both the word-synced (LyricsV2) and the line-synced (YosLyricView) renderers
// anchor their current line at the same level (8% of the viewport height) with
// the same critically damped springs — a single implementation, so the current
// line's on-screen height can never drift apart between the two views.

/** Fraction of the viewport height where the current lyric line is anchored. */
const val LYRIC_ANCHOR_FRACTION = 0.08f

/** Pixel offset (from the viewport top) where the current lyric line is anchored. */
fun lyricAnchorOffsetPx(viewportHeight: Int): Int =
    (viewportHeight * LYRIC_ANCHOR_FRACTION).toInt()

/**
 * Scrolls so the current lyric line sits exactly on the anchor level, gliding
 * with a critically damped spring. When the line is not composed yet, jumps
 * straight to it with the same scroll offset.
 */
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

/**
 * Anchor guard: after everything settles, if the current lyric line is still
 * above the anchor level it is pulled back down exactly onto it with a
 * critically damped spring, so it can never cross upward again.
 */
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
