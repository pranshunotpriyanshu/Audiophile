package com.pryvn.audiophile.ui.pages.ytmusic.onlineartistinfo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.innertube.models.AlbumItem
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.pages.ArtistPageData
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultArtistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.pages.library.playlists.PlayListSearch
import com.pryvn.audiophile.ui.widgets.basic.SearchTextField
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.TitleWithLazyVerticalGrid

@Composable
fun OnlineArtistSongs(navController: NavController, browseId: String) {
    val songsState = remember { mutableStateOf<List<SongItem>?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(browseId) {
        isLoading.value = true
        try {
            val result = YouTubeApi.allArtistSongs(browseId)
            result.fold(
                onSuccess = { songs ->
                    songsState.value = ListeningHistory.rankByListeningHistory(songs) { it.id }
                },
                onFailure = { songsState.value = emptyList() },
            )
        } catch (e: Exception) {
            songsState.value = emptyList()
        } finally {
            isLoading.value = false
        }
    }

    val songs = songsState.value ?: emptyList()
    val yosSongs = remember(songs) { songs.map { it.toYosMediaItem() } }

    val searchText = rememberSaveable { mutableStateOf("") }
    val requestFocusSignal = rememberSaveable {
        mutableIntStateOf(if (LibraryObject.consumeArtistSongsSearchOnOpen()) 1 else 0)
    }
    val displayedSongs = remember { mutableStateOf(yosSongs) }

    LaunchedEffect(yosSongs, searchText.value) {
        if (searchText.value.isNotBlank()) {
            delay(150)
        }

        withContext(Dispatchers.Default) {
            displayedSongs.value = if (searchText.value.isBlank()) {
                yosSongs
            } else {
                PlayListSearch.matchAndRank(yosSongs, searchText.value)
            }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    Title(
        title = LibraryObject.getTargetArtistName() ?: "",
        subTitle = stringResource(id = R.string.page_library_songs),
        onBack = { navController.popBackStack() },
    ) {
        item("oa_songs_search") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchTextField(
                    text = searchText.value,
                    placeholder = stringResource(id = R.string.page_library_search_songs),
                    onValueChange = {
                        searchText.value = it
                    },
                    modifier = Modifier.weight(1f),
                    onSearch = {
                        if (searchText.value.isNotEmpty()) {
                            keyboardController?.hide()
                        }
                    },
                    requestFocusSignal = requestFocusSignal.intValue,
                    onClear = {
                        searchText.value = ""
                    },
                )
            }
        }

        if (isLoading.value) {
            item("oa_songs_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppleLoadingSpinner()
                }
            }
        } else if (displayedSongs.value.isEmpty()) {
            item("oa_songs_empty") {
                Text(
                    text = stringResource(id = R.string.tip_no_song),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .alpha(0.6f),
                )
            }
        } else {
            itemsIndexed(
                displayedSongs.value,
                key = { index, music -> "oa_song_${music.mediaId ?: music.uri}_$index" },
                contentType = { _, _ -> "oa_song" },
            ) { index, music ->
                OnlineArtistSongRow(music = music) {
                    scope.launch(Dispatchers.IO) {
                        MediaController.prepare(music, displayedSongs.value)
                    }
                }

                if (index < displayedSongs.value.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 78.dp)
                            .alpha(0.15f)
                            .height(0.5.dp)
                            .background(Color.Black),
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineArtistSingles(navController: NavController, browseId: String) {
    OnlineArtistReleasesGrid(
        navController = navController,
        browseId = browseId,
        title = stringResource(id = R.string.page_library_artist_singles),
        picker = { it.singlesEPs },
    )
}

@Composable
fun OnlineArtistAlbums(navController: NavController, browseId: String) {
    OnlineArtistReleasesGrid(
        navController = navController,
        browseId = browseId,
        title = stringResource(id = R.string.page_library_albums),
        picker = { it.essentialAlbums },
    )
}

@Composable
private fun OnlineArtistReleasesGrid(
    navController: NavController,
    browseId: String,
    title: String,
    picker: (ArtistPageData) -> List<AlbumItem>,
) {
    val releasesState = remember { mutableStateOf<List<AlbumItem>?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(browseId) {
        isLoading.value = true
        try {
            val result = YouTubeApi.artistPage(browseId)
            result.fold(
                onSuccess = { data -> releasesState.value = picker(data) },
                onFailure = { releasesState.value = emptyList() },
            )
        } catch (e: Exception) {
            releasesState.value = emptyList()
        } finally {
            isLoading.value = false
        }
    }

    val releases = releasesState.value ?: emptyList()

    if (isLoading.value) {
        Title(
            title = title,
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("oa_releases_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppleLoadingSpinner()
                }
            }
        }
        return
    }

    if (releases.isEmpty()) {
        Title(
            title = title,
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("oa_releases_empty") {
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

    TitleWithLazyVerticalGrid(
        title = title,
        onBack = {
            navController.popBackStack()
        },
    ) {
        itemsIndexed(
            releases,
            key = { _, album -> album.browseId },
        ) { _, album ->
            OnlineReleaseGridCard(album = album) {
                navController.navigate("${UI.OnlineAlbumInfo}/${album.browseId}")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlineArtistSongRow(
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
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(music.thumb)
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
                text = music.title ?: defaultTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = music.artistsName ?: defaultArtistsName,
                modifier = Modifier.alpha(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 13.sp,
            )
        }

        music.duration.takeIf { it > 0L }?.let { durationMs ->
            val sec = durationMs / 1000L
            Text(
                text = "%d:%02d".format(sec / 60, sec % 60),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    SongOverflowSheet(
        isOpen = songMenuOpen,
        song = music,
    )
}

@Composable
private fun OnlineReleaseGridCard(
    album: AlbumItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
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
                .padding(bottom = 5.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp)),
        )
        Text(
            text = album.title,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(0.92f),
        )
        album.year?.let {
            Text(
                text = it.toString(),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .alpha(0.55f),
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
