package com.pryvn.audiophile.code.playback

import android.util.Log
import com.pryvn.audiophile.code.playback.extractor.Extractor
import com.pryvn.audiophile.code.playback.models.Context
import com.pryvn.audiophile.code.playback.models.YouTubeClient
import com.pryvn.audiophile.code.playback.models.YouTubeLocale
import com.pryvn.audiophile.code.playback.models.body.PlayerBody
import com.pryvn.audiophile.code.playback.utils.parseCookieString
import com.pryvn.audiophile.code.playback.utils.sha1
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "Ytmusic"

class Ytmusic {
    val normalJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private var httpClient = createClient()

    var locale = YouTubeLocale(gl = "US", hl = "en")
    var visitorData: String? = null
    var dataSyncId: String? = null
    var pageId: String? = null
    var cookie: String? = null
        set(value) {
            field = value
            cookieMap = if (value == null) emptyMap() else parseCookieString(value)
            extractor.logIn(value)
        }

    private var cookieMap = emptyMap<String, String>()
    private var poTokenChallengeRequestKey = "O43z0dpjhgX20SCx4KAo"
    private val extractor = Extractor()

    init { extractor.init() }

    private fun createClient() = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(normalJson) }
        defaultRequest { url("https://music.youtube.com/youtubei/v1/") }
    }

    internal fun HttpRequestBuilder.ytClient(client: YouTubeClient, setLogin: Boolean = false) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientName)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Goog-Authuser", "0")
            pageId?.let { append("X-Goog-Pageid", it) }
            append("x-origin", "https://music.youtube.com")
            if (setLogin) {
                val c = cookie
                c?.let {
                    append("Cookie", it)
                    if ("SAPISID" !in cookieMap || "__Secure-3PAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidCookie = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
                    val sapisidHash = sha1("$currentTime $sapisidCookie https://music.youtube.com")
                    Log.d(TAG, "SAPI SID Hash: SAPISIDHASH ${currentTime}_$sapisidHash")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    internal fun getAuthorizationHeader(): String? = cookie?.let {
        if ("SAPISID" !in cookieMap || "__Secure-3PAPISID" !in cookieMap) return@let null
        val currentTime = System.currentTimeMillis() / 1000
        val sapisidCookie = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
        val sapisidHash = sha1("$currentTime $sapisidCookie https://music.youtube.com")
        "SAPISIDHASH ${currentTime}_$sapisidHash"
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        cpn: String?,
        signatureTimestamp: Int? = null,
    ) = httpClient.post("player") {
        ytClient(client, setLogin = true)
        setBody(PlayerBody(
            context = client.toContext(locale, visitorData).let {
                if (client == YouTubeClient.Companion.TVHTML5) {
                    it.copy(thirdParty = Context.ThirdParty(embedUrl = "https://www.youtube.com/watch?v=$videoId"))
                } else it
            },
            videoId = videoId,
            playlistId = playlistId,
            cpn = cpn,
            playbackContext = PlayerBody.PlaybackContext(
                contentPlaybackContext = PlayerBody.PlaybackContext.ContentPlaybackContext(
                    signatureTimestamp = signatureTimestamp ?: 20073,
                )
            ),
        ))
    }

    fun getNewPipePlayer(videoId: String): List<Pair<Int, String>> = extractor.newPipePlayer(videoId)

    suspend fun is403Url(url: String): Boolean {
        return try {
            httpClient.head(url).status.value in 400..499
        } catch (e: Exception) {
            Log.w(TAG, "is403Url HEAD failed: ${e.message}")
            true
        }
    }

    /**
     * Fetches session cookies from a YouTube watch page ghost request.
     * This mirrors SimpMusic's getVisitorData() which scrapes youtube.com/watch?v=...
     * to obtain fresh cookies and visitorData before the InnerTube player API call.
     *
     * The Set-Cookie headers from the response contain the PREF and SOCS cookies
     * that YouTube uses to identify the session. Without these, YouTube may return
     * different (less complete) streaming data for some videos.
     */
    suspend fun ensureFreshVisitorData(videoId: String) {
        val currentCookie = cookie
        if (currentCookie != null && currentCookie.contains("PREF=") && !visitorData.isNullOrBlank()) return
        runCatching {
            val response = httpClient.get("https://www.youtube.com/watch?v=$videoId") {
                headers {
                    append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    append("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    append("Accept-Language", "en-US,en;q=0.5")
                }
            }

            val setCookies = response.headers.getAll("Set-Cookie")?.mapNotNull { c ->
                c.split(";").firstOrNull()?.takeIf { it.contains("=") }
            }?.joinToString("; ")
            if (!setCookies.isNullOrBlank()) {
                val extracted = "$setCookies; PREF=hl=en&tz=UTC; SOCS=CAI"
                val c = cookie
                if (c.isNullOrBlank() || !c.contains("PREF=")) {
                    cookie = extracted
                    Log.d(TAG, "Ghost request: set cookie=${cookie?.take(40)}...")
                }
            }

            val html = response.bodyAsText()
            val marker = "var ytInitialPlayerResponse = "
            val start = html.indexOf(marker)
            if (start >= 0) {
                val jsonStart = start + marker.length
                val jsonEnd = html.indexOf("};", jsonStart)
                if (jsonEnd >= 0 && visitorData.isNullOrBlank()) {
                    val jsonStr = html.substring(jsonStart, jsonEnd + 1)
                    val ytInit = normalJson.decodeFromString<JsonObject>(jsonStr)
                    val rc = ytInit["responseContext"]?.jsonObject
                    val vd = rc?.get("webResponseContextExtensionData")?.jsonObject
                        ?.get("ytConfigData")?.jsonObject
                        ?.get("visitorData")?.jsonPrimitive?.content
                    if (vd != null) {
                        visitorData = vd
                        Log.d(TAG, "Ghost request: extracted visitorData=$visitorData")
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Ghost request failed for $videoId: ${e.message}")
        }
    }

    /**
     * Alternative player endpoint for unauthenticated requests.
     * Used when the normal player() call fails for logged-out users.
     */
    suspend fun noLogInPlayer(
        videoId: String,
        client: YouTubeClient = YouTubeClient.Companion.WEB_REMIX,
        customCookie: String? = null,
        visitorData: String? = null,
        poToken: String? = null,
    ) = httpClient.post("player") {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientName)
            append("X-YouTube-Client-Version", client.clientVersion)
            customCookie?.let { append("Cookie", it) }
            append("x-origin", "https://music.youtube.com")
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
        setBody(PlayerBody(
            context = client.toContext(locale, visitorData ?: this@Ytmusic.visitorData).let {
                if (client == YouTubeClient.Companion.TVHTML5) {
                    it.copy(thirdParty = com.pryvn.audiophile.code.playback.models.Context.ThirdParty(embedUrl = "https://www.youtube.com/watch?v=$videoId"))
                } else it
            },
            videoId = videoId,
            playlistId = null,
            cpn = null,
            playbackContext = PlayerBody.PlaybackContext(
                contentPlaybackContext = PlayerBody.PlaybackContext.ContentPlaybackContext(
                    signatureTimestamp = 20073,
                )
            ),
            serviceIntegrityDimensions = poToken?.let { PlayerBody.ServiceIntegrityDimensions(poToken = it) },
        ))
    }

    suspend fun createPoTokenChallenge() = httpClient.post(
        "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create"
    ) {
        poHeader()
        setBody("[\"$poTokenChallengeRequestKey\"]")
    }

    suspend fun generatePoToken(challenge: String) = httpClient.post(
        "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT"
    ) {
        poHeader()
        setBody("[\"$poTokenChallengeRequestKey\", \"$challenge\"]")
    }

    private fun HttpRequestBuilder.poHeader() {
        headers {
            append("accept", "*/*")
            append("origin", "https://www.youtube.com")
            append("content-type", "application/json+protobuf")
            append("priority", "u=1, i")
            append("referer", "https://www.youtube.com/")
            append("sec-ch-ua", "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            append("sec-ch-ua-mobile", "?0")
            append("sec-ch-ua-platform", "\"macOS\"")
            append("sec-fetch-dest", "empty")
            append("sec-fetch-mode", "cors")
            append("sec-fetch-site", "cross-site")
            append("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
            append("x-goog-api-key", "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw")
            append("x-user-agent", "grpc-web-javascript/0.1")
        }
    }
}
