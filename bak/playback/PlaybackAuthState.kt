package com.pryvn.audiophile.code.api

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackAuthState(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val poToken: String? = null,
    val poTokenGvs: String? = null,
    val poTokenPlayer: String? = null,
    val webClientPoTokenEnabled: Boolean = false,
) {
    val hasLoginCookie: Boolean
        get() = cookie?.let { hasYouTubeLoginCookie(it) } ?: false

    val hasPlaybackLoginContext: Boolean
        get() = hasLoginCookie && !dataSyncId.isNullOrBlank()

    val sessionId: String?
        get() = if (hasPlaybackLoginContext) dataSyncId else visitorData

    fun normalized(): PlaybackAuthState = copy(
        cookie = cookie?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        visitorData = visitorData?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        dataSyncId = dataSyncId?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        poToken = poToken?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        poTokenGvs = poTokenGvs?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        poTokenPlayer = poTokenPlayer?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
    )

    companion object {
        val EMPTY = PlaybackAuthState()
    }
}

fun hasYouTubeLoginCookie(cookie: String?): Boolean {
    if (cookie.isNullOrBlank()) return false
    return cookie.contains("SAPISID") ||
        cookie.contains("APISID") ||
        cookie.contains("HSID") ||
        cookie.contains("SSID")
}
