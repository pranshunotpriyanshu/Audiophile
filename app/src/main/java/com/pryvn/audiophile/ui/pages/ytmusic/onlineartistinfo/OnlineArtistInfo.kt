package com.pryvn.audiophile.ui.pages.ytmusic.onlineartistinfo

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.innertube.models.AlbumItem
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.pages.ArtistPageData
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.pages.library.artists.ArtistHeroActionButton
import com.pryvn.audiophile.ui.pages.library.artists.ArtistHeroNameText
import com.pryvn.audiophile.ui.pages.library.artists.ArtistOverflowMenu
import com.pryvn.audiophile.ui.pages.library.artists.ArtistSectionHeader
import com.pryvn.audiophile.ui.pages.library.artists.ArtistTopBar
import com.pryvn.audiophile.ui.theme.isAudiophileInDarkMode
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.darken
import com.pryvn.audiophile.ui.widgets.basic.rememberArtworkDominantColor

@Composable
fun OnlineArtistInfo(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()

    val artistPageState = remember { mutableStateOf<ArtistPageData?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val browseId = LibraryObject.getTargetBrowseId()

    LaunchedEffect(browseId) {
        isLoading.value = true
        errorMessage.value = null
        try {
            val result = YouTubeApi.artistPage(browseId)
            result.fold(
                onSuccess = { data -> artistPageState.value = data },
                onFailure = { e -> errorMessage.value = e.message },
            )
        } catch (e: Exception) {
            errorMessage.value = e.message
        } finally {
            isLoading.value = false
        }
    }

    val data = artistPageState.value
    val header = data?.header
    val topSongs = data?.topSongs
    val essentialAlbums = data?.essentialAlbums
    val singlesEPs = data?.singlesEPs

    val heroColor = rememberArtworkDominantColor(header?.thumbnail)
    val isNight = isAudiophileInDarkMode()
    val screenBg = if (isNight) {
        heroColor.darken(0.78f)
    } else {
        lerp(heroColor, Color.White, 0.87f)
    }

    val fullSongItemsState = remember { mutableStateOf<List<SongItem>?>(null) }

    val shelfSongs = remember(topSongs) {
        ListeningHistory.rankByListeningHistory(topSongs.orEmpty()) { it.id }
    }

    suspend fun fullCatalogueSongs(): List<SongItem> {
        fullSongItemsState.value?.let { return it }
        val ranked = ListeningHistory.rankByListeningHistory(
            runCatching { YouTubeApi.allArtistSongs(browseId).getOrThrow() }
                .getOrElse { shelfSongs },
        ) { it.id }
        fullSongItemsState.value = ranked
        return ranked
    }

    LaunchedEffect(browseId) {
        fullCatalogueSongs()
    }

    val allSongItems = fullSongItemsState.value ?: shelfSongs
    val yosTopSongs = remember(allSongItems) {
        allSongItems.map { it.toYosMediaItem() }
    }
    val previewSongs = allSongItems.take(5)

    val isFollowed by remember(header?.title, SettingsLibrary.FollowedArtists) {
        derivedStateOf {
            SettingsLibrary.isArtistFollowed(header?.title ?: "")
        }
    }

    val overflowSheetOpen = remember { mutableStateOf(false) }
    val overflowButtonPosition = remember { mutableStateOf(Offset.Zero) }

    val playAll: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val all = fullCatalogueSongs().map { it.toYosMediaItem() }
            val first = all.firstOrNull() ?: return@launch
            MediaController.prepare(first, all)
        }
    }
    val shuffleAll: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val all = fullCatalogueSongs().map { it.toYosMediaItem() }
            val random = all.randomOrNull() ?: return@launch
            MediaController.prepare(random, all, shuffleModeEnabled = true)
        }
    }

    ArtistOverflowMenu(
        expandedLambda = { overflowSheetOpen.value },
        expandedOnChanged = { overflowSheetOpen.value = it },
        buttonPosition = overflowButtonPosition.value,
        artistName = header?.title ?: "",
        songs = yosTopSongs,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg),
    ) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
        ) {
            if (!isLoading.value && header != null) {
                item("oa_header") {
                    OnlineArtistHero(
                        artistName = header.title ?: "",
                        subtitle = header.subtitle,
                        heroArtwork = header.thumbnail,
                        heroColor = heroColor,
                        screenBg = screenBg,
                        playEnabled = yosTopSongs.isNotEmpty(),
                        onPlay = playAll,
                        onShuffle = shuffleAll,
                    )
                }
            }

            if (isLoading.value) {
                item("oa_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppleLoadingSpinner()
                    }
                }
            }

            if (errorMessage.value != null) {
                item("oa_error") {
                    Text(
                        text = "Error: ${errorMessage.value}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Red,
                    )
                }
            }

            if (!isLoading.value && errorMessage.value == null && header != null) {
                if (allSongItems.isNotEmpty()) {
                    item("oa_songs_header") {
                        ArtistSectionHeader(
                            title = stringResource(id = R.string.page_library_artist_top_songs),
                            onMore = {
                                LibraryObject.setTargetArtistName(header.title)
                                navController.navigate("${UI.OnlineArtistSongs}/$browseId")
                            },
                        )
                    }
                    itemsIndexed(
                        previewSongs,
                        key = { index, song -> "oa_song_${song.id}_$index" },
                        contentType = { _, _ -> "oa_song" },
                    ) { index, song ->
                        OnlineSongRow(song = song, onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                MediaController.prepare(song.toYosMediaItem(), yosTopSongs)
                            }
                        })

                        if (index < minOf(allSongItems.size - 1, 4)) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 88.dp)
                                    .alpha(0.15f)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White),
                            )
                        }
                    }
                }

                if (singlesEPs != null && singlesEPs.isNotEmpty()) {
                    item("oa_singles_header") {
                        ArtistSectionHeader(
                            title = stringResource(id = R.string.page_library_artist_singles),
                            onMore = {
                                LibraryObject.setTargetArtistName(header.title)
                                navController.navigate("${UI.OnlineArtistSingles}/$browseId")
                            },
                        )
                    }
                    item("oa_singles_row") {
                        OnlineAlbumRow(albums = singlesEPs) { album ->
                            navController.navigate("${UI.OnlineAlbumInfo}/${album.browseId}")
                        }
                    }
                }

                if (essentialAlbums != null && essentialAlbums.isNotEmpty()) {
                    item("oa_albums_header") {
                        ArtistSectionHeader(
                            title = stringResource(id = R.string.page_library_albums),
                            onMore = {
                                LibraryObject.setTargetArtistName(header.title)
                                navController.navigate("${UI.OnlineArtistAlbums}/$browseId")
                            },
                        )
                    }
                    item("oa_albums_row") {
                        OnlineAlbumRow(albums = essentialAlbums) { album ->
                            navController.navigate("${UI.OnlineAlbumInfo}/${album.browseId}")
                        }
                    }
                }

                item("oa_bottom_inset") {
                    Column {
                        Spacer(modifier = Modifier.height(150.dp))
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }

        ArtistTopBar(
            isFollowed = isFollowed,
            onBack = {
                navController.popBackStack()
            },
            onFollow = {
                header?.title?.let { SettingsLibrary.toggleArtistFollowed(it) }
            },
            onMore = {
                overflowSheetOpen.value = true
            },
            onMorePositioned = {
                overflowButtonPosition.value = it
            },
            followEnabled = header != null,
        )
    }
}

