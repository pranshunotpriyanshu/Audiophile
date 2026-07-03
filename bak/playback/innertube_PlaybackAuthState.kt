package com.pryvn.audiophile.code.api.innertube

import com.pryvn.audiophile.code.api.innertube.models.YouTubeClient

data class PlaybackAuthState(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val poToken: String? = null,
    val poTokenGvs: String? = null,
    val poTokenPlayer: String? = null,
    val webClientPoTokenEnabled: Boolean = true,
) {
    val hasLoginCookie: Boolean
        get() = !cookie.isNullOrBlank()

    val hasPlaybackLoginContext: Boolean
        get() = hasLoginCookie && !dataSyncId.isNullOrBlank()

    fun resolveGvsPoToken(client: YouTubeClient? = null): String? = poTokenGvs

    fun resolvePlayerPoToken(client: YouTubeClient? = null, explicitPoToken: String? = null): String? =
        explicitPoToken ?: poTokenPlayer

    fun normalized(): PlaybackAuthState = this

    companion object {
        val EMPTY = PlaybackAuthState()
    }
}

private val YOUTUBE_LOGIN_COOKIE_NAMES = listOf(
    "SAPISID",
    "__Secure-3PAPISID",
    "__Secure-1PAPISID",
    "APISID",
)

fun youtubeLoginCookieValue(cookie: String?): String? {
    if (cookie.isNullOrBlank()) return null
    val cookieMap = cookie.split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
        }
        .toMap()
    return YOUTUBE_LOGIN_COOKIE_NAMES.firstNotNullOfOrNull { name ->
        cookieMap[name]?.takeIf { it.isNotBlank() }
    }
}
