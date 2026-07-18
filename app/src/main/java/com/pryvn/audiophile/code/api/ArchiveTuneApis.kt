package com.pryvn.audiophile.code.api

import android.net.Uri
import com.google.gson.Gson
import com.pryvn.audiophile.code.lyrics.LyricsHelper
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.URLEncoder.encode
import kotlin.math.abs

data class AudiophileOnlineTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val source: String
)

data class AudiophileLyrics(
    val provider: String,
    val text: String,
    val isWordSynced: Boolean = false
)

data class AudiophileSyncedLine(
    val timestamp: Long,
    val text: String
)

data class AudiophileTranslation(
    val lyricsId: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val translatedLyrics: String
)

fun parseSyncedLyrics(lrcText: String): List<AudiophileSyncedLine> {
    val regex = Regex("""\[(\d{2}):(\d{2}(?:\.\d{2,3})?)\](.*)""")
    return lrcText.lines().mapNotNull { line ->
        regex.find(line)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val secs = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val text = match.groupValues[3].trim()
            if (text.isBlank()) return@mapNotNull null
            AudiophileSyncedLine(
                timestamp = (minutes * 60_000 + (secs * 1000).toLong()),
                text = text
            )
        }
    }
}

object ArchiveTuneApis {
    private val gson = Gson()

    suspend fun searchMusic(query: String): Result<List<AudiophileOnlineTrack>> =
        runCatching {
            val body = Http.postJson(
                url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
                payload = youtubeSearchPayload(query),
                headers = youtubeHeaders()
            )
            parseYouTubeSearch(body)
        }

