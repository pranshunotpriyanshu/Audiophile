package com.pryvn.audiophile.code.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BetterLyricsProvider : LyricsProvider {
    override val name: String = "BetterLyrics"

    @Serializable
    private data class TTMLResponse(val ttml: String = "", val lyrics: String = "")

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(this@BetterLyricsProvider.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 20000
            }
            defaultRequest { url("https://lyrics-api.boidu.dev/") }
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

        val endpoints = listOf("getLyrics", "kugou/getLyrics")
        for (endpoint in endpoints) {
            try {
                val response = client.get(endpoint) {
                    parameter("s", cleanTitle)
                    parameter("a", cleanArtist)
                    if (cleanAlbum.isNotBlank()) parameter("al", cleanAlbum)
                    if (durationSeconds > 0) parameter("d", durationSeconds)
                }
                if (!response.status.isSuccess()) continue
                val body = response.bodyAsText().trim()
                if (body.isEmpty()) continue

                val ttml = if (body.startsWith("<")) body else {
                    try {
                        val decoded = json.decodeFromString<TTMLResponse>(body)
                        decoded.ttml.ifBlank { decoded.lyrics }.trim()
                    } catch (_: Exception) { continue }
                }

                if (ttml.isNotBlank() && ttml.startsWith("<")) {
                    return AudiophileLyrics(provider = name, text = ttml, isWordSynced = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}