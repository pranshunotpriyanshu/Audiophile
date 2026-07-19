package com.pryvn.audiophile.data.cache

import android.content.Context
import com.pryvn.audiophile.code.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StreamCacheEntry(
    val videoId: String,
    val audioQuality: String,
    val streamUrl: String,
    val mimeType: String?,
    val title: String?,
    val durationSeconds: Int?,
    val artists: String?,
    val thumbnailUrl: String?,
    val album: String?,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

object PersistentStreamCache {
    private const val TAG = "PersistentStreamCache"
    private const val STREAM_CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
    private const val CACHE_DIR_NAME = "stream_cache"
    private const val CACHE_FILE_PREFIX = "stream_"

    private val json = Json { ignoreUnknownKeys = true }
    private var cacheDir: File? = null

    fun initialize(context: Context) {
        cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        cacheDir?.mkdirs()
        // Clean up expired entries on startup
        cleanupExpired()
    }

    private fun getCacheFile(videoId: String, audioQuality: String): File? {
        return cacheDir?.let {
            File(it, "${CACHE_FILE_PREFIX}${videoId}_$audioQuality.json")
        }
    }

    suspend fun putStream(
        videoId: String,
        resolved: MediaController.ResolvedStream,
        audioQuality: String
    ) = withContext(Dispatchers.IO) {
        val file = getCacheFile(videoId, audioQuality) ?: return@withContext
        val entry = StreamCacheEntry(
            videoId = videoId,
            audioQuality = audioQuality,
            streamUrl = resolved.url,
            mimeType = resolved.mimeType,
            title = resolved.title,
            durationSeconds = resolved.durationSeconds,
            artists = resolved.artists,
            thumbnailUrl = resolved.thumbnailUrl,
            album = resolved.album,
            cachedAtMs = System.currentTimeMillis(),
        )
        file.writeText(json.encodeToString(entry))
    }

    suspend fun getStream(
        videoId: String,
        audioQuality: String
    ): MediaController.ResolvedStream? = withContext(Dispatchers.IO) {
        val file = getCacheFile(videoId, audioQuality) ?: return@withContext null
        if (!file.exists()) return@withContext null

        val entry = json.decodeFromString<StreamCacheEntry>(file.readText())
        val age = System.currentTimeMillis() - entry.cachedAtMs
        if (age > STREAM_CACHE_TTL_MS) {
            file.delete()
            return@withContext null
        }

        MediaController.ResolvedStream(
            url = entry.streamUrl,
            mimeType = entry.mimeType,
            title = entry.title,
            durationSeconds = entry.durationSeconds,
            artists = entry.artists,
            thumbnailUrl = entry.thumbnailUrl,
            album = entry.album,
        )
    }

    suspend fun invalidate(videoId: String) = withContext(Dispatchers.IO) {
        cacheDir?.listFiles()?.forEach { file ->
            if (file.name.contains(videoId)) {
                file.delete()
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    private fun cleanupExpired() {
        cacheDir?.listFiles()?.forEach { file ->
            try {
                val entry = json.decodeFromString<StreamCacheEntry>(file.readText())
                val age = System.currentTimeMillis() - entry.cachedAtMs
                if (age > STREAM_CACHE_TTL_MS) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Corrupted file, delete it
                file.delete()
            }
        }
    }
}