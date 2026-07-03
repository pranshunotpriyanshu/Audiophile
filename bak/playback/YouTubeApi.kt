package com.pryvn.audiophile.code.api

import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.code.api.InnerTubeClient
import com.pryvn.audiophile.code.api.InnerTubeClient.ClientConfig

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

data class YTPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val songCount: Int? = null,
    val author: String? = null,
)

data class YTSearchResult(
    val items: List<YTSongItem>,
    val continuation: String? = null,
)

data class YTPlaylistPage(
    val playlist: YTPlaylist,
    val songs: List<YTSongItem>,
    val continuation: String? = null,
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

object YouTubeApi {

    suspend fun fetchAccountInfo(): Result<YTAccountInfo> {
        return runCatching {
            val result = InnerTubeClient.accountMenu()
            val header = result.getOrNull()?.get("actions")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("openPopupAction")?.jsonObject
                ?.get("popup")?.jsonObject
                ?.get("accountMenuPopupRenderer")?.jsonObject
                ?.get("header")?.jsonObject
                ?.get("activeAccountHeaderRenderer")?.jsonObject
            val name = header?.get("accountName")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
            val email = header?.get("email")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
            val channelHandle = header?.get("channelHandle")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
            val avatarUrl = header?.get("accountPhoto")?.jsonObject
                ?.get("thumbnails")?.jsonArray
                ?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull
            YTAccountInfo(name = name, email = email, channelHandle = channelHandle, avatarUrl = avatarUrl)
        }
    }

    suspend fun search(query: String, filter: String? = null): Result<YTSearchResult> =
        searchWithFallback(query = query, filter = filter)

    suspend fun searchWithFallback(query: String, filter: String? = null): Result<YTSearchResult> {
        return withContext(Dispatchers.IO) {
            val setLogin = InnerTubeClient.hasLoginCookie
            val params = filter
            val configs = listOf(
                ClientConfig(InnerTubeClient.CLIENT_NAME, InnerTubeClient.CLIENT_VERSION, InnerTubeClient.CLIENT_ID, ua = InnerTubeClient.USER_AGENT, origin = "https://music.youtube.com", referer = "https://music.youtube.com/"),
                ClientConfig("WEB_REMIX", "2.20250101.00.00", "67", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "https://www.youtube.com", "https://www.youtube.com/"),
                ClientConfig("WEB", "2.20250101.00.00", "1", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "https://www.youtube.com", "https://www.youtube.com/"),
                ClientConfig("ANDROID_MUSIC", "6.42.52", "21", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip", "https://music.youtube.com", "https://music.youtube.com/"),
                ClientConfig("IOS", "19.29.1", "5", "com.google.ios.youtube/19.29.1 (iPhone; U; CPU iOS 17_4 like Mac OS X)", "https://www.youtube.com", "https://www.youtube.com/")
            )

            for (config in configs) {
                val result = InnerTubeClient.search(
                    query = query,
                    params = params,
                    setLogin = setLogin,
                    clientName = config.clientName,
                    clientVersion = config.clientVersion,
                    clientId = config.clientId,
                    ua = config.ua,
                    origin = config.origin,
                    referer = config.referer
                )

                if (result.isSuccess) {
                    val jsonResult = result.getOrNull()
                    if (jsonResult != null) {
                        return@withContext Result.success(parseSearchResults(jsonResult))
                    }
                }
            }

            // Fall back to original approach
            return@withContext runCatching {
                val jsonResult = InnerTubeClient.search(query, params, setLogin = setLogin)
                parseSearchResults(jsonResult.getOrThrow())
            }
        }
    }

    suspend fun searchContinuation(continuation: String): Result<YTSearchResult> =
        InnerTubeClient.search(null, continuation = continuation, setLogin = InnerTubeClient.hasLoginCookie)
            .map { parseSearchContinuation(it) }

    suspend fun artist(browseId: String): Result<JsonObject> = InnerTubeClient.browse(browseId)

    suspend fun album(browseId: String): Result<JsonObject> = InnerTubeClient.browse(browseId)

    suspend fun playlist(playlistId: String): Result<YTPlaylistPage> {
        return runCatching {
            val result = InnerTubeClient.browse(
                browseId = "VL$playlistId",
                setLogin = InnerTubeClient.hasLoginCookie,
            )
            parsePlaylistPage(result.getOrThrow(), playlistId)
        }
    }

    suspend fun home(continuation: String? = null): Result<JsonObject> {
        return homeWithFallback(continuation = continuation)
    }

    private suspend fun browseWithFallback(browseId: String, params: String? = null, continuation: String? = null): Result<JsonObject> = withContext(Dispatchers.IO) {
        val setLogin = InnerTubeClient.hasLoginCookie
        val configs = listOf(
            ClientConfig(InnerTubeClient.CLIENT_NAME, InnerTubeClient.CLIENT_VERSION, InnerTubeClient.CLIENT_ID, ua = InnerTubeClient.USER_AGENT, origin = "https://music.youtube.com", referer = "https://music.youtube.com/"),
            ClientConfig("WEB_REMIX", "2.20250101.00.00", "67", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "https://www.youtube.com", "https://www.youtube.com/"),
            ClientConfig("WEB", "2.20250101.00.00", "1", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "https://www.youtube.com", "https://www.youtube.com/"),
            ClientConfig("ANDROID_MUSIC", "6.42.52", "21", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip", "https://music.youtube.com", "https://music.youtube.com/"),
            ClientConfig("IOS", "19.29.1", "5", "com.google.ios.youtube/19.29.1 (iPhone; U; CPU iOS 17_4 like Mac OS X)", "https://www.youtube.com", "https://www.youtube.com/")
        )

        val effectiveBrowseId = if (continuation != null) null else browseId

        for (config in configs) {
            val result = if (continuation != null) {
                InnerTubeClient.browse(
                    continuation = continuation,
                    setLogin = setLogin,
                    clientName = config.clientName,
                    clientVersion = config.clientVersion,
                    clientId = config.clientId,
                    ua = config.ua,
                    origin = config.origin,
                    referer = config.referer
                )
            } else {
                InnerTubeClient.browse(
                    browseId = effectiveBrowseId,
                    params = params,
                    setLogin = setLogin,
                    clientName = config.clientName,
                    clientVersion = config.clientVersion,
                    clientId = config.clientId,
                    ua = config.ua,
                    origin = config.origin,
                    referer = config.referer
                )
            }

            if (result.isSuccess) {
                val jsonResult = result.getOrNull()
                if (jsonResult != null) {
                    val hasContent = jsonResult["contents"] != null ||
                            jsonResult["sectionListRenderer"] != null ||
                            jsonResult["musicShelfRenderer"] != null
                    if (hasContent) {
                        return@withContext Result.success(jsonResult)
                    }
                    return@withContext Result.success(jsonResult)
                }
            }
        }

        return@withContext if (continuation != null) {
            InnerTubeClient.browse(
                continuation = continuation,
                setLogin = setLogin,
                clientName = InnerTubeClient.CLIENT_NAME,
                clientVersion = InnerTubeClient.CLIENT_VERSION,
                clientId = InnerTubeClient.CLIENT_ID,
                ua = InnerTubeClient.USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
        } else {
            InnerTubeClient.browse(
                browseId = effectiveBrowseId,
                params = params,
                setLogin = setLogin,
                clientName = InnerTubeClient.CLIENT_NAME,
                clientVersion = InnerTubeClient.CLIENT_VERSION,
                clientId = InnerTubeClient.CLIENT_ID,
                ua = InnerTubeClient.USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
        }
    }

    suspend fun explore(continuation: String? = null): Result<JsonObject> {
        return browseWithFallback(browseId = "FEmusic_explore", continuation = continuation)
    }

    suspend fun charts(continuation: String? = null): Result<JsonObject> {
        return browseWithFallback(browseId = "FEmusic_charts", params = "ggMGCgQIgAQ%3D", continuation = continuation)
    }

    suspend fun homeWithFallback(continuation: String? = null): Result<JsonObject> = withContext(Dispatchers.IO) {
        val setLogin = InnerTubeClient.hasLoginCookie
        val configs = listOf(
            ClientConfig(InnerTubeClient.CLIENT_NAME, InnerTubeClient.CLIENT_VERSION, InnerTubeClient.CLIENT_ID, ua = InnerTubeClient.USER_AGENT, origin = "https://music.youtube.com", referer = "https://music.youtube.com/"),
            ClientConfig("WEB_REMIX", "2.20250101.00.00", "67", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "https://www.youtube.com", "https://www.youtube.com/"),
            ClientConfig("WEB", "2.20250101.00.00", "1", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "https://www.youtube.com", "https://www.youtube.com/"),
            ClientConfig("ANDROID_MUSIC", "6.42.52", "21", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip", "https://music.youtube.com", "https://music.youtube.com/"),
            ClientConfig("IOS", "19.29.1", "5", "com.google.ios.youtube/19.29.1 (iPhone; U; CPU iOS 17_4 like Mac OS X)", "https://www.youtube.com", "https://www.youtube.com/")
        )

        val browseId = if (continuation != null) null else "FEmusic_home"

        for (config in configs) {
            val result = if (continuation != null) {
                InnerTubeClient.browse(
                    continuation = continuation,
                    setLogin = setLogin,
                    clientName = config.clientName,
                    clientVersion = config.clientVersion,
                    clientId = config.clientId,
                    ua = config.ua,
                    origin = config.origin,
                    referer = config.referer
                )
            } else {
                InnerTubeClient.browse(
                    browseId = browseId,
                    setLogin = setLogin,
                    clientName = config.clientName,
                    clientVersion = config.clientVersion,
                    clientId = config.clientId,
                    ua = config.ua,
                    origin = config.origin,
                    referer = config.referer
                )
            }

            if (result.isSuccess) {
                val jsonResult = result.getOrNull()
                if (jsonResult != null) {
                    val hasContent = jsonResult["contents"] != null ||
                            jsonResult["sectionListRenderer"] != null ||
                            jsonResult["musicShelfRenderer"] != null
                    if (hasContent) {
                        return@withContext Result.success(jsonResult)
                    }
                    return@withContext Result.success(jsonResult)
                }
            }
        }

        // If all configs failed, fall back to original approach
        return@withContext if (continuation != null) {
            InnerTubeClient.browse(
                continuation = continuation,
                setLogin = setLogin,
                clientName = InnerTubeClient.CLIENT_NAME,
                clientVersion = InnerTubeClient.CLIENT_VERSION,
                clientId = InnerTubeClient.CLIENT_ID,
                ua = InnerTubeClient.USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
        } else {
            InnerTubeClient.browse(
                browseId = "FEmusic_home",
                setLogin = setLogin,
                clientName = InnerTubeClient.CLIENT_NAME,
                clientVersion = InnerTubeClient.CLIENT_VERSION,
                clientId = InnerTubeClient.CLIENT_ID,
                ua = InnerTubeClient.USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
        }
    }

    suspend fun library(browseId: String = "FEmusic_liked_playlists"): Result<JsonObject> {
        return InnerTubeClient.browse(
            browseId = browseId,
            setLogin = InnerTubeClient.hasLoginCookie,
            clientName = InnerTubeClient.CLIENT_NAME,
            clientVersion = InnerTubeClient.CLIENT_VERSION,
            clientId = InnerTubeClient.CLIENT_ID,
            ua = InnerTubeClient.USER_AGENT,
            origin = "https://music.youtube.com",
            referer = "https://music.youtube.com/"
        )
    }

    fun parseHomeSections(root: JsonObject): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
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
            ?: return sections

        for (sectionIdx in 0 until contents.size) {
            val section = contents[sectionIdx].jsonObject
            val sectionItems = mutableListOf<HomeItem>()

            val carousel = section["musicCarouselShelfRenderer"]?.jsonObject
            if (carousel != null) {
                val title = carousel["header"]?.jsonObject
                    ?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject
                    ?.get("title")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: continue

                val carouselContents = carousel["contents"]?.jsonArray
                if (carouselContents != null) {
                    for (ci in 0 until carouselContents.size) {
                        val item = carouselContents[ci].jsonObject
                        val twoRow = item["musicTwoRowItemRenderer"]?.jsonObject
                        if (twoRow != null) {
                            val itemTitle = twoRow["title"]?.jsonObject
                                ?.get("runs")?.jsonArray
                                ?.firstOrNull()?.jsonObject
                                ?.get("text")?.jsonPrimitive?.content ?: continue
                            val thumb = twoRow["thumbnailRenderer"]?.jsonObject
                                ?.get("musicThumbnailRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject
                                ?.get("thumbnails")?.jsonArray
                                ?.lastOrNull()?.jsonObject
                                ?.get("url")?.jsonPrimitive?.contentOrNull
                            val navEp = twoRow["navigationEndpoint"]?.jsonObject
                            val browseId = navEp?.get("browseEndpoint")?.jsonObject
                                ?.get("browseId")?.jsonPrimitive?.contentOrNull
                            val watchId = navEp?.get("watchEndpoint")?.jsonObject
                                ?.get("videoId")?.jsonPrimitive?.contentOrNull

                            val subtitleRuns = twoRow["subtitle"]?.jsonObject
                                ?.get("runs")?.jsonArray
                            val artists = mutableListOf<YTArtist>()
                            subtitleRuns?.forEach { run ->
                                val text = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                                val navId = run.jsonObject["navigationEndpoint"]?.jsonObject
                                    ?.get("browseEndpoint")?.jsonObject
                                    ?.get("browseId")?.jsonPrimitive?.contentOrNull
                                if (navId?.startsWith("UC") == true) {
                                    artists.add(YTArtist(text, navId))
                                }
                            }

                            sectionItems.add(HomeItem(
                                videoId = watchId,
                                title = itemTitle,
                                artists = artists,
                                thumbnailUrl = thumb,
                                browseId = browseId,
                            ))
                        }
                    }
                }
                if (sectionItems.isNotEmpty()) {
                    sections.add(HomeSection(title, sectionItems))
                }
                continue
            }

            val musicShelf = section["musicShelfRenderer"]?.jsonObject
            if (musicShelf != null) {
                val title = musicShelf["header"]?.jsonObject
                    ?.get("musicShelfBasicHeaderRenderer")?.jsonObject
                    ?.get("title")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content

                val shelfContents = musicShelf["contents"]?.jsonArray
                if (shelfContents != null) {
                    for (sci in 0 until shelfContents.size) {
                        val renderer = shelfContents[sci].jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                        parseListItem(renderer)?.let { song ->
                            sectionItems.add(HomeItem(
                                videoId = song.videoId,
                                title = song.title,
                                artists = song.artists,
                                album = song.album,
                                thumbnailUrl = song.thumbnailUrl,
                                durationSeconds = song.durationSeconds,
                                playlistId = song.playlistId,
                            ))
                        }
                    }
                }
                if (sectionItems.isNotEmpty()) {
                    sections.add(HomeSection(title ?: "Songs", sectionItems))
                }
                continue
            }

            val itemSection = section["itemSectionRenderer"]?.jsonObject
            if (itemSection != null) {
                val secContents = itemSection["contents"]?.jsonArray
                if (secContents != null) {
                    for (si in 0 until secContents.size) {
                        val subShelf = secContents[si].jsonObject["musicShelfRenderer"]?.jsonObject ?: continue
                        val subTitle = subShelf["header"]?.jsonObject
                            ?.get("musicShelfBasicHeaderRenderer")?.jsonObject
                            ?.get("title")?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content
                        val subContents = subShelf["contents"]?.jsonArray ?: continue
                        for (sci in 0 until subContents.size) {
                            val renderer = subContents[sci].jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                            parseListItem(renderer)?.let { song ->
                                sectionItems.add(HomeItem(
                                    videoId = song.videoId,
                                    title = song.title,
                                    artists = song.artists,
                                    album = song.album,
                                    thumbnailUrl = song.thumbnailUrl,
                                    durationSeconds = song.durationSeconds,
                                    playlistId = song.playlistId,
                                ))
                            }
                        }
                        if (sectionItems.isNotEmpty()) {
                            sections.add(HomeSection(subTitle ?: "Songs", sectionItems))
                            sectionItems.clear()
                        }
                    }
                }
            }
        }
        return sections
    }

    private var cachedSignatureTimestamp: Int? = null

    suspend fun fetchSignatureTimestamp(): Int {
        if (cachedSignatureTimestamp != null) return cachedSignatureTimestamp!!
        return runCatching {
            val result = InnerTubeClient.browse(browseId = "FEmusic_home")
            val tabs = result.getOrNull()?.get("contents")?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?: result.getOrNull()?.get("contents")?.jsonObject
                    ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                    ?.get("tabs")?.jsonArray
                ?: return@runCatching 24007
            val ts = tabs.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("musicShelfRenderer")?.jsonObject
                ?.get("bottomStatus")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("signatureTimestamp")?.jsonPrimitive?.contentOrNull
                ?.toIntOrNull()
            if (ts != null) {
                cachedSignatureTimestamp = ts
                ts
            } else {
                24007
            }
        }.getOrElse { 24007 }
    }

    suspend fun player(videoId: String, playlistId: String? = null): Result<YTPlayerResponse> {
        return runCatching {
            val result = InnerTubeClient.playerWithFallback(videoId, playlistId)
            parsePlayerResponse(result.getOrThrow())
        }
    }

    suspend fun playerSingle(videoId: String, playlistId: String? = null): Result<YTPlayerResponse> {
        return runCatching {
            val sigTs = fetchSignatureTimestamp()
            val result = InnerTubeClient.player(videoId, playlistId, signatureTimestamp = sigTs)
            parsePlayerResponse(result.getOrThrow())
        }
    }

    suspend fun getSearchSuggestions(input: String): Result<List<String>> {
        return runCatching {
            val result = InnerTubeClient.getSearchSuggestions(input)
            parseSuggestions(result.getOrThrow())
        }
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

    private fun parseSearchResults(root: JsonObject): YTSearchResult {
        val contents = root["contents"]?.jsonObject
            ?.get("tabbedSearchResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray
            ?: return YTSearchResult(emptyList())

        val items = mutableListOf<YTSongItem>()
        var continuation: String? = null

        for (section in contents) {
            val shelf = section.jsonObject["musicShelfRenderer"]?.jsonObject
            if (shelf != null) {
                val shelfContents = shelf["contents"]?.jsonArray ?: continue
                for (content in shelfContents) {
                    val renderer = content.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                    parseListItem(renderer)?.let { items.add(it) }
                }
                val cont = shelf["continuations"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("nextContinuationData")?.jsonObject
                    ?.get("continuation")?.jsonPrimitive?.content
                if (cont != null) continuation = cont
            }

            val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
            if (itemSection != null) {
                val sectionContents = itemSection["contents"]?.jsonArray ?: continue
                for (content in sectionContents) {
                    val inlineRenderer = content.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    if (inlineRenderer != null) {
                        parseListItem(inlineRenderer)?.let { items.add(it) }
                        continue
                    }
                    val shelf2 = content.jsonObject["musicShelfRenderer"]?.jsonObject ?: continue
                    val shelfContents = shelf2["contents"]?.jsonArray ?: continue
                    for (shelfContent in shelfContents) {
                        val renderer = shelfContent.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                        parseListItem(renderer)?.let { items.add(it) }
                    }
                }
            }
        }

        return YTSearchResult(items.distinctBy { it.videoId }, continuation)
    }

    private fun parseSearchContinuation(root: JsonObject): YTSearchResult {
        val continuationContents = root["continuationContents"]?.jsonObject
            ?.get("musicShelfContinuation")?.jsonObject ?: return YTSearchResult(emptyList())

        val items = mutableListOf<YTSongItem>()
        val contents = continuationContents["contents"]?.jsonArray ?: return YTSearchResult(emptyList())

        for (content in contents) {
            val renderer = content.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
            parseListItem(renderer)?.let { items.add(it) }
        }

        val continuation = continuationContents["continuations"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("nextContinuationData")?.jsonObject
            ?.get("continuation")?.jsonPrimitive?.content

        return YTSearchResult(items, continuation)
    }

    private fun parseListItem(renderer: JsonObject): YTSongItem? {
        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        val fixedColumns = renderer["fixedColumns"]?.jsonArray

        val title = flexColumns.firstOrNull()?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray
            ?.joinToString("") { run ->
                run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            }?.trim() ?: return null

        val videoId = renderer["playlistItemData"]?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.content
            ?: renderer["navigationEndpoint"]?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.content
            ?: return null

        val playlistId = renderer["navigationEndpoint"]?.jsonObject
            ?.get("watchEndpoint")?.jsonObject
            ?.get("playlistId")?.jsonPrimitive?.content

        val subtitleRuns = flexColumns.getOrNull(1)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray

        val artists = mutableListOf<YTArtist>()
        var album: YTAlbum? = null
        var thumbnailUrl: String? = null

        subtitleRuns?.forEach { run ->
            val runObj = run.jsonObject
            val text = runObj["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val navEndpoint = runObj["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
            val browseId = navEndpoint?.get("browseId")?.jsonPrimitive?.contentOrNull

            if (browseId?.startsWith("UC") == true) {
                artists.add(YTArtist(text, browseId))
            } else if (browseId?.startsWith("MPRE") == true) {
                album = YTAlbum(text, browseId)
            }
        }

        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray

        thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull

        var durationSeconds: Int? = null
        fixedColumns?.firstOrNull()?.jsonObject
            ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?.let { durationSeconds = parseDuration(it) }

        return YTSongItem(
            videoId = videoId,
            title = title,
            artists = artists,
            album = album,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            playlistId = playlistId,
        )
    }

    private fun parsePlaylistPage(root: JsonObject, playlistId: String): YTPlaylistPage {
        val contents = root["contents"]?.jsonObject
            ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray
            ?: error("Cannot parse playlist page")

        val firstColumnContents = contents.toList()
        val headerContent = firstColumnContents.firstOrNull { content ->
            content.jsonObject["musicResponsiveHeaderRenderer"] != null ||
                content.jsonObject["musicEditablePlaylistDetailHeaderRenderer"] != null
        }?.jsonObject

        val header = headerContent?.get("musicResponsiveHeaderRenderer")?.jsonObject
            ?: headerContent?.get("musicEditablePlaylistDetailHeaderRenderer")?.jsonObject
                ?.get("header")?.jsonObject?.get("musicResponsiveHeaderRenderer")?.jsonObject
            ?: error("Cannot parse playlist header")

        val title = header["title"]?.jsonObject
            ?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: error("Missing playlist title")

        val thumbnail = header["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray
            ?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull

        val subtitleRuns = header["secondSubtitle"]?.jsonObject
            ?.get("runs")?.jsonArray
        val songCount = subtitleRuns?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?.let { parseSongCount(it) }

        val author = header["straplineTextOne"]?.jsonObject
            ?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull

        val secondaryContents = root["contents"]?.jsonObject
            ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
            ?.get("secondaryContents")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray

        val songs = mutableListOf<YTSongItem>()
        var continuation: String? = null

        val songSections = (secondaryContents?.toList() ?: emptyList()) + firstColumnContents
        for (section in songSections) {
            val playlistRenderer = section.jsonObject["musicPlaylistShelfRenderer"]?.jsonObject
            if (playlistRenderer != null) {
                val items = playlistRenderer["contents"]?.jsonArray ?: continue
                for (item in items) {
                    val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                    parseListItem(renderer)?.let { songs.add(it) }
                }
                val cont = playlistRenderer["continuations"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("nextContinuationData")?.jsonObject
                    ?.get("continuation")?.jsonPrimitive?.content
                if (cont != null) continuation = cont
            }
        }

        return YTPlaylistPage(
            playlist = YTPlaylist(
                id = playlistId,
                title = title,
                thumbnailUrl = thumbnail,
                songCount = songCount,
                author = author,
            ),
            songs = songs,
            continuation = continuation,
        )
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

    private fun parsePlayerResponse(root: JsonObject): YTPlayerResponse {
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

    private fun parseSuggestions(root: JsonObject): List<String> {
        val contents = root["contents"]?.jsonArray ?: return emptyList()
        val firstSection = contents.firstOrNull()?.jsonObject
            ?.get("searchSuggestionsSectionRenderer")?.jsonObject
            ?.get("contents")?.jsonArray ?: return emptyList()
        return firstSection.mapNotNull { item ->
            item.jsonObject["searchSuggestionRenderer"]?.jsonObject
                ?.get("suggestion")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.joinToString("") { run ->
                    run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
                }
        }
    }

    private fun parseDuration(text: String): Int? {
        val parts = text.split(":")
        return when (parts.size) {
            2 -> parts[0].toIntOrNull()?.let { m -> parts[1].toIntOrNull()?.let { s -> m * 60 + s } }
            3 -> parts[0].toIntOrNull()?.let { h -> parts[1].toIntOrNull()?.let { m -> parts[2].toIntOrNull()?.let { s -> h * 3600 + m * 60 + s } } }
            else -> null
        }
    }

    private fun parseSongCount(text: String): Int? {
        val digits = text.replace(Regex("[^0-9]"), "")
        return digits.toIntOrNull()
    }
}