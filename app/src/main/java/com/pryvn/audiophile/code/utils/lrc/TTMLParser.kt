package com.pryvn.audiophile.code.utils.lrc

import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedWord(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val isBackground: Boolean = false
)

data class ParsedLine(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val words: List<ParsedWord> = emptyList(),
    val isBackground: Boolean = false,
    val agent: String? = null
)

object TTMLParser {

    fun isTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("<") && (trimmed.contains("<tt") || trimmed.contains("ttml"))
    }

    fun isLineSyncedLrc(lyrics: String): Boolean {
        val timeRegex = Regex("""\[\d{2}:\d{2}(\.\d{2,3})?\]""")
        return timeRegex.containsMatchIn(lyrics)
    }

    fun parseTTML(ttml: String): List<ParsedLine> {
        if (!isTtml(ttml)) return emptyList()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isCoalescing = false
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(ttml.toByteArray()))
            doc.documentElement.normalize()

            val lines = mutableListOf<ParsedLine>()
            val pTags = doc.getElementsByTagNameNS("*", "p")
            if (pTags.length == 0) return parseTtmlRegex(ttml)

            for (i in 0 until pTags.length) {
                val p = pTags.item(i)
                val begin = p.attributes?.getNamedItem("begin")?.textContent
                    ?: p.attributes?.getNamedItemNS("*", "begin")?.textContent ?: continue
                val end = p.attributes?.getNamedItem("end")?.textContent
                    ?: p.attributes?.getNamedItemNS("*", "end")?.textContent
                val agent = p.attributes?.getNamedItemNS("*", "agent")?.textContent
                    ?: p.attributes?.getNamedItem("agent")?.textContent

                val startTime = parseTtmlTime(begin)
                val endTime = if (end != null) parseTtmlTime(end) else startTime + 3.0

                val words = mutableListOf<ParsedWord>()
                val spans = p.childNodes
                var hasWordTiming = false
                var plainText = ""

                for (j in 0 until spans.length) {
                    val child = spans.item(j)
                    if (child.nodeName.equals("span", ignoreCase = true) ||
                        child.localName?.equals("span", ignoreCase = true) == true
                    ) {
                        val wBegin = child.attributes?.getNamedItem("begin")?.textContent
                            ?: child.attributes?.getNamedItemNS("*", "begin")?.textContent
                        val wEnd = child.attributes?.getNamedItem("end")?.textContent
                            ?: child.attributes?.getNamedItemNS("*", "end")?.textContent
                        val wText = child.textContent?.trim() ?: ""
                        val isBg = child.attributes?.getNamedItem("role")?.textContent == "x-bg"
                            || child.attributes?.getNamedItem("class")?.textContent == "x-bg"

                        if (wText.isNotBlank()) {
                            if (wBegin != null) {
                                hasWordTiming = true
                                val ws = parseTtmlTime(wBegin)
                                val we = if (wEnd != null) parseTtmlTime(wEnd) else ws + 0.3
                                words.add(ParsedWord(wText, ws, we, isBg))
                            } else {
                                plainText = wText
                            }
                        }
                    } else if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                        val text = child.textContent?.trim() ?: ""
                        if (text.isNotBlank()) plainText = text
                    }
                }

                val isBackground = agent != null || words.any { it.isBackground }

                if (!hasWordTiming && plainText.isNotBlank()) {
                    val chars = mutableListOf<ParsedWord>()
                    val charList = plainText.toList()
                    val charDuration = (endTime - startTime) / charList.size.coerceAtLeast(1)
                    charList.forEachIndexed { idx, c ->
                        if (c != ' ') {
                            chars.add(
                                ParsedWord(
                                    c.toString(),
                                    startTime + idx * charDuration,
                                    startTime + (idx + 1) * charDuration,
                                    isBackground
                                )
                            )
                        }
                    }
                    if (chars.isNotEmpty()) {
                        lines.add(ParsedLine(plainText, startTime, endTime, chars, isBackground, agent))
                    } else if (plainText.isNotBlank()) {
                        lines.add(ParsedLine(plainText, startTime, endTime, emptyList(), isBackground, agent))
                    }
                } else if (hasWordTiming) {
                    lines.add(ParsedLine(plainText.ifBlank {
                        words.joinToString("") { it.text }
                    }, startTime, endTime, words, isBackground, agent))
                }
            }
            return lines.sortedBy { it.startTime }
        } catch (_: Exception) {
            return parseTtmlRegex(ttml)
        }
    }

    private fun parseTtmlRegex(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        val lineRegex = Regex(
            """<p\s+[^>]*begin="([^"]+)"[^>]*(?:end="([^"]+)")?[^>]*>(.*?)</p>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in lineRegex.findAll(ttml)) {
            val begin = match.groupValues[1]
            val end = match.groupValues[2]
            val content = match.groupValues[3]
            val startTime = parseTtmlTime(begin)
            val endTime = if (end.isNotBlank()) parseTtmlTime(end) else startTime + 3.0
            val isBackground = content.contains("role=\"x-bg\"") || content.contains("class=\"x-bg\"")
            val agent = Regex("""ttm:agent="([^"]+)"""").find(content)?.groupValues?.get(1)

            val spanRegex = Regex(
                """<span[^>]*(?:begin="([^"]+)")?[^>]*(?:end="([^"]+)")?[^>]*>(.*?)</span>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            var plainText = content
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val words = mutableListOf<ParsedWord>()
            var hasWordTiming = false

            for (span in spanRegex.findAll(content)) {
                val wBegin = span.groupValues[1]
                val wEnd = span.groupValues[2]
                val wText = span.groupValues[3]
                    .replace(Regex("<[^>]+>"), "")
                    .trim()
                if (wText.isNotBlank() && wBegin.isNotBlank()) {
                    hasWordTiming = true
                    val ws = parseTtmlTime(wBegin)
                    val we = if (wEnd.isNotBlank()) parseTtmlTime(wEnd) else ws + 0.3
                    words.add(ParsedWord(wText, ws, we, isBackground))
                } else if (wText.isNotBlank()) {
                    plainText = wText
                }
            }

            if (!hasWordTiming) {
                val chars = mutableListOf<ParsedWord>()
                val charList = plainText.toList()
                val charDuration = (endTime - startTime) / charList.size.coerceAtLeast(1)
                charList.forEachIndexed { i, c ->
                    if (c != ' ') {
                        chars.add(ParsedWord(c.toString(), startTime + i * charDuration, startTime + (i + 1) * charDuration, isBackground))
                    }
                }
                if (chars.isNotEmpty()) {
                    lines.add(ParsedLine(plainText, startTime, endTime, chars, isBackground, agent))
                } else if (plainText.isNotBlank()) {
                    lines.add(ParsedLine(plainText, startTime, endTime, emptyList(), isBackground, agent))
                }
            } else {
                lines.add(ParsedLine(plainText, startTime, endTime, words, isBackground, agent))
            }
        }
        return lines.sortedBy { it.startTime }
    }

    fun parseTtmlTime(value: String): Double {
        val clean = value.trim().removeSuffix("s").removeSuffix("ms")
        if (clean.endsWith("t")) {
            return clean.removeSuffix("t").toDoubleOrNull() ?: 0.0
        }
        if (clean.endsWith("f")) {
            return (clean.removeSuffix("f").toDoubleOrNull() ?: 0.0) / 30.0
        }
        val parts = clean.split(":", ";")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toDoubleOrNull() ?: 0.0
                val m = parts[1].toDoubleOrNull() ?: 0.0
                val s = parts[2].toDoubleOrNull() ?: 0.0
                h * 3600.0 + m * 60.0 + s
            }
            2 -> {
                val m = parts[0].toDoubleOrNull() ?: 0.0
                val s = parts[1].toDoubleOrNull() ?: 0.0
                m * 60.0 + s
            }
            else -> clean.toDoubleOrNull() ?: 0.0
        }
    }

    private val lrcTimeRegex = Regex("""\[(\d{2}):(\d{2}(?:\.\d{2,3})?)\]""")

    fun parseSyncedLrc(lrcText: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        val rawLines = lrcText.lines().filter { it.isNotBlank() }
        for (rawLine in rawLines) {
            val timeMatches = lrcTimeRegex.findAll(rawLine)
            val times = timeMatches.map { match ->
                val mins = match.groupValues[1].toIntOrNull() ?: 0
                val secs = match.groupValues[2].toFloatOrNull() ?: 0f
                mins * 60.0 + secs.toDouble()
            }.toList()
            if (times.isEmpty()) continue
            val textPart = rawLine.replace(lrcTimeRegex, "").trim()
            if (textPart.isBlank()) continue
            val lineStart = times.first()
            val lineEnd = times.last() + 2.0

            val wordRegex = Regex("""<(\d{2}:\d{2}(?:\.\d{2,3})?)>""")
            val wordMatches = wordRegex.findAll(textPart).toList()
            if (wordMatches.isNotEmpty()) {
                val words = mutableListOf<ParsedWord>()
                var lastEnd = lineStart
                var lastIndex = 0
                for (wm in wordMatches) {
                    val before = textPart.substring(lastIndex, wm.range.first).trim()
                    if (before.isNotBlank()) {
                        val wordEnd = parseLrcTimeToSec(wm.groupValues[1])
                        words.add(ParsedWord(before, lastEnd, wordEnd))
                        lastEnd = wordEnd
                    }
                    lastIndex = wm.range.last + 1
                }
                val remaining = textPart.substring(lastIndex).trim()
                if (remaining.isNotBlank()) {
                    words.add(ParsedWord(remaining, lastEnd, lineEnd))
                }
                if (words.isNotEmpty()) {
                    lines.add(ParsedLine(textPart, lineStart, lineEnd, words))
                }
            } else {
                lines.add(ParsedLine(textPart, lineStart, lineEnd, emptyList()))
            }
        }
        return lines
    }

    fun insertInstrumentalBreaks(entries: List<ParsedLine>, songDurationMs: Long): List<ParsedLine> {
        if (entries.isEmpty()) return entries
        val result = mutableListOf<ParsedLine>()
        val gapThreshold = 5.0
        val songDurationSec = songDurationMs / 1000.0

        val firstStart = entries.first().startTime
        if (firstStart > gapThreshold) {
            result.add(ParsedLine("", 0.0, firstStart, emptyList()))
        }

        for (i in entries.indices) {
            result.add(entries[i])
            if (i < entries.size - 1) {
                val gap = entries[i + 1].startTime - entries[i].endTime
                if (gap > gapThreshold) {
                    result.add(ParsedLine("", entries[i].endTime, entries[i + 1].startTime, emptyList()))
                }
            }
        }

        val lastEnd = entries.last().endTime
        if (songDurationSec > 0 && lastEnd < songDurationSec - gapThreshold) {
            result.add(ParsedLine("", lastEnd, songDurationSec, emptyList()))
        }

        return result
    }

    private fun parseLrcTimeToSec(time: String): Double {
        val parts = time.split(":")
        if (parts.size != 2) return 0.0
        val mins = parts[0].toIntOrNull() ?: 0
        val secs = parts[1].toFloatOrNull() ?: 0f
        return mins * 60.0 + secs.toDouble()
    }

    fun ttmlToLrc(text: String): String {
        if (!text.trimStart().startsWith("<")) return text
        val lineRegex = Regex("""<p[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        return lineRegex.findAll(text).joinToString("\n") { match ->
            val timestamp = ttmlTimeToLrc(match.groupValues[1])
            val line = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            "[$timestamp]$line"
        }
    }

    private fun ttmlTimeToLrc(value: String): String {
        val parts = value.removeSuffix("s").split(":")
        val seconds = when (parts.size) {
            3 -> parts[0].toFloat() * 3600 + parts[1].toFloat() * 60 + parts[2].toFloat()
            2 -> parts[0].toFloat() * 60 + parts[1].toFloat()
            else -> parts.firstOrNull()?.toFloatOrNull() ?: 0f
        }
        val minutes = (seconds / 60).toInt()
        val remaining = seconds - minutes * 60
        return "%02d:%05.2f".format(minutes, remaining)
    }
}