package com.pryvn.audiophile.ui.pages.library.playlists

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pryvn.audiophile.data.libraries.PlayList

@Stable
object PendingPlayListDeletion {

    /** Snapshot of the deleted playlist + its original list index. */
    data class Pending(val playList: PlayList, val originalIndex: Int)

    private val state = mutableStateOf<Pending?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoDismissJob: Job? = null

    val current: Pending? get() = state.value

    fun stash(playList: PlayList, originalIndex: Int) {
        autoDismissJob?.cancel()
        state.value = Pending(playList, originalIndex)
        autoDismissJob = scope.launch {
            delay(5000)
            if (state.value?.playList?.listID == playList.listID) {
                state.value = null
            }
        }
    }

    fun consume(): Pending? {
        autoDismissJob?.cancel()
        autoDismissJob = null
        val taken = state.value
        state.value = null
        return taken
    }

    fun clear() {
        autoDismissJob?.cancel()
        autoDismissJob = null
        state.value = null
    }
}
