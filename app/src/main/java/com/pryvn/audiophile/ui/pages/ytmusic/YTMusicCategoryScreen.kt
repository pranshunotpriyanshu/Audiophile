package com.pryvn.audiophile.ui.pages.ytmusic

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.*
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTMusicCategoryScreen(
    category: String,
    navController: NavController? = null
) {
    var sections by remember { mutableStateOf<List<SearchResultSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val systemBarsBottom = with(density) { WindowInsets.systemBars.getBottom(this).toDp() }

    LaunchedEffect(category) {
        isLoading = true
        try {
            val result = YouTubeApi.search(category)
            result.onSuccess { searchResult ->
                val mapped = if (searchResult.sections.isNotEmpty()) {
                    searchResult.sections.map { section ->
                        val combined = mutableListOf<Any>()
                        combined.addAll(section.songs)
                        combined.addAll(section.albums)
                        combined.addAll(section.artists)
                        combined.addAll(section.playlists)
                        SearchResultSection(section.title, combined)
                    }
                } else {
                    listOf(SearchResultSection("Songs", searchResult.items, false))
                }
                sections = mapped
            }.onFailure {
                val fallback = ArchiveTuneApis.searchMusic(category)
                fallback.onSuccess { fallbackResults ->
                    val converted = fallbackResults.map { it.toYTSongItem() }
                    sections = listOf(SearchResultSection("Songs", converted, false))
                }.onFailure {
                    sections = emptyList()
                }
            }
        } catch (_: Exception) {
            sections = emptyList()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = category,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SfProFontFamily
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppleLoadingSpinner(modifier = Modifier.size(64.dp))
                    }
                }
                sections.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No Results",
                            fontSize = 17.sp,
                            fontFamily = SfProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Try a different category",
                            fontSize = 14.sp,
                            fontFamily = SfProFontFamily,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = systemBarsBottom + 16.dp),
                        contentPadding = PaddingValues(top = 8.dp)
                    ) {
                        sections.forEach { section ->
                            item {
                                Text(
                                    text = section.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 20.sp,
                                    fontFamily = SfProFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            }
                            items(section.items) { item ->
                                when (item) {
                                    is YTSongItem -> CategorySongRow(item) { song ->
                                        scope.launch(Dispatchers.IO) {
                                            Log.d("CategoryDebug", "category=${category} videoId=${song.videoId}")
                                            MediaController.playOnline(song)
                                        }
                                    }
                                    is YTAlbumSearchItem -> CategoryAlbumRow(item)
                                    is YTArtistSearchItem -> CategoryArtistRow(item)
                                    is YTPlaylist -> CategoryPlaylistRow(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySongRow(song: YTSongItem, onClick: (YTSongItem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(song) }
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CachedArtworkImage(
            url = song.thumbnailUrl,
            contentDescription = null,
            size = 128,
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = song.title,
                fontSize = 17.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = buildString {
                if (song.artists.isNotEmpty()) {
                    append(song.artists.joinToString(", ") { it.name })
                }
                song.album?.name?.let {
                    if (isNotEmpty()) append("  \u2022  ")
                    append(it)
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (song.durationSeconds != null) {
            val min = song.durationSeconds / 60
            val sec = song.durationSeconds % 60
            Text(
                text = "%d:%02d".format(min, sec),
                fontSize = 13.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun CategoryAlbumRow(album: YTAlbumSearchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CachedArtworkImage(
            url = album.thumbnailUrl,
            contentDescription = null,
            size = 128,
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = album.title,
                fontSize = 17.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (album.artist != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = album.artist,
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CategoryArtistRow(artist: YTArtistSearchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CachedArtworkImage(
            url = artist.thumbnailUrl,
            contentDescription = null,
            size = 128,
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = artist.name,
                fontSize = 17.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Artist",
                fontSize = 13.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryPlaylistRow(playlist: YTPlaylist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CachedArtworkImage(
            url = playlist.thumbnailUrl,
            contentDescription = null,
            size = 128,
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = playlist.title,
                fontSize = 17.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = buildString {
                if (playlist.author != null) append(playlist.author)
                if (playlist.songCount != null) {
                    if (isNotEmpty()) append("  \u2022  ")
                    append("${playlist.songCount} songs")
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
