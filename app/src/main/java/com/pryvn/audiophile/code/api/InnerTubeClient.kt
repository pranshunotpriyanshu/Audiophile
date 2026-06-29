package com.pryvn.audiophile.code.api

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import kotlin.random.Random

@Serializable
data class Context(
    val client: ClientContext,
    val user: UserContext? = null,
    val request: RequestContext? = null,
)

@Serializable
data class ClientContext(
    val clientName: String,
    val clientVersion: String,
    val hl: String = "en",
    val gl: String = "US",
    val visitorData: String? = null,
)

@Serializable
data class UserContext(
    val lockedSafetyMode: Boolean = false,
    val onBehalfOfUser: String? = null,
)

@Serializable
data class RequestContext(
    val useSsl: Boolean = true,
)

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

object InnerTubeClient {
    private const val API_BASE = "https://music.youtube.com/youtubei/v1"
    private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"
    private const val CLIENT_NAME = "WEB_REMIX"
    private const val CLIENT_VERSION = "1.20260523.01.00"
    private const val CLIENT_ID = "67"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private val VISITOR_DATA_REGEX = Regex("""C[a-zA-Z0-9_-]{22,}""")

    private var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    fun applyAuthState(state: PlaybackAuthState) {
        authState = state.normalized()
    }

    fun currentAuthState(): PlaybackAuthState = authState

    var cookie: String?
        get() = authState.cookie
        set(value) { authState = authState.copy(cookie = value).normalized() }

