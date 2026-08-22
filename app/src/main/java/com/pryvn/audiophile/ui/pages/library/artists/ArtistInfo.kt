package com.pryvn.audiophile.ui.pages.library.artists

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.pryvn.audiophile.data.libraries.ArtistLibrary
import com.pryvn.audiophile.data.libraries.ArtistRelease
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.defaultAlbum
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.libraries.lazyListKey
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.consumeNowPlayingNavigationMarker
import com.pryvn.audiophile.ui.returnToLibraryFromNowPlaying
import com.pryvn.audiophile.ui.theme.isAudiophileInDarkMode
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.darken
import com.pryvn.audiophile.ui.widgets.basic.rememberArtworkDominantColor

@Composable
fun ArtistInfo(
    navController: NavController,
) {
    val openedFromNowPlaying = rememberSaveable(key = "ArtistInfo_openedFromNowPlaying") {
        mutableStateOf(navController.consumeNowPlayingNavigationMarker())
    }
    val handleBack: () -> Unit = {
        if (openedFromNowPlaying.value) {
            openedFromNowPlaying.value = false
            navController.returnToLibraryFromNowPlaying()
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(onBack = handleBack)

    val artistName = rememberSaveable(key = "ArtistInfo_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val artistSections = remember(artistName.value, MusicLibrary.songs) {
        ArtistLibrary.sectionsForArtist(artistName.value ?: "")
    }
    val artistSongs = artistSections.songs
    val showEmptyState = remember(artistName.value, artistSections) {
        derivedStateOf {
            artistName.value.isNullOrEmpty() ||
                (
                    artistSections.songs.isEmpty() &&
                        artistSections.albums.isEmpty() &&
                        artistSections.singlesAndEps.isEmpty() &&
                        artistSections.featuredOn.isEmpty()
                    )
        }
    }

    if (showEmptyState.value) {
        Title(
            title = stringResource(id = R.string.page_library_artists),
            onBack = handleBack,
        ) {
            item("ArtistInfo_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.tip_no_song),
                        fontSize = 18.sp,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val overflowSheetOpen = remember("ArtistInfo_overflowSheetOpen") {
        mutableStateOf(false)
    }
    val overflowButtonPosition = remember("ArtistInfo_overflowButtonPosition") {
        mutableStateOf(Offset.Zero)
    }
    val isFollowed by remember(artistName.value, SettingsLibrary.FollowedArtists) {
        derivedStateOf {
            SettingsLibrary.isArtistFollowed(artistName.value ?: "")
        }
    }

    val heroArtwork = artistSongs.firstOrNull()?.thumb
    val heroColor = rememberArtworkDominantColor(heroArtwork?.toString())
    val isNight = isAudiophileInDarkMode()
    val screenBg = if (isNight) {
        heroColor.darken(0.78f)
    } else {
        lerp(heroColor, Color.White, 0.87f)
    }

    val openArtistSongs: () -> Unit = {
        LibraryObject.setTargetArtistName(artistName.value)
        LibraryObject.setArtistSongsSearchOnOpen(false)
        navController.toUI(UI.ArtistSongs)
    }
    val openArtistSingles: () -> Unit = {
        LibraryObject.setTargetArtistName(artistName.value)
        navController.toUI(UI.ArtistSingles)
    }
    val openArtistAlbums: () -> Unit = {
        LibraryObject.setTargetArtistName(artistName.value)
        navController.toUI(UI.ArtistAlbums)
    }
    val openAlbum: (ArtistRelease) -> Unit = { release ->
        LibraryObject.setTargetAlbumName(release.albumName)
        navController.toUI(UI.AlbumInfo)
    }
    val playAll: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val first = artistSongs.firstOrNull() ?: return@launch
            MediaController.prepare(first, artistSongs)
        }
    }
    val shuffleAll: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val random = artistSongs.randomOrNull() ?: return@launch
            MediaController.prepare(
                random,
                artistSongs,
                shuffleModeEnabled = true,
            )
        }
    }

    ArtistOverflowMenu(
        expandedLambda = { overflowSheetOpen.value },
        expandedOnChanged = { overflowSheetOpen.value = it },
        buttonPosition = overflowButtonPosition.value,
        artistName = artistName.value ?: "",
        songs = artistSongs,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
        ) {
            item("ArtistInfo_hero") {
                ArtistHero(
                    artistName = artistName.value ?: "",
                    heroArtwork = heroArtwork,
                    heroColor = heroColor,
                    screenBg = screenBg,
                    playEnabled = artistSongs.isNotEmpty(),
                    onPlay = playAll,
                    onShuffle = shuffleAll,
                )
            }

            item("ArtistInfo_top_songs_header") {
                ArtistSectionHeader(
                    title = stringResource(id = R.string.page_library_artist_top_songs),
                    onMore = openArtistSongs,
                )
            }

            itemsIndexed(
                artistSongs.take(5),
                key = { index, music -> music.lazyListKey(index) },
                contentType = { _, _ -> "ArtistInfo_song" },
            ) { index, music ->
                ArtistSongRow(
                    music = music,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(music, artistSongs)
                        }
                    },
                )

                if (index < minOf(artistSongs.lastIndex, 4)) {
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

            if (artistSections.singlesAndEps.isNotEmpty()) {
                item("ArtistInfo_singles_header") {
                    ArtistSectionHeader(
                        title = stringResource(id = R.string.page_library_artist_singles),
                        onMore = openArtistSingles,
                    )
                }
                item("ArtistInfo_singles_row") {
                    ArtistReleaseRow(
                        releases = artistSections.singlesAndEps,
                        onAlbumClick = openAlbum,
                    )
                }
            }

            if (artistSections.albums.isNotEmpty()) {
                item("ArtistInfo_albums_header") {
                    ArtistSectionHeader(
                        title = stringResource(id = R.string.page_library_albums),
                        onMore = openArtistAlbums,
                    )
                }
                item("ArtistInfo_albums_row") {
                    ArtistReleaseRow(
                        releases = artistSections.albums,
                        onAlbumClick = openAlbum,
                    )
                }
            }

            if (artistSections.featuredOn.isNotEmpty()) {
                item("ArtistInfo_featured_header") {
                    ArtistSectionHeader(title = stringResource(id = R.string.page_library_artist_featured_on))
                }
                item("ArtistInfo_featured_row") {
                    ArtistReleaseRow(
                        releases = artistSections.featuredOn,
                        onAlbumClick = openAlbum,
                    )
                }
            }

            item("ArtistInfo_bottom_inset") {
                Column {
                    Spacer(modifier = Modifier.height(150.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }

        ArtistTopBar(
            isFollowed = isFollowed,
            onBack = handleBack,
            onFollow = {
                SettingsLibrary.toggleArtistFollowed(artistName.value ?: "")
            },
            onMore = {
                overflowSheetOpen.value = true
            },
            onMorePositioned = {
                overflowButtonPosition.value = it
            },
        )
    }
}

@Composable
private fun ArtistHero(
    artistName: String,
    heroArtwork: Uri?,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistSongRow(
    music: YosMediaItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val songMenuOpen = remember(music.uri, music.mediaId) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    Vibrator.longClick(context)
                    songMenuOpen.value = true
                },
            )
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowImageWithCache(
            dataLambda = { music.thumb },
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            cornerRadius = 4.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.LOW,
        )

        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = music.title ?: defaultTitle,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ArtistSongSubtitle(music),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(0.55f),
            )
        }
    }

    SongOverflowSheet(
        isOpen = songMenuOpen,
        song = music,
    )
}

private fun ArtistSongSubtitle(music: YosMediaItem): String {
    val album = music.album ?: defaultAlbum
    val year = music.releaseYear ?: music.recordingYear
    return if (year != null) "$album · $year" else album
}

@Composable
private fun ArtistReleaseRow(
    releases: List<ArtistRelease>,
    onAlbumClick: (ArtistRelease) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        items(releases, key = { it.albumName }) { release ->
            ArtistReleaseCard(
                release = release,
                onClick = {
                    onAlbumClick(release)
                },
            )
        }
    }
}

@Composable
private fun ArtistReleaseCard(
    release: ArtistRelease,
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
        ShadowImageWithCache(
            dataLambda = { release.songs.firstOrNull()?.thumb },
            contentDescription = release.albumName,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 10.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.HIGH,
        )

        Text(
            text = release.albumName,
            fontSize = 15.sp,
            maxLines = 2,
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(0.94f),
        )

        release.releaseYear?.let {
            Text(
                text = it.toString(),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .alpha(0.56f),
            )
        }
    }
}
