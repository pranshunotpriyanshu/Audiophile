package com.pryvn.audiophile.ui.pages.library.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.data.libraries.ArtistLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.lazyListKey
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.pages.library.MusicList
import com.pryvn.audiophile.ui.pages.library.playlists.PlayListSearch
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.SearchTextField
import com.pryvn.audiophile.ui.widgets.basic.Title

@Composable
fun ArtistSongs(navController: NavController)
{
    val artistName = rememberSaveable(key = "ArtistSongs_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val artistSongs = ArtistLibrary.songsForArtist(artistName.value ?: "")
    val scope = rememberCoroutineScope()
    val searchText = rememberSaveable(artistName.value) {
        mutableStateOf("")
    }
    val requestFocusSignal = rememberSaveable(artistName.value) {
        mutableIntStateOf(if (LibraryObject.consumeArtistSongsSearchOnOpen()) 1 else 0)
    }
    val displayedSongs = remember(artistName.value) {
        mutableStateOf(artistSongs)
    }
    val showEmptyState = remember(artistName.value, artistSongs) {
        derivedStateOf {
            artistName.value.isNullOrEmpty() || artistSongs.isEmpty()
        }
    }

    if (showEmptyState.value) {
        Title(
            title = stringResource(id = R.string.page_library_songs),
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("ArtistSongs_empty") {
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

    LaunchedEffect(artistSongs, searchText.value) {
        if (searchText.value.isNotBlank()) {
            delay(150)
        }

        withContext(Dispatchers.Default) {
            displayedSongs.value = if (searchText.value.isBlank()) {
                artistSongs
            } else {
                PlayListSearch.matchAndRank(artistSongs, searchText.value)
            }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    Title(
        title = artistName.value ?: "",
        subTitle = stringResource(id = R.string.page_library_songs),
        onBack = {
            navController.popBackStack()
        },
    ) {
        item("ArtistSongs_search") {
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

        if (displayedSongs.value.isEmpty()) {
            item("ArtistSongs_noResults") {
                Text(
                    text = stringResource(id = R.string.tip_no_song),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .alpha(0.55f),
                )
            }
        } else {
            itemsIndexed(
                displayedSongs.value,
                key = { index, music -> music.lazyListKey(index) },
                contentType = { _, _ -> "ArtistSongs_song" },
            ) { index, music ->
                ArtistSongItem(
                    music = music,
                    navController = navController,
                    scope = scope,
                    onPlay = {
                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(music, displayedSongs.value)
                        }
                    },
                )

                if (index < displayedSongs.value.lastIndex) {
                    androidx.compose.foundation.layout.Spacer(
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
    }
}

@Composable
private fun ArtistSongItem(
    music: YosMediaItem,
    navController: NavController,
    onPlay: () -> Unit,
    scope: CoroutineScope,
)
{
    MusicList(
        music = music,
        onQueueSwipe = {
            scope.launch(Dispatchers.IO) {
                MediaController.addToQueue(music)
            }
        },
        navController = navController,
    ) {
        onPlay()
    }
}
