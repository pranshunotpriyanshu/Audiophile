package com.pryvn.audiophile.ui.pages.ytmusic

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cormor.overscroll.core.overScrollVertical
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YTAlbumSearchItem
import com.pryvn.audiophile.code.api.YTArtist
import com.pryvn.audiophile.code.api.YTArtistSearchItem
import com.pryvn.audiophile.code.api.YTPlaylist
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.data.libraries.FavPlayListLibrary
import com.pryvn.audiophile.data.libraries.HistoryEntry
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.PlaybackSource
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.YosRoundedCornerShape
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.theme.userFontWeight
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.ui.widgets.basic.MarqueeText
import com.pryvn.audiophile.ui.widgets.basic.ProfileButton
import com.pryvn.audiophile.ui.widgets.basic.SearchTextField
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class GenreCategory(
    val name: String,
    val songs: List<YosMediaItem>,
    val color: Color,
    val coverUri: Any?
)

@Composable
fun SearchPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val songOverflowSheetOpen = remember { mutableStateOf(false) }
    val selectedSongForMenu = remember { mutableStateOf<YosMediaItem?>(null) }

    val contentState = remember(uiState) {
        when {
            uiState.isLoading -> SearchContentState.Loading
            uiState.showSuggestions && uiState.suggestions.isNotEmpty() -> SearchContentState.Suggestions
            uiState.showRecent && uiState.recentSearches.isNotEmpty() -> SearchContentState.Recent
            uiState.isSearching && uiState.resultsSections.isNotEmpty() -> SearchContentState.Results
            uiState.isSearching && uiState.query.isNotBlank() && uiState.resultsSections.isEmpty() -> SearchContentState.Empty
            else -> SearchContentState.Idle
        }
    }

    BackHandler(
        enabled = contentState != SearchContentState.Idle
    ) {
        focusManager.clearFocus()
        keyboardController?.hide()
        viewModel.clearQuery()
    }

    val density = LocalDensity.current
    val systemBarsBottom = with(density) { WindowInsets.systemBars.getBottom(this).toDp() }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }

    val allSongs = remember { MusicLibrary.songs }

    val history by ListeningHistory.history.collectAsState()
    val recentRows = remember(history, allSongs) {
        history.mapNotNull { entry ->
            entry.toSearchYosMediaItem(allSongs)?.let { entry to it }
        }
    }

    val genreCategories = remember(allSongs) { buildGenreCategories(allSongs) }

    SongOverflowSheet(
        isOpen = songOverflowSheetOpen,
        song = selectedSongForMenu.value,
        navController = navController
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // iOS Large Title + Account/Settings Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.page_search_title),
                color = Color.Black withNight Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            ProfileButton(
                size = 36.dp,
                onClick = { navController.toUI(UI.Settings.Main) }
            )
        }

        // Apple Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            SearchTextField(
                text = uiState.query,
                placeholder = "Artists, Songs, Albums, Playlists...",
                onValueChange = viewModel::onQueryChange,
                onSearch = {
                    if (uiState.query.isNotBlank()) {
                        viewModel.performSearch(uiState.query)
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                },
                onClear = viewModel::clearQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { viewModel.onSearchFocusChanged(it.isFocused) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
        ) {
            if (contentState == SearchContentState.Idle) {
                // Quick Shortcuts
                item("SearchCategories") {
                    Text(
                        text = "Browse Categories",
                        color = Color.Black withNight Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SearchShortcutPill(
                            label = "Songs",
                            iconRes = R.drawable.ic_library_link_icon_songs,
                            onClick = {
                                LibraryObject.setTargetListWithTitle("All Songs", MusicLibrary.songs)
                                navController.toUI(UI.NormalMusic)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SearchShortcutPill(
                            label = "Albums",
                            iconRes = R.drawable.ic_library_link_icon_album,
                            onClick = { navController.toUI(UI.LocalAlbums) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SearchShortcutPill(
                            label = "Artists",
                            iconRes = R.drawable.ic_library_link_icon_artists,
                            onClick = { navController.toUI(UI.LocalArtists) },
                            modifier = Modifier.weight(1f)
                        )
                        SearchShortcutPill(
                            label = "Playlists",
                            iconRes = R.drawable.ic_library_link_icon_playlists,
                            onClick = { navController.toUI(UI.PlayLists) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Genre Cards Section
                if (genreCategories.isNotEmpty()) {
                    item("GenreCategoriesHeader") {
                        Text(
                            text = "Browse Genres & Moods",
                            color = Color.Black withNight Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    items(genreCategories.chunked(2), key = { chunk -> "genre_row_${chunk.first().name}" }) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GenreCard(
                                category = pair[0],
                                onClick = {
                                    LibraryObject.setTargetListWithTitle(pair[0].name, pair[0].songs)
                                    navController.toUI(UI.NormalMusic)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (pair.size > 1) {
                                GenreCard(
                                    category = pair[1],
                                    onClick = {
                                        LibraryObject.setTargetListWithTitle(pair[1].name, pair[1].songs)
                                        navController.toUI(UI.NormalMusic)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item("GenreBottomSpacer") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Recently Played Section
                if (recentRows.isNotEmpty()) {
                    item("RecentlyPlayedHeader") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.home_recently_played_title),
                                color = Color.Black withNight Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Clear History",
                                color = Color(0xFFFA2D48),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(YosRoundedCornerShape(8.dp))
                                    .clickable {
                                        ListeningHistory.clear()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(recentRows.take(10), key = { "recent_${it.second.uri}" }) { row ->
                        val entry = row.first
                        val song = row.second
                        SearchSongRow(
                            song = song,
                            onPlay = {
                                scope.launch(Dispatchers.IO) {
                                    if (entry.source == PlaybackSource.LOCAL) {
                                        MediaController.prepare(song, recentRows.map { it.second })
                                    } else {
                                        MediaController.playOnline(entry.toYTSongItem())
                                    }
                                }
                            },
                            onOverflow = {
                                selectedSongForMenu.value = song
                                songOverflowSheetOpen.value = true
                            }
                        )
                    }
                }
            } else {
                // Search Content View
                when (contentState) {
                    SearchContentState.Loading -> {
                        item { LoadingView(Modifier.fillMaxWidth().padding(vertical = 60.dp)) }
                    }
                    SearchContentState.Suggestions -> {
                        item { SuggestionsList(uiState.suggestions, viewModel::onSuggestionClick) }
                    }
                    SearchContentState.Recent -> {
                        item {
                            RecentSearchesContent(
                                uiState.recentSearches,
                                viewModel::onRecentSearchClick,
                                viewModel::clearRecentSearches
                            )
                        }
                    }
                    SearchContentState.Results -> {
                        items(uiState.resultsSections) { section ->
                            ResultsSection(section, onSongClick = { song ->
                                scope.launch(Dispatchers.IO) {
                                    MediaController.playOnline(song)
                                }
                            })
                        }
                    }
                    SearchContentState.Empty -> {
                        item { EmptyView(Modifier.fillMaxWidth()) }
                    }
                    else -> {}
                }
            }

            item("BottomInset") {
                Spacer(
                    modifier = Modifier.height(
                        if (uiState.isFocused) imeBottom + 8.dp
                        else systemBarsBottom + 134.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchSongRow(
    song: YosMediaItem,
    onPlay: () -> Unit,
    onOverflow: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(YosRoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(YosRoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumb != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(song.thumb).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.placeholder_music_default_artwork),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = song.title ?: "Unknown Title",
                color = Color.Black withNight Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            MarqueeText(
                text = "${song.artists ?: "Unknown Artist"} • ${song.album ?: "Unknown Album"}",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOverflow
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nowplaying_more),
                contentDescription = "Options",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun GenreCard(
    category: GenreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .height(105.dp)
            .clip(YosRoundedCornerShape(14.dp))
            .background(category.color)
            .clickable(onClick = onClick)
    ) {
        if (category.coverUri != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 10.dp)
                    .rotate(18f)
                    .size(68.dp)
                    .shadow(6.dp, YosRoundedCornerShape(8.dp))
                    .clip(YosRoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(category.coverUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = category.name,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp, end = 45.dp)
        )
    }
}

@Composable
private fun SearchShortcutPill(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(YosRoundedCornerShape(14.dp))
            .background(Color.Gray.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color(0xFFFA2D48),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = Color.Black withNight Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Suggestions ───────────────────────────────────────────────────────────

@Composable
private fun SuggestionsList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        suggestions.forEach { suggestion ->
            SuggestionRow(suggestion, onSuggestionClick)
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(suggestion) }
            )
            .padding(vertical = 14.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = suggestion,
            fontSize = 16.sp,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Recent Searches ───────────────────────────────────────────────────────

@Composable
private fun RecentSearchesContent(
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Searches",
                fontSize = 20.sp,
                fontWeight = headingFontWeight(),
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onClearAll) {
                Text(
                    text = "Clear",
                    fontSize = 14.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        recentSearches.forEach { query ->
            RecentSearchRow(query, onRecentClick)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun RecentSearchRow(query: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(query) }
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = query,
            fontSize = 16.sp,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Results ───────────────────────────────────────────────────────────────

@Composable
private fun ResultsSection(
    section: SearchResultSection,
    onSongClick: (YTSongItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                fontSize = 20.sp,
                fontWeight = headingFontWeight(),
                lineHeight = 20.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (section.seeAll) {
                Text(
                    text = "See All",
                    fontSize = 14.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        section.items.forEach { item ->
            when (item) {
                is YTSongItem -> AppleSearchResultRow(item, onSongClick)
                is YTAlbumSearchItem -> AppleAlbumSearchRow(item)
                is YTArtistSearchItem -> AppleArtistSearchRow(item)
                is YTPlaylist -> ApplePlaylistSearchRow(item)
            }
        }
    }
}

@Composable
private fun AppleAlbumSearchRow(album: YTAlbumSearchItem) {
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
                fontWeight = userFontWeight(),
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
private fun AppleArtistSearchRow(artist: YTArtistSearchItem) {
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
                .clip(CircleShape)
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
                fontWeight = userFontWeight(),
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
private fun ApplePlaylistSearchRow(playlist: YTPlaylist) {
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
                fontWeight = userFontWeight(),
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

@Composable
private fun AppleSearchResultRow(song: YTSongItem, onClick: (YTSongItem) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(song) }
            )
            .scale(if (isPressed) 0.98f else 1f)
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
                fontWeight = userFontWeight(),
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

// ─── Loading & Empty ───────────────────────────────────────────────────────

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppleLoadingSpinner(modifier = Modifier.size(64.dp))
    }
}

@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(60.dp))
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No Results",
            fontSize = 17.sp,
            fontFamily = SfProFontFamily,
            fontWeight = userFontWeight(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = "Try a different search term",
            fontSize = 14.sp,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.6f)
        )
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────

private fun buildGenreCategories(allSongs: List<YosMediaItem>): List<GenreCategory> {
    val genrePalette = listOf(
        Color(0xFFE91E63), // Deep Pink
        Color(0xFFE64A19), // Orange Red
        Color(0xFF455A64), // Slate Gray
        Color(0xFFD32F2F), // Bright Crimson
        Color(0xFF7B1FA2), // Rich Purple
        Color(0xFF8D6E63), // Warm Amber
        Color(0xFF00796B), // Deep Teal
        Color(0xFF303F9F), // Indigo Blue
        Color(0xFF2E7D32), // Emerald Green
        Color(0xFF880E4F), // Midnight Pink
        Color(0xFFC2185B), // Ruby Red
        Color(0xFF1976D2)  // Cobalt Blue
    )

    val grouped = allSongs.mapNotNull { song ->
        val g = song.genre?.trim()
        if (!g.isNullOrBlank() && g != "Unknown" && g != "null") g to song else null
    }.groupBy({ it.first }, { it.second })

    val list = mutableListOf<GenreCategory>()
    var colorIdx = 0

    grouped.forEach { (genreName, songsInGenre) ->
        val cover = songsInGenre.firstOrNull { it.thumb != null }?.thumb ?: songsInGenre.firstOrNull()?.thumb
        list.add(
            GenreCategory(
                name = genreName,
                songs = songsInGenre,
                color = genrePalette[colorIdx % genrePalette.size],
                coverUri = cover
            )
        )
        colorIdx++
    }

    if (list.size < 4 && allSongs.isNotEmpty()) {
        val defaultCategories = listOf(
            "Recently Added" to allSongs.take(20),
            "Favorites" to (FavPlayListLibrary.favPlayList.mapNotNull { fav ->
                allSongs.firstOrNull { it.uri != null && it.uri == fav.uri }
                    ?: allSongs.firstOrNull { it.mediaId != null && it.mediaId == fav.mediaId }
            }.ifEmpty { allSongs.take(15) }),
            "Pop & Dance" to allSongs.filter { (it.genre ?: "").lowercase().let { g -> g.contains("pop") || g.contains("dance") } }.ifEmpty { allSongs.take(12) },
            "Rock & Indie" to allSongs.filter { (it.genre ?: "").lowercase().let { g -> g.contains("rock") || g.contains("indie") || g.contains("alt") } }.ifEmpty { allSongs.drop(5).take(12) },
            "Chill & Mood" to allSongs.drop(10).take(12).ifEmpty { allSongs },
            "Library Mix" to allSongs.shuffled().take(15)
        )

        defaultCategories.forEach { (catName, catSongs) ->
            if (catSongs.isNotEmpty() && list.none { it.name == catName }) {
                val cover = catSongs.firstOrNull { it.thumb != null }?.thumb
                list.add(
                    GenreCategory(
                        name = catName,
                        songs = catSongs,
                        color = genrePalette[colorIdx % genrePalette.size],
                        coverUri = cover
                    )
                )
                colorIdx++
            }
        }
    }
    return list
}

private fun HistoryEntry.toSearchYosMediaItem(allSongs: List<YosMediaItem>): YosMediaItem? {
    if (source == PlaybackSource.LOCAL) {
        allSongs.firstOrNull { it.mediaId == videoId || it.uri?.toString() == videoId }?.let { return it }
    }
    return YosMediaItem(
        uri = Uri.parse("ytmusic://$videoId"),
        mediaId = videoId,
        title = title,
        artists = artists,
        thumb = thumbnailUrl?.let { Uri.parse(it) }
    )
}

private fun HistoryEntry.toYTSongItem(): YTSongItem = YTSongItem(
    videoId = videoId,
    title = title,
    artists = artists?.split(", ")?.map { YTArtist(name = it, id = "") } ?: emptyList(),
    durationSeconds = null,
    thumbnailUrl = thumbnailUrl,
    playlistId = null
)