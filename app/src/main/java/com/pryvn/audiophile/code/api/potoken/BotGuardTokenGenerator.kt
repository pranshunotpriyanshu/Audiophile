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

/**
 * Generates cryptographically valid PoTokens by running YouTube's BotGuard
 * challenge inside a headless [WebView].
 *
 * ## Lifecycle
 *
 * 1. [initialize] — called once from `Application.onCreate()` with the app context.
 * 2. [preWarm] — optional, boots the WebView at startup to avoid first-call delay.
 * 3. [mintToken] — suspend function, called per video. Reuses the engine until
 *    it expires (~50 min) or the session changes.
 * 4. [onAppBackgrounded] — releases the WebView to free ~50 MB of memory.
 *
 * ## Optimizations
 *
 * - **Suspend API** — [mintToken] is a suspend function, never blocks the calling thread.
 * - **Pre-warm** — [preWarm] bootstraps the engine at app startup so the first
 *   real mint is instant.
 * - **Player token cache** — Caches per-video tokens (LRU, max 200) to avoid
 *   redundant mints when the same video is played multiple times.
 * - **Session token reuse** — The session token is minted once per engine and
 *   reused for all videos until the engine expires or the session changes.
 * - **Background cleanup** — [onAppBackgrounded] releases the WebView to free memory.
 *   The engine is recreated on the next [mintToken] call.
 */
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

    // ── state ────────────────────────────────────────────────────────
    private var appContext: Context? = null
    private var permanentlyBroken = false

    private val mutex = Mutex()
    private var engine: BotGuardEngine? = null
    private var engineSessionId: String? = null
    private var cachedSessionToken: String? = null
    private var engineReady = false

    /**
     * LRU cache for per-video player tokens.
     * Key: videoId, Value: token string.
     * Avoids redundant mints when the same video is played multiple times.
     */
    private val playerTokenCache: LinkedHashMap<String, String> =
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > PLAYER_TOKEN_CACHE_SIZE
        }

    // ── public API ───────────────────────────────────────────────────

    /** Call once from `Application.onCreate()`. */
    @MainThread
    fun initialize(context: Context) {
        appContext = context.applicationContext
        PoTokenLog.d("Initialized")
    }

    /**
     * Pre-warm the BotGuard engine at app startup.
     * This bootstraps the WebView and generates the session token so that
     * the first real [mintToken] call is instant.
     *
     * Safe to call from a background coroutine. No-op if already warmed.
     */
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

    /**
     * Mint a PoToken pair for the given [videoId] and [sessionId].
     *
     * Returns `null` when:
     * - [initialize] was never called
     * - The system has no usable WebView
     * - BotGuard bootstrap timed out
     * - The WebView is permanently broken
     *
     * This is a **suspend function** — never blocks the calling thread.
     * Call from a coroutine context (e.g. `Dispatchers.IO`).
     */
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

        // Check player token cache first
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
                // Cache the player token
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

    /**
     * Release the WebView to free memory (~50 MB).
     * Call from `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` or similar.
     * The engine is recreated on the next [mintToken] call.
     */
    suspend fun onAppBackgrounded() {
        mutex.withLock {
            if (engine != null) {
                PoTokenLog.d("Releasing engine (app backgrounded)")
                destroyEngine()
            }
        }
    }

    /**
     * Invalidate the player token cache for a specific video.
     * Useful when the user's auth state changes.
     */
    suspend fun invalidatePlayerToken(videoId: String) {
        mutex.withLock {
            playerTokenCache.remove(videoId)
        }
    }

    /**
     * Invalidate all cached tokens.
     * Call when the user logs out or auth state changes.
     */
    suspend fun invalidateAll() {
        mutex.withLock {
            playerTokenCache.clear()
            destroyEngine()
        }
    }

    // ── internal ─────────────────────────────────────────────────────

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

    // ── WebView wrapper ──────────────────────────────────────────────

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

    // ── Challenge Parser (inlined from ChallengeParser.kt) ─────────────

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the raw response from YouTube's `api/jnn/v1/Create` endpoint into a JSON object
     * that can be embedded directly into a JavaScript snippet for `runBotGuard()`.
     *
     * The response is a JSON array.  The first element may be:
     * - a nested JSON array (unscrambled challenge), or
     * - a base64-encoded string (scrambled challenge) that must be descrambled first.
     *
     * The resulting challenge array contains (by index):
     *   [0]  messageId
     *   [1]  interpreterJavascript array (or null)
     *   [2]  interpreterTrustedResourceUrl array (or null)
     *   [3]  interpreterHash
     *   [4]  program (base64)
     *   [5]  globalName
     *   [6]  (unknown)
     *   [7]  clientExperimentsStateBlob
     */
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

    /**
     * Parses the raw response from YouTube's `api/jnn/v1/GenerateIT` endpoint.
     *
     * Returns a pair of:
     * - A JavaScript `Uint8Array(...)` string representation of the integrity token
     * - The token's lifetime in seconds
     */
    private fun parseIntegrityToken(rawResponse: String): Pair<String, Long> {
        val arr = json.parseToJsonElement(rawResponse).jsonArray
        val tokenU8 = base64ToJsUint8Array(arr[0].jsonPrimitive.content)
        val lifetimeSeconds = arr[1].jsonPrimitive.content.toLong()
        return tokenU8 to lifetimeSeconds
    }

    /**
     * Converts a plain-string identifier to a JavaScript `Uint8Array(...)` literal.
     */
    private fun stringToJsUint8Array(identifier: String): String {
        val bytes = identifier.toByteArray(charset = Charsets.UTF_8)
        return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
    }

    /**
     * Converts a comma-separated byte list (output of `Uint8Array.toString()` in JS)
     * to the YouTube-specific base64 encoding used for PoTokens.
     */
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