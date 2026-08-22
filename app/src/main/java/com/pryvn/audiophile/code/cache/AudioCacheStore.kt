package com.pryvn.audiophile.code.cache

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.pryvn.audiophile.UriTypeAdapter
import com.pryvn.audiophile.YosBasicApplication
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.data.libraries.FavPlayListLibrary
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.PlayListLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

object AudioCacheStore {

    private const val DIR_NAME = "audio_cache"
    private const val META_FILE_NAME = ".metadata.json"
    private const val NETWORK_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024

    /** Progress of one in-flight download, keyed by the raw videoId. */
    @Stable
    data class DownloadProgress(
        val totalBytes: Long,
        val downloadedBytes: Long,
    ) {
        /** 0f..1f when the total size is known, -1f when indeterminate. */
        val fraction: Float
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                -1f
            }
    }

    // Guards against duplicate concurrent downloads of the same video.
    private val downloading = ConcurrentHashMap.newKeySet<String>()

    private val cancelledDownloads = ConcurrentHashMap.newKeySet<String>()

    // Bumped by clear(); in-flight downloads abort when it changes.
    @Volatile
    private var clearGeneration = 0

    val activeDownloads = mutableStateMapOf<String, DownloadProgress>()

    private val dir: File
        get() = File(YosBasicApplication.instance.filesDir, DIR_NAME).apply { mkdirs() }

    private val metaFile: File
        get() = File(dir, META_FILE_NAME)

    private fun safeName(videoId: String): String =
        videoId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "unknown" }

    private fun fileFor(videoId: String): File = File(dir, "${safeName(videoId)}.m4a")


    private data class CachedSongRecord(
        val videoId: String,
        val item: YosMediaItem,
    )

    private val metadataLock = Any()
    private var metadataCache: MutableMap<String, CachedSongRecord>? = null

    private val gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .create()

    private fun loadMetadata(): MutableMap<String, CachedSongRecord> {
        metadataCache?.let { return it }
        synchronized(metadataLock) {
            metadataCache?.let { return it }
            val map = try {
                if (!metaFile.isFile) {
                    mutableMapOf()
                } else {
                    val json = metaFile.readText()
                    if (json.isBlank()) {
                        mutableMapOf()
                    } else {
                        val type = object : TypeToken<MutableMap<String, CachedSongRecord>>() {}.type
                        gson.fromJson<MutableMap<String, CachedSongRecord>>(json, type) ?: mutableMapOf()
                    }
                }
            } catch (_: Exception) {
                mutableMapOf()
            }
            metadataCache = map
            return map
        }
    }

    private fun saveMetadata() {
        synchronized(metadataLock) {
            try {
                val json = gson.toJson(loadMetadata())
                val tmp = File(dir, "$META_FILE_NAME.tmp")
                tmp.writeText(json)
                metaFile.delete()
                if (!tmp.renameTo(metaFile)) tmp.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** Records the display metadata of a song that is being/has been cached. */
    fun rememberMetadata(videoId: String, item: YosMediaItem) {
        if (videoId.isBlank()) return
        if (item.title.isNullOrBlank() && item.artists.isNullOrBlank() && item.thumb == null) return
        synchronized(metadataLock) {
            loadMetadata()[safeName(videoId)] = CachedSongRecord(videoId, item)
            saveMetadata()
        }
    }


    private data class CacheStats(val count: Int, val bytes: Long)

    private val statsLock = Any()
    private var statsInitialized = false
    private val stats = mutableStateOf(CacheStats(0, 0L))

    private fun refreshStats() {
        stats.value = CacheStats(count(), totalBytes())
    }

    private fun ensureStats() {
        if (statsInitialized) return
        synchronized(statsLock) {
            if (statsInitialized) return
            refreshStats()
            statsInitialized = true
        }
    }

    /** Number of fully cached songs (Compose-observable). */
    val cachedCount: Int
        get() {
            ensureStats()
            return stats.value.count
        }

    /** Total bytes of cached audio files (Compose-observable). */
    val cachedBytes: Long
        get() {
            ensureStats()
            return stats.value.bytes
        }


    /** Local file URI when [videoId] is already cached, else null. */
    fun getCachedUri(videoId: String?): String? {
        if (videoId.isNullOrBlank()) return null
        return try {
            val file = fileFor(videoId)
            if (file.isFile && file.length() > 0L) Uri.fromFile(file).toString() else null
        } catch (_: Exception) {
            null
        }
    }

    /** Cached file size for [videoId], or 0 when not cached. */
    fun cachedFileSize(videoId: String?): Long {
        if (videoId.isNullOrBlank()) return 0L
        return try {
            val file = fileFor(videoId)
            if (file.isFile) file.length() else 0L
        } catch (_: Exception) {
            0L
        }
    }

    /** Progress of an in-flight download for [videoId], or null. */
    fun progressOf(videoId: String?): DownloadProgress? =
        videoId?.let { activeDownloads[it] }

    fun cancelDownload(videoId: String?) {
        if (videoId.isNullOrBlank()) return
        cancelledDownloads.add(videoId)
        activeDownloads.remove(videoId)
    }

    fun removeSongs(songs: List<YosMediaItem>) {
        val ids = songs.mapNotNull { it.mediaId }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        ids.forEach { id ->
            cancelledDownloads.add(id)
            activeDownloads.remove(id)
            downloading.remove(id)
        }
        synchronized(metadataLock) {
            val meta = loadMetadata()
            ids.forEach { id -> meta.remove(safeName(id)) }
            saveMetadata()
        }
        ids.forEach { id ->
            val file = fileFor(id)
            file.delete()
            File(dir, "${file.name}.tmp").delete()
        }
        refreshStats()
    }

    /** Display title for a videoId that is being downloaded, if known. */
    fun titleFor(videoId: String?): String? {
        if (videoId.isNullOrBlank()) return null
        return loadMetadata()[safeName(videoId)]?.item?.title
            ?: knownSongsById()[videoId]?.title
    }

    fun cachedSongs(): List<YosMediaItem> {
        val metadata = loadMetadata()
        val knownById = knownSongsById()
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".m4a") && it.length() > 0L }
            .mapNotNull { file ->
                val key = file.name.removeSuffix(".m4a")
                val fileUri = Uri.fromFile(file)
                val record = metadata[key]
                val item = record?.item ?: knownById[record?.videoId ?: key]
                if (item != null) {
                    item.copy(
                        uri = fileUri,
                        mediaId = record?.videoId ?: key,
                        isLocalMedia = true,
                    )
                } else {
                    YosMediaItem(uri = fileUri, mediaId = key, title = key, isLocalMedia = true)
                }
            }
            .sortedBy { (it.title ?: "").lowercase(Locale.ROOT) }
    }

    /** Number of fully cached songs (raw disk scan). */
    fun count(): Int = try {
        dir.listFiles()?.count { it.isFile && it.name.endsWith(".m4a") && it.length() > 0L } ?: 0
    } catch (_: Exception) {
        0
    }

    /** Total bytes of cached audio files (raw disk scan). */
    fun totalBytes(): Long = try {
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".m4a") }?.sumOf { it.length() } ?: 0L
    } catch (_: Exception) {
        0L
    }

    /** Removes every cached audio file and its metadata. */
    fun clear() {
        clearGeneration++
        downloading.clear()
        activeDownloads.clear()
        synchronized(metadataLock) {
            metadataCache = null
        }
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
        refreshStats()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }


    suspend fun download(
        videoId: String?,
        url: String?,
        item: YosMediaItem? = null,
    ) = withContext(Dispatchers.IO) {
        if (videoId.isNullOrBlank() || url.isNullOrBlank()) return@withContext
        if (url.startsWith("file://")) return@withContext
        if (!downloading.add(videoId)) return@withContext

        val generationAtStart = clearGeneration
        item?.let { rememberMetadata(videoId, it) }

        val dest = fileFor(videoId)
        val tmp = File(dir, "${dest.name}.tmp")
        try {
            if (dest.isFile && dest.length() > 0L) return@withContext
            tmp.delete()

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = NETWORK_TIMEOUT_MS
                    readTimeout = NETWORK_TIMEOUT_MS
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
                val code = connection.responseCode
                if (code !in 200..299) return@withContext

                val total = runCatching { connection.contentLengthLong }.getOrDefault(-1L)
                val input = connection.inputStream
                val output = tmp.outputStream()
                var written = 0L
                try {
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (!coroutineContext.isActive ||
                            generationAtStart != clearGeneration ||
                            videoId in cancelledDownloads
                        ) return@withContext
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (written % BUFFER_SIZE == 0L) {
                            activeDownloads[videoId] = DownloadProgress(total, written)
                        }
                    }
                    output.flush()
                } finally {
                    output.close()
                    input.close()
                }

                activeDownloads.remove(videoId)
                if (tmp.length() > 0L) {
                    dest.delete()
                    if (!tmp.renameTo(dest)) tmp.delete()
                } else {
                    tmp.delete()
                }
            } finally {
                connection?.disconnect()
            }
        } catch (_: Exception) {
            tmp.delete()
        } finally {
            activeDownloads.remove(videoId)
            downloading.remove(videoId)
            cancelledDownloads.remove(videoId)
            refreshStats()
        }
    }


    private fun knownSongsById(): Map<String, YosMediaItem> {
        val songs = buildList {
            addAll(PlayListLibrary.playList.flatMap { it.songDataList })
            addAll(FavPlayListLibrary.favPlayList)
            addAll(MediaController.playingMusicList.value.orEmpty())
            addAll(MediaController.nextInQueueMusicList.value)
            addAll(MediaController.historyMusicList.value)
            addAll(
                ListeningHistory.history.value.map { entry ->
                    YosMediaItem(
                        mediaId = entry.videoId,
                        title = entry.title,
                        artists = entry.artists,
                        thumb = entry.thumbnailUrl?.let { Uri.parse(it) },
                    )
                }
            )
        }
        return songs.filter { it.mediaId != null }.associateBy { it.mediaId!! }
    }
}
