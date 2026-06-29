package com.pryvn.audiophile.code.utils.lrc

import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.WordSyncedLine
import com.pryvn.audiophile.data.objects.WordSyncedWord

object LyricsProcessor {

    fun applyLyrics(
        onlineLyrics: AudiophileLyrics,
        lrcEntriesSetter: (List<List<Pair<Float, String>>>) -> Unit,
    ) {
        val text = onlineLyrics.text
        if (text.isBlank()) return

        MediaViewModelObject.onlineLyrics.value = text
        MediaViewModelObject.lyricsSource.value = onlineLyrics.provider

        val lrcFactory = YosLrcFactory()
        val isWordSynced = onlineLyrics.isWordSynced || TTMLParser.isTtml(text)

        when {
            isWordSynced && TTMLParser.isTtml(text) -> {
                val parsed = TTMLParser.parseTTML(text)
                if (parsed.isNotEmpty()) {
                    MediaViewModelObject.hasWordSyncedLyrics.value = parsed.any { it.words.isNotEmpty() }
                    MediaViewModelObject.wordSyncedLines.value = parsed.map { line ->
                        WordSyncedLine(
                            text = line.text,
                            startTimeMs = (line.startTime * 1000).toLong(),
                            endTimeMs = (line.endTime * 1000).toLong(),
                            words = line.words.map { word ->
                                WordSyncedWord(
                                    text = word.text,
                                    startTimeMs = (word.startTime * 1000).toLong(),
                                    endTimeMs = (word.endTime * 1000).toLong(),
                                    isBackground = word.isBackground,
                                )
                            },
                        )
                    }
                    val lrcText = TTMLParser.ttmlToLrc(text)
                    lrcEntriesSetter(lrcFactory.formatLrcEntries(lrcText))
                } else {
                    clearWordSync()
                    lrcEntriesSetter(lrcFactory.formatLrcEntries(text))
                }
            }
            TTMLParser.isLineSyncedLrc(text) -> {
                clearWordSync()
                lrcEntriesSetter(lrcFactory.formatLrcEntries(text))
            }
            else -> {
                clearWordSync()
                val lines = text.lines().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    val dummyLrc = lines.mapIndexed { idx, line ->
                        val time = String.format("[%02d:%05.2f]", idx * 30, (idx * 30) % 60)
                        "$time$line"
                    }.joinToString("\n")
                    lrcEntriesSetter(lrcFactory.formatLrcEntries(dummyLrc))
                }
            }
        }
    }

    fun clearWordSync() {
        MediaViewModelObject.hasWordSyncedLyrics.value = false
        MediaViewModelObject.wordSyncedLines.value = emptyList()
    }

    fun resetLyricsState() {
        clearWordSync()
        MediaViewModelObject.onlineLyrics.value = null
        MediaViewModelObject.lyricsSource.value = null
    }
}
