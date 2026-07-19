package com.pryvn.audiophile.code.utils.lyrics

import com.pryvn.audiophile.code.utils.lrc.TTMLParser
import com.pryvn.audiophile.data.objects.LyricsEntry
import com.pryvn.audiophile.data.objects.WordTimestamp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Converts raw lyrics text to List<LyricsEntry> using Audiophile's TTMLParser.
 */
object LyricsEntryBridge {
    private const val LYRICS_NOT_FOUND_MARKER = "LYRICS_NOT_FOUND"

    fun fromRawLyrics(lyrics: String?, durationMs: Long = 0L): List<LyricsEntry> {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND_MARKER) return emptyList()

        return when {
            TTMLParser.isTtml(lyrics) -> {
                val parsed = TTMLParser.parseTTML(lyrics)
                val entries = parsed.map { pl -> parsedLineToEntry(pl) }
                if (entries.isNotEmpty() && entries.first().time >= 0) {
                    listOf(LyricsEntry.HEAD_LYRICS_ENTRY) + entries
                } else {
                    entries
                }
            }
            TTMLParser.isLineSyncedLrc(lyrics) -> {
                val parsed = TTMLParser.parseSyncedLrc(lyrics)
                val dur = if (durationMs > 0L) durationMs / 1000.0 else 0.0
                val withBreaks = TTMLParser.insertInstrumentalBreaks(
                    parsed,
                    if (durationMs > 0L) durationMs else 30000L
                )
                val entries = withBreaks.map { pl -> parsedLineToEntry(pl) }
                if (entries.isNotEmpty() && entries.first().time >= 0) {
                    listOf(LyricsEntry.HEAD_LYRICS_ENTRY) + entries
                } else {
                    entries
                }
            }
            else -> {
                lyrics.lines()
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, line ->
                        LyricsEntry(time = -1L, text = line.trim())
                    }
            }
        }
    }

    private fun parsedLineToEntry(pl: com.pryvn.audiophile.code.utils.lrc.ParsedLine): LyricsEntry {
        val words = if (pl.words.isNotEmpty()) {
            pl.words.map { pw ->
                WordTimestamp(
                    text = pw.text,
                    startTime = pw.startTime,
                    endTime = pw.endTime,
                    isBackground = pw.isBackground
                )
            }
        } else null

        val text = if (words != null) words.joinToString("") { it.text } else pl.text
        val timeMs = (pl.startTime * 1000).toLong()

        // Detect if this is an instrumental break (empty text with timing gap)
        val isInstrumental = text.isBlank() && pl.startTime >= 0

        return LyricsEntry(
            time = timeMs,
            text = text,
            words = words,
            agent = pl.agent,
            isInstrumental = isInstrumental,
            durationMs = ((pl.endTime - pl.startTime) * 1000).toLong().coerceAtLeast(0L),
            romanizedTextFlow = MutableStateFlow(null)
        )
    }
}
