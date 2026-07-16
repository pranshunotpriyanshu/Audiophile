package com.pryvn.audiophile.data.libraries

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackSource {
    LOCAL, ONLINE
}

data class HistoryEntry(
    val videoId: String,
    val title: String,
    val artists: String?,
    val thumbnailUrl: String?,
    val lastPlayedAt: Long,
    val source: PlaybackSource = PlaybackSource.ONLINE,
)

object ListeningHistory {
    private const val mmkvID = "yos_player_core"
    private const val historyKey = "listening_history"
    private const val maxEntries = 50

    private val mmkv by lazy { MMKV.mmkvWithID(mmkvID) }
    private val gson = GsonBuilder().create()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        loadFromStorage()
    }

    fun record(
        videoId: String,
        title: String,
        artists: String?,
        thumbnailUrl: String?,
        source: PlaybackSource = PlaybackSource.ONLINE,
    ) {
        val entry = HistoryEntry(
            videoId = videoId,
            title = title,
            artists = artists,
            thumbnailUrl = thumbnailUrl,
            lastPlayedAt = System.currentTimeMillis(),
            source = source,
        )

        val current = _history.value.toMutableList()
        current.removeAll { it.videoId == entry.videoId }
        current.add(0, entry)
        val trimmed = current.take(maxEntries)
        _history.value = trimmed
        saveToStorage()
    }

    private fun loadFromStorage() {
        try {
            val json = mmkv.decodeString(historyKey)
            if (json != null) {
                val type = object : TypeToken<List<HistoryEntry>>() {}.type
                val entries: List<HistoryEntry> = gson.fromJson(json, type)
                _history.value = entries
            }
        } catch (_: Exception) {
            _history.value = emptyList()
        }
    }

    private fun saveToStorage() {
        try {
            val json = gson.toJson(_history.value)
            mmkv.encode(historyKey, json)
        } catch (_: Exception) { }
    }
}
