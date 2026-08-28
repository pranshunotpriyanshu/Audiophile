package com.pryvn.audiophile.np.state

import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.abs
import kotlin.math.max

class PositionDurationHolder {

    var lastCommittedPosition: Long? = null
        private set
    var lastCommitWallClockMs: Long = 0L
        private set
    var durationAtLastCommitMs: Long = 0L
        private set
    var previousMediaId: String? = null
        private set
    var currentMediaId: String? = null

    val durationMs = mutableLongStateOf(0L)
    val positionMs = mutableLongStateOf(0L)
    val scrubTargetMs = mutableLongStateOf(-1L)
    val renderPositionMs = mutableLongStateOf(-1L)

    fun setScrubTarget(positionMs: Long, durationMs: Long) {
        scrubTargetMs.longValue = max(0L, positionMs).coerceAtMost(max(0L, durationMs))
    }

    companion object {
        const val SEEK_JUMP_THRESHOLD_MS = 1000L
        const val FORCE_REFRESH_INTERVAL_MS = 2000L

        fun commit(
            holder: PositionDurationHolder,
            newPositionMs: Long,
            newDurationMs: Long
        ) {
            val now = System.currentTimeMillis()
            val duration = max(0L, newDurationMs)
            val clampedPosition = max(0L, newPositionMs).coerceAtMost(duration)
            holder.durationMs.longValue = duration
            holder.positionMs.longValue = clampedPosition
            holder.lastCommittedPosition = clampedPosition
            holder.renderPositionMs.longValue = clampedPosition
            holder.lastCommitWallClockMs = now
            holder.durationAtLastCommitMs = duration
            holder.previousMediaId = holder.currentMediaId
        }

        fun shouldCommit(
            holder: PositionDurationHolder,
            observedPositionMs: Long,
            observedDurationMs: Long,
            observedMediaId: String?
        ): Boolean {
            val trackChanged = holder.previousMediaId != null &&
                observedMediaId != holder.previousMediaId
            val lastPosition = holder.lastCommittedPosition ?: return true
            val seekJump = abs(observedPositionMs - lastPosition) > SEEK_JUMP_THRESHOLD_MS
            val throttleElapsed =
                System.currentTimeMillis() - holder.lastCommitWallClockMs >= FORCE_REFRESH_INTERVAL_MS
            val noOpSkip = abs(observedPositionMs - lastPosition) <= SEEK_JUMP_THRESHOLD_MS && !trackChanged
            return trackChanged || seekJump || (!noOpSkip && throttleElapsed) ||
                observedDurationMs != holder.durationMs.longValue
        }
    }
}


