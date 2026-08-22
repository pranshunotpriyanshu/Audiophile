package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.utils.PhoneticProvider

class AutoParser(
    private val fallbackPhoneticProvider: PhoneticProvider? = null,
    private val parsers: List<ILyricsParser> = listOf(
        TTMLParser(fallbackPhoneticProvider = fallbackPhoneticProvider),
        LyricifySyllableParser,
        EnhancedLrcParser,
        KugouKrcParser,
        NeteaseYrcParser
    )
) : ILyricsParser {

    override fun canParse(content: String): Boolean =
        parsers.any { it.canParse(content) }

    override fun parse(content: String): SyncedLyrics {
        val parser = parsers.firstOrNull { it.canParse(content) }
        return parser?.parse(content) ?: SyncedLyrics(emptyList())
    }
}
