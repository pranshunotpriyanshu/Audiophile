package com.pryvn.audiophile.code.api

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PipedAudioStream(
    val url: String,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val quality: String? = null,
    val format: String? = null,
    val codec: String? = null,
    val videoOnly: Boolean = false,
)

@Serializable
data class PipedStreamsResponse(
    val audioStreams: List<PipedAudioStream> = emptyList(),
    val title: String? = null,
    val uploader: String? = null,
    val duration: Int? = null,
    val thumbnailUrl: String? = null,
    val uploadDate: String? = null,
    val views: Long? = null,
    val description: String? = null,
)

object PipedClient {
    private const val DEFAULT_INSTANCE = "pipedapi.kavin.rocks"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun streams(videoId: String, instance: String = DEFAULT_INSTANCE): Result<PipedStreamsResponse> {
        return runCatching {
            client.get("https://$instance/streams/$videoId").body<PipedStreamsResponse>()
        }
    }

    private val fallbackInstances = listOf(
        "pipedapi.kavin.rocks",
        "pipedapi.adminforge.de",
        "pipedapi.smnz.de",
        "pipedapi.lunar.icu",
    )

    suspend fun streamsWithFallback(videoId: String): Result<PipedStreamsResponse> {
        for (instance in fallbackInstances) {
            val result = streams(videoId, instance)
            if (result.isSuccess && result.getOrNull()?.audioStreams?.isNotEmpty() == true) {
                return result
            }
        }
        return streams(videoId, fallbackInstances.first())
    }
}
