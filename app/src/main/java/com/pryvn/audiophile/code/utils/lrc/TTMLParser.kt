package com.pryvn.audiophile.code.utils.lrc

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

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
    val agent: String? = null,
    val key: String? = null,
    val transliteration: String? = null,
    val subtitle: String? = null
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

            val transliterationByKey = doc.parseMetadataTextByKey("transliterations")
            val subtitleByKey = doc.parseMetadataTextByKey("subtitles")

            for (i in 0 until pTags.length) {
                val p = pTags.item(i)
                val begin = p.attributes?.getNamedItem("begin")?.textContent
                    ?: p.attributes?.getNamedItemNS("*", "begin")?.textContent ?: continue
                val end = p.attributes?.getNamedItem("end")?.textContent
                    ?: p.attributes?.getNamedItemNS("*", "end")?.textContent
                val agent = p.attributes?.getNamedItemNS("*", "agent")?.textContent
                    ?: p.attributes?.getNamedItem("agent")?.textContent
                val key = p.attributes?.getNamedItem("key")?.textContent
                    ?: p.attributes?.getNamedItemNS("*", "key")?.textContent

                val startTime = parseTtmlTime(begin)

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

                // 行结束时间：优先取 end 属性；缺失时回退到最后一个词/字/字符的
                // 结束时间，最后才回退到行开始时间（与 Shourya TtmlFactory 一致），
                // 避免范围重叠导致多行同时高亮。
                val endTime = if (end != null) {
                    parseTtmlTime(end)
                } else {
                    words.lastOrNull()?.endTime ?: startTime
                }

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
                        lines.add(
                            ParsedLine(
                                plainText, startTime, endTime, chars, isBackground, agent,
                                key, transliterationByKey[key], subtitleByKey[key]
                            )
                        )
                    } else if (plainText.isNotBlank()) {
                        lines.add(
                            ParsedLine(
                                plainText, startTime, endTime, emptyList(), isBackground, agent,
                                key, transliterationByKey[key], subtitleByKey[key]
                            )
                        )
                    }
                } else if (hasWordTiming) {
                    lines.add(
                        ParsedLine(
                            plainText.ifBlank { words.joinToString("") { it.text } },
                            startTime, endTime, words, isBackground, agent,
                            key, transliterationByKey[key], subtitleByKey[key]
                        )
                    )
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

            val endTime = if (end.isNotBlank()) {
                parseTtmlTime(end)
            } else {
                words.lastOrNull()?.endTime ?: startTime
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

    private fun Document.parseMetadataTextByKey(containerLocalName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        descendantsByLocalName(containerLocalName).forEach { containerElement ->
            containerElement.descendantsByLocalName("text").forEach { textElement ->
                val textKey = textElement.readAttribute("for") ?: return@forEach
                val text = textElement.textContent.normalizeMetadataText()
                if (text.isNotBlank() && !result.containsKey(textKey)) {
                    result[textKey] = text
                }
            }
        }
        return result
    }

    private fun org.w3c.dom.Node.descendantsByLocalName(localName: String): List<org.w3c.dom.Element> {
        val result = mutableListOf<org.w3c.dom.Element>()
        fun visit(node: org.w3c.dom.Node) {
            val childNodes = node.childNodes
            for (childIndex in 0 until childNodes.length) {
                val childNode = childNodes.item(childIndex)
                if (childNode.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val childElement = childNode as org.w3c.dom.Element
                    if (childElement.localName == localName ||
                        childElement.tagName.substringAfter(':') == localName
                    ) {
                        result.add(childElement)
                    }
                    visit(childElement)
                }
            }
        }
        visit(this)
        return result
    }

    private fun org.w3c.dom.Element.readAttribute(localName: String): String? {
        if (hasAttribute(localName)) {
            return getAttribute(localName).takeIf { it.isNotBlank() }
        }
        for (attributeIndex in 0 until attributes.length) {
            val attribute = attributes.item(attributeIndex)
            if (attribute.localName == localName ||
                attribute.nodeName.substringAfter(':') == localName
            ) {
                return attribute.nodeValue.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun String.normalizeMetadataText(): String {
        return replace(Regex("\\s+"), " ").trim()
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
        // Apple Music / QQ Music enhanced LRC carries a vocal-agent marker right after
        // the line timestamp, e.g. "[00:06.118]v1:<00:06.118>Yeah, ..." or "v2000:"/"bg:".
        // Strip it so it does not leak into the rendered lyric text.
        val voicePrefixRegex = Regex("""^(v\d+|bg):\s*""")
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
            val voiceMatch = voicePrefixRegex.find(textPart)
            val lyricText = if (voiceMatch != null) {
                textPart.substring(voiceMatch.range.last + 1).trim()
            } else {
                textPart
            }
            if (lyricText.isBlank()) continue
            val isBackground = voiceMatch?.groupValues?.get(1) == "bg"
            val agent = voiceMatch?.groupValues?.get(1)
            val lineStart = times.first()
            val lineEnd = times.last() + 2.0

            val wordRegex = Regex("""<(\d{2}:\d{2}(?:\.\d{2,3})?)>""")
            val wordMatches = wordRegex.findAll(lyricText).toList()
            if (wordMatches.isNotEmpty()) {
                val words = mutableListOf<ParsedWord>()
                var lastEnd = lineStart
                var lastIndex = 0
                for (wm in wordMatches) {
                    val before = lyricText.substring(lastIndex, wm.range.first).trim()
                    if (before.isNotBlank()) {
                        val wordEnd = parseLrcTimeToSec(wm.groupValues[1])
                        words.add(ParsedWord(before, lastEnd, wordEnd))
                        lastEnd = wordEnd
                    }
                    lastIndex = wm.range.last + 1
                }
                val remaining = lyricText.substring(lastIndex).trim()
                if (remaining.isNotBlank()) {
                    words.add(ParsedWord(remaining, lastEnd, lineEnd))
                }
                if (words.isNotEmpty()) {
                    lines.add(ParsedLine(lyricText, lineStart, lineEnd, words, isBackground, agent))
                }
            } else {
                lines.add(ParsedLine(lyricText, lineStart, lineEnd, emptyList(), isBackground, agent))
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