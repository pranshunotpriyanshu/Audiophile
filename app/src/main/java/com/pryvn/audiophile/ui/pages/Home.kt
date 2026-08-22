package com.pryvn.audiophile.ui.pages

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.HomeSection
import com.pryvn.audiophile.code.api.YTArtist
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.HomeItem
import com.pryvn.audiophile.data.libraries.HistoryEntry
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.PlaybackSource
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.toHighResThumbnail
import com.pryvn.audiophile.data.models.ImageViewModel
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.userFontWeight
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.theme.screenTitleFontWeight
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.ui.widgets.basic.ProfileButton
import com.pryvn.audiophile.ui.widgets.basic.PullToRefreshLayout
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

private fun HistoryEntry.toYTSongItem(): YTSongItem = YTSongItem(
    videoId = videoId,
    title = title,
    artists = artists?.split(", ")?.map { YTArtist(name = it) } ?: emptyList(),
    thumbnailUrl = thumbnailUrl,
)

private fun HomeItem.toYTSongItemLocal(): YTSongItem = YTSongItem(
    videoId = videoId ?: "",
    title = title,
    artists = artists,
    album = album,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    playlistId = playlistId,
)

private fun YTSongItem.toYosMediaItem(): YosMediaItem = YosMediaItem(
    uri = Uri.parse("ytmusic://$videoId"),
    mediaId = videoId,
    title = title,
    artists = artists.joinToString(", ") { it.name },
    thumb = thumbnailUrl?.let { Uri.parse(it) },
)

private data class MoodCategory(
    val name: String,
    val color: Color,
    val icon: String,
)

private val moodCategories = listOf(
    MoodCategory("Feel Good", Color(0xFFF5A623), "\u2606"),
    MoodCategory("Love", Color(0xFFE91E63), "\u2665"),
    MoodCategory("Chill", Color(0xFF5C6BC0), "\u2615"),
    MoodCategory("Workout", Color(0xFFE53935), "\u26A1"),
    MoodCategory("Focus", Color(0xFF43A047), "\u2605"),
)

