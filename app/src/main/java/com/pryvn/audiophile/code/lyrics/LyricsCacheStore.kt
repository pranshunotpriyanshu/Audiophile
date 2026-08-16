package com.pryvn.audiophile.code.lyrics

import androidx.compose.runtime.mutableStateOf
import com.pryvn.audiophile.YosBasicApplication
import java.io.File

/**
 * Permanent on-disk lyrics cache ("cache forever").
 *
 * One small text file per song key (videoId, or "title" fallback) under
 * filesDir/lyrics_cache/. The in-memory MediaViewModelObject.lyricsCache stays
 * a bounded hot cache; this store is the authoritative long-term record and is
 * never evicted, so lyrics survive app restarts and re-fetch only when a song
 * is genuinely never seen before.
 */
object LyricsCacheStore {

    private const val DIR_NAME = "lyrics_cache"

    private val dir: File
        get() = File(YosBasicApplication.instance.filesDir, DIR_NAME).apply { mkdirs() }

    private fun fileFor(key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }
        val hash = key.hashCode().toUInt().toString(16)
        return File(dir, "${safe}_$hash.txt")
    }

    /** Reads the cached lyrics for [key], or null when absent/empty/corrupt. */
    fun get(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return try {
            val file = fileFor(key)
            if (file.isFile && file.length() > 0L) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    /** Writes [text] for [key] atomically (temp file + rename). */
    fun put(key: String?, text: String) {
        if (key.isNullOrBlank() || text.isBlank()) return
        try {
            val file = fileFor(key)
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(text)
            file.delete()
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (_: Exception) {
        }
        invalidateStats()
    }

    /** Number of cached songs (diagnostics only). */
    fun count(): Int = try {
        dir.listFiles()?.count { it.isFile && it.name.endsWith(".txt") } ?: 0
    } catch (_: Exception) {
        0
    }

    /** Removes every cached lyrics file. */
    fun clear() {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
        invalidateStats()
    }

    // Snapshot-backed count so the Settings page updates after clears/writes
    // without a manual refresh. -1 means "not computed yet".
    private val stats = mutableStateOf(-1L)

    /** Number of cached songs (Compose-observable). */
    val cachedCount: Int
        get() {
            val cached = stats.value
            if (cached < 0) {
                val fresh = count().toLong()
                stats.value = fresh
                return fresh.toInt()
            }
            return cached.toInt()
        }

    private fun invalidateStats() {
        stats.value = -1L
    }
}
