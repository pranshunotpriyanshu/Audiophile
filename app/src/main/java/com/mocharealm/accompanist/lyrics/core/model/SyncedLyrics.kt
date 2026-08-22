package com.mocharealm.accompanist.lyrics.core.model

data class SyncedLyrics(
    val lines: List<ISyncedLine>,
    val title: String = "",
    val id: String = "0",
    val artists: List<Artist>? = emptyList(),
) {
    fun getCurrentFirstHighlightLineIndexByTime(time: Int): Int {
        if (lines.isEmpty()) return 0

        var low = 0
        var high = lines.size - 1
        var resultIndex = lines.size

        while (low <= high) {
            val mid = low + (high - low) / 2
            val line = lines[mid]

            if (line.start > time) {
                resultIndex = mid
                high = mid - 1
            } else if (line.end < time) {
                low = mid + 1
            } else {
                resultIndex = mid
                high = mid - 1
            }
        }

        return if (resultIndex < lines.size && time in lines[resultIndex].start..lines[resultIndex].end) {
            resultIndex
        } else {
            low.coerceAtMost(lines.size)
        }
    }

    fun getCurrentAllHighlightLineIndicesByTime(time: Int): List<Int> {
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<Int>()

        var low = 0
        var high = lines.size - 1
        var firstAfterIndex = lines.size

        while (low <= high) {
            val mid = low + (high - low) / 2
            if (lines[mid].start > time) {
                firstAfterIndex = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        for (i in (firstAfterIndex - 1) downTo 0) {
            val line = lines[i]

            if (time in line.start..line.end) {
                results.add(i)
            }
        }

        return results.sorted()
    }
}
