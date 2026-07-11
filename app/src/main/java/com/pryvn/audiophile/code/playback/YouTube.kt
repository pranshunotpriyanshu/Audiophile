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
        return tempRes.copy(
            streamingData = tempRes.streamingData?.copy(
                formats = tempRes.streamingData.formats?.map { format ->
                    format.copy(url = streamsList.find { it.first == format.itag }?.second)
                },
                adaptiveFormats = tempRes.streamingData.adaptiveFormats.map { format ->
                    format.copy(url = streamsList.find { it.first == format.itag }?.second)
                },
            ),
        )
    }

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        noLogIn: Boolean = false,
    ): Result<PlayerResponse> = runCatching {
        Log.d(TAG, "player: calling InnerTube API for videoId=$videoId")
        val cpn = generateCpn()
        val daysSinceEpoch = (System.currentTimeMillis() / 86400000L).toInt()
        val signatureTimestamp = daysSinceEpoch

        val rawJson = ytMusic.player(WEB_REMIX, videoId, playlistId, cpn, signatureTimestamp).bodyAsText()
        Log.d(TAG, "player: received InnerTube response for videoId=$videoId")
        val tempRes = ytMusic.normalJson.decodeFromString<PlayerResponse>(rawJson)
        Log.d(TAG, "player: decoded PlayerResponse playability=${tempRes.playabilityStatus.status} videoId=$videoId videoDetails.id=${tempRes.videoDetails?.videoId}")

        if (tempRes.playabilityStatus.status != "OK") {
            throw RuntimeException("Playability: ${tempRes.playabilityStatus.status} - ${tempRes.playabilityStatus.reason}")
        }

        val decodedResponse = newPipePlayer(videoId, tempRes)
        if (decodedResponse == null) throw RuntimeException("No URL found via NewPipe extraction")

        Log.d(TAG, "player: success videoId=$videoId")
        decodedResponse
    }
}
