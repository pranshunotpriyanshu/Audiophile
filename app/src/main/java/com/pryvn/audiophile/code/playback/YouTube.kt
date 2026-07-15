package com.pryvn.audiophile.code.playback

import android.util.Log
import com.pryvn.audiophile.code.playback.models.YouTubeClient.Companion.WEB_REMIX
import com.pryvn.audiophile.code.playback.models.response.PlayerResponse
import com.pryvn.audiophile.code.playback.utils.generateCpn
import io.ktor.client.statement.bodyAsText

private const val TAG = "YouTube"

class YouTube(
    val ytMusic: Ytmusic,
) {
    suspend fun newPipePlayer(
        videoId: String,
        tempRes: PlayerResponse,
    ): PlayerResponse? {
        if (tempRes.playabilityStatus.status != "OK") return null
        val streamsList = ytMusic.getNewPipePlayer(videoId)
        if (streamsList.isEmpty()) return null

        Log.d(TAG, "newPipePlayer: videoId=$videoId streams=${streamsList.size}")

        val sigResponse = tempRes
        var decodedSigResponse: PlayerResponse = sigResponse.copy(
            streamingData = sigResponse.streamingData?.copy(
                formats = sigResponse.streamingData.formats?.map { format ->
                    format.copy(url = streamsList.find { it.first == format.itag }?.second)
                },
                adaptiveFormats = sigResponse.streamingData.adaptiveFormats.map { format ->
                    format.copy(url = streamsList.find { it.first == format.itag }?.second)
                },
                hlsManifestUrl = streamsList.firstOrNull { it.first == 96 }?.second,
            ),
        )

        decodedSigResponse = decodedSigResponse.copy(
            streamingData = decodedSigResponse.streamingData?.copy(
                formats = decodedSigResponse.streamingData.formats?.let { formats ->
                    val copy = formats.toMutableList()
                    streamsList.filter { isManifestUrl(it.second) }.forEach { manifest ->
                        copy.add(
                            PlayerResponse.StreamingData.Format(
                                itag = manifest.first,
                                url = manifest.second,
                                mimeType = "",
                                bitrate = 0,
                                width = if (manifest.first == 96) 1920 else 1080,
                                height = if (manifest.first == 96) 1080 else 720,
                                contentLength = 0,
                                quality = "",
                                fps = 0,
                                qualityLabel = "",
                                averageBitrate = 0,
                                audioQuality = "",
                                approxDurationMs = "",
                                audioSampleRate = 0,
                                audioChannels = 0,
                                loudnessDb = 0.0,
                                lastModified = 0,
                                signatureCipher = "",
                            ),
                        )
                    }
                    copy.filter { it.itag != 0 }
                    copy
                },
            ),
        )

        val allUrls = (
            decodedSigResponse.streamingData?.adaptiveFormats?.mapNotNull { it.url }?.toMutableList()
                ?: mutableListOf()
            ).apply {
            decodedSigResponse.streamingData?.formats?.mapNotNull { it.url }?.let { addAll(it) }
        }

        allUrls.forEach { Log.d(TAG, "newPipePlayer URL: $it") }

        val randomUrl = allUrls.randomOrNull() ?: return null
        if (allUrls.isNotEmpty() && !ytMusic.is403Url(randomUrl)) {
            Log.d(TAG, "newPipePlayer: valid URL found for $videoId")
            return decodedSigResponse
        }
        Log.d(TAG, "newPipePlayer: all URLs invalid for $videoId")
        return null
    }

    private fun isManifestUrl(url: String): Boolean = url.contains(".m3u8") || url.contains(".mpd") || url.contains("manifest")

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        noLogIn: Boolean = false,
    ): Result<PlayerResponse> {
        Log.d(TAG, "player: calling InnerTube API for videoId=$videoId")
        val cpn = generateCpn()
        val daysSinceEpoch = (System.currentTimeMillis() / 86400000L).toInt()
        val signatureTimestamp = daysSinceEpoch

        if (!noLogIn) {
            ytMusic.ensureFreshVisitorData(videoId)
        }

        val rawJson = runCatching {
            ytMusic.player(WEB_REMIX, videoId, playlistId, cpn, signatureTimestamp).bodyAsText()
        }.getOrElse { e ->
            Log.e(TAG, "player: InnerTube request failed for $videoId", e)
            return Result.failure(e)
        }

        val tempRes = runCatching {
            ytMusic.normalJson.decodeFromString<PlayerResponse>(rawJson)
        }.getOrElse { e ->
            Log.e(TAG, "player: response parse failed for $videoId", e)
            return Result.failure(e)
        }

        Log.d(TAG, "player: decoded PlayerResponse playability=${tempRes.playabilityStatus.status} videoId=$videoId videoDetails.id=${tempRes.videoDetails?.videoId}")

        if (tempRes.playabilityStatus.status != "OK") {
            val reason = tempRes.playabilityStatus.reason ?: "playability check failed"
            Log.w(TAG, "player: playability check failed for $videoId: ${tempRes.playabilityStatus.status} - $reason")
            return Result.failure(RuntimeException("Song is not playable: $reason"))
        }

        val decodedResponse = newPipePlayer(videoId, tempRes)
        if (decodedResponse == null) {
            val fmtCount = tempRes.streamingData?.formats?.size ?: 0
            val afmtCount = tempRes.streamingData?.adaptiveFormats?.size ?: 0
            Log.e(TAG, "player: stream resolution failed for $videoId (formats=$fmtCount adaptive=$afmtCount)")
            return Result.failure(RuntimeException("Could not resolve audio stream for this song"))
        }

        Log.d(TAG, "player: success videoId=$videoId")
        return Result.success(decodedResponse)
    }

}
