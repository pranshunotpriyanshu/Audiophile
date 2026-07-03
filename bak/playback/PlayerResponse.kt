package com.pryvn.audiophile.code.api.innertube.models.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val responseContext: ResponseContext? = null,
    val playabilityStatus: PlayabilityStatus? = null,
    val playerConfig: PlayerConfig? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playbackTracking: PlaybackTracking? = null,
) {
    @Serializable
    data class ResponseContext(
        val visitorData: String? = null,
    )

    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig? = null,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double? = null,
            val perceptualLoudnessDb: Double? = null,
        )
    }

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format> = emptyList(),
        val expiresInSeconds: Int? = null,
    )

    @Serializable
    data class Format(
        val itag: Int? = null,
        val url: String? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val width: Int? = null,
        val height: Int? = null,
        val contentLength: Long? = null,
        val quality: String? = null,
        val fps: Int? = null,
        val qualityLabel: String? = null,
        val averageBitrate: Int? = null,
        val audioQuality: String? = null,
        val approxDurationMs: String? = null,
        val audioSampleRate: String? = null,
        val audioChannels: Int? = null,
        val loudnessDb: Double? = null,
        val signatureCipher: String? = null,
        @SerialName("cipher")
        val cipher: String? = null,
    ) {
        val isAudio: Boolean
            get() = mimeType?.startsWith("audio/") == true
    }

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val musicVideoType: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails? = null,
    )

    @Serializable
    data class PlaybackTracking(
        val videostatsPlaybackUrl: UrlWrapper? = null,
        val videostatsWatchtimeUrl: UrlWrapper? = null,
        val atrUrl: UrlWrapper? = null,
    )

    @Serializable
    data class UrlWrapper(
        val baseUrl: String? = null,
    )

    @Serializable
    data class Thumbnails(
        val thumbnails: List<Thumbnail> = emptyList(),
    )

    @Serializable
    data class Thumbnail(
        val url: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    )
}
