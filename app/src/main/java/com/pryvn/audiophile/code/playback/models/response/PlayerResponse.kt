package com.pryvn.audiophile.code.playback.models.response

import com.pryvn.audiophile.code.playback.models.ResponseContext
import com.pryvn.audiophile.code.playback.models.Thumbnails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val responseContext: ResponseContext,
    val playabilityStatus: PlayabilityStatus,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    @SerialName("playbackTracking")
    val playbackTracking: PlaybackTracking?,
    @SerialName("captions")
    val captions: YouTubeInitialPageCaptions? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String,
        val reason: String?,
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double?,
            val perceptualLoudnessDb: Double?,
        )
    }

    @Serializable
    data class StreamingData(
        val hlsManifestUrl: String? = null,
        val formats: List<Format>?,
        val adaptiveFormats: List<Format>,
        val expiresInSeconds: Int,
        val serverAbrStreamingUrl: String? = null,
    ) {
        @Serializable
        data class Format(
            val itag: Int,
            val url: String?,
            val mimeType: String,
            val bitrate: Int,
            val width: Int?,
            val height: Int?,
            val contentLength: Long?,
            val quality: String,
            val fps: Int?,
            val qualityLabel: String?,
            val averageBitrate: Int?,
            val audioQuality: String?,
            val approxDurationMs: String?,
            val audioSampleRate: Int?,
            val audioChannels: Int?,
            val loudnessDb: Double?,
            val lastModified: Long?,
            val signatureCipher: String?,
        ) {
            val isAudio: Boolean get() = width == null
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String,
        val title: String?,
        val author: String?,
        val channelId: String,
        val authorAvatar: String? = null,
        val authorSubCount: String? = null,
        val lengthSeconds: String,
        val musicVideoType: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails,
        val description: String? = null,
    )

    @Serializable
    data class PlaybackTracking(
        @SerialName("videostatsPlaybackUrl")
        val videostatsPlaybackUrl: VideostatsPlaybackUrl?,
        @SerialName("videostatsWatchtimeUrl")
        val videostatsWatchtimeUrl: VideostatsWatchtimeUrl?,
        @SerialName("atrUrl")
        val atrUrl: AtrUrl?,
    ) {
        @Serializable
        data class VideostatsPlaybackUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )

        @Serializable
        data class VideostatsWatchtimeUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )

        @Serializable
        data class AtrUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
    }
}

@Serializable
data class YouTubeInitialPageCaptions(
    val playerCaptionsTracklistRenderer: PlayerCaptionsTracklistRenderer? = null,
) {
    @Serializable
    data class PlayerCaptionsTracklistRenderer(
        val captionTracks: List<CaptionTrack>? = null,
    ) {
        @Serializable
        data class CaptionTrack(
            val baseUrl: String? = null,
            val name: Name? = null,
            val languageCode: String? = null,
            val kind: String? = null,
        ) {
            @Serializable
            data class Name(
                val simpleText: String? = null,
            )
        }
    }
}
