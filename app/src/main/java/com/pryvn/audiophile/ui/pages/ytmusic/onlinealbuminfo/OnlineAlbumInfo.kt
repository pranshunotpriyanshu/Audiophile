package com.pryvn.audiophile.ui.pages.ytmusic.onlinealbuminfo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.innertube.YouTube
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.pages.AlbumPage
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsList
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.theme.userFontWeight
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImage
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import com.pryvn.audiophile.ui.widgets.effects.ShadowType

@Composable
fun OnlineAlbumInfo(navController: NavController, browseIdArg: String? = null) {
    Box(
        Modifier
            .fillMaxSize()
    ) {
        val browseId = rememberSaveable(key = "OnlineAlbumInfo_browseId") {
            mutableStateOf(browseIdArg?.takeIf { it.isNotBlank() } ?: LibraryObject.getTargetBrowseId())
        }

        val hideMusic = remember("OnlineAlbumInfo_showMusic") {
            derivedStateOf {
                browseId.value.isEmpty()
            }
        }
        if (hideMusic.value) {
            val message = stringResource(id = R.string.tip_no_album_info)
            Title(
                title = stringResource(id = R.string.page_library_album_info_title), onBack = {
                    navController.popBackStack()
                }
            ) {
                item("tip_no_song") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(text = message, fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                    }
                }
            }
        } else {
            val albumState = remember { mutableStateOf<AlbumPage?>(null) }
            val songsState = remember { mutableStateOf<List<YosMediaItem>>(emptyList()) }
            val isLoading = remember { mutableStateOf(true) }
            val loadFailed = remember { mutableStateOf(false) }
            val songsContinuation = remember { mutableStateOf<String?>(null) }
            val isLoadingMore = remember { mutableStateOf(false) }

            LaunchedEffect(browseId.value) {
                isLoading.value = true
                loadFailed.value = false
                songsState.value = emptyList()
                songsContinuation.value = null

                // Lazy path: album metadata + the first song batch render
                // immediately; the remaining songs stream in via continuation as
                // the user scrolls. No stream URLs are resolved here.
                var page = YouTube.albumFirstPage(browseId.value).getOrNull()
                var firstBatch: List<SongItem>? = null
                var firstContinuation: String? = null

                if (page != null) {
                    if (page.songs.isNotEmpty() || page.songsContinuation != null) {
                        // The album browse itself carries the tracklist.
                        firstBatch = page.songs
                        firstContinuation = page.songsContinuation
                    } else {
                        // No inline shelf — lazily pull the first batch from the
                        // album's playlist browse instead of blocking on the full
                        // album (still lazy: batch + continuation, not everything).
                        page.album.playlistId?.let { playlistId ->
                            val songsPage = YouTube.albumSongsFirstPage(playlistId, page.album).getOrNull()
                            if (songsPage != null) {
                                firstBatch = songsPage.songs
                                firstContinuation = songsPage.continuation
                            }
                        }
                    }
                } else {
                    // Total failure of the first-page parse — last-resort fallback
                    // to the old full loader so the screen still shows the album.
                    page = YouTube.album(browseId.value).getOrNull()
                    firstBatch = page?.songs
                    firstContinuation = null
                }

                if (page != null) {
                    albumState.value = page
                    songsState.value = (firstBatch ?: emptyList()).mapIndexed { index, song ->
                        song.toAlbumYosMediaItem(index + 1)
                    }
                    songsContinuation.value = firstContinuation
                } else {
                    loadFailed.value = true
                }
                isLoading.value = false
            }

            val page = albumState.value
            val songs = songsState.value

            if (isLoading.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Box
            }
            if (page == null || loadFailed.value) {
                Title(
                    title = stringResource(id = R.string.page_library_album_info_title), onBack = {
                        navController.popBackStack()
                    }
                ) {
                    item("load_failed") {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                        ) {
                            Text(text = "Unable to load album info", fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                        }
                    }
                }
                return@Box
            }

            val state = rememberLazyListState()
            val density = LocalDensity.current
            val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val navBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

            val mainArtists = remember(page) {
                page.album.artists?.map { it.name } ?: emptyList()
            }
            val mainArtistsName = remember(page) {
                page.album.artists?.joinToString(", ") { it.name } ?: ""
            }

            val (songCount, totalMinutes) = remember(songs) {
                val totalDuration = songs.sumOf { it.duration }
                val totalMinutes = totalDuration / 60000
                val songCount = songs.size
                songCount to totalMinutes
            }

            val scope = rememberCoroutineScope()

            // Progressive loading: request the next continuation page as the user
            // scrolls near the end of the currently loaded songs.
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= info.totalItemsCount - 8
                }
            }
            LaunchedEffect(shouldLoadMore, songsContinuation.value) {
                if (!shouldLoadMore) return@LaunchedEffect
                val continuation = songsContinuation.value ?: return@LaunchedEffect
                if (isLoadingMore.value) return@LaunchedEffect
                isLoadingMore.value = true
                val next = YouTube.albumSongsNext(continuation, page.album).getOrNull()
                if (next != null && next.songs.isNotEmpty()) {
                    val startIndex = songsState.value.size
                    songsState.value = songsState.value + next.songs.mapIndexed { index, song ->
                        song.toAlbumYosMediaItem(startIndex + index + 1)
                    }
                    songsContinuation.value = next.continuation
                } else {
                    songsContinuation.value = null
                }
                isLoadingMore.value = false
            }

            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical(),
                flingBehavior = rememberOverscrollFlingBehavior { state },
                contentPadding = PaddingValues(bottom = 18.dp, top = 54.dp)
            ) {
                item("OnlineAlbumInfo") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 9.5.dp)
                            .padding(horizontal = 18.dp)
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ShadowImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 54.5.dp),
                            dataLambda = { page.album.thumbnail },
                            contentDescription = null,
                            cornerRadius = 7.dp,
                            imageQuality = ImageQuality.RAW,
                            shadowType = ShadowType.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = page.album.title,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp,
