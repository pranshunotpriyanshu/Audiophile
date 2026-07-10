package com.pryvn.audiophile.ui.pages.ytmusic

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.statusBarsHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage

@Composable
fun OnlinePlaylistScreen(navController: NavController) {
    val playlistId = rememberSaveable(key = "YTMusicPlaylist_playlistId") {
        mutableStateOf(LibraryObject.getTargetPlaylistId())
    }

    val id = playlistId.value
    if (id.isEmpty()) {
        navController.popBackStack()
        return
    }

    val scope = rememberCoroutineScope()
    val pageResult = remember { mutableStateOf<com.pryvn.audiophile.code.api.YTPlaylistPage?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(id) {
        scope.launch(Dispatchers.IO) {
            val result = YouTubeApi.playlist(id)
            result.onSuccess { p ->
                pageResult.value = p
            }
            isLoading.value = false
        }
    }

    val page = pageResult.value
    if (isLoading.value || page == null) return

    val songs = page.songs
    val playlist = page.playlist

    val state = rememberLazyListState()

    val totalMinutes = remember(songs) {
        val totalSeconds = songs.sumOf { it.durationSeconds?.toLong() ?: 0L }
        (totalSeconds / 60).toInt()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { state },
            contentPadding = PaddingValues(bottom = 18.dp, top = 54.dp)
        ) {
            item("PlaylistHeader") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 9.5.dp)
                        .padding(horizontal = 18.dp)
                        .statusBarsPadding(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CachedArtworkImage(
                        url = playlist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 54.5.dp),
                        size = 640,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = playlist.title,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    if (playlist.author != null) {
                        Text(
                            text = playlist.author,
                            fontSize = 17.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 23.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text = "PLAYLIST",
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .alpha(0.4f)
                            .padding(top = 2.dp),
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 15.dp)
                    ) {
                        NormalButton(
                            icon = painterResource(id = R.drawable.button_icon_play),
                            label = stringResource(id = R.string.normal_button_play),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (songs.isNotEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    MediaController.playOnline(songs.first().videoId, songs.first().title)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(15.dp))
                        NormalButton(
                            icon = painterResource(id = R.drawable.button_icon_shuffle),
                            label = stringResource(id = R.string.normal_button_shuffle),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (songs.isNotEmpty()) {
                                val randomSong = songs.random()
                                scope.launch(Dispatchers.IO) {
                                    MediaController.playOnline(randomSong.videoId, randomSong.title)
                                }
                            }
                        }
                    }
                }
            }

            item {
                PlaylistDivider()
            }

            val typedSongs: List<com.pryvn.audiophile.code.api.YTSongItem> = songs
            itemsIndexed(
                typedSongs,
                key = { _: Int, song: com.pryvn.audiophile.code.api.YTSongItem -> song.videoId },
            ) { index, song ->
                PlaylistSongRow(index = index, song = song) {
                    scope.launch(Dispatchers.IO) {
                        MediaController.playOnline(song.videoId, song.title)
                    }
                }

                val needDivider = index < typedSongs.size - 1
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

            item {
                PlaylistDivider()
            }

            item("PlaylistInfo") {
                Text(
                    text = "${songs.size} songs • ${totalMinutes} min",
                    fontSize = 15.sp,
                    modifier = Modifier
                        .alpha(0.4f)
                        .padding(horizontal = 18.dp)
                        .padding(top = 18.dp),
                )
            }

            item("navbar") {
                Spacer(modifier = Modifier.navigationBarsHeight(134.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsHeight(54.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
            ) {
                Box(
                    Modifier.statusBarsHeight(48.dp),
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
                                onClick = { navController.popBackStack() }
                            ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NormalButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp
        )
    }
}

@Composable
private fun PlaylistDivider() =
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .alpha(0.2f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White)
    )

@Composable
private fun PlaylistSongRow(
    index: Int,
    song: YTSongItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 16.sp,
                modifier = Modifier.alpha(0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
            if (song.artists.isNotEmpty()) {
                Text(
                    text = song.artists.joinToString(", ") { it.name },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier.alpha(0.4f),
                )
            }
        }
    }
}