@Composable
private fun OnlineArtistHero(
    artistName: String,
    subtitle: String?,
    heroArtwork: String?,
    heroColor: Color,
    screenBg: Color,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp.dp * 0.60f).coerceIn(460.dp, 620.dp)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(heroArtwork)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .placeholder(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .allowHardware(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isAudiophileInDarkMode()) {
                            listOf(
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.05f),
                                heroColor.darken(0.28f),
                                heroColor.darken(0.52f),
                                screenBg,
                            )
                        } else {
                            listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent,
                                heroColor.copy(alpha = 0.16f),
                                lerp(heroColor, Color.White, 0.55f),
                                screenBg,
                            )
                        },
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            ArtistHeroNameText(artistName = artistName)

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = if (isAudiophileInDarkMode()) {
                        Color.White.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    },
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ArtistHeroActionButton(
                    painter = painterResource(id = R.drawable.button_icon_play),
                    contentDescription = stringResource(id = R.string.normal_button_play),
                    enabled = playEnabled,
                    onClick = onPlay,
                )
                ArtistHeroActionButton(
                    painter = painterResource(id = R.drawable.button_icon_shuffle),
                    contentDescription = stringResource(id = R.string.normal_button_shuffle),
                    enabled = playEnabled,
                    onClick = onShuffle,
                )
            }
        }
    }
}

@Composable
private fun OnlineSongRow(
    song: SongItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.thumbnail)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .placeholder(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp)),
        )

        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = song.album?.name ?: song.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown Artist" },
                modifier = Modifier.alpha(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 13.sp,
            )
        }
    }
}

@Composable
private fun OnlineAlbumRow(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        items(albums, key = { it.browseId }) { album ->
            OnlineAlbumCard(
                album = album,
                onClick = {
                    onAlbumClick(album)
                },
            )
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.thumbnail)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .placeholder(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .build(),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(10.dp)),
        )

        Text(
            text = album.title,
            fontSize = 15.sp,
            maxLines = 2,
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(0.94f),
        )

        album.artists?.firstOrNull()?.name?.let { artistName ->
            Text(
                text = artistName,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

private fun SongItem.toYosMediaItem(): YosMediaItem {
    return YosMediaItem(
        uri = Uri.parse("ytmusic://$id"),
        mediaId = id,
        title = title,
        artists = artists.joinToString(", ") { it.name },
        album = album?.name,
        thumb = thumbnail?.let { Uri.parse(it) },
        duration = (duration ?: 0) * 1000L,
    )
}