@Composable
fun Home(
    navController: NavController,
    imageViewModel: ImageViewModel,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var sections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var recentlyPlayed by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var recentLoading by remember { mutableStateOf(false) }

    var relatedSongs by remember { mutableStateOf<List<YTSongItem>>(emptyList()) }
    var relatedLoading by remember { mutableStateOf(false) }

    val lastRelatedSeed = remember { mutableStateOf<String?>(null) }

    var dailyArtistSongs by remember { mutableStateOf<List<YTSongItem>>(emptyList()) }
    var dailyArtistName by remember { mutableStateOf("") }
    var dailyDiscoverSongs by remember { mutableStateOf<List<YTSongItem>>(emptyList()) }
    var dailyDiscoverTitle by remember { mutableStateOf("Discover Weekly") }

    fun loadHome(showSpinner: Boolean = true) {
        if (isLoading) return
        isLoading = showSpinner
        loadError = false
        scope.launch(Dispatchers.IO) {
            val fallbackToCache: suspend () -> Unit = {
                val cached = readHomeCache(context)
                withContext(Dispatchers.Main) {
                    if (cached != null) {
                        sections = cached
                        loadError = false
                    } else {
                        loadError = true
                    }
                    isLoading = false
                }
            }
            try {
                val result = withTimeoutOrNull(20_000L) { YouTubeApi.home() }
                if (result != null) {
                    result.onSuccess { json ->
                        val parsed = YouTubeApi.parseHomeSections(json)
                        writeHomeCache(context, json.toString())
                        withContext(Dispatchers.Main) {
                            sections = parsed
                            loadError = false
                            isLoading = false
                        }
                    }.onFailure { fallbackToCache() }
                } else {
                    fallbackToCache()
                }
            } catch (_: Exception) {
                fallbackToCache()
            }
        }
    }

    fun loadDailySections() {
        val prefs = context.getSharedPreferences("home_daily", Context.MODE_PRIVATE)
        val lastRefresh = prefs.getLong("last_refresh", 0)
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.HOURS.toMillis(24)

        val cachedArtist = prefs.getString("artist_name", null)
        val cachedArtistSongs = prefs.getString("artist_songs", null)
        val cachedDiscoverSongs = prefs.getString("discover_songs", null)
        val cachedDiscoverTitle = prefs.getString("discover_title", "Discover Weekly")

        if (now - lastRefresh < oneDayMs && cachedArtist != null && cachedArtistSongs != null) {
            dailyArtistName = cachedArtist
            dailyArtistSongs = parseSongList(cachedArtistSongs)
            dailyDiscoverTitle = cachedDiscoverTitle ?: "Discover Weekly"
            dailyDiscoverSongs = parseSongList(cachedDiscoverSongs ?: "[]")
            return
        }

        scope.launch(Dispatchers.IO) {
            val history = ListeningHistory.history.value
            val counts = mutableMapOf<String, Int>()
            for (entry in history) {
                val artists = entry.artists ?: continue
                for (name in artists.split(", ")) {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        counts[trimmed] = (counts[trimmed] ?: 0) + 1
                    }
                }
            }
            val artistCounts = counts.entries.sortedByDescending { it.value }

            if (artistCounts.isNotEmpty()) {
                val topArtist = artistCounts[0].key
                dailyArtistName = topArtist
                val artistResults = withTimeoutOrNull(15_000L) {
                    YouTubeApi.search(topArtist)
                }?.getOrNull()?.items
                    ?.filter { it.videoId != null }
                    ?.distinctBy { it.videoId }
                    ?.take(8)
                    ?: emptyList()
                dailyArtistSongs = artistResults

                val secondArtist = artistCounts.getOrNull(1)?.key
                if (secondArtist != null) {
                    dailyDiscoverTitle = secondArtist
                    val discoverResults = withTimeoutOrNull(15_000L) {
                        YouTubeApi.search(secondArtist)
                    }?.getOrNull()?.items
                        ?.filter { it.videoId != null }
                        ?.distinctBy { it.videoId }
                        ?.take(8)
                        ?: emptyList()
                    dailyDiscoverSongs = discoverResults
                } else {
                    dailyDiscoverTitle = "Discover Weekly"
                    dailyDiscoverSongs = emptyList()
                }
            }

            val edit = prefs.edit()
            edit.putLong("last_refresh", System.currentTimeMillis())
            edit.putString("artist_name", dailyArtistName)
            edit.putString("artist_songs", writeSongList(dailyArtistSongs))
            edit.putString("discover_title", dailyDiscoverTitle)
            edit.putString("discover_songs", writeSongList(dailyDiscoverSongs))
            edit.apply()
        }
    }

    fun applyHistory(entries: List<HistoryEntry>) {
        if (entries != recentlyPlayed) {
            recentlyPlayed = entries
        }

        val seed = entries.firstOrNull()
        if (seed == null) {
            if (relatedSongs.isNotEmpty()) relatedSongs = emptyList()
            if (lastRelatedSeed.value != null) lastRelatedSeed.value = null
            return
        }
        val seedKey = seed.videoId
        if (seedKey != lastRelatedSeed.value) {
            lastRelatedSeed.value = seedKey
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val query = seed.artists?.split(", ")?.firstOrNull() ?: seed.title
                    runCatching {
                        withTimeoutOrNull(15_000L) { YouTubeApi.search(query) }?.getOrNull()
                            ?.items
                            ?.filter { it.videoId != seed.videoId }
                            ?.distinctBy { it.videoId }
                            ?.take(10)
                            ?: emptyList()
                    }.getOrDefault(emptyList())
                }
                if (lastRelatedSeed.value == seedKey) {
                    relatedSongs = results
                }
            }
        }
    }

    fun loadRecentlyPlayed() {
        if (recentLoading) return
        recentLoading = true
        applyHistory(ListeningHistory.history.value)
        recentLoading = false
    }

    fun refreshHome() {
        loadHome()
        loadRecentlyPlayed()
        loadDailySections()
    }

    LaunchedEffect(Unit) {
        if (sections.isEmpty()) {
            val cached = withContext(Dispatchers.IO) { readHomeCache(context) }
            if (cached != null && sections.isEmpty()) {
                sections = cached
            }
            loadHome(showSpinner = false)
        }
        loadDailySections()
    }

    val tryTheseSongs = remember(sections) {
        sections.flatMap { it.items }
            .filter { it.videoId != null }
            .distinctBy { it.videoId }
            .shuffled()
            .take(5)
            .map { it.toYTSongItemLocal() }
    }

    LaunchedEffect(Unit) {
        ListeningHistory.history.collect { applyHistory(it) }
    }

    val listState = rememberLazyListState()
    val bottomInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val songMenuOpen = remember { mutableStateOf(false) }
    val menuSong = remember { mutableStateOf<YosMediaItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 54.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.page_home_title),
                fontSize = 35.sp,
                fontWeight = screenTitleFontWeight(),
                lineHeight = 40.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ProfileButton(
                size = 32.dp,
                onClick = { navController.toUI(UI.Settings.Main) },
            )
        }

        PullToRefreshLayout(
            isRefreshing = isLoading || recentLoading || relatedLoading,
            onRefresh = ::refreshHome,
            listState = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 134.dp),
            ) {
                if (loadError) {
                    item("error") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Unable to load content",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontFamily = SfProFontFamily,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Check your internet connection and try again.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                fontFamily = SfProFontFamily,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { refreshHome() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Retry",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = SfProFontFamily,
                                )
                            }
                        }
                    }
                }

                if (!isLoading && !loadError) {
                    // Featured Picks
                    if (tryTheseSongs.isNotEmpty()) {
                        item("toppicks_title") {
                            SectionHeader("Featured Picks")
                        }
                        item("toppicks_pager") {
                            val pagerState = rememberPagerState(
                                pageCount = { tryTheseSongs.size },
                            )
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                contentPadding = PaddingValues(start = 20.dp, end = 136.dp),
                                pageSize = PageSize.Fill,
                                modifier = Modifier.height(420.dp),
                            ) { page ->
                                FeaturedPickCard(
                                    song = tryTheseSongs[page],
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            MediaController.playOnline(tryTheseSongs[page])
                                        }
                                    },
                                    onLongClick = {
                                        menuSong.value = tryTheseSongs[page].toYosMediaItem()
                                        songMenuOpen.value = true
                                    },
                                )
                            }
                        }
                    }

                    // Recently Played
                    if (!recentLoading || recentlyPlayed.isNotEmpty()) {
                        item("recent_header") {
                            SectionHeaderWithArrow(
                                title = "Recently Played",
                                onClick = { navController.toUI(UI.RecentlyPlayed) },
                            )
                        }
                        if (recentlyPlayed.isNotEmpty()) {
                            item("recent_list") {
                                LazyRow(
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(recentlyPlayed, key = { it.videoId }) { entry ->
                                        val song = entry.toYTSongItem()
                                        AlbumCard(
                                            song = song,
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    when (entry.source) {
                                                        PlaybackSource.LOCAL -> {
                                                            val localSong = MediaController.mainMusicList
                                                                .find { it.mediaId == entry.videoId }
                                                            localSong?.let {
                                                                val queue = buildList {
                                                                    add(it)
                                                                    recentlyPlayed.asSequence()
                                                                        .filter { h -> h.videoId != entry.videoId }
                                                                        .mapNotNull { h ->
                                                                            MediaController.mainMusicList
                                                                                .find { m -> m.mediaId == h.videoId }
                                                                        }
                                                                        .distinctBy { it.mediaId }
                                                                        .forEach { add(it) }
                                                                }
                                                                MediaController.prepare(it, queue)
                                                            }
                                                        }
                                                        PlaybackSource.ONLINE -> {
                                                            MediaController.playOnline(song)
                                                        }
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                menuSong.value = song.toYosMediaItem()
                                                songMenuOpen.value = true
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            item("recent_empty") {
                                EmptyMessage("No recently played songs.")
                            }
                        }
                    }

                    // Related
                    item("related_title") {
                        SectionHeader("Because You Recently Listened")
                    }
                    if (relatedSongs.isNotEmpty()) {
                        item("related_list") {
                            LazyRow(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(relatedSongs, key = { it.videoId }) { song ->
                                    AlbumCard(
                                        song = song,
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                MediaController.playOnline(song)
                                            }
                                        },
                                        onLongClick = {
                                            menuSong.value = song.toYosMediaItem()
                                            songMenuOpen.value = true
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        item("related_empty") {
                            EmptyMessage("Play some music to receive recommendations.")
                        }
                    }

                    // Daily Artist Spotlight
                    if (dailyArtistSongs.isNotEmpty()) {
                        item("daily_artist_title") {
                            SectionHeaderWithArrow(
                                title = "World of $dailyArtistName",
                                onClick = {},
                            )
                        }
                        item("daily_artist_list") {
                            LazyRow(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(dailyArtistSongs, key = { it.videoId }) { song ->
                                    AlbumCard(
                                        song = song,
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                MediaController.playOnline(song)
                                            }
                                        },
                                        onLongClick = {
                                            menuSong.value = song.toYosMediaItem()
                                            songMenuOpen.value = true
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Daily Discover
                    if (dailyDiscoverSongs.isNotEmpty()) {
                        item("daily_discover_title") {
                            SectionHeaderWithArrow(
                                title = dailyDiscoverTitle,
                                onClick = {},
                            )
                        }
                        item("daily_discover_list") {
                            LazyRow(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(dailyDiscoverSongs, key = { it.videoId }) { song ->
                                    AlbumCard(
                                        song = song,
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                MediaController.playOnline(song)
                                            }
                                        },
                                        onLongClick = {
                                            menuSong.value = song.toYosMediaItem()
                                            songMenuOpen.value = true
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Find Your Mood
                    item("mood_title") {
                        SectionHeader("Find Your Mood")
                    }
                    item("mood_list") {
                        LazyRow(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(moodCategories) { mood ->
                                MoodCard(
                                    mood = mood,
                                    onClick = {},
                                )
                            }
                        }
                    }

                    // API sections
                    sections.forEach { section ->
                        item("foryou_header_${section.title}") {
                            SectionHeader(section.title)
                        }
                        item("foryou_carousel_${section.title}") {
                            LazyRow(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(section.items, key = { it.title + (it.videoId ?: it.browseId ?: "") }) { item ->
                                    HomeCard(
                                        item = item,
                                        onClick = {
                                            if (item.playlistId != null) {
                                                LibraryObject.setTargetPlaylistId(item.playlistId)
                                                navController.toUI(UI.OnlinePlaylist)
                                            } else if (item.browseId?.startsWith("VL") == true || item.browseId?.startsWith("PL") == true) {
                                                LibraryObject.setTargetPlaylistId(item.browseId)
                                                navController.toUI(UI.OnlinePlaylist)
                                            } else if (item.browseId != null) {
                                                LibraryObject.setTargetBrowseId(item.browseId)
                                                navController.toUI(UI.OnlineAlbumInfo)
                                            } else {
                                                item.videoId?.let {
                                                    scope.launch(Dispatchers.IO) {
                                                        MediaController.playOnline(item.toYTSongItemLocal())
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = if (item.videoId != null) {
                                            {
                                                menuSong.value = item.toYTSongItemLocal().toYosMediaItem()
                                                songMenuOpen.value = true
                                            }
                                        } else null,
                                    )
                                }
                            }
                        }
                    }
                }

                if (sections.isEmpty() && !isLoading && !loadError) {
                    item("empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No content available",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontFamily = SfProFontFamily,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Pull down to refresh or tap the retry button.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                fontFamily = SfProFontFamily,
                            )
                        }
                    }
                }
            }
        }

        SongOverflowSheet(
            isOpen = songMenuOpen,
            song = menuSong.value,
            navController = navController,
        )
    }
}

private const val HOME_FEED_CACHE_FILE = "home_feed.json"

private fun writeHomeCache(context: Context, jsonText: String) {
    try {
        File(context.cacheDir, HOME_FEED_CACHE_FILE).writeText(jsonText)
    } catch (_: Exception) {
    }
}

private fun readHomeCache(context: Context): List<HomeSection>? {
    return try {
        val file = File(context.cacheDir, HOME_FEED_CACHE_FILE)
        if (!file.exists()) return null
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        YouTubeApi.parseHomeSections(root).takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

private fun writeSongList(songs: List<YTSongItem>): String {
    val arr = JSONArray()
    songs.forEach { song ->
        val obj = JSONObject()
        obj.put("videoId", song.videoId)
        obj.put("title", song.title)
        val artistsArr = JSONArray()
        song.artists.forEach { artist ->
            artistsArr.put(JSONObject().put("name", artist.name))
        }
        obj.put("artists", artistsArr)
        obj.put("thumbnailUrl", song.thumbnailUrl)
        arr.put(obj)
    }
    return arr.toString()
}

private fun parseSongList(json: String): List<YTSongItem> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            YTSongItem(
                videoId = obj.optString("videoId"),
                title = obj.optString("title"),
                artists = obj.optJSONArray("artists")?.let { artistsArr ->
                    (0 until artistsArr.length()).map { j ->
                        YTArtist(name = artistsArr.getJSONObject(j).optString("name"))
                    }
                } ?: emptyList(),
                thumbnailUrl = obj.optString("thumbnailUrl"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = headingFontWeight(),
        fontSize = 22.sp,
        lineHeight = 22.sp,
        fontFamily = SfProFontFamily,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun SectionHeaderWithArrow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, top = 24.dp, bottom = 10.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontWeight = headingFontWeight(),
            fontSize = 22.sp,
            lineHeight = 22.sp,
            fontFamily = SfProFontFamily,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "\u203A",
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontFamily = SfProFontFamily,
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        fontFamily = SfProFontFamily,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedPickCard(
    song: YTSongItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            CachedArtworkImage(
                url = song.thumbnailUrl.toHighResThumbnail(),
                contentDescription = null,
                size = 556,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = "From Library",
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.title,
                    fontSize = 19.sp,
                    fontWeight = userFontWeight(),
                    fontFamily = SfProFontFamily,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artists.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song.artists.joinToString(", ") { it.name },
                        fontSize = 14.sp,
                        fontFamily = SfProFontFamily,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    song: YTSongItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        CachedArtworkImage(
            url = song.thumbnailUrl.toHighResThumbnail(),
            contentDescription = null,
            size = 320,
            modifier = Modifier
                .width(160.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Text(
            text = song.title,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = SfProFontFamily,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        if (song.artists.isNotEmpty()) {
            Text(
                text = song.artists.joinToString(", ") { it.name },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = SfProFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodCard(
    mood: MoodCategory,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mood.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mood.icon,
                fontSize = 48.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = mood.name,
            fontSize = 14.sp,
            fontFamily = SfProFontFamily,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCard(
    item: HomeItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CachedArtworkImage(
            url = item.thumbnailUrl.toHighResThumbnail(),
            contentDescription = null,
            size = 300,
            modifier = Modifier
                .width(150.dp)
                .height(150.dp),
        )
        Text(
            text = item.title,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = SfProFontFamily,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (item.artists.isNotEmpty()) {
            Text(
                text = item.artists.joinToString(", ") { it.name },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = SfProFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
