package com.pryvn.audiophile.code.utils.lrc

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastJoinToString
import com.pryvn.audiophile.data.objects.MediaViewModelObject

/**
 * LRC lyrics text processing
 */
class YosLrcFactory(private val formatText: Boolean = true) {
    /**
     * LRC lyrics text processing method
     * @param lrcText LRC format text
     */
    /*fun formatLrcEntries(lrcText: String): List<Pair<Float, String>> {
        val lrcLines = lrcText.lines()
        return lrcLines.mapNotNull { line ->
            val timeIndex = line.indexOf("]")
            if (timeIndex == -1) return@mapNotNull null
            val timeText = line.substring(1, timeIndex)
            val timeParts = timeText.split(":")
            if (timeParts.size != 2) return@mapNotNull null
            val minutes = timeParts[0].toIntOrNull() ?: return@mapNotNull null
            val seconds = timeParts[1].toFloatOrNull() ?: return@mapNotNull null
            val time = (minutes * 60 + seconds) * 1000
            val lyric = line.substring(timeIndex + 1)
            if (lyric.isBlank() || lyric.trim() == "//") return@mapNotNull null
            time to if (formatText) lyric.replace(Regex("(?!\\n)\\s+"), " ").trim() else lyric
        }
    }*/
    fun formatLrcEntries(lrcText: String): List<List<Pair<Float, String>>> {
        val lrcLines = lrcText.lines()
        val timeLyricPairs = mutableListOf<MutableList<Pair<Float, String>>>()
        lrcLines.fastForEachIndexed { index, line ->
            var remainingLine =
                line.replace(Regex("([\\[\\]]){2,}"), "$1").replace(Regex("<([^>]+)>"), "[$1]")
                    .replace(Regex("(\\[\\d{2}:\\d{2}\\.\\d{2,3}]){2,}"), "$1")
            println("lyrics processing: $remainingLine")
            val currentLinePairs = mutableListOf<Pair<Float, String>>()
            while (remainingLine.isNotEmpty()) {
                /*val timeIndex = remainingLine.indexOf("]")
                if (timeIndex == -1) break
                val timeText = remainingLine.substring(1, timeIndex)
                val timeParts = timeText.split(":")
                if (timeParts.size != 2) break
                val minutes = timeParts[0].toIntOrNull() ?: break
                val seconds = timeParts[1].toFloatOrNull() ?: break
                val time = (minutes * 60 + seconds) * 1000
                remainingLine = remainingLine.substring(timeIndex + 1)
                val nextTimeIndex = remainingLine.indexOf("[")
                val lyric = if (nextTimeIndex != -1) {
                    remainingLine.substring(0, nextTimeIndex)
                } else {
                    remainingLine
                }*/

                val timeIndex = remainingLine.indexOf("[")
                if (timeIndex == -1) break
                val timeAfter = remainingLine.indexOf("]")
                if (timeAfter == -1) break
                val timeText = remainingLine.substring(timeIndex + 1, timeAfter)
                val timeParts = timeText.split(":")
                if (timeParts.size != 2) break
                val minutes = timeParts[0].toIntOrNull() ?: break
                val seconds = timeParts[1].toFloatOrNull() ?: break
                val time = (minutes * 60 + seconds) * 1000

                if (remainingLine.substring(timeAfter + 1, remainingLine.length)
                        .isBlank() && remainingLine.substring(0, timeIndex).isBlank()
                ) {
                    // Check next line's time difference
                    if (index + 1 < lrcLines.size) {
                        val nextLine = lrcLines[index + 1]
                        val nextTimeIndex = nextLine.indexOf("[")
                        val nextTimeAfter = nextLine.indexOf("]")
                        if (nextTimeIndex != -1 && nextTimeAfter != -1) {
                            val nextTimeText = nextLine.substring(nextTimeIndex + 1, nextTimeAfter)
                            val nextTimeParts = nextTimeText.split(":")
                            if (nextTimeParts.size == 2) {
                                val nextMinutes = nextTimeParts[0].toIntOrNull()
                                val nextSeconds = nextTimeParts[1].toFloatOrNull()
                                if (nextMinutes != null && nextSeconds != null) {
                                    val nextTime = (nextMinutes * 60 + nextSeconds) * 1000
                                    if (nextTime - time <= 4200) {
                                        // Skip current line, process next line
                                        break
                                    }
                                }
                            }
                        }
                    } else {
                        // This is the last line and it's empty
                        break
                    }
                }

                val nextTimeIndex = remainingLine.substring(timeAfter + 1).indexOf("[")

                // Line start or word end
                var lyric = remainingLine.substring(0, timeIndex)

                if (lyric.isEmpty()) {
                    // Sentence start
                    lyric = ""
                    currentLinePairs.add(time to lyric.replace(Regex("(?!\\n)\\s+"), " "))
                } else {
                    // Normal sentence component
                    if (/*lyric.isNotBlank() && */lyric.trim() != "//") {
                        currentLinePairs.add(
                            time to lyric.replace(Regex("(?!\\n)\\s+"), " ")
                        )
                    }
                }

                remainingLine = remainingLine.substring(timeAfter + 1)
                if (nextTimeIndex == -1) {
                    if (lyric == "") {
                        currentLinePairs.add(
                            time to remainingLine.replace("//", "").replace(
                                Regex("(?!\\n)\\s+"),
                                " "
                            )/*.trim()*/
                        )
                    }
                    remainingLine = ""
                }
            }
            if (currentLinePairs.isNotEmpty()) {
                val existingList =
                    timeLyricPairs.find { it.first().first == currentLinePairs.first().first }
                if (existingList != null) {
                    // existingList.remove(currentLinePairs[0].first to "")
                    existingList.addAll(currentLinePairs)
                } else {
                    currentLinePairs.add(currentLinePairs[0].first to "")
                    timeLyricPairs.add(currentLinePairs)
                }
            }
        }
        val processedEntries = processOtherSide(timeLyricPairs)
        return processedEntries.filter { it.isNotEmpty() }
    }

