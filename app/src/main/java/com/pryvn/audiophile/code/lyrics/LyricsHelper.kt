/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported lyrics provider orchestration. Mirrors ArchiveTune's LyricsHelper
 * fallback chain (first provider tried synchronously, the rest raced via
 * select{}) but adapted to Audiophile: no Hilt, no DataStore ordering,
 * no GlobalLog. Returns the existing AudiophileLyrics? contract so the
 * rest of the app (LyricsProcessor, MediaController) is unchanged.
 */

package com.pryvn.audiophile.code.lyrics

import com.pryvn.audiophile.code.api.AudiophileLyrics
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope

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
    private val baseProviders =
        listOf(
            BetterLyricsProvider,
            YouLyPlusLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            SimpMusicLyricsProvider,
            UnisonLyricsProvider,
            PaxsenixAppleMusicLyricsProvider,
            PaxsenixNeteaseLyricsProvider,
            PaxsenixSpotifyLyricsProvider,
            PaxsenixMusixmatchLyricsProvider,
            PaxsenixYouTubeLyricsProvider,
        )

    /**
     * Fetches lyrics for a song by racing every provider in [baseProviders]
     * order and returning the first non-empty result, exactly like
     * ArchiveTune's LyricsHelper: the first provider is awaited
     * synchronously, then the remainder are launched concurrently and the
     * first meaningful result wins (cancelling the rest).
     *
     * @return the resolved [AudiophileLyrics], or null if no provider
     * returned usable lyrics.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        videoId: String?,
    ): AudiophileLyrics? {
        val durationSeconds = if (durationMs > 0) (durationMs / 1000L).toInt() else -1
        val providers = baseProviders
        val lyrics = fetchPriorityLyrics(providers, videoId, title, artist, album, durationSeconds)
        return lyrics
    }

    private suspend fun fetchPriorityLyrics(
        providers: List<LyricsProvider>,
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): AudiophileLyrics? {
        if (providers.isEmpty()) return null

        fetchProviderLyrics(providers.first(), videoId, title, artist, album, durationSeconds)?.let {
            return it
        }

        return fetchFirstMeaningfulLyrics(providers.drop(1), videoId, title, artist, album, durationSeconds)
    }

    private suspend fun fetchFirstMeaningfulLyrics(
        providers: List<LyricsProvider>,
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): AudiophileLyrics? =
        supervisorScope {
            val requests =
                providers.map { provider ->
                    async(Dispatchers.IO) {
                        fetchProviderLyrics(provider, videoId, title, artist, album, durationSeconds)
                    }
                }

            if (requests.isEmpty()) return@supervisorScope null

            val pending = requests.toMutableSet()
            while (pending.isNotEmpty()) {
                val (request, lyrics) =
                    select<Pair<Deferred<AudiophileLyrics?>, AudiophileLyrics?>> {
                        pending.forEach { deferred ->
                            deferred.onAwait { result -> deferred to result }
                        }
                    }
                pending.remove(request)
                if (lyrics != null) {
                    pending.forEach { it.cancel() }
                    return@supervisorScope lyrics
                }
            }

            null
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
                                isWordSynced = isTtml(it),
                            )
                        }
                    },
                    onFailure = { null },
                )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }

    private fun isTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("<tt") || trimmed.contains("w3.org/ns/ttml")
    }
}
