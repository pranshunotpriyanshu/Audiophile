package com.pryvn.audiophile.code.api.innertube.models.body

import com.pryvn.audiophile.code.api.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
)

@Serializable
data class PlaybackContext(
    val contentPlaybackContext: ContentPlaybackContext,
)

@Serializable
data class ContentPlaybackContext(
    val signatureTimestamp: Int,
)

@Serializable
data class ServiceIntegrityDimensions(
    val poToken: String? = null,
)
