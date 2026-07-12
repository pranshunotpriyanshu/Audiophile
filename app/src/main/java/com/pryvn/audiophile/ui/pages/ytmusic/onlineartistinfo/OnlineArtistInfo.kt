package com.pryvn.audiophile.ui.pages.ytmusic.onlineartistinfo

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.innertube.models.AlbumItem
import com.pryvn.audiophile.code.api.innertube.models.ArtistItem
import com.pryvn.audiophile.code.api.innertube.models.Artist
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.pages.ArtistHeader
import com.pryvn.audiophile.code.api.innertube.pages.ArtistPageData
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.R
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalComposeUiApi
@ExperimentalMaterial3Api
@Composable
fun OnlineArtistInfo(navController: NavController) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var headerHeight by remember { mutableStateOf(400) }
    var showBackButton by remember { mutableStateOf(false) }
    var showFloatingPlay by remember { mutableStateOf(false) }

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
                onSuccess = { data ->
                    artistPageState.value = data
                },
                onFailure = { e ->
                    errorMessage.value = e.message
                }
            )
        } catch (e: Exception) {
            errorMessage.value = e.message
        } finally {
            isLoading.value = false
        }
    }

    val header = artistPageState.value?.header
    val topSongs = artistPageState.value?.topSongs
    val essentialAlbums = artistPageState.value?.essentialAlbums
    val singlesEPs = artistPageState.value?.singlesEPs
    val description = artistPageState.value?.description
    val relatedArtists = artistPageState.value?.relatedArtists
    val latestRelease = artistPageState.value?.latestRelease

    val headerAlpha = animateFloatAsState(
        targetValue = if (scrollState.firstVisibleItemIndex > 0) 1f else 0f,
        animationSpec = tween(200)
    )

    val parallaxOffset = animateFloatAsState(
        targetValue = -scrollState.firstVisibleItemScrollOffset / 2f,
        animationSpec = spring(dampingRatio = 0.8f)
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (header != null) {
            HeroHeader(
                header = header,
                parallaxOffset = parallaxOffset.value,
                headerAlpha = headerAlpha.value,
                onBackClick = { navController.popBackStack() },
                onOverflowClick = { /* TODO */ },
                onFloatingPlayClick = { /* TODO: Play artist radio */ }
            )
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = (headerHeight + 24).dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (isLoading.value) {
                item {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else if (errorMessage.value != null) {
                item {
                    Text(
                        text = "Error: ${errorMessage.value}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Red
                    )
                }
            } else {
                if (latestRelease != null) {
                    item {
                        LatestReleaseCard(
                            release = latestRelease,
                            onClick = { /* TODO: Navigate to album */ }
                        )
                    }
                }

                if (topSongs != null && topSongs.isNotEmpty()) {
                    item {
                        TopSongsSection(
                            songs = topSongs,
                            onSongClick = { song, index ->
                                playSong(song, index, topSongs)
                            }
                        )
                    }
                }

                if (essentialAlbums != null && essentialAlbums.isNotEmpty()) {
                    item {
                        EssentialAlbumsSection(
                            albums = essentialAlbums,
                            onAlbumClick = { album ->
                                navController.navigate("${UI.OnlineAlbumInfo}/${album.browseId!!}")
                            }
                        )
                    }
                }

                if (singlesEPs != null && singlesEPs.isNotEmpty()) {
                    item {
                        SinglesEPsSection(
                            items = singlesEPs,
                            onItemClick = { item ->
                                navController.navigate("${UI.OnlineAlbumInfo}/${item.browseId!!}")
                            }
                        )
                    }
                }

                if (description != null && description.isNotBlank()) {
                    item {
                        AboutSection(description = description)
                    }
                }

                item {
                    GenreSection(genres = header?.subtitle?.split(", ") ?: listOf())
                }

                if (relatedArtists != null && relatedArtists.isNotEmpty()) {
                    item {
                        SimilarArtistsSection(
                            artists = relatedArtists,
                            onArtistClick = { artist ->
                                navController.navigate("${UI.OnlineArtistInfo}/${artist.id!!}")
                            }
                        )
                    }
                }
            }
        }

        if (showBackButton) {
            BackButton(onClick = { navController.popBackStack() }, alpha = headerAlpha.value)
        }



    }
}

@Composable
fun HeroHeader(
    header: ArtistHeader,
    parallaxOffset: Float,
    headerAlpha: Float,
    onBackClick: () -> Unit,
    onOverflowClick: () -> Unit,
    onFloatingPlayClick: () -> Unit
) {
    val thumbnailUrl = header.thumbnail ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .graphicsLayer {
                translationY = parallaxOffset
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .placeholder(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .build(),
            contentDescription = "Artist header",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black
                        ),
                        startY = 0.3f,
                        endY = 1f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                IconButton(onClick = onOverflowClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            Text(
                text = header.title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            header.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            header.description?.let { desc ->
                Text(
                    text = desc,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onFloatingPlayClick) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text(
                    text = "Play",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun LatestReleaseCard(
    release: AlbumItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .height(180.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(release.thumbnail)
                    .crossfade(true)
                    .error(R.drawable.placeholder_music_default_artwork)
                    .placeholder(R.drawable.placeholder_music_default_artwork)
                    .fallback(R.drawable.placeholder_music_default_artwork)
                    .build(),
                contentDescription = "Latest release",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp, 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(16.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Latest Release",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                release.artists?.firstOrNull()?.let { artist ->
                    Text(
                        text = artist.name,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                release.year?.let { year ->
                    Text(
                        text = year.toString(),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            Icon(Icons.Default.ArrowForward, contentDescription = "Navigate", tint = Color.Gray, modifier = Modifier.padding(end = 16.dp))
        }
    }
}

@Composable
fun TopSongsSection(
    songs: List<SongItem>,
    onSongClick: (SongItem, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top Songs",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "See All",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            songs.take(5).forEachIndexed { index, song ->
                SongRow(
                    song = song,
                    index = index + 1,
                    onClick = { onSongClick(song, index) }
                )
            }
        }
    }
}

@Composable
fun SongRow(
    song: SongItem,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            modifier = Modifier.width(32.dp)
        )

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.thumbnail)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .placeholder(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .build(),
            contentDescription = "Song thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            song.artists?.firstOrNull()?.let { artist ->
                Text(
                    text = artist.name,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            song.album?.let { album ->
                Text(
                    text = album.name,
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
fun EssentialAlbumsSection(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Essential Albums",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "See All",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }

        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(220.dp)
        ) {
            items(albums) { album ->
                EssentialAlbumCard(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
fun EssentialAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable { onClick() }
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShadowImageWithCache(
            dataLambda = { album.thumbnail },
            contentDescription = album.title,
            imageQuality = ImageQuality.HIGH,
            cornerRadius = 16.dp
        )

        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        album.artists?.firstOrNull()?.let { artist ->
            Text(
                text = artist.name,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        album.year?.let { year ->
            Text(
                text = year.toString(),
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SinglesEPsSection(
    items: List<AlbumItem>,
    onItemClick: (AlbumItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Singles & EPs",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "See All",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }

        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(200.dp)
        ) {
            items(items) { item ->
                SingleEPCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
fun SingleEPCard(
    item: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShadowImageWithCache(
            dataLambda = { item.thumbnail },
            contentDescription = item.title,
            imageQuality = ImageQuality.HIGH,
            cornerRadius = 12.dp
        )

        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        item.artists?.firstOrNull()?.let { artist ->
            Text(
                text = artist.name,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item.releaseType?.let { type ->
            Text(
                text = type.name.capitalize(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Blue.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AboutSection(description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "About",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        ExpandableText(text = description)
    }
}

@Composable
fun ExpandableText(
    text: String,
    maxLines: Int = 4
) {
    var expanded by remember { mutableStateOf(false) }
    val shouldShowButton = remember { mutableStateOf(false) }

    Text(
        text = text,
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.8f),
        maxLines = if (expanded) Int.MAX_VALUE else maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult ->
            shouldShowButton.value = layoutResult.hasVisualOverflow
        }
    )

    if (shouldShowButton.value) {
        Text(
            text = if (expanded) "Show Less" else "Show More",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Blue.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable { expanded = !expanded }
        )
    }
}

@Composable
fun GenreSection(genres: List<String>) {
    if (genres.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Genres",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    Text(
                        text = genre.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SimilarArtistsSection(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Similar Artists",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "See All",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }

        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(160.dp)
        ) {
            items(artists) { artist ->
                SimilarArtistCard(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
fun SimilarArtistCard(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.thumbnail ?: "")
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .placeholder(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .build(),
            contentDescription = artist.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(60.dp))
        )

        Text(
            text = artist.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        artist.subscriberCountText?.let { count ->
            Text(
                text = count,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BackButton(
    onClick: () -> Unit,
    alpha: Float
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(16.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
    }
}

private fun playSong(
    song: SongItem,
    index: Int,
    queue: List<SongItem>
) {
    val items = queue.map { s ->
        YosMediaItem(
            uri = null,
            mediaId = s.id,
            mimeType = null,
            title = s.title,
            artists = s.artists?.map { it.name }?.joinToString(", "),
            album = s.album?.name,
            thumb = s.thumbnail?.let { Uri.parse(it) },
            duration = (s.duration?.toLong() ?: 0L) * 1000L,
        )
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        MediaController.prepare(
            music = items[index],
            thisMusicList = items,
            play = true
        )
    }
}