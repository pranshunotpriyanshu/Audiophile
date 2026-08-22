package com.pryvn.audiophile.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.navigation.NavController
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YTArtist
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.data.libraries.HistoryEntry
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.PlaybackSource
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.toHighResThumbnail
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.screenTitleFontWeight
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun HistoryEntry.toYTSongItem(): YTSongItem = YTSongItem(
    videoId = videoId,
    title = title,
    artists = artists?.split(", ")?.map { YTArtist(name = it) } ?: emptyList(),
    thumbnailUrl = thumbnailUrl,
)

private fun YTSongItem.toYosMediaItem(): YosMediaItem = YosMediaItem(
    uri = Uri.parse("ytmusic://$videoId"),
    mediaId = videoId,
    title = title,
    artists = artists.joinToString(", ") { it.name },
    thumb = thumbnailUrl?.let { Uri.parse(it) },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentlyPlayed(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val history by ListeningHistory.history.collectAsState()
    val songMenuOpen = remember { mutableStateOf(false) }
    val menuSong = remember { mutableStateOf<YosMediaItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, top = 54.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Recently Played",
                fontSize = 30.sp,
                fontWeight = screenTitleFontWeight(),
                fontFamily = SfProFontFamily,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recently played songs.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontFamily = SfProFontFamily,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history, key = { it.videoId }) { entry: HistoryEntry ->
                    val song = entry.toYTSongItem()
                    Column(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        when (entry.source) {
                                            PlaybackSource.LOCAL -> {
                                                val localSong = MediaController.mainMusicList
                                                    .find { it.mediaId == entry.videoId }
                                                localSong?.let {
                                                    val queue = buildList {
                                                        add(it)
                                                        history.asSequence()
                                                            .filter { h -> h.videoId != entry.videoId }
                                                            .mapNotNull { h ->
                                                                MediaController.mainMusicList
                                                                    .find { l -> l.mediaId == h.videoId }
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
                                }
                            )
                    ) {
                        CachedArtworkImage(
                            url = song.thumbnailUrl.toHighResThumbnail(),
                            contentDescription = null,
                            size = 300,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
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
            }
        }
    }

    SongOverflowSheet(
        isOpen = songMenuOpen,
        song = menuSong.value,
        navController = navController,
    )
}
