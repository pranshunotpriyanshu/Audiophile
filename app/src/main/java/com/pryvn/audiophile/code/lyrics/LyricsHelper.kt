@file:OptIn(ExperimentalCoroutinesApi::class)

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported lyrics provider orchestration. Runs all providers in parallel,
 * scores results (TTML > synced LRC > plain text), returns the best.
 * Isolated from parent cancellation via NonCancellable so fetch continues
 * even if the UI exits NowPlaying. Each provider has a 10s timeout;
 * overall fetch capped at 15s.
 */

package com.pryvn.audiophile.code.lyrics

import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.code.utils.lrc.TTMLParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

interface LyricsProvider {
    val name: String

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): Result<String>
}

object LyricsHelper {
    /* TTML-capable providers first, then synced-LRC, then plain-text fallbacks */
    private val baseProviders =
        listOf(
            BetterLyricsProvider,
            PaxsenixAppleMusicLyricsProvider,
            PaxsenixNeteaseLyricsProvider,
            PaxsenixSpotifyLyricsProvider,
            YouLyPlusLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            SimpMusicLyricsProvider,
            UnisonLyricsProvider,
            PaxsenixMusixmatchLyricsProvider,
            PaxsenixYouTubeLyricsProvider,
        )

    private const val PER_PROVIDER_TIMEOUT_MS = 10_000L
    private const val TOTAL_TIMEOUT_MS = 15_000L

    /**
     * Fetches lyrics for a song by running every provider in [baseProviders]
     * in parallel, collecting all results within 15s, and returning the
     * highest-scored one (TTML > synced LRC > plain text).
     *
     * Runs on [NonCancellable] so the fetch is not interrupted when the
     * calling UI scope cancels (e.g. user exits NowPlaying). The best
     * result is cached in [MediaViewModelObject.lyricsCache] by the caller.
     *
     * @return the best [AudiophileLyrics], or null if every provider failed
     * or timed out.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        videoId: String?,
    ): AudiophileLyrics? = withContext(NonCancellable + Dispatchers.IO) {
        val durationSeconds = if (durationMs > 0) (durationMs / 1000L).toInt() else -1
        fetchAllWithScoring(baseProviders, videoId, title, artist, album, durationSeconds)
    }

    /**
     * Launches every provider as a separate async (10s per-provider timeout),
     * collects results with a 15s total ceiling, then returns the winner
     * via [score].
     */
    private suspend fun fetchAllWithScoring(
        providers: List<LyricsProvider>,
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): AudiophileLyrics? = supervisorScope {
        val deferreds =
            providers.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        withTimeout(PER_PROVIDER_TIMEOUT_MS) {
                            fetchProviderLyrics(
                                provider, videoId, title, artist, album, durationSeconds,
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }

        if (deferreds.isEmpty()) return@supervisorScope null

        val allResults: List<AudiophileLyrics?> =
            try {
                withTimeout(TOTAL_TIMEOUT_MS) {
                    deferreds.map { it.await() }
                }
            } catch (_: TimeoutCancellationException) {
                deferreds.mapNotNull { deferred ->
                    if (deferred.isCompleted) {
                        try { deferred.getCompleted() } catch (_: Exception) { null }
                    } else {
                        deferred.cancel()
                        null
                    }
                }
            }

        allResults.maxByOrNull { score(it) }
    }

    /**
     * Scoring:
     * 100  — TTML / word-synced (either flagged by provider or detected)
     *  50  — line-synced LRC (has [mm:ss.xx] timestamps)
     *  10  — plain text (no timestamps, no XML)
     *   0  — null / blank
     */
    private fun score(lyrics: AudiophileLyrics?): Int {
        if (lyrics == null || lyrics.text.isBlank()) return 0
        return when {
            lyrics.isWordSynced -> 100
            TTMLParser.isTtml(lyrics.text) -> 100
            TTMLParser.isLineSyncedLrc(lyrics.text) -> 50
            else -> 10
        }
    }

    private suspend fun fetchProviderLyrics(
        provider: LyricsProvider,
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): AudiophileLyrics? =
        try {
            provider
                .getLyrics(
                    id = videoId ?: "",
                    title = title,
                    artist = artist,
                    album = album,
                    durationSeconds = durationSeconds,
                ).fold(
                    onSuccess = { raw ->
                        raw.takeIf { it.isNotBlank() }?.let {
                            AudiophileLyrics(
                                provider = provider.name,
                                text = it,
                                isWordSynced = TTMLParser.isTtml(it),
                            )
                        }
                    },
                    onFailure = { null },
                )
        } catch (_: Exception) {
            null
        }
}
