package com.pryvn.audiophile.code.api

import kotlinx.serialization.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.pryvn.audiophile.code.api.innertube.YouTube
import com.pryvn.audiophile.code.api.innertube.SearchFilter
import com.pryvn.audiophile.code.api.innertube.models.*
import com.pryvn.audiophile.code.api.innertube.pages.ArtistPageData
import com.pryvn.audiophile.code.api.innertube.pages.ArtistHeader

data class YTSongItem(
    val videoId: String,
    val title: String,
    val artists: List<YTArtist> = emptyList(),
    val album: YTAlbum? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val playlistId: String? = null,
)

data class YTArtist(
    val name: String,
    val id: String? = null,
)

data class YTAlbum(
    val name: String?,
    val id: String? = null,
    val thumbnailUrl: String? = null,
)

data class YTAlbumSearchItem(
    val browseId: String,
    val title: String,
    val artist: String? = null,
    val thumbnailUrl: String? = null,
)

data class YTArtistSearchItem(
    val browseId: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

data class YTSearchSection(
    val title: String,
    val songs: List<YTSongItem> = emptyList(),
    val albums: List<YTAlbumSearchItem> = emptyList(),
    val artists: List<YTArtistSearchItem> = emptyList(),
    val playlists: List<YTPlaylist> = emptyList(),
)

data class YTSearchResult(
    val items: List<YTSongItem>,
    val sections: List<YTSearchSection> = emptyList(),
    val continuation: String? = null,
)

data class YTPlaylistPage(
    val playlist: YTPlaylist,
    val songs: List<YTSongItem>,
    val continuation: String? = null,
)

data class YTPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val songCount: Int? = null,
    val author: String? = null,
)

data class YTPlayerResponse(
    val videoId: String,
    val title: String?,
    val artist: String?,
    val thumbnailUrl: String?,
    val lengthSeconds: Int?,
    val streamUrl: String?,
    val expiresInSeconds: Int?,
)

data class HomeItem(
    val videoId: String?,
    val title: String,
    val artists: List<YTArtist> = emptyList(),
    val album: YTAlbum? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val playlistId: String? = null,
    val browseId: String? = null,
)

data class HomeSection(
    val title: String,
    val items: List<HomeItem>,
)

data class YTAccountInfo(
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val avatarUrl: String? = null,
)

private fun SongItem.toYTSongItem() = YTSongItem(
    videoId = id,
    title = title,
    artists = artists.map { YTArtist(name = it.name, id = it.id) },
    album = album?.let { YTAlbum(name = it.name, id = it.id) },
    durationSeconds = duration,
    thumbnailUrl = thumbnail,
)

internal fun HomeItem.toYTSongItem(): YTSongItem = YTSongItem(
    videoId = videoId ?: "",
    title = title,
    artists = artists,
    album = album,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    playlistId = playlistId,
)

object YouTubeApi {

    suspend fun fetchAccountInfo(): Result<YTAccountInfo> = runCatching {
        val info = YouTube.fetchAccountInfo().getOrThrow()
        YTAccountInfo(
            name = info.name,
            email = info.email,
            channelHandle = info.channelHandle,
            avatarUrl = info.thumbnailUrl,
        )
    }