    suspend fun fetchLyrics(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        videoId: String? = null
    ): AudiophileLyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = title?.takeIf { it.isNotBlank() } ?: return@withContext null
        val cleanArtist = artist?.takeIf { it.isNotBlank() } ?: ""
        LyricsHelper.getLyrics(
            title = cleanTitle,
            artist = cleanArtist,
            album = album,
            durationMs = durationMs,
            videoId = videoId,
        )
    }

    suspend fun fetchTranslation(
        lyrics: String,
        targetLang: String = "en",
        sourceLang: String? = null
    ): Result<String> = runCatching {
        val source = sourceLang ?: "auto"
        val encoded = URLEncoder.encode(lyrics, "UTF-8")
        val url = "https://lingva.ml/api/v1/$source/$targetLang/$encoded"
        val body = Http.get(url)
        val json = gson.fromJson(body, JsonObject::class.java)
        json.get("translation")?.asString ?: throw Exception("No translation returned")
    }

    private fun youtubeSearchPayload(query: String): String =
        """
        {
          "context": {
            "client": {
              "clientName": "WEB_REMIX",
              "clientVersion": "1.20240624.01.00",
              "hl": "en",
              "gl": "US"
            }
          },
          "query": ${gson.toJson(query)},
          "params": "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D"
        }
        """.trimIndent()

    private fun youtubeHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Origin" to "https://music.youtube.com",
        "Referer" to "https://music.youtube.com/search"
    )

    private fun parseYouTubeSearch(body: String): List<AudiophileOnlineTrack> {
        val root = gson.fromJson(body, JsonObject::class.java)
        val text = root.toString()
        val videoIdRegex = Regex("\"videoId\":\"([^\"]+)\"")
        val titleRegex = Regex("\"title\"\\s*:\\s*\\{\"runs\"\\s*:\\s*\\[\\{\"text\":\"([^\"]+)\"")
        val ids = videoIdRegex.findAll(text).map { it.groupValues[1] }.distinct().take(20).toList()
        val titles = titleRegex.findAll(text).map { it.groupValues[1] }.toList()
        return ids.mapIndexed { index, id ->
            AudiophileOnlineTrack(
                id = id,
                title = titles.getOrNull(index) ?: id,
                artist = null,
                album = null,
                durationSeconds = null,
                thumbnailUrl = "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
                source = "YouTube Music"
            )
        }
    }

    private suspend fun fetchLrcLib(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?
    ): AudiophileLyrics? {
        val url = Uri.parse("https://lrclib.net/api/search").buildUpon()
            .appendQueryParameter("track_name", title)
            .appendQueryParameter("artist_name", artist)
            .apply { album?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("album_name", it) } }
            .build()
            .toString()

        val type = object : TypeToken<List<LrcLibTrack>>() {}.type
        val tracks: List<LrcLibTrack> = gson.fromJson(Http.get(url), type)
        val track = tracks
            .filter { durationSeconds == null || it.duration == null || abs(it.duration - durationSeconds) <= 3 }
            .minByOrNull { abs((it.duration ?: durationSeconds ?: 0) - (durationSeconds ?: it.duration ?: 0)) }
            ?: tracks.firstOrNull()

        val lyrics = track?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: track?.plainLyrics?.takeIf { it.isNotBlank() }
        return lyrics?.let { AudiophileLyrics("LrcLib", it) }
    }

    private suspend fun fetchKuGou(title: String, artist: String, durationSeconds: Int?): AudiophileLyrics? {
        val keyword = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" - ")
        val searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${keyword.urlEncoded()}&duration=${durationSeconds ?: 0}&hash="
        val search = gson.fromJson(Http.get(searchUrl), JsonObject::class.java)
        val candidates = search.getAsJsonArray("candidates") ?: return null
        val candidate = candidates.firstOrNull()?.asJsonObject ?: return null
        val id = candidate.get("id")?.asString ?: return null
        val accessKey = candidate.get("accesskey")?.asString ?: return null
        val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=${accessKey.urlEncoded()}&fmt=lrc&charset=utf8"
        val download = gson.fromJson(Http.get(downloadUrl), JsonObject::class.java)
        val encoded = download.get("content")?.asString ?: return null
        val lyrics = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
        return AudiophileLyrics("KuGou", lyrics)
    }

    private suspend fun fetchBetterLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?,
        preferTTML: Boolean = false,
    ): AudiophileLyrics? {
        if (preferTTML) {
            val ttmlUrl = Uri.parse("https://lyrics-api.boidu.dev/ttml/getLyrics").buildUpon()
                .appendQueryParameter("title", title)
                .appendQueryParameter("artist", artist)
                .apply {
                    album?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("album", it) }
                    durationSeconds?.let { appendQueryParameter("duration", it.toString()) }
                }
                .build()
                .toString()
            val ttmlBody = runCatching { Http.get(ttmlUrl) }.getOrNull()
            if (ttmlBody != null) {
                val parsed = runCatching { gson.fromJson(ttmlBody, JsonObject::class.java) }.getOrNull()
                val ttml = parsed?.get("ttml")?.asString?.takeIf { it.trimStart().startsWith("<") }
                    ?: parsed?.get("lyrics")?.asString?.takeIf { it.trimStart().startsWith("<") }
                if (ttml != null) return AudiophileLyrics("BetterLyrics", ttml, isWordSynced = true)
                val lrc = parsed?.get("lyrics")?.asString?.takeIf { it.startsWith("[") }
                    ?: ttmlBody.takeIf { it.startsWith("[") }
                if (lrc != null) return AudiophileLyrics("BetterLyrics", lrc, isWordSynced = false)
            }
        }
        val url = Uri.parse("https://lyrics-api.boidu.dev/getLyrics").buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("artist", artist)
            .apply {
                album?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("album", it) }
                durationSeconds?.let { appendQueryParameter("duration", it.toString()) }
            }
            .build()
            .toString()
        val body = Http.get(url)
        val parsed = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
        val ttml = parsed?.get("ttml")?.asString?.takeIf { it.trimStart().startsWith("<") }
        val lyrics = ttml
            ?: parsed?.get("lyrics")?.asString
            ?: body.takeIf { it.startsWith("[") }
        if (lyrics != null) {
            val isWord = ttml != null
            return AudiophileLyrics("BetterLyrics", if (isWord) ttml else lyrics, isWordSynced = isWord)
        }
        return null
    }

    private suspend fun fetchYouLyPlus(videoId: String, durationSeconds: Int?): AudiophileLyrics? {
        val endpoints = listOf(
            "https://lyricsplus.binimum.org/v2/lyrics/get",
            "https://lyricsplus.prjktla.my.id/v2/lyrics/get",
            "https://lyricsplus.prjktla.workers.dev/v2/lyrics/get"
        )
        for (endpoint in endpoints) {
            val url = Uri.parse(endpoint).buildUpon()
                .appendQueryParameter("id", videoId)
                .apply { durationSeconds?.let { appendQueryParameter("duration", it.toString()) } }
                .build()
                .toString()
            val lyrics = runCatching { Http.get(url) }.getOrNull()
                ?.let { decodeLyricsPlus(it) }
                ?.takeIf { it.isNotBlank() }
            if (lyrics != null) return AudiophileLyrics("YouLyPlus", lyrics)
        }
        return null
    }

    private suspend fun fetchSimpMusic(videoId: String, durationSeconds: Int?): AudiophileLyrics? {
        val url = "https://api-lyrics.simpmusic.org/v1/$videoId"
        val body = runCatching { Http.get(url) }.getOrNull() ?: return null
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return null
        if (root.get("success")?.asBoolean != true) return null
        val data = root.getAsJsonArray("data") ?: return null
        val matched = if (durationSeconds != null) {
            data.firstOrNull { item ->
                val itemObj = item.asJsonObject
                val dur = itemObj.get("duration")?.asInt ?: return@firstOrNull false
                abs(dur - durationSeconds) <= 3
            }?.asJsonObject
        } else null
        val best = matched ?: data.firstOrNull()?.asJsonObject ?: return null
        val synced = best.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        val plain = best.get("plainLyrics")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        val lyrics = synced ?: plain ?: return null
        return AudiophileLyrics("SimpMusic", lyrics)
    }

    private suspend fun fetchUnison(
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): AudiophileLyrics? {
        val base = Uri.parse("https://unison.boidu.dev/lyrics").buildUpon()
            .appendQueryParameter("v", videoId)
            .appendQueryParameter("song", title)
            .appendQueryParameter("artist", artist)
        durationSeconds?.let { base.appendQueryParameter("duration", it.toString()) }
        val url = base.build().toString()
        val body = runCatching { Http.get(url) }.getOrNull() ?: return null
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return null
        if (root.get("success")?.asBoolean != true) return null
        val data = root.getAsJsonObject("data") ?: return null
        val lyrics = data.get("lyrics")?.asString?.takeIf { it.isNotBlank() } ?: return null
        return AudiophileLyrics("Unison", lyrics)
    }

    private suspend fun fetchPaxsenix(
        title: String,
        artist: String,
        durationSeconds: Int?
    ): AudiophileLyrics? {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" - ")
        val baseUrl = "https://lyrics.paxsenix.org"

        val strategies = listOf<suspend () -> AudiophileLyrics?>(
            { paxsenixAppleMusicSearch(baseUrl, query) },
            { paxsenixNetEase(baseUrl, query) },
            { paxsenixSpotify(baseUrl, query) },
            { paxsenixMusixmatch(baseUrl, title, artist, durationSeconds) },
            { paxsenixYouTube(baseUrl, query) },
        )

        return strategies.firstNotNullOfOrNull { strategy ->
            runCatching { strategy() }.getOrNull()?.takeIf { it.text.isNotBlank() }
        }
    }

    private suspend fun paxsenixMusixmatch(
        baseUrl: String,
        title: String,
        artist: String,
        durationSeconds: Int?
    ): AudiophileLyrics? {
        val url = Uri.parse("$baseUrl/musixmatch/lyrics").buildUpon()
            .appendQueryParameter("q", "$title $artist")
            .appendQueryParameter("t", title)
            .appendQueryParameter("a", artist)
            .apply { durationSeconds?.let { appendQueryParameter("d", it.toString()) } }
            .build()
            .toString()
        val body = runCatching { Http.get(url) }.getOrNull() ?: return null
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return null
        val lyrics = root.get("lyrics")?.asString?.takeIf { it.isNotBlank() }
        return lyrics?.let { AudiophileLyrics("Paxsenix", it) }
    }

    private suspend fun paxsenixAppleMusicSearch(baseUrl: String, query: String): AudiophileLyrics? {
        val searchUrl = "$baseUrl/apple-music/search?q=${query.urlEncoded()}"
        val searchBody = runCatching { Http.get(searchUrl) }.getOrNull() ?: return null
        val searchJson = runCatching { gson.fromJson(searchBody, JsonObject::class.java) }.getOrNull() ?: return null
        val results = searchJson.getAsJsonArray("results") ?: searchJson.getAsJsonArray("data") ?: return null
        val firstId = results.firstOrNull()?.asJsonObject?.get("id")?.asString ?: return null
        val lyricsUrl = "$baseUrl/apple-music/lyrics?id=$firstId&ttml=true"
        val lyricsBody = runCatching { Http.get(lyricsUrl) }.getOrNull() ?: return null
        val lyricsJson = runCatching { gson.fromJson(lyricsBody, JsonObject::class.java) }.getOrNull() ?: return null
        val ttml = lyricsJson.get("lyrics")?.asString
            ?: lyricsJson.get("ttml")?.asString
            ?: return null
        val isWord = ttml.trimStart().startsWith("<")
        return AudiophileLyrics("Paxsenix", if (isWord) ttml else ttmlToLrc(ttml), isWordSynced = isWord)
    }

    private suspend fun paxsenixNetEase(baseUrl: String, query: String): AudiophileLyrics? {
        val searchUrl = "$baseUrl/netease/search?q=${query.urlEncoded()}"
        val searchBody = runCatching { Http.get(searchUrl) }.getOrNull() ?: return null
        val searchJson = runCatching { gson.fromJson(searchBody, JsonObject::class.java) }.getOrNull() ?: return null
        val results = searchJson.get("result")?.asJsonObject?.get("songs")?.asJsonArray
            ?: searchJson.get("songs")?.asJsonArray
            ?: return null
        val firstId = results.firstOrNull()?.asJsonObject?.get("id")?.asLong ?: return null
        val lyricsUrl = "$baseUrl/netease/lyrics?id=$firstId&word=true"
        val lyricsBody = runCatching { Http.get(lyricsUrl) }.getOrNull() ?: return null
        val lyricsJson = runCatching { gson.fromJson(lyricsBody, JsonObject::class.java) }.getOrNull() ?: return null
        val lrc = lyricsJson.getAsJsonObject("lrc")?.get("lyric")?.asString?.takeIf { it.isNotBlank() }
            ?: lyricsJson.getAsJsonObject("tlyric")?.get("lyric")?.asString?.takeIf { it.isNotBlank() }
            ?: return null
        return AudiophileLyrics("Paxsenix", lrc)
    }

    private suspend fun paxsenixSpotify(baseUrl: String, query: String): AudiophileLyrics? {
        val searchUrl = "$baseUrl/spotify/search?q=${query.urlEncoded()}"
        val searchBody = runCatching { Http.get(searchUrl) }.getOrNull() ?: return null
        val searchJson = runCatching { gson.fromJson(searchBody, JsonObject::class.java) }.getOrNull() ?: return null
        val tracks = searchJson.getAsJsonObject("tracks")?.getAsJsonArray("items")
            ?: searchJson.getAsJsonArray("items")
            ?: searchJson.getAsJsonArray("tracks")
            ?: return null
        val first = tracks.firstOrNull()?.asJsonObject ?: return null
        val realId = first.get("id")?.asString
            ?: first.getAsJsonObject("external_ids")?.get("isrc")?.asString
            ?: return null
        val lyricsUrl = "$baseUrl/spotify/lyrics?id=$realId"
        val lyricsBody = runCatching { Http.get(lyricsUrl) }.getOrNull() ?: return null
        val lyricsJson = runCatching { gson.fromJson(lyricsBody, JsonObject::class.java) }.getOrNull() ?: return null
        val lyrics = lyricsJson.get("lyrics")?.asString?.takeIf { it.isNotBlank() }
        return lyrics?.let { AudiophileLyrics("Paxsenix", it) }
    }

    private suspend fun paxsenixYouTube(baseUrl: String, query: String): AudiophileLyrics? {
        val searchUrl = "$baseUrl/youtube/search?q=${query.urlEncoded()}"
        val searchBody = runCatching { Http.get(searchUrl) }.getOrNull() ?: return null
        val searchJson = runCatching { gson.fromJson(searchBody, JsonObject::class.java) }.getOrNull() ?: return null
        val results = searchJson.getAsJsonArray("results") ?: searchJson.getAsJsonArray("items") ?: return null
        val first = results.firstOrNull()?.asJsonObject ?: return null
        val realId = first.get("id")?.asString
            ?: first.getAsJsonObject("id")?.get("videoId")?.asString
            ?: return null
        val lyricsUrl = "$baseUrl/youtube/lyrics?id=$realId"
        val lyricsBody = runCatching { Http.get(lyricsUrl) }.getOrNull() ?: return null
        val lyricsJson = runCatching { gson.fromJson(lyricsBody, JsonObject::class.java) }.getOrNull() ?: return null
        val lyrics = lyricsJson.get("lyrics")?.asString?.takeIf { it.isNotBlank() }
        return lyrics?.let { AudiophileLyrics("Paxsenix", it) }
    }

    private fun decodeLyricsPlus(body: String): String? {
        val parsed = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
        return parsed?.get("lyrics")?.asString
            ?: parsed?.getAsJsonObject("data")?.get("lyrics")?.asString
            ?: body.takeIf { it.startsWith("[") }
    }

    private fun ttmlToLrc(text: String): String {
        if (!text.trimStart().startsWith("<")) return text
        val lineRegex = Regex("""<p[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        return lineRegex.findAll(text).joinToString("\n") { match ->
            val timestamp = ttmlTimeToLrc(match.groupValues[1])
            val line = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            "[$timestamp]$line"
        }
    }

    private fun ttmlTimeToLrc(value: String): String {
        val parts = value.removeSuffix("s").split(":")
        val seconds = when (parts.size) {
            3 -> parts[0].toFloat() * 3600 + parts[1].toFloat() * 60 + parts[2].toFloat()
            2 -> parts[0].toFloat() * 60 + parts[1].toFloat()
            else -> parts.firstOrNull()?.toFloatOrNull() ?: 0f
        }
        val minutes = (seconds / 60).toInt()
        val remaining = seconds - minutes * 60
        return "%02d:%05.2f".format(minutes, remaining)
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    private data class LrcLibTrack(
        val duration: Int? = null,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null
    )
}

private object Http {
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        request(url, "GET")
    }

    suspend fun postJson(url: String, payload: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            request(url, "POST", payload, headers)
        }

    private fun request(
        url: String,
        method: String,
        payload: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Audiophile/1.0")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (payload != null) {
                doOutput = true
                outputStream.use { it.write(payload.toByteArray()) }
            }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }
}