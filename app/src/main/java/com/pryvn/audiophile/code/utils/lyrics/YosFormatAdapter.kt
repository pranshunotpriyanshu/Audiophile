package com.pryvn.audiophile.code.utils.lyrics

import com.pryvn.audiophile.data.objects.LyricsEntry
import com.pryvn.audiophile.data.objects.WordTimestamp

object YosFormatAdapter {

    fun convertToYosFormat(entries: List<LyricsEntry>): List<List<Pair<Float, String>>> {
        if (entries.isEmpty()) return emptyList()

        return entries.map { entry ->
            convertEntryToYosLine(entry)
        }
    }

    private fun convertEntryToYosLine(entry: LyricsEntry): List<Pair<Float, String>> {
        val result = mutableListOf<Pair<Float, String>>()

        val lineStartTimeMs = (entry.time / 1000.0).toFloat()
        result.add(lineStartTimeMs to "")

        // Handle word-synced lyrics
        if (!entry.words.isNullOrEmpty()) {
            entry.words.forEach { word ->
                val wordTimeMs = (word.startTime / 1000.0).toFloat()
                result.add(wordTimeMs to word.text)
            }
        } else {
            // Line-synced: add the full text at line start time
            result.add(lineStartTimeMs to entry.text)
        }

        return result
    }

    fun detectSingerAlignment(entry: LyricsEntry): Boolean {
        val text = entry.text.trim()
        return text.endsWith(":") || text.endsWith("：")
    }

    fun hasWordTiming(entries: List<LyricsEntry>): Boolean {
        return entries.any { !it.words.isNullOrEmpty() }
    }

    fun getNextLineTime(entries: List<LyricsEntry>, currentIndex: Int): Float {
        if (currentIndex >= entries.size - 1) {
            // Last line - estimate duration
            val currentEntry = entries.getOrNull(currentIndex)
            if (currentEntry != null && currentEntry.durationMs > 0) {
                return (currentEntry.time + currentEntry.durationMs) / 1000.0f
            }
            return (entries.lastOrNull()?.time ?: 0) / 1000.0f + 3.0f
        }
        return entries[currentIndex + 1].time / 1000.0f
    }
}
