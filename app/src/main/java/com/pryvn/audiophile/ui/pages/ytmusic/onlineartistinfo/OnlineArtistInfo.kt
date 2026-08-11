package com.pryvn.audiophile.ui.pages.ytmusic.onlineartistinfo

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.innertube.models.AlbumItem
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.pages.ArtistPageData
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.theme.userFontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun OnlineArtistInfo(navController: NavController) {
    val context = LocalContext.current
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
                onFailure = { e -> errorMessage.value = e.message }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
    ) {
        // Back button + Artist picture at top (not sticky)
        item("header") {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Artist picture
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(header?.thumbnail)
                        .crossfade(true)
                        .error(R.drawable.placeholder_music_default_artwork)
                        .placeholder(R.drawable.placeholder_music_default_artwork)
                        .fallback(R.drawable.placeholder_music_default_artwork)
                        .build(),
                    contentDescription = "Artist photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )

                // Back button
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(top = 40.dp, start = 8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Artist name + subtitle at bottom of image
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = header?.title ?: "",
                        fontSize = 28.sp,
                        fontWeight = headingFontWeight(),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    header?.subtitle?.let { sub ->
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Loading
        if (isLoading.value) {
            item("loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Error
        if (errorMessage.value != null) {
            item("error") {
                Text(
                    text = "Error: ${errorMessage.value}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Red
                )
            }
        }

        // Action buttons (shuffle / play)
        if (!isLoading.value && topSongs != null && topSongs.isNotEmpty()) {
            item("actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionCircleButton(
                        iconRes = R.drawable.button_icon_shuffle,
                        contentDescription = "Shuffle",
                        onClick = {
                            val yosSongs = topSongs.map { it.toYosMediaItem() }
                            coroutineScope.launch(Dispatchers.IO) {
                                MediaController.prepare(yosSongs.random(), yosSongs, shuffleModeEnabled = true)
                            }
                        }
                    )
                    ActionCircleButton(
                        iconRes = R.drawable.button_icon_play,
                        contentDescription = "Play",
                        onClick = {
                            val yosSongs = topSongs.map { it.toYosMediaItem() }
                            coroutineScope.launch(Dispatchers.IO) {
                                MediaController.prepare(yosSongs.first(), yosSongs)
                            }
                        }
                    )
                }
            }
        }

        // Top Songs section
        if (topSongs != null && topSongs.isNotEmpty()) {
            item("songs_header") {
                SectionHeader(title = "Songs")
            }
            itemsIndexed(
                topSongs.take(5),
                key = { index, song -> "song_${song.id}_$index" }
            ) { index, song ->
                OnlineSongRow(
                    song = song,
                    onClick = {
                        val yosSongs = topSongs.map { it.toYosMediaItem() }
                        coroutineScope.launch(Dispatchers.IO) {
                            MediaController.prepare(song.toYosMediaItem(), yosSongs)
                        }
                    }
                )
                if (index < minOf(topSongs.size - 1, 4)) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 88.dp)
                            .alpha(0.15f)
                            .height(0.5.dp)
                            .background(Color.Black.copy(alpha = 0.15f))
                    )
                }
            }
        }

        // Albums section
        if (essentialAlbums != null && essentialAlbums.isNotEmpty()) {
            item("albums_header") {
                SectionHeader(title = "Albums")
            }
            item("albums_row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)
                ) {
                    items(essentialAlbums, key = { it.browseId }) { album ->
                        OnlineAlbumCard(
                            album = album,
                            onClick = {
                                navController.navigate("${UI.OnlineAlbumInfo}/${album.browseId}")
                            }
                        )
                    }
                }
            }
        }

        // Singles & EPs section
        if (singlesEPs != null && singlesEPs.isNotEmpty()) {
            item("singles_header") {
                SectionHeader(title = "Singles & EPs")
            }
            item("singles_row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)
                ) {
                    items(singlesEPs, key = { it.browseId }) { album ->
                        OnlineAlbumCard(
                            album = album,
                            onClick = {
                                navController.navigate("${UI.OnlineAlbumInfo}/${album.browseId}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 22.sp,
        fontWeight = headingFontWeight(),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun ActionCircleButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun OnlineSongRow(
    song: SongItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
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
                .clip(RoundedCornerShape(4.dp))
        )

        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                fontFamily = SfProFontFamily
            )
            Text(
                text = song.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown Artist" },
                modifier = Modifier.alpha(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontFamily = SfProFontFamily
            )
        }

        song.duration?.let { dur ->
            val min = dur / 60
            val sec = dur % 60
            Text(
                text = "%d:%02d".format(min, sec),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = userFontWeight(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = SfProFontFamily
        )
        album.artists?.firstOrNull()?.name?.let { artistName ->
            Text(
                text = artistName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = SfProFontFamily
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
        duration = (duration ?: 0) * 1000L
    )
}
