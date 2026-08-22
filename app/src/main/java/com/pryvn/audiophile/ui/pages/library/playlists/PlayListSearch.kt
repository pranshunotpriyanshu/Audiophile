package com.pryvn.audiophile.ui.pages.library.playlists

import androidx.compose.runtime.Stable
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import kotlin.math.max
import kotlin.math.min

@Stable
object PlayListSearch {

    fun matchAndRank(items: List<YosMediaItem>, query: String): List<YosMediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        val threshold = fuzzyThreshold(q.length)
        return items
            .mapNotNull { item ->
                val score = score(item, q, threshold) ?: return@mapNotNull null
                item to score
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** Public for tests / sort comparators. */
    fun fuzzyThreshold(queryLength: Int): Int = when {
        queryLength <= 4 -> 1
        queryLength <= 8 -> 2
        else -> 3
    }

    private fun score(item: YosMediaItem, q: String, threshold: Int): Int? {
        val title = item.title?.lowercase()
        val artists = item.artistsName?.lowercase()
        val album = item.album?.lowercase()
        val albumArtists = item.albumArtists?.lowercase()
        val genre = item.genre?.lowercase()
        val composer = item.composer?.lowercase()
        val writer = item.writer?.lowercase()
        val author = item.author?.lowercase()
        val year = (item.recordingYear ?: item.releaseYear)?.toString()

        if (title == q) return 0
        if (title != null && title.startsWith(q)) return 10
        if (title != null && title.contains(q)) return 20 + title.indexOf(q)
        if (artists?.contains(q) == true) return 100 + artists.indexOf(q)
        if (album?.contains(q) == true) return 110 + album.indexOf(q)
        if (albumArtists?.contains(q) == true) return 120
        if (genre?.contains(q) == true) return 200
        if (composer?.contains(q) == true) return 210
        if (writer?.contains(q) == true) return 220
        if (author?.contains(q) == true) return 230
        if (year != null && year.contains(q)) return 240
        listOfNotNull(title, artists, album, albumArtists, genre, composer, writer, author)
            .forEachIndexed { idx, field ->
                val d = bestEditDistance(field, q, threshold)
                if (d != null) return 1000 + idx * 100 + d * 10
            }
        return null
    }

    private fun bestEditDistance(haystack: String?, query: String, threshold: Int): Int? {
        if (haystack == null) return null
        if (haystack.length + threshold < query.length) return null
        val minLen = max(1, query.length - threshold)
        val maxLen = query.length + threshold

        var best: Int? = null
        for (start in 0..haystack.length) {
            for (len in minLen..maxLen) {
                val end = start + len
                if (end > haystack.length) break
                val sub = haystack.substring(start, end)
                val d = boundedLevenshtein(query, sub, threshold)
                if (d != null && (best == null || d < best)) {
                    best = d
                    if (best == 0) return 0
                }
            }
        }
        return best
    }

    private fun boundedLevenshtein(a: String, b: String, threshold: Int): Int? {
        if (kotlin.math.abs(a.length - b.length) > threshold) return null
        val n = a.length
        val m = b.length
        if (n == 0) return if (m <= threshold) m else null
        if (m == 0) return if (n <= threshold) n else null

        var previous = IntArray(m + 1) { it }
        var current = IntArray(m + 1)

        for (i in 1..n) {
            current[0] = i
            var rowMin = current[0]
            val jStart = max(1, i - threshold)
            val jEnd = min(m, i + threshold)
            // don't pollute later min() calls.
            for (j in 1..m) {
                current[j] = if (j in jStart..jEnd) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    minOf(
                        previous[j] + 1,        // deletion
                        current[j - 1] + 1,      // insertion
                        previous[j - 1] + cost,  // substitution
                    )
                } else {
                    threshold + 1
                }
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > threshold) return null
            // Swap rows.
            val tmp = previous
            previous = current
            current = tmp
        }
        val result = previous[m]
        return if (result <= threshold) result else null
    }
}