    private fun processOtherSide(lrcEntries: List<List<Pair<Float, String>>>): List<List<Pair<Float, String>>> {
        // Duet handling
        val otherSideResult = mutableStateListOf<Boolean>()
        val backgroundResult = mutableStateListOf<Boolean>()
        var otherSide = false
        var lastSinger: String? = null
        var otherSideFirstTime = false

        // Vocal-agent marker right after the line timestamp, mirroring word-synced
        // lyrics: "v1:"/"v1000:" = first vocalist (left side), "v2:"/"v2000:" =
        // second vocalist (right side), "bg:" = background vocal. The marker is
        // stripped from the rendered text.
        val voicePrefixRegex = Regex("""^(v\d+|bg):\s*""")
        val singerOnlyRegex = Regex(".+\\s*:\\s*")

        val filteredLrcEntries = lrcEntries.map { lines ->
            val lyric = lines.fastJoinToString(separator = "", transform = {
                it.second
            })

            var deleteType = -1

            val voiceMatch = voicePrefixRegex.find(lyric)
            // "bg:" marks a background-vocal line: the line-synced renderer draws
            // it smaller and dimmer (CArchiveTune background styling). The marker
            // itself is stripped from the rendered text below.
            val isBackgroundLine = voiceMatch?.groupValues?.get(1)?.equals("bg", ignoreCase = true) == true
            if (voiceMatch != null) {
                // Deterministic side from the vocal agent.
                val agent = voiceMatch.groupValues[1]
                when (agent) {
                    "v2", "v2000" -> otherSide = true
                    "v1", "v1000" -> otherSide = false
                    else -> { /* bg etc.: keep the current side */ }
                }
                deleteType = 1
            } else if (lyric.endsWith(":") || lyric.endsWith("：")) {
                otherSide = !otherSide
            } else if (lines.size > 1) {
                val currentSinger = lines[1].second
                if (currentSinger.matches(singerOnlyRegex)) {
                    deleteType = 0
                    if (lastSinger != null && lastSinger == currentSinger) {
                        // Keep otherSide unchanged
                    } else {
                        if (otherSideFirstTime) {
                            otherSide = !otherSide
                        } else {
                            otherSideFirstTime = true
                        }
                    }
                    lastSinger = currentSinger
                }
            }

            otherSideResult.add(otherSide)
            backgroundResult.add(isBackgroundLine)

            if (deleteType == 1 && voiceMatch != null) {
                // Strip the vocal-agent marker from the first text pair.
                lines.mapIndexedNotNull { index, char ->
                    if (index == 1) {
                        val stripped = char.second.removePrefix(voiceMatch.value)
                        if (stripped.isBlank()) null else char.first to stripped
                    } else {
                        char
                    }
                }
            } else {
                lines.filterIndexed { index, char ->
                    !((index == 1 && char.second.matches(singerOnlyRegex)) && deleteType == 0)
                }
            }
        }

        MediaViewModelObject.otherSideForLines.clear()
        MediaViewModelObject.otherSideForLines.addAll(otherSideResult)
        MediaViewModelObject.backgroundLines.clear()
        MediaViewModelObject.backgroundLines.addAll(backgroundResult)
        //println(MediaViewModelObject.otherSideForLines)

        //println(filteredLrcEntries)
        return filteredLrcEntries
    }
}

/*
private fun String.ifNeedMirror(): Boolean {
    val directionality = Character.getDirectionality(this.trim().first())
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}*/
