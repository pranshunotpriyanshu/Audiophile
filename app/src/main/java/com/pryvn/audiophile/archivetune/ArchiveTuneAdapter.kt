package com.pryvn.audiophile.archivetune

import android.content.Context
import android.net.ConnectivityManager
import com.pryvn.audiophile.YosBasicApplication
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.data.cache.PersistentStreamCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import moe.rukamori.archivetune.utils.YTPlayerUtils
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object ArchiveTuneAdapter {
    private const val TAG = "ATAdapter"
    private const val STREAM_CACHE_TTL_MS = 6L * 60 * 60 * 1000
    private const val SIGNATURE_TIMESTAMP_TTL_MS = 15L * 60 * 1000 // 15 min
    private val audioQuality = AudioQuality.HIGH

    // In-memory cache (fastest)
    private data class CachedStream(
        val resolved: MediaController.ResolvedStream,
        val cachedAtMs: Long,
    )
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    // Signature timestamp cache
    private var cachedSignatureTimestamp: Int? = null
    private var signatureTimestampCachedAt: Long = 0

    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize(context: Context) {
        PersistentStreamCache.initialize(context)
        // Pre-warm BotGuard WebView
        scope.launch {
            runCatching {
                val visitor = YouTube.currentPlaybackAuthState().visitorData
                if (!visitor.isNullOrBlank()) {
                    BotGuardTokenGenerator.preWarm(visitor)
                }
            }
        }
    }

    fun prefetch(videoId: String) {
        val key = cacheKey(videoId)
        if (streamCache.containsKey(key)) return
        scope.launch {
            Timber.tag(TAG).d("Prefetching stream for %s", videoId)
            runCatching { resolveInternal(videoId) }
        }
    }

    private fun cacheKey(videoId: String) = "${videoId}_${audioQuality.name}"

    private fun getCached(videoId: String): MediaController.ResolvedStream? {
        val entry = streamCache[cacheKey(videoId)] ?: return null
        if (System.currentTimeMillis() - entry.cachedAtMs > STREAM_CACHE_TTL_MS) {
            streamCache.remove(cacheKey(videoId))
            return null
        }
        Timber.tag(TAG).d("VideoId cache HIT for %s (age=%ds)", videoId,
            (System.currentTimeMillis() - entry.cachedAtMs) / 1000)
        return entry.resolved
    }

    private fun putCache(videoId: String, resolved: MediaController.ResolvedStream) {
        streamCache[cacheKey(videoId)] = CachedStream(resolved, System.currentTimeMillis())
    }

    suspend fun resolve(videoId: String): MediaController.ResolvedStream {
        val key = cacheKey(videoId)
        val cached = getCached(videoId)
        if (cached != null) {
            Timber.tag(TAG).i("[TIMING] resolve() MEMORY CACHE HIT — 0ms total")
            return cached
        }

        // Check persistent cache
        val persistent = PersistentStreamCache.getStream(videoId, audioQuality.name)
        if (persistent != null) {
            putCache(videoId, persistent)
            Timber.tag(TAG).i("[TIMING] resolve() PERSISTENT CACHE HIT — 0ms total")
            return persistent
        }

        val result = resolveInternal(videoId)
        putCache(videoId, result)
        // Store in persistent cache (non-blocking)
        scope.launch { PersistentStreamCache.putStream(videoId, result, audioQuality.name) }
        return result
    }

    private suspend fun resolveInternal(videoId: String): MediaController.ResolvedStream {
        val timings = mutableListOf<String>()
        fun mark(label: String, start: Long) {
            timings.add("$label: ${System.currentTimeMillis() - start} ms")
        }

        val tStart = System.currentTimeMillis()

        ensureVisitorData()
        val tVisitor = System.currentTimeMillis()
        mark("VisitorData", tStart)

        val connectivityManager =
            YosBasicApplication.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Get cached or fresh signature timestamp
        val signatureTimestamp = getSignatureTimestamp()
        val tSig = System.currentTimeMillis()
        mark("SignatureTimestamp", tVisitor)

        val playbackData = YTPlayerUtils.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager,
        ).getOrThrow()

        val tPlayer = System.currentTimeMillis()
        mark("Player request", tSig)

        val videoDetails = playbackData.videoDetails

        val resolved = MediaController.ResolvedStream(
            url = playbackData.streamUrl,
            mimeType = playbackData.format?.mimeType,
            title = videoDetails?.title,
            durationSeconds = videoDetails?.lengthSeconds?.toIntOrNull(),
            artists = videoDetails?.author,
            thumbnailUrl = null,
            album = null,
        )

        val tEnd = System.currentTimeMillis()
        mark("Extraction", tPlayer)

        val timingLog = timings.joinToString("\n")
        Timber.tag(TAG).i(
            "==================================\n" +
            "Playback Resolve Profile for %s\n" +
            "%s\n" +
            "Total: %d ms\n" +
            "==================================",
            videoId, timingLog, tEnd - tStart,
        )

        return resolved.copy(timingLog = timingLog)
    }

    private suspend fun getSignatureTimestamp(): Int {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (cachedSignatureTimestamp != null && now - signatureTimestampCachedAt < SIGNATURE_TIMESTAMP_TTL_MS) {
                Timber.tag(TAG).d("Signature timestamp cache HIT")
                return@withContext cachedSignatureTimestamp!!
            }
            val timestamp = YouTubeApi.fetchSignatureTimestamp()
            cachedSignatureTimestamp = timestamp
            signatureTimestampCachedAt = now
            timestamp
        }
    }

    fun updateAuth(
        cookie: String? = null,
        visitorData: String? = null,
        dataSyncId: String? = null,
        pageId: String? = null,
    ) {
        YouTube.authState = PlaybackAuthState(
            cookie = cookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
        )
        if (visitorData != null) PlaybackCache.visitorData = visitorData
        if (cookie != null) PlaybackCache.cookie = cookie
        if (dataSyncId != null) PlaybackCache.dataSyncId = dataSyncId
    }

    suspend fun ensureVisitorData() {
        val currentVisitor = YouTube.authState.visitorData
        if (!currentVisitor.isNullOrBlank()) return

        val cached = PlaybackCache.visitorData
        if (!cached.isNullOrBlank()) {
            YouTube.visitorData = cached
            return
        }

        runCatching {
            YouTube.visitorData().getOrNull()?.let {
                YouTube.visitorData = it
                PlaybackCache.visitorData = it
            }
        }
    }

    fun restorePersistedAuth() {
        val cookie = PlaybackCache.cookie
        val visitorData = PlaybackCache.visitorData
        val dataSyncId = PlaybackCache.dataSyncId
        if (cookie != null || visitorData != null || dataSyncId != null) {
            YouTube.authState = PlaybackAuthState(
                cookie = cookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
        }
    }

    fun invalidateCache(videoId: String) {
        streamCache.remove(cacheKey(videoId))
        scope.launch { PersistentStreamCache.invalidate(videoId) }
    }

    fun clearAllCaches() {
        streamCache.clear()
        scope.launch { PersistentStreamCache.clearAll() }
    }
}