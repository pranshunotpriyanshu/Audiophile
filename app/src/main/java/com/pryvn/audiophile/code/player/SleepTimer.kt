package com.pryvn.audiophile.code.player

import androidx.compose.runtime.Stable
import kotlinx.coroutines.*

@Stable
class SleepTimer {
    private var job: Job? = null
    var isActive: Boolean = false
        private set
    var remainingMinutes: Int = 0
        private set

    fun start(minutes: Int, onPause: () -> Unit) {
        job?.cancel()
        isActive = true
        remainingMinutes = minutes
        job = CoroutineScope(Dispatchers.Main).launch {
            var count = minutes
            while (count > 0) {
                delay(60_000L)
                count--
                this@SleepTimer.remainingMinutes = count
            }
            this@SleepTimer.isActive = false
            onPause()
        }
    }

    fun cancel() {
        job?.cancel()
        isActive = false
        remainingMinutes = 0
    }
}
