package com.pryvn.audiophile.code.utils.lyrics

import com.pryvn.audiophile.data.objects.LyricsEntry
import com.pryvn.audiophile.data.objects.WordTimestamp

/**
 * Adapter to convert App 1's LyricsEntry format to App 2's YosLyricView format.
 * 
 * App 1 format: List<LyricsEntry> with WordTimestamp objects
 * App 2 format: List<List<Pair<Float, String>>> where each inner list represents
 *              a line with word-level timing: (timeMs, text)
 */
object YosFormatAdapter {

    /**
     * Converts App 1's LyricsEntry list to App 2's format.
     * 
     * @param entries App 1's lyrics entries
     * @return App 2 format: List<List<Pair<Float, String>>>
     */
    fun convertToYosFormat(entries: List<LyricsEntry>): List<List<Pair<Float, String>>> {
        if (entries.isEmpty()) return emptyList()

        return entries.map { entry ->
            convertEntryToYosLine(entry)
        }
    }

    /**
     * Converts a single LyricsEntry to App 2's line format.
     * 
     * For word-synced lyrics: creates time pairs for each word
     * For line-synced lyrics: creates a single time pair for the whole line
     */
    private fun convertEntryToYosLine(entry: LyricsEntry): List<Pair<Float, String>> {
        val result = mutableListOf<Pair<Float, String>>()

        // Add line start marker (empty string at line start time)
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

    /**
     * Extracts singer information for duet positioning.
     * App 2 detects singers by looking for ":" suffix at end of words.
     * We'll adapt this to work with App 1's data structure.
     */
    fun detectSingerAlignment(entry: LyricsEntry): Boolean {
        // Returns true if this line should be right-aligned (second singer)
        // This is a simplified version - App 2 has more sophisticated detection
        val text = entry.text.trim()
        return text.endsWith(":") || text.endsWith("：")
    }

    /**
     * Checks if lyrics have word-level timing for word-by-word highlighting.
     */
    fun hasWordTiming(entries: List<LyricsEntry>): Boolean {
        return entries.any { !it.words.isNullOrEmpty() }
    }

    /**
     * Gets the next line's start time for animation timing.
     */
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
