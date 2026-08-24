package com.pryvn.audiophile.code.player

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.pryvn.audiophile.code.MediaController.mediaControl
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sleep timer for the Now Playing overflow menu. Singleton; in-memory only
 * (no MMKV persistence, no AlarmManager). Process death cancels the timer.
 *
 * Three timer modes:
 * - [SleepTimerOption.Duration] — fires after a wall-clock delay
 * - [SleepTimerOption.EndOfTrack] — fires on the next media item transition
 * - [SleepTimerOption.EndOfQueue] — fires on the next transition after which
 *   the player no longer has a successor item.
 *
 * The playback service calls [onMediaItemTransition] from its Player.Listener.
 * Compose UI reads [state] and [remainingMs] directly — both are
 * MutableState-backed.
 */
@Stable
object SleepTimer {

    /** Current state — Inactive or Active. Compose-observable. */
    val state = mutableStateOf<SleepTimerState>(SleepTimerState.Inactive)

    /**
     * Live remaining time in ms for [SleepTimerOption.Duration] timers.
     * Updated every 1s while the timer is active. Returns 0 when no timer
     * is running.
     */
    val remainingMs = mutableLongStateOf(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    fun start(option: SleepTimerOption) {
        cancel()
        val now = SystemClock.elapsedRealtime()
        val expiresAt = when (option) {
            is SleepTimerOption.Duration -> now + option.durationMs
            SleepTimerOption.EndOfTrack -> null
            SleepTimerOption.EndOfQueue -> null
        }
        state.value = SleepTimerState.Active(option, now, expiresAt)
        if (option is SleepTimerOption.Duration) {
            remainingMs.longValue = option.durationMs
            tickerJob = scope.launch { runDurationTimer(option, now) }
        } else {
            remainingMs.longValue = 0L
        }
    }

    fun addDuration(durationMs: Long) {
        val activeTimerState = state.value as? SleepTimerState.Active

        if (activeTimerState?.option is SleepTimerOption.Duration) {
            val remainingDurationMs =
                (activeTimerState.expiresAtElapsedMs ?: SystemClock.elapsedRealtime()) -
                    SystemClock.elapsedRealtime()

            start(
                SleepTimerOption.Duration(
                    remainingDurationMs.coerceAtLeast(0L) + durationMs,
                ),
            )
            return
        }

        start(SleepTimerOption.Duration(durationMs))
    }

    fun cancel() {
        tickerJob?.cancel()
        tickerJob = null
        state.value = SleepTimerState.Inactive
        remainingMs.longValue = 0L
    }

    fun onMediaItemTransition(hasNext: Boolean) {
        when (val current = state.value) {
            is SleepTimerState.Active -> when (current.option) {
                SleepTimerOption.EndOfTrack -> fireAndCleanUp()
                SleepTimerOption.EndOfQueue -> if (!hasNext) fireAndCleanUp()
                is SleepTimerOption.Duration -> Unit
            }
            SleepTimerState.Inactive -> Unit
        }
    }

    private suspend fun runDurationTimer(option: SleepTimerOption.Duration, startedAt: Long) {
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            val remaining = (startedAt + option.durationMs) - now
            if (remaining <= 0L) {
                remainingMs.longValue = 0L
                fireAndCleanUp()
                return
            }
            remainingMs.longValue = remaining
            delay(1_000L)
        }
    }

    private fun fireAndCleanUp() {
        runCatching {
            mediaControl?.fadePause()
        }
        state.value = SleepTimerState.Inactive
        remainingMs.longValue = 0L
        tickerJob = null
    }
}

/** Discrete states of the sleep timer. Compose-stable. */
@Stable
sealed class SleepTimerState {
    @Stable
    data object Inactive : SleepTimerState()

    @Stable
    data class Active(
        val option: SleepTimerOption,
        val startedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long?,
    ) : SleepTimerState()
}

/** Sleep timer mode. */
@Stable
sealed class SleepTimerOption {
    @Stable
    data class Duration(val durationMs: Long) : SleepTimerOption()

    @Stable
    data object EndOfTrack : SleepTimerOption()

    @Stable
    data object EndOfQueue : SleepTimerOption()
}
