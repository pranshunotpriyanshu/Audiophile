package com.pryvn.audiophile.code.playback

import android.util.Log
import com.pryvn.audiophile.code.api.InnerTubeClient
import com.pryvn.audiophile.code.api.PlaybackAuthState
import com.pryvn.audiophile.code.api.innertube.YouTube as ArchiveTuneYouTube
import com.pryvn.audiophile.code.playback.models.YouTubeLocale
import com.pryvn.audiophile.code.playback.models.response.PlayerResponse

private const val TAG = "SimpMusicResolver"
private const val CACHE_EXPIRY_MS = 6L * 60 * 60 * 1000

object SimpMusicStreamResolver {
    private val ytMusic = Ytmusic()
    private val youTube = YouTube(ytMusic)
    private val urlCache = mutableMapOf<String, CachedStream>()

    fun updateAuth(
        cookie: String?,
        visitorData: String?,
        dataSyncId: String? = null,
        pageId: String? = null,
    ) {
        ytMusic.cookie = cookie
        ytMusic.visitorData = visitorData
        ytMusic.dataSyncId = dataSyncId
        ytMusic.pageId = pageId

        val apiAuthState = PlaybackAuthState(
            cookie = cookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
        ).normalized()
        InnerTubeClient.applyAuthState(apiAuthState)
        ArchiveTuneYouTube.authState = com.pryvn.audiophile.code.api.innertube.PlaybackAuthState(
            cookie = apiAuthState.cookie,
            visitorData = apiAuthState.visitorData,
            dataSyncId = apiAuthState.dataSyncId,
        )

        Log.d(TAG, "Auth updated: cookie=${cookie?.take(20)}... visitorData=$visitorData")
    }

    fun updateLocale(gl: String = "US", hl: String = "en") {
        ytMusic.locale = YouTubeLocale(gl = gl, hl = hl)
    }

    suspend fun resolve(videoId: String): Result<ResolvedStream> = runCatching {
        Log.d(TAG, "Resolving stream for videoId=$videoId")
        Log.d("PlaybackDebug", "Resolver: videoId=$videoId")

        urlCache[videoId]?.let { cached ->
            if (System.currentTimeMillis() < cached.expiresAt) {
                Log.d(TAG, "Using cached URL for $videoId")
                return@runCatching cached.toResolvedStream(videoId)
            }
            urlCache.remove(videoId)
        }

        Log.d(TAG, "Trying YouTube player API (SimpMusic) for $videoId...")
        val playerResponse = youTube.player(videoId).fold(
            onSuccess = { response ->
                Log.d(TAG, "SimpMusic player API success for $videoId")
                response
            },
            onFailure = { e ->
                Log.w(TAG, "SimpMusic player API failed for $videoId: ${e.message}")
                throw e
            }
        )

        val responseVideoId = playerResponse.videoDetails?.videoId
        Log.d("VideoIdChain", "Resolver: requested=$videoId response.videoDetails.id=$responseVideoId")

        val url = pickBestAudioUrl(playerResponse)
            ?: throw RuntimeException("Could not retrieve audio stream. Try a different song.")

        urlCache[videoId] = CachedStream(url, System.currentTimeMillis() + CACHE_EXPIRY_MS)

        val title = playerResponse.videoDetails?.title
        val durationSeconds = playerResponse.videoDetails?.lengthSeconds?.toIntOrNull()

        Log.d(TAG, "Resolved URL for $videoId: itag=${findPickedItag(playerResponse)}")
        ResolvedStream(url = url, title = title, durationSeconds = durationSeconds)
    }

    private fun pickBestAudioUrl(response: PlayerResponse): String? {
        val streamingData = response.streamingData ?: return null

        val candidates = (streamingData.formats.orEmpty() + streamingData.adaptiveFormats)
            .filter { it.isAudio && it.bitrate > 0 && !it.url.isNullOrBlank() }
            .sortedByDescending { it.bitrate }

        if (candidates.isEmpty()) {
            Log.w(TAG, "No audio format with url found")
            return null
        }

        return candidates.first().url
    }

    private fun findPickedItag(response: PlayerResponse): Int? {
        val streamingData = response.streamingData ?: return null
        val candidates = (streamingData.formats.orEmpty() + streamingData.adaptiveFormats)
            .filter { it.isAudio && it.bitrate > 0 && !it.url.isNullOrBlank() }
            .sortedByDescending { it.bitrate }
        return candidates.firstOrNull()?.itag
    }

    fun invalidateCache(videoId: String) {
        urlCache.remove(videoId)
    }

    data class ResolvedStream(
        val url: String,
        val title: String?,
        val durationSeconds: Int?,
    )

    private data class CachedStream(
        val url: String,
        val expiresAt: Long,
    ) {
        fun toResolvedStream(videoId: String): ResolvedStream {
            Log.d(TAG, "Cache hit for $videoId (expires in ${(expiresAt - System.currentTimeMillis()) / 1000}s)")
            return ResolvedStream(url = url, title = null, durationSeconds = null)
        }
    }
}
