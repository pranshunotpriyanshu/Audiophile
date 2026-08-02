package com.pryvn.audiophile.code.utils.lrc

import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.WordSyncedLine
import com.pryvn.audiophile.data.objects.WordSyncedWord

/**
 * Converts word-synced lines into the App 2 lyric-view entry shape:
 * each line is [(lineStart, ""), (wordStart_i, word_i)..., (lineStart, "")].
 * The trailing pair is the translation slot (blank here), the leading empty
 * pair marks the sentence start. This lets the copied App 2 view drive the
 * per-word gradient fill/bounce off the word start times.
 */
fun wordSyncedToEntries(lines: List<WordSyncedLine>): List<List<Pair<Float, String>>> {
    MediaViewModelObject.otherSideForLines.clear()
    MediaViewModelObject.otherSideForLines.addAll(List(lines.size) { false })
    return lines.map { line ->
        buildList {
            val startMs = line.startTimeMs.toFloat()
            add(startMs to "")
            line.words.filter { !it.isBackground }.forEach { word ->
                add(word.startTimeMs.toFloat() to word.text)
            }
            add(startMs to "")
        }
    }
}

object LyricsProcessor {

    fun applyLyrics(
        onlineLyrics: AudiophileLyrics,
        lrcEntriesSetter: (List<List<Pair<Float, String>>>) -> Unit,
        songDurationMs: Long = 0L,
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
                    val lrcEntries = wordSyncedToEntries(MediaViewModelObject.wordSyncedLines.value)
                    lrcEntriesSetter(lrcEntries)
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