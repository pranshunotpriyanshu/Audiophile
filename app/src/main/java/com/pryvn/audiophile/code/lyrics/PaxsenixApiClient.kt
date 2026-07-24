package com.pryvn.audiophile.code.lyrics

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.pryvn.audiophile.code.lyrics.PaxsenixAppleMusicLyricsResponse
import com.pryvn.audiophile.code.lyrics.PaxsenixDeezerResponse
import com.pryvn.audiophile.code.lyrics.PaxsenixLyricsResponse
import com.pryvn.audiophile.code.lyrics.PaxsenixMusixmatchResponse
import com.pryvn.audiophile.code.lyrics.PaxsenixNetEaseLyricsResponse
import com.pryvn.audiophile.code.lyrics.PaxsenixSearchResult
import com.pryvn.audiophile.code.lyrics.PaxsenixSearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object PaxsenixApiClient {
    private const val BASE_URL = "https://lyrics.paxsenix.org/"
    private const val USER_AGENT = "Audiophile/1.0"
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 15000
    private val gson = Gson()

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        videoId: String?,
    ): PaxsenixLyricsResponse? = withContext(Dispatchers.IO) {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" - ")
        val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else -1

        System.err.println("PaxsenixApiClient: Starting fetch for [$title] by [$artist] (durationSec=$durationSec)")

        val providers = listOf(
            Provider("Apple Music", "apple-music/lyrics", true, { searchAppleMusic(query) }),
            Provider("Deezer", "deezer/lyrics", true, { searchDeezer(query) }),
            Provider("Musixmatch", "musixmatch/lyrics", true, { searchMusixmatch(query, title, artist, durationSec) }),
            Provider("Spotify", "spotify/lyrics", true, { searchSpotify(query) }),
            Provider("YouTube", "youtube/lyrics", true, { searchYouTube(query) }),
            Provider("NetEase", "netease/lyrics", true, { searchNetEase(query) }),
        )

        for (provider in providers) {
            System.err.println("PaxsenixApiClient: Trying provider ${provider.name}")
            val trackId = provider.search()
            if (trackId == null) {
                System.err.println("PaxsenixApiClient: Provider ${provider.name} returned null trackId")
                continue
            }
            System.err.println("PaxsenixApiClient: Provider ${provider.name} found trackId=$trackId")

            val lyricsResponse = fetchLyricsFromEndpoint(provider.endpoint, trackId, title, artist, durationSec)
            if (lyricsResponse != null && lyricsResponse.lyrics.isNotBlank()) {
                System.err.println("PaxsenixApiClient: SUCCESS from ${provider.name}")
                return@withContext lyricsResponse.copy(source = provider.name)
            } else {
                System.err.println("PaxsenixApiClient: Provider ${provider.name} returned empty/null lyrics")
            }
        }
        System.err.println("PaxsenixApiClient: All providers failed")
        null
    }

    private data class Provider(
        val name: String,
        val endpoint: String,
        val supportsTtml: Boolean,
        val search: () -> String?,
    )

    private fun fetchLyricsFromEndpoint(
        endpoint: String,
        trackId: String,
        title: String,
        artist: String,
        durationSec: Int,
    ): PaxsenixLyricsResponse? {
        val baseUrl = "$BASE_URL$endpoint?id=${trackId.urlEncoded()}"
        val ttmlUrl = "$baseUrl&ttml=true"

        System.err.println("PaxsenixApiClient: Fetching lyrics from $ttmlUrl")

        val response = try {
            httpGet(ttmlUrl)
        } catch (e: Exception) {
            System.err.println("PaxsenixApiClient: Exception fetching lyrics: ${e.message}")
            return null
        }

        System.err.println("PaxsenixApiClient: Response received, length=${response?.length ?: 0}")

        if (response.isNullOrBlank()) {
            System.err.println("PaxsenixApiClient: Empty response")
            return null
        }

        val (lyrics, isWordSynced) = parseLyricsResponse(endpoint, response)
        if (lyrics.isNullOrBlank()) {
            System.err.println("PaxsenixApiClient: Parsed lyrics is empty")
            return null
        }

        System.err.println("PaxsenixApiClient: Parsed lyrics successfully, isWordSynced=$isWordSynced, lyrics length=${lyrics.length}")

        return PaxsenixLyricsResponse(
            lyrics = lyrics,
            source = endpoint,
            isWordSynced = isWordSynced,
            trackId = trackId,
        )
    }

    private fun parseLyricsResponse(endpoint: String, response: String): Pair<String?, Boolean> {
        return when {
            endpoint.contains("apple-music") -> {
                val data = runCatching { gson.fromJson(response, PaxsenixAppleMusicLyricsResponse::class.java) }.getOrNull()
                val ttml = data?.ttml ?: data?.lyrics ?: data?.content
                val isTtml = ttml?.trimStart()?.startsWith("<") == true
                ttml?.let { it to isTtml } ?: data?.content?.let { it to false } ?: null to false
            }
            endpoint.contains("deezer") -> {
                val data = runCatching { gson.fromJson(response, PaxsenixDeezerResponse::class.java) }.getOrNull()
                data?.lyrics?.let { it to false } ?: data?.content?.let { it to false } ?: null to false
            }
            endpoint.contains("musixmatch") -> {
                val data = runCatching { gson.fromJson(response, PaxsenixMusixmatchResponse::class.java) }.getOrNull()
                data?.lyrics?.let { it to true } ?: data?.content?.let { it to false } ?: null to false
            }
            endpoint.contains("netease") -> {
                val data = runCatching { gson.fromJson(response, PaxsenixNetEaseLyricsResponse::class.java) }.getOrNull()
                data?.klyric?.lyric?.let { it to true } ?: data?.lrc?.lyric?.let { it to false } ?: data?.tlyric?.lyric?.let { it to false } ?: null to false
            }
            else -> {
                val data = runCatching { gson.fromJson(response, JsonObject::class.java) }.getOrNull()
                data?.getString("lyrics")?.let { it to false } ?: data?.getString("content")?.let { it to false } ?: response to false
            }
        }
    }

    private fun searchAppleMusic(query: String): String? {
        val url = "$BASE_URL/apple-music/search?q=${query.urlEncoded()}"
        val body = httpGet(url) ?: return null
        val json = runCatching { gson.fromJson(body, PaxsenixSearchResponse::class.java) }.getOrNull() ?: return null
        val results = json.results ?: json.data ?: return null
        return results.firstOrNull()?.id
    }

    private fun searchDeezer(query: String): String? {
        val url = "$BASE_URL/deezer/search?q=${query.urlEncoded()}"
        val body = httpGet(url) ?: return null
        val json = runCatching { gson.fromJson(body, PaxsenixSearchResponse::class.java) }.getOrNull() ?: return null
        val results = json.results ?: json.data ?: json.items ?: return null
        return results.firstOrNull()?.id
    }

    private fun searchMusixmatch(query: String, title: String, artist: String, durationSec: Int): String? {
        val url = "$BASE_URL/musixmatch/lyrics?q=${query.urlEncoded()}&t=${title.urlEncoded()}&a=${artist.urlEncoded()}&d=$durationSec&type=word"
        val body = httpGet(url) ?: return null
        val json = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return null
        return if (json.getString("lyrics")?.isNotBlank() == true) "word" else null
    }

    private fun searchSpotify(query: String): String? {
        val url = "$BASE_URL/spotify/search?q=${query.urlEncoded()}"
        val body = httpGet(url) ?: return null
        val json = runCatching { gson.fromJson(body, PaxsenixSearchResponse::class.java) }.getOrNull() ?: return null
        val tracks = json.tracks?.items ?: json.items ?: json.tracks?.items ?: return null
        val first = tracks.firstOrNull() ?: return null
        return first.id ?: first.realId
    }

    private fun searchYouTube(query: String): String? {
        val url = "$BASE_URL/youtube/search?q=${query.urlEncoded()}"
        val body = httpGet(url) ?: return null
        val json = runCatching { gson.fromJson(body, PaxsenixSearchResponse::class.java) }.getOrNull() ?: return null
        val results = json.results ?: json.items ?: return null
        val first = results.firstOrNull() ?: return null
        return first.id ?: first.realId
    }

    private fun searchNetEase(query: String): String? {
        val url = "$BASE_URL/netease/search?q=${query.urlEncoded()}"
        val body = httpGet(url) ?: return null
        val json = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return null
        val songs = json.getJsonObject("result")?.getJsonArray("songs") ?: json.getJsonArray("songs") ?: return null
        return if (songs.size() > 0) songs[0].asJsonObject?.getLong("id")?.toString() else null
    }

    private fun httpGet(url: String): String? {
        System.err.println("PaxsenixApiClient: HTTP GET $url")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            val responseCode = connection.responseCode
            System.err.println("PaxsenixApiClient: HTTP response code: $responseCode")
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            System.err.println("PaxsenixApiClient: HTTP response body length=${body?.length ?: 0}")
            return body
        } catch (e: Exception) {
            System.err.println("PaxsenixApiClient: HTTP exception: ${e.message}")
            return null
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    // Gson JsonObject/JsonElement extensions
    private fun JsonObject.getString(key: String): String? =
        get(key)?.let { if (it.isJsonPrimitive) it.asString else it.toString() }?.takeIf { it.isNotBlank() }

    private fun JsonObject.getLong(key: String): Long? =
        get(key)?.let { if (it.isJsonPrimitive) it.asLong else null }

    private fun JsonObject.getJsonObject(key: String): JsonObject? =
        get(key)?.let { if (it.isJsonObject) it as JsonObject else null }

    private fun JsonObject.getJsonArray(key: String): JsonArray? =
        get(key)?.let { if (it.isJsonArray) it as JsonArray else null }

    private val JsonElement.asString: String
        get() = (this as com.google.gson.JsonPrimitive).asString

    private val JsonElement.asLong: Long
        get() = (this as com.google.gson.JsonPrimitive).asLong

    private val JsonElement.asJsonObject: JsonObject
        get() = this as JsonObject

    private val JsonElement.asJsonArray: JsonArray
        get() = this as JsonArray
}