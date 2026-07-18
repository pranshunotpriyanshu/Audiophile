/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.pryvn.audiophile.code.lyrics

import moe.rukamori.archivetune.betterlyrics.BetterLyrics

object BetterLyricsProvider : LyricsProvider {
    override val name: String = "BetterLyrics"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): Result<String> {
        val result = BetterLyrics.getLyrics(title, artist, album, durationSeconds)
        return result.fold(
            onSuccess = { text ->
                val trimmed = text.trimStart()
                if (trimmed.startsWith("<tt") || trimmed.contains("w3.org/ns/ttml")) {
                    result
                } else {
                    Result.failure(IllegalStateException("Not TTML"))
                }
            },
            onFailure = { result }
        )
    }
}
