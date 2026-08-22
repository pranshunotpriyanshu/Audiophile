package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics

interface ILyricsParser {
    fun canParse(content: String): Boolean

    fun parse(lines: List<String>): SyncedLyrics {
        return parse(lines.joinToString("\n"))
    }

    fun parse(content: String): SyncedLyrics {
        // Default implementation: split String by newline into List<String>, then call the other parse method.
        return parse(content.split('\n'))
    }
}
