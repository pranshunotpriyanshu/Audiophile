package com.pryvn.audiophile.ui.pages.ytmusic.onlinealbuminfo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.code.api.innertube.YouTube
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.pages.AlbumPage
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsList
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.theme.userFontWeight
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImage
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import com.pryvn.audiophile.ui.widgets.basic.rememberArtworkDominantColor
import com.pryvn.audiophile.ui.widgets.basic.darken
import com.pryvn.audiophile.ui.widgets.effects.ShadowType
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet

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

                var page = YouTube.albumFirstPage(browseId.value).getOrNull()
                var firstBatch: List<SongItem>? = null
                var firstContinuation: String? = null

                if (page != null) {
                    if (page.songs.isNotEmpty() || page.songsContinuation != null) {
                        firstBatch = page.songs
                        firstContinuation = page.songsContinuation
                    } else {
                        page.album.playlistId?.let { playlistId ->
                            val songsPage = YouTube.albumSongsFirstPage(playlistId, page.album).getOrNull()
                            if (songsPage != null) {
                                firstBatch = songsPage.songs
                                firstContinuation = songsPage.continuation
                            }
                        }
                    }
                } else {
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
                    AppleLoadingSpinner()
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

            val albumThumbUrl = remember(page) { page.album.thumbnail }
            val dominantColor = rememberArtworkDominantColor(url = albumThumbUrl)

            val (songCount, totalMinutes) = remember(songs) {
                val totalDuration = songs.sumOf { it.duration }
                val totalMinutes = totalDuration / 60000
                val songCount = songs.size
                songCount to totalMinutes
            }

            val scope = rememberCoroutineScope()

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

            // Scroll progress for hero collapse
            val scrollProgress by remember {
                derivedStateOf {
                    val firstItem = state.layoutInfo.visibleItemsInfo.firstOrNull()
                    if (firstItem == null || firstItem.size == 0) 0f
                    else {
                        val offset = firstItem.offset.coerceAtMost(0)
                        (-offset.toFloat() / firstItem.size).coerceIn(0f, 1f)
                    }
                }
            }

            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical(),
                flingBehavior = rememberOverscrollFlingBehavior { state },
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                // ── Hero section with dominant color background ──
                item("hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        dominantColor.darken(0.45f).copy(alpha = 0.7f),
                                        dominantColor.darken(0.6f).copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.background,
                                    ),
                                )
                            )
                            .statusBarsPadding()
                            .padding(top = 54.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Large album artwork
                            ShadowImage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                dataLambda = { albumThumbUrl },
                                contentDescription = null,
                                cornerRadius = 8.dp,
                                imageQuality = ImageQuality.RAW,
                                shadowType = ShadowType.Large,
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Album title
                            Text(
                                text = page.album.title,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp,
                                fontWeight = headingFontWeight(),
                                fontFamily = SfProFontFamily,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Artist name
                            Text(
                                text = mainArtistsName,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = SfProFontFamily,
                                fontWeight = userFontWeight(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // "ALBUM" label
                            Text(
                                text = "ALBUM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = Color.White.copy(alpha = 0.45f),
                                fontFamily = SfProFontFamily,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Play / Shuffle buttons
                            YosWrapper {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AlbumActionButton(
                                        icon = painterResource(id = R.drawable.button_icon_play),
                                        label = stringResource(id = R.string.normal_button_play),
                                        modifier = Modifier.weight(1f),
                                        accent = true,
                                    ) {
                                        scope.launch(Dispatchers.IO) {
                                            val first = songs.firstOrNull() ?: return@launch
                                            MediaController.prepare(first, songs)
                                        }
                                    }
                                    AlbumActionButton(
                                        icon = painterResource(id = R.drawable.button_icon_shuffle),
                                        label = stringResource(id = R.string.normal_button_shuffle),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        scope.launch(Dispatchers.IO) {
                                            val random = songs.randomOrNull() ?: return@launch
                                            MediaController.prepare(random, songs, shuffleModeEnabled = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Track list ──
                itemsIndexed(
                    songs,
                    key = { _, music -> music }
                ) { index, music ->
                    key(music) {
                        AlbumSongsItem(
                            music = music,
                            mainArtists = mainArtists,
                            trackColor = dominantColor.darken(0.3f),
                        ) {
                            scope.launch(Dispatchers.IO) {
                                MediaController.prepare(music, songs)
                            }
                        }
                    }

                    key(index) {
                        val needDivider = index < songs.size - 1
                        if (needDivider) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 58.dp, end = 18.dp)
                                    .alpha(0.12f)
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
                            AppleLoadingSpinner()
                        }
                    }
                }

                // ── Album info footer ──
                item("album_footer") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .padding(top = 20.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.12f)
                                .height(0.5.dp)
                                .background(Color.Black withNight Color.White)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(
                                id = R.string.page_library_album_info_others,
                                songCount,
                                totalMinutes
                            ),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            fontFamily = SfProFontFamily,
                            lineHeight = 20.sp,
                        )
                    }
                }

                item("navbar") {
                    Spacer(modifier = Modifier.height(navBarHeight + 134.dp))
                }
            }

            // ── Floating back button ──
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
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    dominantColor.darken(0.55f).copy(alpha = (0.5f - scrollProgress * 0.5f).coerceAtLeast(0f))
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { navController.popBackStack() }
                                ),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumActionButton(
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.15f)
    }
    val textColor = if (accent) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }
    val iconTint = if (accent) Color.White else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .background(color = bgColor, shape = shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = textColor,
            fontWeight = userFontWeight(),
            fontSize = 16.sp,
            fontFamily = SfProFontFamily,
        )
    }
}

@Composable
fun NormalButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AlbumActionButton(icon = icon, label = label, modifier = modifier, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumSongsItem(
    modifier: Modifier = Modifier,
    music: YosMediaItem,
    mainArtists: List<String>,
    trackColor: Color = MaterialTheme.colorScheme.background,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val songMenuOpen = remember(music.uri, music.mediaId) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    Vibrator.longClick(context)
                    songMenuOpen.value = true
                },
            )
            .padding(horizontal = 18.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Box(
            contentAlignment = Alignment.TopCenter, modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
        ) {
            Text(
                text = "${music.trackNumber ?: "-"}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(0.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = SfProFontFamily,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = music.title ?: defaultTitle,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SfProFontFamily,
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
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontFamily = SfProFontFamily,
                    )
                }
            }
        }

        // Duration
        if (music.duration > 0) {
            val min = (music.duration / 60000).toInt()
            val sec = ((music.duration % 60000) / 1000).toInt()
            Text(
                text = "%d:%02d".format(min, sec),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                fontFamily = SfProFontFamily,
            )
        }
    }

    SongOverflowSheet(
        isOpen = songMenuOpen,
        song = music,
    )
}

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
