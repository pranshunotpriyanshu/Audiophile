package com.mocharealm.accompanist.lyrics.core.exporter

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.utils.toTimeFormattedString

object EnhancedLrcExporter : ILyricsExporter {
    override fun export(lyrics: SyncedLyrics): String {
        if (lyrics.lines.isEmpty()) return ""

        val builder = StringBuilder()

        if (lyrics.title.isNotBlank()) {
            builder.appendLine("[ti:${lyrics.title}]")
        }
        if (!lyrics.artists.isNullOrEmpty() && lyrics.artists.all { it.name.isNotBlank() }) {
            builder.appendLine(
                "[ar:${lyrics.artists.joinToString("/") { it.name }}]"
            )
        }

        lyrics.lines.forEach { line ->
            val timeTag = "[${line.start.toTimeFormattedString()}]"

            when (line) {
                is SyncedLine -> {
                    builder.appendLine("$timeTag${line.content}")
                    line.translation?.let { builder.appendLine("$timeTag$it") }
                }

                is KaraokeLine -> {
                    val syllablesStr = line.syllables.joinToString("") { s ->
                        "<${s.start.toTimeFormattedString()}>${s.content}"
                    } + "<${line.end.toTimeFormattedString()}>"

                    builder.appendLine("$timeTag$syllablesStr")
                    line.translation?.let { builder.appendLine("$timeTag$it") }

                    if (line is KaraokeLine.MainKaraokeLine) {
                        line.accompanimentLines?.forEach { bgLine ->
                            val bgSyllablesStr = bgLine.syllables.joinToString("") { s ->
                                "<${s.start.toTimeFormattedString()}>${s.content}"
                            } + "<${bgLine.end.toTimeFormattedString()}>"

                            builder.appendLine("[bg:$bgSyllablesStr]")

                            bgLine.translation?.let { trans ->
                                builder.appendLine("[bg:<${bgLine.start.toTimeFormattedString()}>$trans<${bgLine.end.toTimeFormattedString()}>]")
                            }
                        }
                    }
                }
            }
        }

        return builder.toString()
    }
}
