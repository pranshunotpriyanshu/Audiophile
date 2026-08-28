package com.pryvn.audiophile.code.utils.player

import kotlin.math.abs
import kotlin.math.max

/**
 * Flamingo-recovered behavioral logic, injected into Audiophone's existing UI.
 * Pure algorithms — no UI code. Each function maps to a verified smali source.
 */
object FlamingoBehavior {

    // === PANEL PHYSICS (source: j7/W, j7/X, j7/b0, M6/w.1) ===
    // Tap/button expand: spring(dampingRatio=1.0f, stiffness=400f, visibilityThreshold=0.5f)
    // Fling settle:      default Animatable spring (dampingRatio=1.0f, stiffness=1500f)
    // Velocity clamp:    Flamingo does NOT clamp velocity (raw pass-through)
    const val PANEL_TAP_SPRING_DAMPING = 1.0f
    const val PANEL_TAP_SPRING_STIFFNESS = 400f
    const val PANEL_FLING_SPRING_DAMPING = 1.0f
    const val PANEL_FLING_SPRING_STIFFNESS = 1500f
    const val PANEL_MID_DRAG_MIN = 0.001f
    const val PANEL_MID_DRAG_MAX = 0.999f

    fun isMidDrag(expandProgress: Float): Boolean =
        expandProgress > PANEL_MID_DRAG_MIN && expandProgress < PANEL_MID_DRAG_MAX

    // === REPEAT CYCLE (source: j7/C1 case 1) ===
    // OFF(0) → ALL(2) → ONE(1) → OFF(0)
    fun cycleRepeatMode(currentMode: Int): Int = when (currentMode) {
        0 -> 2   // OFF → ALL
        2 -> 1   // ALL → ONE
        else -> 0 // ONE/unknown → OFF
    }

    // === CLICK-TO-SEEK OFFSET CLAMP (source: z7/Z0 lines 395-431) ===
    const val TIMING_OFFSET_CLAMP_MS = 1000

    fun clampTimingOffset(offsetMs: Int): Int =
        offsetMs.coerceIn(-TIMING_OFFSET_CLAMP_MS, TIMING_OFFSET_CLAMP_MS)

    fun seekTargetFromLyricClick(entryBeginMs: Long, timingOffsetMs: Int): Long =
        max(entryBeginMs, 0L) - clampTimingOffset(timingOffsetMs)

    // === SEEK GUARD FSM (source: z7/r1) ===
    const val SEEK_GUARD_DEADLINE_MS = 2000L
    const val SEEK_GUARD_ACK_TOLERANCE_MS = 500L
    const val BACKWARD_JITTER_WINDOW_MS = 500L

    // === LYRIC FOLLOW WATCHDOG (source: j7/E0 + j7/D0) ===
    const val FOLLOW_RESET_DELAY_MS = 2500L

    /**
     * Two-stage watchdog reset.
     * Stage 1: delay(FOLLOW_RESET_DELAY_MS)
     * Stage 2: only release if elapsed since last interaction > FOLLOW_RESET_DELAY_MS
     */
    fun shouldReleaseFollow(
        elapsedSinceLastInteractionMs: Long,
        currentPageIsLyric: Boolean,
        followEnabled: Boolean,
        autoFollowActive: Boolean
    ): Boolean =
        elapsedSinceLastInteractionMs >= FOLLOW_RESET_DELAY_MS &&
            currentPageIsLyric && followEnabled && autoFollowActive

    // === PLAYBACK POSITION COMMIT THRESHOLDS (source: j7/b1 companion) ===
    const val POSITION_SEEK_JUMP_MS = 1000L
    const val POSITION_FORCE_REFRESH_INTERVAL_MS = 2000L

    fun shouldCommitPosition(
        lastCommitted: Long?,
        observed: Long,
        observedDurationMs: Long,
        cachedDurationMs: Long,
        trackChanged: Boolean,
        wallClockSinceLastCommitMs: Long
    ): Boolean {
        if (lastCommitted == null || trackChanged) return true
        if (abs(observed - lastCommitted) > POSITION_SEEK_JUMP_MS) return true
        if (observedDurationMs != cachedDurationMs) return true
        return wallClockSinceLastCommitMs >= POSITION_FORCE_REFRESH_INTERVAL_MS
    }

    // === VOLUME HUD SPRINGS (source: j7/l.m) ===
    const val VOLUME_SPRING_DAMPING = 0.6f
    const val VOLUME_SPRING_STIFFNESS = 200f
    const val VOLUME_SCALE_VISIBLE = 1.05f
    const val VOLUME_BACKDROP_ALPHA_VISIBLE = 0.62f
    const val VOLUME_BACKDROP_ALPHA_HIDDEN = 0.45f

    // === HEADER CROSSFADE SPRING (source: j7/J0/K0) ===
    const val HEADER_CROSSFADE_DAMPING = 1.2f
    const val HEADER_CROSSFADE_STIFFNESS = 800f
    const val HEADER_CROSSFADE_THRESHOLD = 0.001f

    // === ARTWORK CROSSFADE (source: A7/C) ===
    const val ARTWORK_CROSSFADE_TWEEN_MS = 300

    // === TRANSLATION VISIBILITY TWEEN (source: z7/d.h) ===
    const val TRANSLATION_VISIBILITY_TWEEN_MS = 220

    // === SMOOTHSTEP (source: z7/C, WavyKaraokeMath) ===
    fun smoothstep(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // === KARAOKE CHAR WINDOW (source: z7/d.n) ===
    fun charAnimationWindowFraction(tokenDurationMs: Float): Float =
        (tokenDurationMs - 1000f) / 2000f

    // === PLAYBACK CLOCK SMOOTHER (source: z7/O) ===
    fun smootherstep(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * c * (c * (c * 6f - 15f) + 10f)
    }
}
