package com.pryvn.audiophile.code.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

class YouLyPlusProvider : LyricsProvider {
    override val name: String = "YouLyPlus"

    private val TTML_PATH = "v1/ttml/get"
    private val LYRICS_PATH = "v2/lyrics/get"

    private val baseUrls = listOf(
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.prjktla.workers.dev/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus-seven.vercel.app/",
    )

    @Serializable
    private data class YouLyPlusTtmlResponse(val ttml: String? = null)

    @Serializable
    private data class YouLyPlusLyricsResponse(
        val type: String? = null,
        val lyrics: List<YouLyPlusLine> = emptyList(),
    )

    @Serializable
    private data class YouLyPlusLine(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        val syllabus: List<YouLyPlusSyllable>? = null,
    )

    @Serializable
    private data class YouLyPlusSyllable(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        val isBackground: Boolean = false,
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 20000
            }
            defaultRequest {
                headers.append("Accept", "application/json")
                headers.append("User-Agent", "ArchiveTune")
            }
            expectSuccess = false
        }
    }

    override suspend fun fetch(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        videoId: String?,
    ): AudiophileLyrics? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanAlbum = album?.trim().orEmpty()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        val durationSeconds = if (durationMs != null && durationMs > 0) (durationMs / 1000).toInt() else -1

        return try {
            // Try TTML first
            val ttml = fetchTtml(cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
            if (ttml != null) {
                return AudiophileLyrics(provider = name, text = ttml, isWordSynced = true)
            }

            // Fallback to LRC lyrics
            val lrc = fetchLrc(cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
            if (lrc != null) {
                return AudiophileLyrics(provider = name, text = lrc, isWordSynced = lrc.contains("["))
            }

            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchTtml(title: String, artist: String, album: String, durationSeconds: Int): String? {
        for (baseUrl in baseUrls) {
            currentCoroutineContext().ensureActive()
            try {
                val response = client.get("$baseUrl$TTML_PATH") {
                    parameter("title", title)
                    parameter("artist", artist)
                    if (album.isNotBlank()) parameter("album", album)
                    if (durationSeconds > 0) parameter("duration", durationSeconds)
                }
                if (!response.status.isSuccess()) continue
                val body = response.bodyAsText().trim()
                val ttml = if (body.startsWith("<")) body else {
                    runCatching { json.decodeFromString<YouLyPlusTtmlResponse>(body).ttml?.trim() }.getOrNull()
                }
                if (!ttml.isNullOrBlank() && ttml.startsWith("<")) return ttml
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private suspend fun fetchLrc(title: String, artist: String, album: String, durationSeconds: Int): String? {
        for (baseUrl in baseUrls) {
            currentCoroutineContext().ensureActive()
            try {
                val response = client.get("$baseUrl$LYRICS_PATH") {
                    parameter("title", title)
                    parameter("artist", artist)
                    if (album.isNotBlank()) parameter("album", album)
                    if (durationSeconds > 0) parameter("duration", durationSeconds)
                }
                if (!response.status.isSuccess()) continue
                val body = response.bodyAsText()
                val parsed = runCatching { json.decodeFromString<YouLyPlusLyricsResponse>(body) }.getOrNull()
                val lyricsText = parsed?.toLyricsText()
                if (!lyricsText.isNullOrBlank()) return lyricsText
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    // Returns plain text lines (without timestamps)
    private fun YouLyPlusLyricsResponse.toLyricsText(): String? {
        if (lyrics.isEmpty()) return null
        return lyrics.mapNotNull { it.text?.trim() }.filter { it.isNotBlank() }
            .joinToString("\n").takeIf { it.isNotBlank() }
    }

    // Returns both text and syllable data (for future use)
    private fun YouLyPlusLyricsResponse.toLyricsWithSyllables(): Pair<String, List<List<WordTiming>>>? {
        if (lyrics.isEmpty()) return null

        val allSyllables = mutableListOf<List<WordTiming>>()
        val lines = lyrics.mapNotNull { line ->
            val syllables = line.syllabus?.mapNotNull { syllable ->
                val text = syllable.text?.trim()
                if (!text.isNullOrBlank() && syllable.time != null) {
                    // Convert Long to Float explicitly
                    val startMs = syllable.time.toFloat()
                    val endMs = startMs + (syllable.duration?.toFloat() ?: 0f)
                    WordTiming(
                        startMs = startMs,
                        endMs = endMs,
                        text = text,
                        isBackground = syllable.isBackground
                    )
                } else null
            } ?: emptyList()

            if (syllables.isNotEmpty()) {
                allSyllables.add(syllables)
            }
            line.text?.trim()
        }.filter { !it.isNullOrBlank() }

        return if (lines.isNotEmpty() && allSyllables.isNotEmpty()) {
            Pair(lines.joinToString("\n"), allSyllables)
        } else null
    }

    private fun formatLrcTimestamp(timeMs: Long, bracketed: Boolean): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val minutes = safeTime / 60000L
        val seconds = (safeTime % 60000L) / 1000L
        val millis = safeTime % 1000L
        val timestamp = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        return if (bracketed) "[$timestamp]" else "<$timestamp>"
    }
}