    suspend fun search(query: String, filter: String? = null): Result<YTSearchResult> = runCatching {
        val innertubeFilter = filter?.let {
            when {
                it.contains("song", ignoreCase = true) -> SearchFilter.FILTER_SONG
                it.contains("album", ignoreCase = true) -> SearchFilter.FILTER_ALBUM
                it.contains("artist", ignoreCase = true) -> SearchFilter.FILTER_ARTIST
                it.contains("playlist", ignoreCase = true) -> SearchFilter.FILTER_COMMUNITY_PLAYLIST
                else -> null
            }
        }

        if (innertubeFilter != null) {
            val result = YouTube.search(query, innertubeFilter).getOrThrow()
            val songs = result.items.filterIsInstance<SongItem>().map { it.toYTSongItem() }
            val albums = result.items.filterIsInstance<AlbumItem>().map {
                YTAlbumSearchItem(browseId = it.browseId, title = it.title, artist = it.artists?.firstOrNull()?.name, thumbnailUrl = it.thumbnail)
            }
            val artists = result.items.filterIsInstance<ArtistItem>().map {
                YTArtistSearchItem(browseId = it.id, name = it.title, thumbnailUrl = it.thumbnail)
            }
            val playlists = result.items.filterIsInstance<PlaylistItem>().map {
                YTPlaylist(id = it.id, title = it.title, thumbnailUrl = it.thumbnail, author = it.author?.name, songCount = it.songCountText?.filter { c -> c.isDigit() }?.toIntOrNull())
            }
            YTSearchResult(
                items = songs.distinctBy { it.videoId },
                sections = listOf(
                    YTSearchSection("Songs", songs = songs),
                    YTSearchSection("Albums", albums = albums),
                    YTSearchSection("Artists", artists = artists),
                    YTSearchSection("Community Playlists", playlists = playlists),
                ).filter { it.songs.isNotEmpty() || it.albums.isNotEmpty() || it.artists.isNotEmpty() || it.playlists.isNotEmpty() },
                continuation = result.continuation,
            )
        } else {
            // No filter: search all content types in parallel
            coroutineScope {
                val songResult = async { YouTube.search(query, SearchFilter.FILTER_SONG).getOrNull() }
                val albumResult = async { YouTube.search(query, SearchFilter.FILTER_ALBUM).getOrNull() }
                val artistResult = async { YouTube.search(query, SearchFilter.FILTER_ARTIST).getOrNull() }
                val playlistResult = async { YouTube.search(query, SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull() }

            val songs = songResult.await()?.items
                ?.filterIsInstance<SongItem>()?.map { it.toYTSongItem() }
                ?.distinctBy { it.videoId } ?: emptyList()
            val albums = albumResult.await()?.items
                ?.filterIsInstance<AlbumItem>()?.map {
                    YTAlbumSearchItem(browseId = it.browseId, title = it.title, artist = it.artists?.firstOrNull()?.name, thumbnailUrl = it.thumbnail)
                } ?: emptyList()
            val artists = artistResult.await()?.items
                ?.filterIsInstance<ArtistItem>()?.map {
                    YTArtistSearchItem(browseId = it.id, name = it.title, thumbnailUrl = it.thumbnail)
                } ?: emptyList()
            val playlists = playlistResult.await()?.items
                ?.filterIsInstance<PlaylistItem>()?.map {
                    YTPlaylist(id = it.id, title = it.title, thumbnailUrl = it.thumbnail, author = it.author?.name, songCount = it.songCountText?.filter { c -> c.isDigit() }?.toIntOrNull())
                } ?: emptyList()

            YTSearchResult(
                items = songs,
                sections = listOf(
                    YTSearchSection("Songs", songs = songs),
                    YTSearchSection("Albums", albums = albums),
                    YTSearchSection("Artists", artists = artists),
                    YTSearchSection("Community Playlists", playlists = playlists),
                ).filter { it.songs.isNotEmpty() || it.albums.isNotEmpty() || it.artists.isNotEmpty() || it.playlists.isNotEmpty() },
            )
            }
        }
    }

    suspend fun searchContinuation(continuation: String): Result<YTSearchResult> = runCatching {
        val result = YouTube.searchContinuation(continuation).getOrThrow()
        val songs = result.items.filterIsInstance<SongItem>().map { it.toYTSongItem() }
        YTSearchResult(
            items = songs.distinctBy { it.videoId },
            continuation = result.continuation,
        )
    }

    suspend fun artist(browseId: String): Result<JsonObject> =
        YouTube.browseJson(browseId = browseId)

    suspend fun artistPage(browseId: String): Result<ArtistPageData> = runCatching {
        val page = YouTube.artist(browseId).getOrThrow()
        val header = ArtistHeader.fromArtistItem(page.artist)

        // Parse sections
        val topSongs = page.sections
            .firstOrNull { it.title.contains("Top songs", ignoreCase = true) || it.title.contains("Popular", ignoreCase = true) }
            ?.items
            ?.filterIsInstance<SongItem>() ?: emptyList()

        val latestRelease = page.sections
            .firstOrNull { it.title.contains("Latest release", ignoreCase = true) || it.title.contains("New release", ignoreCase = true) }
            ?.items
            ?.firstOrNull()
            ?.let { (it as? AlbumItem) }

        val essentialAlbums = page.sections
            .firstOrNull { it.title.contains("Essential", ignoreCase = true) || it.title.contains("Albums", ignoreCase = true) }
            ?.items
            ?.filterIsInstance<AlbumItem>() ?: emptyList()

        val singlesEPs = page.sections
            .firstOrNull { it.title.contains("Single", ignoreCase = true) || it.title.contains("EP", ignoreCase = true) }
            ?.items
            ?.filterIsInstance<AlbumItem>() ?: emptyList()

        val relatedArtists = page.sections
            .firstOrNull { it.title.contains("Similar", ignoreCase = true) || it.title.contains("Related", ignoreCase = true) }
            ?.items
            ?.filterIsInstance<ArtistItem>() ?: emptyList()

        ArtistPageData(
            artist = page.artist,
            header = header,
            topSongs = topSongs,
            essentialAlbums = essentialAlbums,
            singlesEPs = singlesEPs,
            description = page.description,
            relatedArtists = relatedArtists,
            latestRelease = latestRelease,
        )
    }

    /**
     * The COMPLETE artist song catalogue — not just the top-songs shelf preview.
     *
     * The artist browse page only carries an initial batch of songs; the Songs
     * shelf's browse endpoint ([ArtistSection.moreEndpoint]) plus its
     * continuation pages contain the full catalogue. This walks the whole
     * paginated list and returns every song once, preserving catalogue order.
     */
    suspend fun allArtistSongs(browseId: String): Result<List<SongItem>> = runCatching {
        val page = YouTube.artist(browseId).getOrThrow()

        // Prefer a songs section that carries a paginated "see all" endpoint;
        // fall back to any songs section (whose shelf items remain usable).
        val songsSection =
            page.sections.firstOrNull { section ->
                section.moreEndpoint != null &&
                    (section.title.contains("Top songs", ignoreCase = true) ||
                        section.title.contains("Popular", ignoreCase = true) ||
                        section.title.contains("Songs", ignoreCase = true))
            } ?: page.sections.firstOrNull { section ->
                section.title.contains("Top songs", ignoreCase = true) ||
                    section.title.contains("Popular", ignoreCase = true) ||
                    section.title.contains("Songs", ignoreCase = true)
            }

        val firstPage =
            songsSection?.moreEndpoint?.let { endpoint ->
                YouTube.artistItems(endpoint).getOrNull()
            }

        val allSongs = linkedMapOf<String, SongItem>()

        // Baseline: every song the artist page already surfaced (the preview
        // head). Kept unconditionally so the catalogue is always a SUPERSET of
        // the preview — never smaller than what the overview shows.
        songsSection?.items?.filterIsInstance<SongItem>()?.forEach { allSongs[it.id] = it }

        // Overlay the complete paginated catalogue; same-id entries replace the
        // shelf copies (which lack duration), extras append in catalogue order.
        firstPage?.items?.filterIsInstance<SongItem>()?.forEach { allSongs[it.id] = it }

        var continuation = firstPage?.continuation
        var requestCount = 0
        val maxRequests = 50
        while (continuation != null && requestCount < maxRequests) {
            requestCount++
            val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
            next.items.filterIsInstance<SongItem>().forEach { allSongs[it.id] = it }
            continuation = next.continuation
        }

        val result = allSongs.values.toList()
        android.util.Log.d(
            "ArtistCatalogue",
            "allArtistSongs(browseId=$browseId): section=${songsSection?.title}, " +
                "hasMoreEndpoint=${songsSection?.moreEndpoint != null}, " +
                "shelf=${songsSection?.items?.size}, firstPage=${firstPage?.items?.size}, " +
                "continuation=${firstPage?.continuation != null}, total=$result.size",
        )
        result
    }

    suspend fun album(browseId: String): Result<JsonObject> =
        YouTube.browseJson(browseId = browseId)

    suspend fun playlist(playlistId: String): Result<YTPlaylistPage> = runCatching {
        val page = YouTube.playlist(playlistId).getOrThrow()
        android.util.Log.d("PlaylistDebug", "YouTubeApi.playlist: playlistId=$playlistId songs=${page.songs.size} continuation=${page.songsContinuation}")
        YTPlaylistPage(
            playlist = YTPlaylist(
                id = page.playlist.id,
                title = page.playlist.title,
                thumbnailUrl = page.playlist.thumbnail,
                author = page.playlist.author?.name,
            ),
            songs = page.songs.map { it.toYTSongItem() },
            continuation = page.songsContinuation,
        )
    }

    suspend fun home(continuation: String? = null): Result<JsonObject> = runCatching {
        if (continuation != null) {
            YouTube.browseJson(continuation = continuation).getOrThrow()
        } else {
            YouTube.browseJson(browseId = "FEmusic_home").getOrThrow()
        }
    }

    suspend fun homeWithFallback(continuation: String? = null): Result<JsonObject> = home(continuation)

    suspend fun recentlyPlayedSongs(limit: Int = 10): Result<List<YTSongItem>> = runCatching {
        val page = YouTube.musicHistory().getOrThrow()
        page.sections.orEmpty()
            .flatMap { it.songs }
            .map { it.toYTSongItem() }
            .distinctBy { it.videoId }
            .take(limit)
    }

    suspend fun explore(continuation: String? = null): Result<JsonObject> = runCatching {
        if (continuation != null) {
            YouTube.browseJson(continuation = continuation).getOrThrow()
        } else {
            YouTube.browseJson(browseId = "FEmusic_explore").getOrThrow()
        }
    }

    suspend fun charts(continuation: String? = null): Result<JsonObject> = runCatching {
        if (continuation != null) {
            YouTube.browseJson(continuation = continuation).getOrThrow()
        } else {
            YouTube.browseJson(browseId = "FEmusic_charts", params = "ggMGCgQIgAQ%3D").getOrThrow()
        }
    }

    suspend fun library(browseId: String = "FEmusic_liked_playlists"): Result<JsonObject> =
        YouTube.browseJson(browseId = browseId, setLogin = YouTube.hasLoginCookie())

    private var cachedSignatureTimestamp: Int? = null

    suspend fun fetchSignatureTimestamp(): Int {
        if (cachedSignatureTimestamp != null) return cachedSignatureTimestamp!!
        return runCatching {
            YouTube.fetchSignatureTimestamp()
        }.getOrElse { 24007 }.also { cachedSignatureTimestamp = it }
    }

    suspend fun getSearchSuggestions(input: String): Result<List<String>> = runCatching {
        YouTube.searchSuggestions(input).getOrThrow().queries
    }

    fun parseHomeSections(root: JsonObject): List<HomeSection> {
        val sectionList = root["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?: return emptyList()
        val contents = sectionList["contents"]?.jsonArray ?: return emptyList()
        val sections = mutableListOf<HomeSection>()
        for (content in contents) {
            val shelf = content.jsonObject["musicCarouselShelfRenderer"]?.jsonObject ?: continue
            val header = shelf["header"]?.jsonObject
                ?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject ?: continue
            val title = header["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: continue
            val items = shelf["contents"]?.jsonArray?.mapNotNull { item ->
                val twoRow = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject ?: return@mapNotNull null
                val itemTitle = twoRow["title"]?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val navEp = twoRow["navigationEndpoint"]?.jsonObject
                val browseId = navEp?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.contentOrNull
                val videoId = navEp?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull
                val playlistId = navEp?.get("watchEndpoint")?.jsonObject
                    ?.get("playlistId")?.jsonPrimitive?.contentOrNull
                val thumbnail = twoRow["thumbnailRenderer"]?.jsonObject
                    ?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject
                    ?.get("thumbnails")?.jsonArray
                    ?.lastOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull
                val subtitleRuns = twoRow["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                val artists = mutableListOf<YTArtist>()
                subtitleRuns?.forEach { run ->
                    val text = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val id = run.jsonObject["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (id?.startsWith("UC") == true) artists.add(YTArtist(text, id))
                }
                HomeItem(
                    videoId = videoId,
                    title = itemTitle,
                    artists = artists,
                    thumbnailUrl = thumbnail,
                    playlistId = playlistId,
                    browseId = browseId,
                )
            }.orEmpty()
            if (items.isEmpty()) continue
            sections.add(HomeSection(title = title, items = items))
        }
        return sections
    }

    fun parseLibraryPlaylists(root: JsonObject): List<YTPlaylist> {
        val result = mutableListOf<YTPlaylist>()
        val contents = root["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray
            ?: root["contents"]?.jsonObject
                ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
            ?: return result

        for (sectionIdx in 0 until contents.size) {
            val section = contents[sectionIdx].jsonObject

            val itemSection = section["itemSectionRenderer"]?.jsonObject
            if (itemSection != null) {
                val secContents = itemSection["contents"]?.jsonArray ?: continue
                for (si in 0 until secContents.size) {
                    val shelf = secContents[si].jsonObject["musicShelfRenderer"]?.jsonObject ?: continue
                    val shelfContents = shelf["contents"]?.jsonArray ?: continue
                    for (sci in 0 until shelfContents.size) {
                        val renderer = shelfContents[sci].jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                        val flexColumns = renderer["flexColumns"]?.jsonArray ?: continue
                        val title = flexColumns[0].jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: continue
                        val playlistId = renderer["navigationEndpoint"]?.jsonObject
                            ?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.content
                            ?.removePrefix("VL") ?: continue

                        val thumb = renderer["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject
                            ?.get("url")?.jsonPrimitive?.contentOrNull

                        val subtitleRuns = flexColumns.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject
                            ?.get("runs")?.jsonArray

                        var author: String? = null
                        var songCount: Int? = null
                        subtitleRuns?.forEach { run ->
                            val text = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            if (text.contains("song", ignoreCase = true) || text.contains("track", ignoreCase = true)) {
                                val digits = text.replace(Regex("[^0-9]"), "")
                                songCount = digits.toIntOrNull()
                            } else if (run.jsonObject["navigationEndpoint"] == null && text.isNotBlank()) {
                                if (author == null) author = text
                            }
                        }

                        result.add(YTPlaylist(
                            id = playlistId,
                            title = title,
                            thumbnailUrl = thumb,
                            songCount = songCount,
                            author = author,
                        ))
                    }
                }
            }

            val gridRenderer = section["musicGridRenderer"]?.jsonObject
            if (gridRenderer != null) {
                val gridContents = gridRenderer["items"]?.jsonArray ?: continue
                for (gi in 0 until gridContents.size) {
                    val renderer = gridContents[gi].jsonObject["musicTwoRowItemRenderer"]?.jsonObject ?: continue
                    val title = renderer["title"]?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content ?: continue
                    val playlistId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.content
                        ?.removePrefix("VL") ?: continue
                    val thumb = renderer["thumbnailRenderer"]?.jsonObject
                        ?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject
                        ?.get("thumbnails")?.jsonArray
                        ?.lastOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.contentOrNull
                    val subRuns = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                    var songCount: Int? = null
                    subRuns?.forEach { run ->
                        val text = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val digits = text.replace(Regex("[^0-9]"), "")
                        songCount = digits.toIntOrNull()
                    }
                    result.add(YTPlaylist(
                        id = playlistId,
                        title = title,
                        thumbnailUrl = thumb,
                        songCount = songCount,
                    ))
                }
            }
        }
        return result.distinctBy { it.id }
    }

    private fun extractUrlFromFormat(fmt: JsonObject): String? {
        fmt["url"]?.jsonPrimitive?.contentOrNull?.let { return it }
        val cipher = fmt["cipher"]?.jsonPrimitive?.contentOrNull
            ?: fmt["signatureCipher"]?.jsonPrimitive?.contentOrNull ?: return null
        val params = cipher.split("&").associate { param ->
            val eq = param.indexOf("=")
            if (eq > 0) param.substring(0, eq) to param.substring(eq + 1)
            else param to ""
        }
        val url = params["url"]?.replace("%3A", ":")?.replace("%2F", "/")
            ?.replace("%3F", "?")?.replace("%3D", "=")?.replace("%26", "&") ?: return null
        val sig = params["s"] ?: params["sig"]
        val sp = params["sp"] ?: "sig"
        if (sig != null) {
            val separator = if (url.contains("?")) "&" else "?"
            return "$url$separator$sp=$sig"
        }
        return url
    }

    fun parsePlayerResponse(root: JsonObject): YTPlayerResponse {
        val videoDetails = root["videoDetails"]?.jsonObject
            ?: error("Missing videoDetails")

        val videoId = videoDetails["videoId"]?.jsonPrimitive?.content ?: ""
        val title = videoDetails["title"]?.jsonPrimitive?.contentOrNull
        val artist = videoDetails["author"]?.jsonPrimitive?.contentOrNull
        val thumbnailUrl = videoDetails["thumbnail"]?.jsonObject
            ?.get("thumbnails")?.jsonArray
            ?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
        val lengthSeconds = videoDetails["lengthSeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

        val streamingData = root["streamingData"]?.jsonObject
        val streamUrl = streamingData?.get("adaptiveFormats")?.jsonArray
            ?.firstOrNull { fmt ->
                val mime = fmt.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                mime.contains("audio/mp4") || mime.contains("audio/webm")
            }?.let { extractUrlFromFormat(it.jsonObject) }
            ?: streamingData?.get("formats")?.jsonArray
                ?.firstOrNull()?.let { extractUrlFromFormat(it.jsonObject) }
            ?: streamingData?.get("hlsManifestUrl")?.jsonPrimitive?.contentOrNull
            ?: streamingData?.get("dashManifestUrl")?.jsonPrimitive?.contentOrNull

        val expiresInSeconds = streamingData?.get("expiresInSeconds")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

        return YTPlayerResponse(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            lengthSeconds = lengthSeconds,
            streamUrl = streamUrl,
            expiresInSeconds = expiresInSeconds,
        )
    }
}