package com.pryvn.audiophile.code.api

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class SBSegment(
    val segment: List<Double>,
    val UUID: String? = null,
    val category: String? = null,
    val actionType: String? = null,
    val videoDuration: Double? = null,
)

@Serializable
data class SBSkipSegmentResult(
    val videoID: String? = null,
    val hash: String? = null,
    val segments: List<SBSegment> = emptyList(),
)

object SponsorBlockClient {
    private const val API_BASE = "https://sponsor.ajay.app"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun fetchSegments(
        videoId: String,
        categories: List<String> = listOf("sponsor", "intro", "outro", "selfpromo", "music_offtopic", "preview", "filler", "interaction"),
    ): Result<List<SBSegment>> {
        return runCatching {
            val hash = sha256Hex(videoId)
            val prefix = hash.substring(0, 4)
            val catParam = categories.joinToString(",", "[", "]") { "\"$it\"" }
            val httpResponse = client.get("$API_BASE/api/skipSegments/$prefix") {
                parameter("categories", catParam)
            }
            if (httpResponse.status == HttpStatusCode.NotFound) return@runCatching emptyList<SBSegment>()
            val response: List<SBSkipSegmentResult> = httpResponse.body()
            response.firstOrNull { it.videoID == videoId }?.segments ?: emptyList()
        }
    }
}
