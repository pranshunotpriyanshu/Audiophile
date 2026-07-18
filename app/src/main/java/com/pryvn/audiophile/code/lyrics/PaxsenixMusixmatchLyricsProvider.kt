/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.pryvn.audiophile.code.lyrics

import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics

object PaxsenixMusixmatchLyricsProvider : LyricsProvider {
    override val name: String = "Paxsenix (Musixmatch)"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): Result<String> = PaxsenixLyrics.getMusixmatchLyrics(title, artist, durationSeconds)
}
