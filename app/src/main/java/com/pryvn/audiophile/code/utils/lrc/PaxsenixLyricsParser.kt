package com.pryvn.audiophile.code.utils.lrc

import com.pryvn.audiophile.code.lyrics.ParsedLineSynced
import com.pryvn.audiophile.code.lyrics.ParsedWord
import com.pryvn.audiophile.code.lyrics.ParsedWordSyncedLine
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object PaxsenixLyricsParser {

    fun parseTtml(ttml: String): List<ParsedWordSyncedLine> {
        if (!isTtml(ttml)) return emptyList()
        return try {
            parseTtmlDom(ttml)
        } catch (_: Exception) {
            parseTtmlRegex(ttml)
        }
    }

    private fun parseTtmlDom(ttml: String): List<ParsedWordSyncedLine> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isCoalescing = false
        }
        val builder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(ByteArrayInputStream(ttml.toByteArray()))
        doc.documentElement.normalize()

        val lines = mutableListOf<ParsedWordSyncedLine>()
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
                            words.add(ParsedWord(wText, (ws * 1000).toLong(), (we * 1000).toLong(), isBg))
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
                        chars.add(ParsedWord(
                            c.toString(),
                            ((startTime + idx * charDuration) * 1000).toLong(),
                            ((startTime + (idx + 1) * charDuration) * 1000).toLong(),
                            isBackground,
                        ))
                    }
                }
                if (chars.isNotEmpty()) {
                    lines.add(ParsedWordSyncedLine(plainText, (startTime * 1000).toLong(), (endTime * 1000).toLong(), chars, agent ?: "v1"))
                } else if (plainText.isNotBlank()) {
                    lines.add(ParsedWordSyncedLine(plainText, (startTime * 1000).toLong(), (endTime * 1000).toLong(), emptyList(), agent ?: "v1"))
                }
            } else if (hasWordTiming) {
                val text = if (plainText.isNotBlank()) plainText else words.joinToString("") { it.text }
                lines.add(ParsedWordSyncedLine(text, (startTime * 1000).toLong(), (endTime * 1000).toLong(), words, agent ?: "v1"))
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }

    private fun parseTtmlRegex(ttml: String): List<ParsedWordSyncedLine> {
        val lines = mutableListOf<ParsedWordSyncedLine>()
        val lineRegex = Regex("""<p\s+[^>]*begin="([^"]+)"[^>]*(?:end="([^"]+)")?[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (match in lineRegex.findAll(ttml)) {
            val begin = match.groupValues[1]
            val end = match.groupValues[2]
            val content = match.groupValues[3]
            val startTime = parseTtmlTime(begin)
            val endTime = if (end.isNotBlank()) parseTtmlTime(end) else startTime + 3.0
            val isBackground = content.contains("role=\"x-bg\"") || content.contains("class=\"x-bg\"")
            val agent = Regex("""ttm:agent="([^"]+)"""").find(content)?.groupValues?.get(1)

            val spanRegex = Regex("""<span[^>]*(?:begin="([^"]+)")?[^>]*(?:end="([^"]+)")?[^>]*>(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            var plainText = content.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            val words = mutableListOf<ParsedWord>()
            var hasWordTiming = false

            for (span in spanRegex.findAll(content)) {
                val wBegin = span.groupValues[1]
                val wEnd = span.groupValues[2]
                val wText = span.groupValues[3].replace(Regex("<[^>]+>"), "").trim()
                if (wText.isNotBlank() && wBegin.isNotBlank()) {
                    hasWordTiming = true
                    val ws = parseTtmlTime(wBegin)
                    val we = if (wEnd.isNotBlank()) parseTtmlTime(wEnd) else ws + 0.3
                    words.add(ParsedWord(wText, (ws * 1000).toLong(), (we * 1000).toLong(), isBackground))
                } else if (wText.isNotBlank()) {
                    plainText = wText
                }
            }

            if (!hasWordTiming && plainText.isNotBlank()) {
                val chars = mutableListOf<ParsedWord>()
                val charList = plainText.toList()
                val charDuration = (endTime - startTime) / charList.size.coerceAtLeast(1)
                charList.forEachIndexed { idx, c ->
                    if (c != ' ') {
                        chars.add(ParsedWord(
                            c.toString(),
                            ((startTime + idx * charDuration) * 1000).toLong(),
                            ((startTime + (idx + 1) * charDuration) * 1000).toLong(),
                            isBackground,
                        ))
                    }
                }
                if (chars.isNotEmpty()) {
                    lines.add(ParsedWordSyncedLine(plainText, (startTime * 1000).toLong(), (endTime * 1000).toLong(), chars, agent ?: "v1"))
                } else if (plainText.isNotBlank()) {
                    lines.add(ParsedWordSyncedLine(plainText, (startTime * 1000).toLong(), (endTime * 1000).toLong(), emptyList(), agent ?: "v1"))
                }
            } else if (hasWordTiming) {
                lines.add(ParsedWordSyncedLine(plainText.ifBlank { words.joinToString("") { it.text } }, (startTime * 1000).toLong(), (endTime * 1000).toLong(), words, agent ?: "v1"))
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }

    fun parsePaxsenixWordSynced(raw: String): List<ParsedWordSyncedLine> {
        val lines = mutableListOf<ParsedWordSyncedLine>()
        val lineRegex = Regex("""\[(\d{2}:\d{2}\.\d{2,3})\]\s*(\w+):\s*(.*)""")
        
        for (line in raw.lines()) {
            val match = lineRegex.find(line) ?: continue
            val timestamp = match.groupValues[1]
            val voice = match.groupValues[2]
            val content = match.groupValues[3]
            
            val lineStartMs = parseLrcTimestamp(timestamp)
            val wordRegex = Regex("""<(\d{2}:\d{2}\.\d{2,3})>([^<]+)""")
            val wordMatches = wordRegex.findAll(content).toList()
            
            if (wordMatches.isEmpty()) continue
            
            val words = mutableListOf<ParsedWord>()
            var lastEndMs = lineStartMs
            for (wm in wordMatches) {
                val wordTime = parseLrcTimestamp(wm.groupValues[1])
                val wordText = wm.groupValues[2].trim()
                if (wordText.isNotBlank()) {
                    val wordStartMs = if (words.isEmpty()) lineStartMs else lastEndMs
                    words.add(ParsedWord(wordText, wordStartMs, wordTime))
                    lastEndMs = wordTime
                }
            }
            
            val lineText = words.joinToString("") { it.text }
            val lineEndMs = words.lastOrNull()?.endTimeMs ?: lineStartMs + 3000
            
            lines.add(ParsedWordSyncedLine(
                text = lineText,
                startTimeMs = lineStartMs,
                endTimeMs = lineEndMs,
                words = words,
                voice = voice,
            ))
        }
        return lines.sortedBy { it.startTimeMs }
    }

    fun parseLrc(lrc: String): List<ParsedLineSynced> {
        val lines = mutableListOf<ParsedLineSynced>()
        val timeRegex = Regex("""\[(\d{2}):(\d{2}(?:\.\d{2,3})?)\]""")
        
        for (rawLine in lrc.lines()) {
            val timeMatches = timeRegex.findAll(rawLine).toList()
            if (timeMatches.isEmpty()) continue
            
            val textPart = rawLine.replace(timeRegex, "").trim()
            if (textPart.isBlank()) continue
            
            val lineStartSec = timeMatches.map { match ->
                val mins = match.groupValues[1].toIntOrNull() ?: 0
                val secs = match.groupValues[2].toFloatOrNull() ?: 0f
                mins * 60.0 + secs
            }.first()
            
            lines.add(ParsedLineSynced(
                startTimeMs = (lineStartSec * 1000).toLong(),
                text = textPart,
            ))
        }
        return lines.sortedBy { it.startTimeMs }
    }

    fun isTtml(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("<") && (trimmed.contains("<tt") || trimmed.contains("ttml") || trimmed.contains("<p "))
    }

    fun isPaxsenixWordSynced(text: String): Boolean {
        return text.lines().any { it.matches(Regex("""\[\d{2}:\d{2}\.\d{2,3}\]\s*\w+:\s*<""")) }
    }

    fun isLineSyncedLrc(text: String): Boolean {
        return Regex("""\[\d{2}:\d{2}(?:\.\d{2,3})?\]""").containsMatchIn(text)
    }

    private fun parseTtmlTime(value: String): Double {
        val clean = value.trim().removeSuffix("s").removeSuffix("ms")
        return if (clean.endsWith("t")) {
            clean.removeSuffix("t").toDoubleOrNull() ?: 0.0
        } else if (clean.endsWith("f")) {
            (clean.removeSuffix("f").toDoubleOrNull() ?: 0.0) / 30.0
        } else {
            val parts = clean.split(":", ";")
            when (parts.size) {
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
    }

    private fun parseLrcTimestamp(ts: String): Long {
        val parts = ts.split(":")
        if (parts.size != 2) return 0
        val mins = parts[0].toIntOrNull() ?: 0
        val secs = parts[1].toFloatOrNull() ?: 0f
        return (mins * 60_000L + (secs * 1000).toLong())
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