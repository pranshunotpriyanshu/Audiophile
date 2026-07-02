package com.pryvn.audiophile.code.api.lyrics

data class AudiophileLyrics(
    val provider: String,
    val text: String,
    val isWordSynced: Boolean = false,
    val syllables: List<List<WordTiming>>? = null,
)

data class WordTiming(
    val startMs: Float,
    val endMs: Float,
    val text: String,
    val isBackground: Boolean = false,
)

data class AudiophileSyncedLine(
    val timestamp: Long,
    val text: String,
    val translation: String? = null,
)

data class AudiophileTranslation(
    val sourceLang: String = "auto",
    val targetLang: String,
    val translatedLines: List<String>,
)