fontWeight = headingFontWeight()
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = mainArtistsName,
                            fontSize = 17.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 23.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "ALBUM",
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .alpha(0.4f)
                                .padding(top = 2.dp)
                        )

                        YosWrapper {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 15.dp)
                            ) {

                                NormalButton(
                                    icon = painterResource(id = R.drawable.button_icon_play),
                                    label = stringResource(id = R.string.normal_button_play),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch(Dispatchers.IO) {
                                        val first = songs.firstOrNull() ?: return@launch
                                        MediaController.prepare(
                                            first,
                                            songs
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(15.dp))
                                NormalButton(
                                    icon = painterResource(id = R.drawable.button_icon_shuffle),
                                    label = stringResource(id = R.string.normal_button_shuffle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch(Dispatchers.IO) {
                                        val random = songs.randomOrNull() ?: return@launch
                                        MediaController.prepare(
                                            random,
                                            songs,
                                            shuffleModeEnabled = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    AlbumDivider()
                }

                itemsIndexed(
                    songs,
                    key = { _, music -> music }
                ) { index, music ->
                    key(music) {
                        AlbumSongsItem(
                            music = music,
                            mainArtists = mainArtists
                        ) {
                            scope.launch(Dispatchers.IO) {
                                MediaController.prepare(
                                    music,
                                    songs
                                )
                            }
                        }
                    }

                    key(index) {
                        val needDivider = index < songs.size - 1
                        if (needDivider) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 50.dp, end = 18.dp)
                                    .alpha(0.25f)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White)
                            )
                        }
                    }
                }

                if (songsContinuation.value != null || isLoadingMore.value) {
                    item("oa_album_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        }
                    }
                }

                item {
                    AlbumDivider()
                }

                item("OnlineAlbumInfo_others") {
                    Text(
                        text = stringResource(
                            id = R.string.page_library_album_info_others,
                            songCount,
                            totalMinutes
                        ), fontSize = 15.sp, modifier = Modifier
                            .alpha(0.4f)
                            .padding(horizontal = 18.dp)
                            .padding(top = 18.dp)
                    )
                }

                item("navbar") {
                    Spacer(modifier = Modifier.height(navBarHeight + 134.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight + 54.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                ) {
                    Box(
                        Modifier.height(statusBarHeight + 48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = null,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 10.dp)
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        navController.popBackStack()
                                    }
                                ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NormalButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = userFontWeight(),
            fontSize = 17.sp
        )
    }
}

@Composable
private fun AlbumDivider(modifier: Modifier = Modifier) =
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .alpha(0.2f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White)
    )

@Composable
private fun AlbumSongsItem(
    modifier: Modifier = Modifier,
    music: YosMediaItem,
    mainArtists: List<String>,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.TopCenter, modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            Text(
                text = "${music.trackNumber?:"-"}",
                fontSize = 16.sp,
                modifier = Modifier.alpha(0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                text = music.title ?: defaultTitle,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            YosWrapper {
                val needShowArtists = remember(music) {
                    derivedStateOf {
                        !mainArtists.containsAll(music.artistsList ?: emptyList())
                    }
                }
                if (needShowArtists.value) {
                    Text(
                        text = music.artistsName ?: "",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                        modifier = Modifier.alpha(0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Converts an album song to a playable item WITHOUT resolving any stream URL:
 * the existing online playback path resolves the ytmusic:// URI lazily when the
 * song actually needs to play.
 */
private fun SongItem.toAlbumYosMediaItem(trackNumber: Int): YosMediaItem {
    return YosMediaItem(
        uri = Uri.parse("ytmusic://$id"),
        mediaId = id,
        title = title,
        artists = artists.joinToString(", ") { it.name },
        album = album?.name,
        thumb = thumbnail?.let { Uri.parse(it) },
        trackNumber = trackNumber,
        duration = (duration ?: 0) * 1000L,
    )
}
