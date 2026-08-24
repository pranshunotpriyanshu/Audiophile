/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.pryvn.audiophile.code.lyrics

import moe.rukamori.archivetune.simpmusic.SimpMusicLyrics

object SimpMusicLyricsProvider : LyricsProvider {
    override val name: String = "SimpMusic"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): Result<String> = SimpMusicLyrics.getLyrics(id, durationSeconds)
}