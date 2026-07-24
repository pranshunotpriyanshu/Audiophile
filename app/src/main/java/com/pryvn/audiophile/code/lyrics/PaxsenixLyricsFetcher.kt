package com.pryvn.audiophile.code.lyrics

import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.code.utils.lrc.PaxsenixLyricsParser
import com.pryvn.audiophile.code.utils.lrc.YosLrcFactory
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.WordSyncedLine
import com.pryvn.audiophile.data.objects.WordSyncedWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PaxsenixLyricsFetcher {

    suspend fun fetch(
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long = 0L,
        videoId: String? = null,
    ): AudiophileLyrics? = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(title, artist, album, videoId)

        val cached = MediaViewModelObject.lyricsCache[cacheKey]
        if (cached != null) {
            val isWord = PaxsenixLyricsParser.isTtml(cached) || PaxsenixLyricsParser.isPaxsenixWordSynced(cached)
            println("PaxsenixLyricsFetcher: Cache hit for [$title] by [$artist]")
            return@withContext AudiophileLyrics("Cache", cached, isWordSynced = isWord)
        }

        println("PaxsenixLyricsFetcher: Fetching from API for [$title] by [$artist]")
        val response = PaxsenixApiClient.fetchLyrics(title, artist, album, durationMs, videoId)
            ?: run {
                println("PaxsenixLyricsFetcher: API returned null for [$title] by [$artist]")
                return@withContext null
            }

        println("PaxsenixLyricsFetcher: Got response from ${response.source}, isWordSynced=${response.isWordSynced}, text length=${response.lyrics.length}")

        val (lyricsToStore, isWordSynced, wordSyncedLines) = when {
            response.isWordSynced && PaxsenixLyricsParser.isTtml(response.lyrics) -> {
                val parsed = PaxsenixLyricsParser.parseTtml(response.lyrics)
                if (parsed.isNotEmpty()) {
                    val wsLines = parsed.map { line ->
                        WordSyncedLine(
                            text = line.text,
                            startTimeMs = line.startTimeMs,
                            endTimeMs = line.endTimeMs,
                            words = line.words.map { w ->
                                WordSyncedWord(
                                    text = w.text,
                                    startTimeMs = w.startTimeMs,
                                    endTimeMs = w.endTimeMs,
                                    isBackground = w.isBackground,
                                )
                            },
                        )
                    }
                    val lrcText = toLrcFormat(parsed)
                    Triple(lrcText, true, wsLines)
                } else {
                    Triple(response.lyrics, false, emptyList())
                }
            }
            response.isWordSynced && PaxsenixLyricsParser.isPaxsenixWordSynced(response.lyrics) -> {
                val parsed = PaxsenixLyricsParser.parsePaxsenixWordSynced(response.lyrics)
                if (parsed.isNotEmpty()) {
                    val wsLines = parsed.map { line ->
                        WordSyncedLine(
                            text = line.text,
                            startTimeMs = line.startTimeMs,
                            endTimeMs = line.endTimeMs,
                            words = line.words.map { w ->
                                WordSyncedWord(
                                    text = w.text,
                                    startTimeMs = w.startTimeMs,
                                    endTimeMs = w.endTimeMs,
                                    isBackground = w.isBackground,
                                )
                            },
                        )
                    }
                    val lrcText = toLrcFormat(parsed)
                    Triple(lrcText, true, wsLines)
                } else {
                    Triple(response.lyrics, false, emptyList())
                }
            }
            PaxsenixLyricsParser.isLineSyncedLrc(response.lyrics) -> {
                Triple(response.lyrics, false, emptyList())
            }
            else -> {
                Triple(response.lyrics, false, emptyList())
            }
        }

        if (lyricsToStore.isNotBlank()) {
            MediaViewModelObject.lyricsCache[cacheKey] = lyricsToStore
            if (MediaViewModelObject.lyricsCache.size > 20) {
                val keys = MediaViewModelObject.lyricsCache.keys.toList()
                for (i in 0 until (MediaViewModelObject.lyricsCache.size - 20)) {
                    MediaViewModelObject.lyricsCache.remove(keys[i])
                }
            }
        }

        if (isWordSynced && wordSyncedLines.isNotEmpty()) {
            MediaViewModelObject.hasWordSyncedLyrics.value = true
            MediaViewModelObject.wordSyncedLines.value = wordSyncedLines
        } else {
            MediaViewModelObject.hasWordSyncedLyrics.value = false
            MediaViewModelObject.wordSyncedLines.value = emptyList()
        }

        AudiophileLyrics(response.source, lyricsToStore, isWordSynced = isWordSynced)
    }

    suspend fun fetchAndProcess(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        videoId: String?,
        lrcEntriesSetter: (List<List<Pair<Float, String>>>) -> Unit,
    ): AudiophileLyrics? = withContext(Dispatchers.IO) {
        val lyrics = fetch(title, artist, album, durationMs, videoId)
            ?: return@withContext null

        val text = lyrics.text
        if (text.isBlank()) return@withContext null

        val lrcFactory = YosLrcFactory()
        val isWordSynced = lyrics.isWordSynced || PaxsenixLyricsParser.isTtml(text)

        if (isWordSynced && PaxsenixLyricsParser.isTtml(text)) {
            val parsed = PaxsenixLyricsParser.parseTtml(text)
            if (parsed.isNotEmpty()) {
                MediaViewModelObject.hasWordSyncedLyrics.value = parsed.any { it.words.isNotEmpty() }
                MediaViewModelObject.wordSyncedLines.value = parsed.map { line ->
                    WordSyncedLine(
                        text = line.text,
                        startTimeMs = line.startTimeMs,
                        endTimeMs = line.endTimeMs,
                        words = line.words.map { w ->
                            WordSyncedWord(
                                text = w.text,
                                startTimeMs = w.startTimeMs,
                                endTimeMs = w.endTimeMs,
                                isBackground = w.isBackground,
                            )
                        },
                    )
                }
                val lrcText = PaxsenixLyricsParser.ttmlToLrc(text)
                lrcEntriesSetter(lrcFactory.formatLrcEntries(lrcText))
                return@withContext lyrics
            } else {
                MediaViewModelObject.clearWordSync()
                lrcEntriesSetter(lrcFactory.formatLrcEntries(text))
                return@withContext lyrics
            }
        } else if (isWordSynced && PaxsenixLyricsParser.isPaxsenixWordSynced(text)) {
            val parsed = PaxsenixLyricsParser.parsePaxsenixWordSynced(text)
            if (parsed.isNotEmpty()) {
                MediaViewModelObject.hasWordSyncedLyrics.value = parsed.any { it.words.isNotEmpty() }
                MediaViewModelObject.wordSyncedLines.value = parsed.map { line ->
                    WordSyncedLine(
                        text = line.text,
                        startTimeMs = line.startTimeMs,
                        endTimeMs = line.endTimeMs,
                        words = line.words.map { w ->
                            WordSyncedWord(
                                text = w.text,
                                startTimeMs = w.startTimeMs,
                                endTimeMs = w.endTimeMs,
                                isBackground = w.isBackground,
                            )
                        },
                    )
                }
                val lrcText = toLrcFormat(parsed)
                lrcEntriesSetter(lrcFactory.formatLrcEntries(lrcText))
                return@withContext lyrics
            } else {
                MediaViewModelObject.clearWordSync()
                lrcEntriesSetter(lrcFactory.formatLrcEntries(text))
                return@withContext lyrics
            }
        } else if (PaxsenixLyricsParser.isLineSyncedLrc(text)) {
            MediaViewModelObject.clearWordSync()
            lrcEntriesSetter(lrcFactory.formatLrcEntries(text))
            return@withContext lyrics
        } else {
            MediaViewModelObject.clearWordSync()
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val dummyLrc = lines.mapIndexed { idx, line ->
                    val time = String.format("[%02d:%05.2f]", idx * 30, (idx * 30) % 60)
                    "$time$line"
                }.joinToString("\n")
                lrcEntriesSetter(lrcFactory.formatLrcEntries(dummyLrc))
            }
            return@withContext lyrics
        }
    }

    private fun toLrcFormat(lines: List<com.pryvn.audiophile.code.lyrics.ParsedWordSyncedLine>): String =
        lines.joinToString("\n") { line ->
            val min = line.startTimeMs / 60000
            val sec = (line.startTimeMs % 60000) / 1000
            val ms = (line.startTimeMs % 1000) / 10
            "[%02d:%02d.%02d]%s".format(min, sec, ms, line.text)
        }

    private fun buildCacheKey(title: String, artist: String, album: String?, videoId: String?): String =
        videoId ?: "$title|$artist|${album ?: ""}".take(200)

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