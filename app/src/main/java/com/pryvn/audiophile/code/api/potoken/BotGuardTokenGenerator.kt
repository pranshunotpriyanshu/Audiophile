package com.pryvn.audiophile.code.api.potoken

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BotGuardTokenGenerator {
    private const val TAG = "BotGuardTokenGen"
    private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
    private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val WV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
    private const val JS_BRIDGE = "BotGuardBridge"

    /** Timeout for first call (cold start: WebView boot + BotGuard bootstrap). */
    private const val COLD_START_TIMEOUT_MS = 15_000L

    /** Timeout for subsequent calls (warm: just mint a token). */
    private const val WARM_TIMEOUT_MS = 5_000L

    /** Maximum number of cached player tokens. */
    private const val PLAYER_TOKEN_CACHE_SIZE = 200

    private val httpClient = OkHttpClient()

    private var appContext: Context? = null
    private var permanentlyBroken = false

    private val mutex = Mutex()
    private var engine: BotGuardEngine? = null
    private var engineSessionId: String? = null
    private var cachedSessionToken: String? = null
    private var engineReady = false

    private val playerTokenCache: LinkedHashMap<String, String> =
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > PLAYER_TOKEN_CACHE_SIZE
        }


    /** Call once from `Application.onCreate()`. */
    @MainThread
    fun initialize(context: Context) {
        appContext = context.applicationContext
        PoTokenLog.d("Initialized")
    }

    suspend fun preWarm(sessionId: String) {
        val ctx = appContext ?: return
        if (permanentlyBroken) return
        if (engineReady) return

        try {
            withTimeout(COLD_START_TIMEOUT_MS) {
                ensureEngineReady(ctx, sessionId)
            }
            PoTokenLog.d("Pre-warm complete")
        } catch (e: Exception) {
            PoTokenLog.w("Pre-warm failed (non-fatal)", e)
        }
    }

    suspend fun mintToken(
        videoId: String,
        sessionId: String,
    ): PoTokenResult? {
        val ctx =
            appContext ?: run {
                PoTokenLog.w("initialize() not called")
                return null
            }
        if (permanentlyBroken) return null

        mutex.withLock {
            val cachedPlayer = playerTokenCache[videoId]
            if (cachedPlayer != null && cachedSessionToken != null && engineReady) {
                PoTokenLog.d("Cache hit for $videoId")
                return PoTokenResult(playerToken = cachedPlayer, sessionToken = cachedSessionToken!!)
            }
        }

        val isFirstCall = !engineReady
        val timeout = if (isFirstCall) COLD_START_TIMEOUT_MS else WARM_TIMEOUT_MS

        return try {
            withTimeout(timeout) {
                val result = mintTokenInternal(ctx, videoId, sessionId, forceNewEngine = false)
                mutex.withLock {
                    playerTokenCache[videoId] = result.playerToken
                }
                result
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            PoTokenLog.w("Timed out after ${timeout}ms — proceeding without PoToken")
            destroyEngine()
            null
        } catch (e: BrokenWebViewException) {
            PoTokenLog.e("Permanently broken WebView", e)
            permanentlyBroken = true
            null
        } catch (e: Exception) {
            PoTokenLog.e("mintToken failed: ${e.message}", e)
            null
        }
    }

    suspend fun onAppBackgrounded() {
        mutex.withLock {
            if (engine != null) {
                PoTokenLog.d("Releasing engine (app backgrounded)")
                destroyEngine()
            }
        }
    }

    suspend fun invalidatePlayerToken(videoId: String) {
        mutex.withLock {
            playerTokenCache.remove(videoId)
        }
    }

    suspend fun invalidateAll() {
        mutex.withLock {
            playerTokenCache.clear()
            destroyEngine()
        }
    }


    private suspend fun ensureEngineReady(
        ctx: Context,
        sessionId: String,
    ): PoTokenResult = mintTokenInternal(ctx, "__warmup__", sessionId, forceNewEngine = false)

    private suspend fun mintTokenInternal(
        ctx: Context,
        videoId: String,
        sessionId: String,
        forceNewEngine: Boolean,
    ): PoTokenResult {
        val (eng, sessionTok, wasNew) =
            mutex.withLock {
                val needsNew =
                    forceNewEngine ||
                        engine == null ||
                        engine!!.isExpired ||
                        engineSessionId != sessionId

                if (needsNew) {
                    withContext(Dispatchers.Main) {
                        engine?.close()
                    }
                    engine = BotGuardEngine.create(ctx)
                    engineSessionId = sessionId
                    cachedSessionToken = engine!!.mint(sessionId)
                    engineReady = true
                }

                Triple(engine!!, cachedSessionToken!!, needsNew)
            }

        val playerTok =
            try {
                eng.mint(videoId)
            } catch (e: Throwable) {
                if (wasNew) throw e
                PoTokenLog.w("mint failed, retrying with fresh engine", e)
                return mintTokenInternal(ctx, videoId, sessionId, forceNewEngine = true)
            }

        return PoTokenResult(playerToken = playerTok, sessionToken = sessionTok)
    }

    private suspend fun destroyEngine() {
        withContext(Dispatchers.Main) {
            engine?.close()
        }
        engine = null
        engineSessionId = null
        cachedSessionToken = null
        engineReady = false
    }


    private class BotGuardEngine private constructor(
        private val webView: WebView,
        private val readySignal: Continuation<BotGuardEngine>,
    ) {
        private val scope = MainScope()
        private val pendingMints =
            Collections.synchronizedMap(
                java.util.HashMap<String, Continuation<String>>(),
            )
        private lateinit var expiry: Instant

        val isExpired: Boolean get() = Instant.now().isAfter(expiry)

        fun startBootstrap() {
            scope.launch(exceptionHandler) {
                val html =
                    withContext(Dispatchers.IO) {
                        webView.context.assets
                            .open("po_token.html")
                            .bufferedReader()
                            .use { it.readText() }
                    }
                val patched = html.replaceFirst("</script>", "\n$JS_BRIDGE.onPageLoaded()</script>")
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    patched,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onPageLoaded() {
            PoTokenLog.d("Page loaded — requesting challenge from Create")
            postToBotGuard(CREATE_URL, "[ \"$REQUEST_KEY\" ]") { body ->
                val challengeJson = parseCreateChallenge(body)
                webView.evaluateJavascript(
                    """
                    try {
                        var data = $challengeJson;
                        runBotGuard(data).then(function(r) {
                            this.webPoSignalOutput = r.webPoSignalOutput;
                            $JS_BRIDGE.onBotGuardReady(r.botguardResponse);
                        }, function(e) {
                            $JS_BRIDGE.onFatalError(e + "\n" + e.stack);
                        });
                    } catch(e) { $JS_BRIDGE.onFatalError(e + "\n" + e.stack); }
                    """.trimIndent(),
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onBotGuardReady(botguardResponse: String) {
            PoTokenLog.d("BotGuard executed — requesting integrity token from GenerateIT")
            postToBotGuard(GENERATE_IT_URL, "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { body ->
                try {
                    val (tokenU8, lifetimeSec) = parseIntegrityToken(body)
                    expiry = Instant.now().plusSeconds(lifetimeSec).minus(10, ChronoUnit.MINUTES)

                    webView.evaluateJavascript(
                        """
                        try {
                            this.integrityToken = $tokenU8;
                            createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                                $JS_BRIDGE.onMinterReady();
                            }).catch(function(e) {
                                $JS_BRIDGE.onFatalError(e + "\n" + e.stack);
                            });
                        } catch(e) { $JS_BRIDGE.onFatalError(e + "\n" + e.stack); }
                        """.trimIndent(),
                        null,
                    )
                } catch (e: Exception) {
                    PoTokenLog.e("parseIntegrityToken failed", e)
                    signalError(PoTokenException("GenerateIT parse failed: ${e.message}"))
                }
            }
        }

        @JavascriptInterface
        fun onMinterReady() {
            PoTokenLog.d("Minter ready")
            readySignal.resume(this@BotGuardEngine)
        }

        @JavascriptInterface
        fun onFatalError(error: String) {
            PoTokenLog.e("Fatal JS error: $error")
            signalError(classifyJsError(error))
        }

        suspend fun mint(identifier: String): String =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    pendingMints[identifier] = cont
                    val u8Arg = stringToJsUint8Array(identifier)
                    webView.evaluateJavascript(
                        """
                        try {
                            obtainPoToken($u8Arg).then(function(u8) {
                                $JS_BRIDGE.onMintOk("$identifier", u8.join(","));
                            }).catch(function(e) {
                                $JS_BRIDGE.onMintErr("$identifier", e + "\n" + (e.stack || ''));
                            });
                        } catch(e) { $JS_BRIDGE.onMintErr("$identifier", e + "\n" + e.stack); }
                        """.trimIndent(),
                        null,
                    )
                }
            }

        @JavascriptInterface
        fun onMintOk(
            identifier: String,
            csvBytes: String,
        ) {
            val base64 = commaSeparatedBytesToBase64(csvBytes)
            PoTokenLog.d("Minted token for $identifier (${base64.length} chars)")
            pendingMints.remove(identifier)?.resume(base64)
        }

        @JavascriptInterface
        fun onMintErr(
            identifier: String,
            error: String,
        ) {
            PoTokenLog.e("Mint failed for $identifier: $error")
            pendingMints.remove(identifier)?.resumeWithException(classifyJsError(error))
        }

        private val exceptionHandler = CoroutineExceptionHandler { _, t -> signalError(t) }

        private fun signalError(error: Throwable) {
            close()
            readySignal.resumeWithException(error)
        }

        private fun postToBotGuard(
            url: String,
            jsonBody: String,
            onSuccess: (String) -> Unit,
        ) {
            scope.launch(exceptionHandler) {
                val request =
                    okhttp3.Request
                        .Builder()
                        .url(url)
                        .post(jsonBody.toRequestBody())
                        .headers(
                            mapOf(
                                "User-Agent" to WV_USER_AGENT,
                                "Accept" to "application/json",
                                "Content-Type" to "application/json+protobuf",
                                "x-goog-api-key" to API_KEY,
                                "x-user-agent" to "grpc-web-javascript/0.1",
                            ).toHeaders(),
                        ).build()

                val response =
                    withContext(Dispatchers.IO) {
                        httpClient.newCall(request).execute()
                    }

                if (response.code != 200) {
                    signalError(PoTokenException("BotGuard HTTP ${response.code} from $url"))
                } else {
                    val body = withContext(Dispatchers.IO) { response.body!!.string() }
                    onSuccess(body)
                }
            }
        }

        @MainThread
        fun close() {
            scope.cancel()
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }

        companion object {
            suspend fun create(context: Context): BotGuardEngine {
                return withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val wv =
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.userAgentString = WV_USER_AGENT
                                settings.blockNetworkLoads = true
                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                            if (m.message().contains("Uncaught")) {
                                                val err = "\"${m.message()}\", ${m.sourceId()} (${m.lineNumber()})"
                                                cont.resumeWithException(BrokenWebViewException(err))
                                            }
                                            return super.onConsoleMessage(m)
                                        }
                                    }
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onRenderProcessGone(
                                            view: WebView,
                                            detail: android.webkit.RenderProcessGoneDetail,
                                        ): Boolean {
                                            PoTokenLog.w("WebView renderer gone (crashed=${detail.didCrash()})")
                                            runCatching {
                                                cont.resumeWithException(
                                                    PoTokenException("WebView renderer process gone"),
                                                )
                                            }
                                            return true
                                        }
                                    }
                            }
                        val engine = BotGuardEngine(wv, cont)
                        wv.addJavascriptInterface(engine, JS_BRIDGE)
                        engine.startBootstrap()
                    }
                }
            }
        }
    }


    private val json = Json { ignoreUnknownKeys = true }

    private fun parseCreateChallenge(rawResponse: String): String {
        val outer = json.parseToJsonElement(rawResponse).jsonArray

        val challenge =
            if (outer.size > 1 && outer[1].jsonPrimitive.isString) {
                // Scrambled: base64-decode then add 97 to each byte
                val decoded = descramble(outer[1].jsonPrimitive.content)
                json.parseToJsonElement(decoded).jsonArray
            } else {
                outer[0].jsonArray
            }

        val program = challenge[4].jsonPrimitive.content
        val globalName = challenge[5].jsonPrimitive.content

        val interpreterJs =
            challenge[1]
                .takeIf { it !is JsonNull }
                ?.jsonArray
                ?.firstOrNull { it.jsonPrimitive.isString }

        val interpreterUrl =
            challenge[2]
                .takeIf { it !is JsonNull }
                ?.jsonArray
                ?.firstOrNull { it.jsonPrimitive.isString }

        return json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "program" to JsonPrimitive(program),
                    "globalName" to JsonPrimitive(globalName),
                    "interpreterJavascript" to
                        JsonObject(
                            mapOf(
                                "privateDoNotAccessOrElseSafeScriptWrappedValue" to (interpreterJs ?: JsonNull),
                                "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to (interpreterUrl ?: JsonNull),
                            ),
                        ),
                ),
            ),
        )
    }

    private fun parseIntegrityToken(rawResponse: String): Pair<String, Long> {
        val arr = json.parseToJsonElement(rawResponse).jsonArray
        val tokenU8 = base64ToJsUint8Array(arr[0].jsonPrimitive.content)
        val lifetimeSeconds = arr[1].jsonPrimitive.content.toLong()
        return tokenU8 to lifetimeSeconds
    }

    private fun stringToJsUint8Array(identifier: String): String {
        val bytes = identifier.toByteArray(charset = Charsets.UTF_8)
        return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
    }

    private fun commaSeparatedBytesToBase64(commaBytes: String): String =
        commaBytes
            .split(",")
            .map { it.trim().toInt().toByte() }
            .toByteArray()
            .toByteString()
            .base64()
            .replace('+', '-')
            .replace('/', '_')

    // --- internal helpers ---

    private fun descramble(base64Payload: String): String =
        base64ToByteArray(base64Payload)
            .map { (it + 97).toByte() }
            .toByteArray()
            .decodeToString()

    private fun base64ToJsUint8Array(base64: String): String {
        val bytes = base64ToByteArray(base64)
        return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
    }

    private fun base64ToByteArray(base64: String): ByteArray {
        val normalised =
            base64
                .replace('-', '+')
                .replace('_', '/')
                .replace('.', '=')
        return (
            normalised.decodeBase64()
                ?: throw PoTokenException("Cannot decode base64: ${base64.take(40)}…")
        ).toByteArray()
    }

}
