package com.pryvn.audiophile.code.api.innertube.models.body

import com.pryvn.audiophile.code.api.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class RegisterPlaybackBody(
    val context: Context,
    val videoId: String,
)
