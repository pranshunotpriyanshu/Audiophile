package com.pryvn.audiophile.code.api.innertube.models.body

import com.pryvn.audiophile.code.api.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
    val context: Context,
    val videoIds: List<String>? = null,
    val playlistId: String? = null,
)
