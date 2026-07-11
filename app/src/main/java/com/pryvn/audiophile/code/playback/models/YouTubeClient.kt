package com.pryvn.audiophile.code.playback.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val api_key: String,
    val userAgent: String,
    val osVersion: String? = null,
    val referer: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val timeZone: String? = null,
    val utcOffsetMinutes: Int? = null,
    val xClientName: Int? = null,
) {
    fun toContext(
        locale: YouTubeLocale,
        visitorData: String?,
    ) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            osVersion = osVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData,
            userAgent = userAgent,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            osName = osName,
            timeZone = timeZone,
            utcOffsetMinutes = utcOffsetMinutes,
        ),
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
        const val USER_AGENT_WEB = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private const val USER_AGENT_ANDROID = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
        private const val USER_AGENT_IOS = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.27.52",
            api_key = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = USER_AGENT_ANDROID,
            osName = "Android",
            osVersion = "11",
            xClientName = 21,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260304.03.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = USER_AGENT_WEB,
            referer = REFERER_YOUTUBE_MUSIC,
        )

        val WEB_EMBEDDED = YouTubeClient(
            clientName = "WEB_EMBEDDED_PLAYER",
            clientVersion = "1.20250310.01.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = USER_AGENT_WEB,
            referer = REFERER_YOUTUBE_MUSIC,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20250312.16.00",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "19.45.4",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            userAgent = USER_AGENT_IOS,
            api_key = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            osName = "iPhone",
            osVersion = "17.5.1.21F90",
            timeZone = "UTC",
            utcOffsetMinutes = 0,
            xClientName = 5,
        )
    }
}
