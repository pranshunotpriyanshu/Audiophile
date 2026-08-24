package com.pryvn.audiophile.code.api.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class GetQueueResponse(
    val queueDatas: List<QueueData> = emptyList(),
) {
    @Serializable
    data class QueueData(
        val content: QueueContent? = null,
    )

    @Serializable
    data class QueueContent(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
    )

    @Serializable
    data class PlaylistPanelVideoRenderer(
        val title: Title? = null,
        val lengthText: LengthText? = null,
        val shortBylineText: ShortBylineText? = null,
        val videoId: String? = null,
        val thumbnail: Thumbnail? = null,
    ) {
        @Serializable
        data class Title(val runs: List<Run>? = null) {
            @Serializable
            data class Run(val text: String? = null)
        }

        @Serializable
        data class LengthText(val simpleText: String? = null)

        @Serializable
        data class ShortBylineText(val runs: List<Run>? = null) {
            @Serializable
            data class Run(
                val text: String? = null,
                val navigationEndpoint: NavigationEndpoint? = null,
            )
        }

        @Serializable
        data class Thumbnail(val thumbnails: List<ThumbnailUrl>? = null) {
            @Serializable
            data class ThumbnailUrl(val url: String? = null)
        }

        @Serializable
        data class NavigationEndpoint(val watchEndpoint: WatchEndpoint? = null) {
            @Serializable
            data class WatchEndpoint(val videoId: String? = null, val playlistId: String? = null)
        }
    }

    @Serializable
    data class Run(val text: String? = null)

    @Serializable
    data class ShortBylineText(val runs: List<Run>? = null) {
        @Serializable
        data class Run(
            val text: String? = null,
            val navigationEndpoint: NavigationEndpoint? = null,
        )
    }

    @Serializable
    data class NavigationEndpoint(val watchEndpoint: WatchEndpoint? = null) {
        @Serializable
        data class WatchEndpoint(val videoId: String? = null, val playlistId: String? = null)
    }
}
