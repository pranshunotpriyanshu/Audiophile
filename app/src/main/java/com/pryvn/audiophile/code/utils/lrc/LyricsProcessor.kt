package com.pryvn.audiophile.code.utils.lrc

import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.code.lyrics.LyricsCacheStore
import com.pryvn.audiophile.code.lyrics.LyricsHelper
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.WordSyncedLine
import com.pryvn.audiophile.data.objects.WordSyncedWord
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

const val BACKGROUND_WORD_MARKER = "\u200B"

fun wordSyncedToEntries(lines: List<WordSyncedLine>): List<List<Pair<Float, String>>> {
    MediaViewModelObject.otherSideForLines.clear()
    MediaViewModelObject.otherSideForLines.addAll(
        lines.map { it.agent?.equals("v2", ignoreCase = true) == true }
    )
    MediaViewModelObject.backgroundLines.clear()
    MediaViewModelObject.backgroundLines.addAll(lines.map { line -> line.words.any { it.isBackground } })
    MediaViewModelObject.lyricLineTransliterations.clear()
    MediaViewModelObject.lyricLineTransliterations.addAll(lines.map { it.transliteration })
    MediaViewModelObject.lyricLineSubtitles.clear()
    MediaViewModelObject.lyricLineSubtitles.addAll(lines.map { it.subtitle })
    return lines.map { line ->
        buildList {
            val startMs = line.startTimeMs.toFloat()
            add(startMs to "")
            line.words.forEach { word ->
                val text = word.text.trimEnd()
                val marked = if (word.isBackground && text.isNotEmpty()) "$BACKGROUND_WORD_MARKER$text" else text
                val withSpacing = when {
                    text.isEmpty() || text.last().isCjkChar() || text.endsWith("：") || text.endsWith(":") -> marked
                    else -> "$marked "
                }
                add(word.endTimeMs.toFloat() to withSpacing)
            }
            add(startMs to "")
        }
    }
}

private fun Char.isCjkChar(): Boolean {
    val c = code
    return c in 0x3040..0x30FF || // Hiragana / Katakana
        c in 0x3400..0x4DBF || // CJK Extension A
        c in 0x4E00..0x9FFF || // CJK Unified Ideographs
        c in 0xF900..0xFAFF    // CJK Compatibility Ideographs
}

object LyricsProcessor {

    private val autoParser = AutoParser()

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#x22;", "\"")
            .replace("&#x3C;", "<")
            .replace("&#x3E;", ">")
            .replace("&#x26;", "&")
    }

    fun applyLyrics(
        onlineLyrics: AudiophileLyrics,
        lrcEntriesSetter: (List<List<Pair<Float, String>>>) -> Unit,
        songDurationMs: Long = 0L,
    ) {
        val text = decodeHtmlEntities(onlineLyrics.text)
        if (text.isBlank()) return

        MediaViewModelObject.onlineLyrics.value = text
        MediaViewModelObject.lyricsSource.value = onlineLyrics.provider

        try {
            val parsed = autoParser.parse(text)
            MediaViewModelObject.parsedSyncedLyrics.value = parsed
        } catch (_: Exception) {
            MediaViewModelObject.parsedSyncedLyrics.value = null
        }

        val lrcFactory = YosLrcFactory()
        val isWordSynced = onlineLyrics.isWordSynced || TTMLParser.isTtml(text)

        when {
            TTMLParser.isKaraokeSyncedLrc(text) -> {
                val parsed = lrcToWordLines(text)
                if (parsed.isNotEmpty()) {
                    MediaViewModelObject.hasWordSyncedLyrics.value = parsed.any { it.words.isNotEmpty() }
                    MediaViewModelObject.wordSyncedLines.value = parsed
                    lrcEntriesSetter(wordSyncedToEntries(parsed))
                } else {
                    clearWordSync()
                    lrcEntriesSetter(lrcFactory.formatLrcEntries(text))
                }
            }
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
                            agent = line.agent,
                            transliteration = line.transliteration,
                            subtitle = line.subtitle,
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
                MediaViewModelObject.isUnsyncedLyrics.value = true
                val lines = text.lines().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    val dummyLrc = lines.mapIndexed { idx, line ->
                        val time = String.format("[%02d:%05.2f]", idx * 30, ((idx * 30) % 60).toFloat())
                        "$time$line"
                    }.joinToString("\n")
                    lrcEntriesSetter(lrcFactory.formatLrcEntries(dummyLrc))
                }
            }
        }
    }

    private fun lrcToWordLines(text: String): List<WordSyncedLine> =
        TTMLParser.parseSyncedLrc(text).map { line ->
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
                agent = line.agent,
                transliteration = null,
                subtitle = null,
            )
        }

    suspend fun refetchLyrics(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        videoId: String?,
    ): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        MediaViewModelObject.isLoadingLyrics.value = true
        try {
            val currentText = MediaViewModelObject.onlineLyrics.value
            val currentScore = currentText?.let {
                LyricsHelper.scoreLyrics(
                    AudiophileLyrics(
                        provider = MediaViewModelObject.lyricsSource.value ?: "",
                        text = it,
                        isWordSynced = MediaViewModelObject.hasWordSyncedLyrics.value,
                    )
                )
            } ?: 0

            val fresh = try {
                ArchiveTuneApis.fetchLyrics(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    videoId = videoId,
                )
            } catch (_: Exception) {
                null
            }

            if (fresh == null || fresh.text.isBlank() ||
                LyricsHelper.scoreLyrics(fresh) <= currentScore
            ) {
                return@withContext false
            }

            val cacheKey = videoId ?: (title ?: "unknown")
            MediaViewModelObject.lyricsCache[cacheKey] = fresh.text
            LyricsCacheStore.put(cacheKey, fresh.text)
            if (MediaViewModelObject.lyricsCache.size > 20) {
                val keys = MediaViewModelObject.lyricsCache.keys.toList()
                for (i in 0 until (MediaViewModelObject.lyricsCache.size - 20)) {
                    MediaViewModelObject.lyricsCache.remove(keys[i])
                }
            }
            applyLyrics(fresh, lrcEntriesSetter = { MediaViewModelObject.lrcEntries.value = it })
            true
        } finally {
            MediaViewModelObject.isLoadingLyrics.value = false
        }
    }

    fun clearWordSync() {
        MediaViewModelObject.hasWordSyncedLyrics.value = false
        MediaViewModelObject.wordSyncedLines.value = emptyList()
        MediaViewModelObject.isUnsyncedLyrics.value = false
    }

    fun resetLyricsState() {
        clearWordSync()
        MediaViewModelObject.parsedSyncedLyrics.value = null
        MediaViewModelObject.onlineLyrics.value = null
        MediaViewModelObject.lyricsSource.value = null
    }
}
