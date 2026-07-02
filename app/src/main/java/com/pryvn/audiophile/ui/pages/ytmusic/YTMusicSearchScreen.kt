package com.pryvn.audiophile.ui.pages.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.google.accompanist.insets.statusBarsPadding
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.api.AudiophileOnlineTrack
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val moods = listOf("Happy", "Energetic", "Relaxed", "Sad", "Romantic", "Focus")
private val genres = listOf("Workout", "Party", "Chill", "Travel", "Jazz", "Hip Hop", "Pop", "Rock", "Classical", "R&B", "Electronic", "Country")

private val sectionBackgrounds = mapOf(
    "Happy" to listOf(Color(0xFFFF9A9E), Color(0xFFFAD0C4)),
    "Energetic" to listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
    "Relaxed" to listOf(Color(0xFF96E6A1), Color(0xFFD4FC79)),
    "Sad" to listOf(Color(0xFF89ABE3), Color(0xFFF5F7FA)),
    "Romantic" to listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
    "Focus" to listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
    "Workout" to listOf(Color(0xFFFF6B6B), Color(0xFFEE5A24)),
    "Party" to listOf(Color(0xFFF9D423), Color(0xFFFF4E50)),
    "Chill" to listOf(Color(0xFF0ABD8B), Color(0xFF0052D4)),
    "Travel" to listOf(Color(0xFF1E90FF), Color(0xFF00D2FF)),
    "Jazz" to listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
    "Hip Hop" to listOf(Color(0xFFF97316), Color(0xFFFACC15)),
    "Pop" to listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)),
    "Rock" to listOf(Color(0xFF6B7280), Color(0xFF374151)),
    "Classical" to listOf(Color(0xFFD4A574), Color(0xFFF5E6CC)),
    "R&B" to listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)),
    "Electronic" to listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
    "Country" to listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTMusicSearchScreen(
    showBackButton: Boolean = true,
    initialQuery: String? = null,
    isMoodGenreBrowse: Boolean = false,
    onBackClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf(initialQuery ?: "") }
    var items by remember { mutableStateOf<List<YTSongItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var suggestionJob by remember { mutableStateOf<Job?>(null) }
    var showResults by remember { mutableStateOf(initialQuery != null) }
    var hasSearched by remember { mutableStateOf(initialQuery != null) }

    fun performSearch(q: String) {
        if (q.isBlank()) return
        focusManager.clearFocus()
        showSuggestions = false
        showResults = true
        hasSearched = true
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeApi.search(q)
                result.onSuccess { searchResult ->
                    withContext(Dispatchers.Main) {
                        items = searchResult.items
                        isLoading = false
                    }
                }.onFailure { e1 ->
                    val fallback = ArchiveTuneApis.searchMusic(q)
                    fallback.onSuccess { fallbackResults ->
                        withContext(Dispatchers.Main) {
                            items = fallbackResults.map { it.toYTSongItem() }
                            isLoading = false
                        }
                    }.onFailure { e2 ->
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            performSearch(initialQuery)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 4.dp)) {
        // Header & Search Bar container
        Column(
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            // Header
            if (showResults && query.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBackButton || isMoodGenreBrowse) {
                        IconButton(
                            onClick = { onBackClick?.invoke() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isMoodGenreBrowse) query else "Results",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SfProFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Text(
                    text = "Search",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }

            // Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_uitabbar_search),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { q ->
                            query = q
                            if (showResults && !isMoodGenreBrowse) {
                                showResults = false
                            }
                            if (q.length >= 2 && !showResults) {
                                suggestionJob?.cancel()
                                suggestionJob = scope.launch(Dispatchers.IO) {
                                    delay(300)
                                    try {
                                        val result = YouTubeApi.getSearchSuggestions(q)
                                        result.onSuccess { sugg ->
                                            withContext(Dispatchers.Main) {
                                                suggestions = sugg
                                                showSuggestions = true
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            } else {
                                suggestions = emptyList()
                                showSuggestions = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            fontFamily = SfProFontFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        text = if (isMoodGenreBrowse) "Search within ${query}..." else "Search for songs, artists, albums...",
                                        fontSize = 17.sp,
                                        fontFamily = SfProFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (!isMoodGenreBrowse) performSearch(query)
                        }),
                    )
                    if (query.isNotEmpty() && !isMoodGenreBrowse) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                query = ""
                                items = emptyList()
                                suggestions = emptyList()
                                showResults = false
                                hasSearched = false
                                showSuggestions = false
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                painterResource(id = R.drawable.ic_tips_plus),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 45f },
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
                // Red underline
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Content
        if (isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner(
                    modifier = Modifier.size(42.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else if (showSuggestions && suggestions.isNotEmpty() && items.isEmpty() && !showResults) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(suggestions) { sugg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                query = sugg
                                showSuggestions = false
                                performSearch(sugg)
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(id = R.drawable.ic_uitabbar_search),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = sugg,
                            fontSize = 16.sp,
                            fontFamily = SfProFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        } else if (showResults && items.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(id = R.drawable.ic_uitabbar_search),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No songs found",
                        fontFamily = SfProFontFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        } else if (items.isNotEmpty()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items) { song ->
                    SearchResultRow(song = song, onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                MediaController.playOnline(song.videoId, song.title)
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        ctx, e.message ?: "Playback failed",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    })
                }
            }
        } else if (query.isBlank() && !isMoodGenreBrowse) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "Browse Categories",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "Discover music by mood",
                    fontSize = 14.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    moods.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { mood ->
                                MoodGenreCard(
                                    label = mood,
                                    colors = sectionBackgrounds[mood] ?: listOf(Color.Gray, Color.Gray),
                                    onClick = { query = mood; performSearch(mood) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowItems.size < 2) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Genres",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(genres) { genre ->
                        MoodGenreCard(
                            label = genre,
                            colors = sectionBackgrounds[genre] ?: listOf(Color.Gray, Color.Gray),
                            onClick = { query = genre; performSearch(genre) },
                            modifier = Modifier.width(130.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun AudiophileOnlineTrack.toYTSongItem() = YTSongItem(
    videoId = id,
    title = title,
    artists = if (artist != null) listOf(com.pryvn.audiophile.code.api.YTArtist(name = artist, id = "")) else emptyList(),
    album = album?.let { com.pryvn.audiophile.code.api.YTAlbum(name = it, id = "", thumbnailUrl = null) },
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    playlistId = null,
)

@Composable
private fun MoodGenreCard(label: String, colors: List<Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = Brush.horizontalGradient(colors = colors))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.0f), Color.Black.copy(alpha = 0.45f))
                    )
                )
        )
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun SearchResultRow(song: YTSongItem, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(song.thumbnailUrl)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .precision(Precision.INEXACT)
                .size(128)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(52.dp).height(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (song.durationSeconds != null) {
            val min = song.durationSeconds / 60
            val sec = song.durationSeconds % 60
            Text(
                text = "%d:%02d".format(min, sec),
                fontSize = 12.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(start = 10.dp, end = 2.dp),
            )
        }
    }
}