    var visitorData: String?
        get() = authState.visitorData
        set(value) { authState = authState.copy(visitorData = value).normalized() }

    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) { authState = authState.copy(dataSyncId = value).normalized() }

    var poToken: String?
        get() = authState.poToken
        set(value) { authState = authState.copy(poToken = value).normalized() }

    var poTokenPlayer: String?
        get() = authState.poTokenPlayer
        set(value) { authState = authState.copy(poTokenPlayer = value).normalized() }

    val hasLoginCookie: Boolean get() = authState.hasLoginCookie

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(this@InnerTubeClient.json)
        }
        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 20000
        }
        engine {
            config {
                proxy(Proxy.NO_PROXY)
            }
        }
        defaultRequest {
            url(API_BASE)
        }
    }

    private fun buildContext(setLogin: Boolean = false): Context {
        if (authState.visitorData.isNullOrBlank()) {
            authState = authState.copy(visitorData = generateVisitorData()).normalized()
        }
        return Context(
            client = ClientContext(
                clientName = CLIENT_NAME,
                clientVersion = CLIENT_VERSION,
                hl = "en",
                gl = "US",
                visitorData = authState.visitorData,
            ),
            user = UserContext(
                lockedSafetyMode = false,
                onBehalfOfUser = if (setLogin && authState.hasLoginCookie && !authState.dataSyncId.isNullOrBlank()) {
                    authState.dataSyncId
                } else {
                    null
                },
            ),
            request = RequestContext(),
        )
    }

    private fun HttpRequestBuilder.ytHeaders(endpoint: String = "", setLogin: Boolean = false) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", CLIENT_ID)
            append("X-YouTube-Client-Version", CLIENT_VERSION)
            append("X-Origin", "https://music.youtube.com")
            append("Referer", "https://music.youtube.com/")
            if (endpoint.isNotBlank()) {
                append("X-Goog-Request-Info", "name=$endpoint")
            }
            authState.visitorData?.let { append("X-Goog-Visitor-Id", it) }
            authState.poToken?.let { append("X-Goog-Po-Token", it) }
            if (setLogin) {
                val cookie = authState.cookie
                if (!cookie.isNullOrBlank()) {
                    append("Cookie", cookie)
                    val loginCookie = youtubeLoginCookieValue(cookie)
                    if (loginCookie != null) {
                        val currentTime = System.currentTimeMillis() / 1000
                        val hash = sha1("$currentTime $loginCookie https://music.youtube.com")
                        append("Authorization", "SAPISIDHASH ${currentTime}_$hash")
                    }
                }
            }
        }
        userAgent(USER_AGENT)
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        var delayMs = 400L
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val transient = e is IOException ||
                    e is HttpRequestTimeoutException ||
                    (e is ClientRequestException && e.response.status.value in 500..599)
                if (!transient || attempt >= maxAttempts - 1) throw e
                attempt++
                delay(delayMs)
                delayMs = (delayMs * 1.8).toLong()
            }
        }
    }

    suspend fun ensureVisitorData() {
        if (!visitorData.isNullOrBlank()) return
        fetchVisitorData().onSuccess { visitorData = it }
            .onFailure { visitorData = generateVisitorData() }
    }

    suspend fun fetchVisitorData(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get("https://music.youtube.com/sw.js_data")
            val body = response.bodyAsText().removePrefix(")]}'")
            val root = json.parseToJsonElement(body).jsonArray
            root[0].jsonArray[2].jsonArray
                .first { element ->
                    (element as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                        VISITOR_DATA_REGEX.containsMatchIn(candidate)
                    } == true
                }
                .jsonPrimitive.content
        }
    }

    private fun generateVisitorData(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
        val randomStr = (1..48).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "Cgt" + randomStr.take(42) + "Vw"
    }

    fun generateSapisidHashPublic(cookie: String, origin: String): String? {
        val loginCookie = youtubeLoginCookieValue(cookie) ?: return null
        val currentTime = System.currentTimeMillis() / 1000
        val hash = sha1("$currentTime $loginCookie $origin")
        return "${currentTime}_$hash"
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun search(
        query: String?,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            withRetry {
                client.post("search") {
                    ytHeaders("search", setLogin = setLogin)
                    setBody(buildJsonObject {
                        put("context", json.encodeToJsonElement(buildContext(setLogin)))
                        query?.let { put("query", it) }
                        params?.let { put("params", it) }
                    })
                    continuation?.let {
                        parameter("continuation", it)
                        parameter("ctoken", it)
                    }
                    parameter("key", API_KEY)
                    parameter("prettyPrint", false)
                }.body<JsonObject>()
            }
        }
    }

    suspend fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            withRetry {
                client.post("browse") {
                    ytHeaders("browse", setLogin = setLogin)
                    setBody(buildJsonObject {
                        put("context", json.encodeToJsonElement(buildContext(setLogin)))
                        browseId?.let { put("browseId", it) }
                        params?.let { put("params", it) }
                        continuation?.let { put("continuation", it) }
                    })
                    parameter("key", API_KEY)
                    parameter("prettyPrint", false)
                }.body<JsonObject>()
            }
        }
    }

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        signatureTimestamp: Int? = null,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            withRetry {
                client.post("player") {
                    ytHeaders("player", setLogin = hasLoginCookie)
                    setBody(PlayerBody(
                        context = buildContext(setLogin = hasLoginCookie),
                        videoId = videoId,
                        playlistId = playlistId,
                        playbackContext = signatureTimestamp?.let {
                            PlaybackContext(ContentPlaybackContext(it))
                        },
                        serviceIntegrityDimensions = authState.poTokenPlayer?.let {
                            ServiceIntegrityDimensions(poToken = it)
                        },
                    ))
                    parameter("key", API_KEY)
                    parameter("prettyPrint", false)
                }.body<JsonObject>()
            }
        }
    }

    suspend fun next(
        videoId: String?,
        playlistId: String? = null,
        continuation: String? = null,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            withRetry {
                client.post("next") {
                    ytHeaders("next", setLogin = hasLoginCookie)
                    setBody(buildJsonObject {
                        put("context", json.encodeToJsonElement(buildContext(setLogin = hasLoginCookie)))
                        videoId?.let { put("videoId", it) }
                        playlistId?.let { put("playlistId", it) }
                        continuation?.let { put("continuation", it) }
                    })
                    parameter("key", API_KEY)
                    parameter("prettyPrint", false)
                }.body<JsonObject>()
            }
        }
    }

    suspend fun accountMenu(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            withRetry {
                client.post("account/account_menu") {
                    ytHeaders("account", setLogin = true)
                    setBody(buildJsonObject {
                        put("context", json.encodeToJsonElement(buildContext(setLogin = true)))
                    })
                    parameter("key", API_KEY)
                    parameter("prettyPrint", false)
                }.body<JsonObject>()
            }
        }
    }

    suspend fun getSearchSuggestions(input: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            withRetry {
                client.post("music/get_search_suggestions") {
                    ytHeaders("search")
                    setBody(buildJsonObject {
                        put("context", json.encodeToJsonElement(buildContext()))
                        put("input", input)
                    })
                    parameter("key", API_KEY)
                    parameter("prettyPrint", false)
                }.body<JsonObject>()
            }
        }
    }

    fun close() {
        client.close()
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
