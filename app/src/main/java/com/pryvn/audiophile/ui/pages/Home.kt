package com.pryvn.audiophile.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import android.content.Context
import java.io.File
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.HomeSection
import com.pryvn.audiophile.code.api.YTArtist
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.HomeItem
import com.pryvn.audiophile.code.api.toYTSongItem
import com.pryvn.audiophile.data.libraries.HistoryEntry
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.PlaybackSource
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

private fun HistoryEntry.toYTSongItem(): YTSongItem = YTSongItem(
    videoId = videoId,
    title = title,
    artists = artists?.split(", ")?.map { YTArtist(name = it) } ?: emptyList(),
    thumbnailUrl = thumbnailUrl,
)

@Composable
fun Home(
    navController: NavController,
    imageViewModel: ImageViewModel
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Original Home state (working albums backend) ──
    var sections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // ── Try These (online songs derived from home sections) ──
    // (declared below via remember(sections) to avoid stale local state)

    // ── Featured Songs (derived from sections, no API call) ──
    val featuredSongs = remember(sections) {
        sections.flatMap { it.items }
            .filter { it.videoId != null }
            .distinctBy { it.videoId }
            .take(15)
    }

    // ── Recently Played ──
    var recentlyPlayed by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var recentLoading by remember { mutableStateOf(false) }

    // ── Related Songs ──
    var relatedSongs by remember { mutableStateOf<List<YTSongItem>>(emptyList()) }
    var relatedLoading by remember { mutableStateOf(false) }

    // Seed (song) the current "Because you recently listened" row was built from,
    // so background history updates only refetch when the seed actually changes.
    val lastRelatedSeed = remember { mutableStateOf<String?>(null) }

    // ── Curated Songs (derived from sections, no API call) ──
    val curatedSongs = remember(sections) {
        sections.flatMap { it.items }
            .filter { it.videoId != null }
            .distinctBy { it.videoId }
            .drop(15)
            .take(48)
    }

    // ── Original loadHome (working albums backend) ──
    // Network call is timeout-guarded so the pull-to-refresh spinner can never
    // spin forever on a hung connection, and a successful response is written to
    // the on-disk home feed cache for instant cold starts.
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
                    }.onFailure {
                        fallbackToCache()
                    }
                } else {
                    // timed out
                    fallbackToCache()
                }
            } catch (_: Exception) {
                fallbackToCache()
            }
        }
    }

    // ── Recently Played loader (reads from local listening history) ──
    // Applies history updates SILENTLY: sections update in place without
    // triggering the pull-to-refresh spinner, and related songs are only
    // refetched when the seed song actually changes.
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
                        withTimeoutOrNull(15_000L) { YouTubeApi.search(query) }?.getOrThrow()
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

    // Manual pull-to-refresh: shows the refresh indicator while reloading.
    fun loadRecentlyPlayed() {
        if (recentLoading) return
        recentLoading = true
        applyHistory(ListeningHistory.history.value)
        recentLoading = false
    }

    fun refreshHome() {
        loadHome()
        loadRecentlyPlayed()
    }


    // ── Initial load: render the last cached feed instantly, then refresh in
    //     the background without showing the pull-to-refresh spinner. ──
    LaunchedEffect(Unit) {
        if (sections.isEmpty()) {
            val cached = withContext(Dispatchers.IO) { readHomeCache(context) }
            if (cached != null && sections.isEmpty()) {
                sections = cached
            }
            loadHome(showSpinner = false)
        }
    }

    // ── Try These: pick a few online songs from the home sections ──
    val tryTheseSongs = remember(sections) {
        sections.flatMap { it.items }
            .filter { it.videoId != null }
            .distinctBy { it.videoId }
            .shuffled()
            .take(5)
            .map { it.toYTSongItem() }
    }

    // ── Observe listening history: fires immediately with current value,
    //     then on every song recording. Applies updates silently so the
    //     screen never shows a refresh spinner or whole-screen churn. ──
    LaunchedEffect(Unit) {
        ListeningHistory.history.collect {
            applyHistory(it)
        }
    }

    val listState = rememberLazyListState()
    val bottomInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 40.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.page_home_title),
                fontSize = 35.sp,
                fontWeight = screenTitleFontWeight(),
                lineHeight = 40.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
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
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 134.dp)
            ) {

            // ─── Loading / Error states ──────────────────────────────────────
            // NOTE: the refresh spinner is rendered by PullToRefreshLayout itself
            // (bound to isRefreshing = isLoading || recentLoading || relatedLoading),
            // so no in-content spinner is needed here — it would be a duplicate.
            if (loadError) {
                item("error") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
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
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
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

            // ─── Content (sections loaded) ─────────────────────────────────
            if (!isLoading && !loadError) {
                // ═══ 1. Try These (random from library) ═════════════════════════
                if (tryTheseSongs.isNotEmpty()) {
                    item("trythese_title") {
                        SectionTitle(stringResource(R.string.home_recommend_title))
                    }
                    item("trythese_carousel") {
                        val pagerState = rememberPagerState(
                            pageCount = { tryTheseSongs.size },
                        )
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            contentPadding = PaddingValues(start = 20.dp, end = 136.dp),
                            pageSize = PageSize.Fixed(278.dp),
                        ) { page ->
                            TryTheseCard(
                                song = tryTheseSongs[page],
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.playOnline(tryTheseSongs[page])
                                    }
                                },
                            )
                        }
                    }
                }

                // ═══ 2. Featured Songs ════════════════════════════════════════
                if (featuredSongs.isNotEmpty()) {
                    item("featured_title") {
                        SectionTitle("Featured Songs")
                    }
                    item("featured_carousel") {
                        val rowState = rememberLazyListState()
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(featuredSongs, key = { it.title + (it.videoId ?: "") }) { item ->
                                FeaturedSongCard(item = item, onClick = {
                                    item.videoId?.let {
                                        scope.launch(Dispatchers.IO) {
                                            MediaController.playOnline(item.toYTSongItem())
                                        }
                                    }
                                })
                            }
                        }
                    }
                }

                // ═══ 3. Recently Played ═══════════════════════════════════════
                if (!recentLoading || recentlyPlayed.isNotEmpty()) {
                    item("recent_title") { SectionTitle("Recently Played") }
                    if (recentlyPlayed.isNotEmpty()) {
                        item("recent_list") {
                            val rowState = rememberLazyListState()
                            LazyRow(
                                state = rowState,
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(recentlyPlayed, key = { it.videoId }) { entry: HistoryEntry ->
                                    val song = entry.toYTSongItem()
                                    SongCard(song = song, onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            when (entry.source) {
                                                PlaybackSource.LOCAL -> {
                                                    // Find local song by mediaId and play via prepare
                                                    val localSong = MediaController.mainMusicList
                                                        .find { it.mediaId == entry.videoId }
                                                    localSong?.let {
                                                        MediaController.prepare(it, listOf(it))
                                                    }
                                                }
                                                PlaybackSource.ONLINE -> {
                                                    MediaController.playOnline(song)
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } else {
                        item("recent_empty") { SectionMessage("No recently played songs.") }
                    }
                }

                // ═══ 4. Related Songs ═════════════════════════════════════════
                item("related_title") { SectionTitle("Because You Recently Listened") }
                if (relatedSongs.isNotEmpty()) {
                    item("related_list") {
                        val rowState = rememberLazyListState()
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                                items(relatedSongs, key = { it.videoId }) { song: YTSongItem ->
                                    SongCard(song = song, onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            MediaController.playOnline(song)
                                        }
                                    })
                                }
                        }
                    }
                } else {
                    item("related_empty") { SectionMessage("Play some music to receive recommendations.") }
                }

                // ═══ 5. For You (original sections, working album backend) ═════════
                sections.forEach { section ->
                    item("foryou_header_${section.title}") {
                        SectionTitle(section.title)
                    }
                    item("foryou_carousel_${section.title}") {
                        val rowState = rememberLazyListState()
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.items, key = { it.title + (it.videoId ?: it.browseId ?: "") }) { item ->
                                HomeCard(item = item, onClick = {
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
                                                MediaController.playOnline(item.toYTSongItem())
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }

                // ═══ 6. Curated Songs (4—N horizontal pager) ═══════════════════
                if (curatedSongs.isNotEmpty()) {
                    item("curated_title") { SectionTitle("Curated Songs") }
                    item("curated_pager") {
                        CuratedSongsPager(songs = curatedSongs, onClick = { song ->
                            song.videoId?.let {
                                scope.launch(Dispatchers.IO) { MediaController.playOnline(song.toYTSongItem()) }
                            }
                        })
                    }
                }
            }

            // ─── Empty state (only when nothing else shown) ─────────────────
            if (sections.isEmpty() && !isLoading && !loadError) {
                item("empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
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
}

}

// ═══════════════════════════════════════════════════════════════════════════
//  Home feed disk cache
// ═══════════════════════════════════════════════════════════════════════════
// The home feed JSON is cached on disk so a cold start or a failed/timed-out
// request still renders the last-known feed instantly instead of a spinner.

private const val HOME_FEED_CACHE_FILE = "home_feed.json"

private fun writeHomeCache(context: Context, jsonText: String) {
    try {
        File(context.cacheDir, HOME_FEED_CACHE_FILE).writeText(jsonText)
    } catch (_: Exception) {
        // cache write failures are non-fatal
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

// ═══════════════════════════════════════════════════════════════════════════
//  Reusable composables
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(text: String) {
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
private fun SectionMessage(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        fontFamily = SfProFontFamily,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// ─── Featured Song Card (large artwork) ────────────────────────────────────

@Composable
private fun FeaturedSongCard(
    item: HomeItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            CachedArtworkImage(
                url = item.thumbnailUrl.toHighResThumbnail(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 19.sp,
                    fontWeight = userFontWeight(),
                    fontFamily = SfProFontFamily,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.artists.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.artists.joinToString(", ") { it.name },
                        fontSize = 14.sp,
                        fontFamily = SfProFontFamily,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.album?.name?.let { albumName ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = albumName,
                        fontSize = 12.sp,
                        fontFamily = SfProFontFamily,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Song Card (medium, for Recently Played / Related) ─────────────────────

@Composable
private fun SongCard(
    song: YTSongItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CachedArtworkImage(
            url = song.thumbnailUrl.toHighResThumbnail(),
            contentDescription = null,
            size = 300,
            modifier = Modifier
                .width(150.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = song.title,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = SfProFontFamily,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        if (song.artists.isNotEmpty()) {
            Text(
                text = song.artists.joinToString(", ") { it.name },
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

// ─── Try These Card (random from library, pager item) ───────────────────────

@Composable
private fun TryTheseCard(
    song: YTSongItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(278.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .width(278.dp)
                .height(278.dp)
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
                    text = song.title,
                    fontSize = 18.sp,
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
                        fontSize = 13.sp,
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

// ─── Curated Songs 4—N pager ───────────────────────────────────────────────

@Composable
private fun CuratedSongsPager(
    songs: List<HomeItem>,
    onClick: (HomeItem) -> Unit,
) {
    val columns = songs.chunked(4)
    val columnsPerPage = 4
    val pages = columns.chunked(columnsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { page ->
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pages[page].forEach { columnSongs ->
                Column(
                    modifier = Modifier.width(100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    columnSongs.forEach { song ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onClick(song) })
                        ) {
                            Column {
                CachedArtworkImage(
                    url = song.thumbnailUrl.toHighResThumbnail(),
                    contentDescription = null,
                    size = 200,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                                Text(
                                    text = song.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = SfProFontFamily,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                if (song.artists.isNotEmpty()) {
                                    Text(
                                        text = song.artists.joinToString(", ") { it.name },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontFamily = SfProFontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Original Home card (For You section) ──────────────────────────────────

@Composable
private fun HomeCard(
    item: HomeItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
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
