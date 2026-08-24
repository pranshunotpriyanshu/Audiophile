package com.pryvn.audiophile.code.api

import android.util.Log
import com.pryvn.audiophile.code.api.innertube.models.YouTubeClient
import com.pryvn.audiophile.code.api.InnerTubeClient
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.potoken.BotGuardTokenGenerator
import com.pryvn.audiophile.code.api.potoken.PoTokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles player resolution for YouTube Music playback.
 * Integrates BotGuardTokenGenerator for PoToken minting to enable stream URL resolution.
 */
object YTPlayerUtils {
    private const val TAG = "YTPlayerUtils"

    /**
     * Resolve a playable stream URL for the given video.
     * Uses InnerTubeClient's multi-client fallback with PoToken support.
     */
    suspend fun resolvePlayable(
        videoId: String,
        playlistId: String? = null,
        preferredClient: YouTubeClient = YouTubeClient.WEB_REMIX,
    ): Result<YTPlayerResponse> = withContext(Dispatchers.IO) {
        val innerTubeResult = runCatching {
            // Step 1: Generate PoTokens for this video
            val poTokenResult = generatePoTokens(videoId)
            
            // Step 2: Update InnerTubeClient auth state with generated tokens
            if (poTokenResult != null) {
                InnerTubeClient.poTokenPlayer = poTokenResult.playerToken
                InnerTubeClient.poToken = poTokenResult.sessionToken
                Log.d(TAG, "PoTokens generated for $videoId: playerToken=${poTokenResult.playerToken.take(20)}..., sessionToken=${poTokenResult.sessionToken.take(20)}...")
            } else {
                Log.w(TAG, "PoToken generation returned null for $videoId, proceeding without tokens")
            }

            // Step 3: Try InnerTube with multi-client fallback
            val result = InnerTubeClient.playerWithFallback(videoId, playlistId)
            val raw = result.getOrThrow()
            YouTubeApi.parsePlayerResponse(raw)
        }

        if (innerTubeResult.isSuccess) {
            innerTubeResult
        } else {
            Log.w(TAG, "InnerTube failed for $videoId, falling back to Piped: ${innerTubeResult.exceptionOrNull()?.message}")
            runCatching {
                tryPipedFallback(videoId)
            }
        }
    }

    /**
     * Generate PoTokens for the given video using BotGuardTokenGenerator.
     * Returns null if generation fails (will proceed without tokens).
     */
    private suspend fun generatePoTokens(videoId: String): PoTokenResult? {
        val sessionId = InnerTubeClient.visitorData
        if (sessionId == null || sessionId.isEmpty()) {
            Log.w(TAG, "No session ID (visitorData) available, cannot generate PoTokens")
            return null
        }

        return try {
            BotGuardTokenGenerator.mintToken(videoId, sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "PoToken generation failed for $videoId: ${e.message}", e)
            null
        }
    }

    private suspend fun tryPipedFallback(videoId: String): YTPlayerResponse = withContext(Dispatchers.IO) {
        val pipedResult = PipedClient.streamsWithFallback(videoId)
        val piped = pipedResult.getOrThrow()
        val bestAudio = piped.audioStreams
            .filter { !it.videoOnly }
            .maxByOrNull { it.bitrate ?: 0 }
            ?: throw Exception("No audio streams available from Piped")
        YTPlayerResponse(
            videoId = videoId,
            title = piped.title,
            artist = piped.uploader,
            thumbnailUrl = piped.thumbnailUrl,
            lengthSeconds = piped.duration,
            streamUrl = bestAudio.url,
            expiresInSeconds = null,
        )
    }
}