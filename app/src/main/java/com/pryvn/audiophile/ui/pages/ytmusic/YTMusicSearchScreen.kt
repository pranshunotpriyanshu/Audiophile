package com.pryvn.audiophile.ui.pages.ytmusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.pryvn.audiophile.R
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.api.AudiophileOnlineTrack
import com.pryvn.audiophile.code.api.YTAlbum
import com.pryvn.audiophile.code.api.YTArtist
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// -----------------------------------------------------------------------------
// Data models – unchanged
// -----------------------------------------------------------------------------
data class SearchResultSection(val title: String, val items: List<Any>, val seeAll: Boolean = false)

sealed class SearchContentState {
    object Loading : SearchContentState()
    object Suggestions : SearchContentState()
    object Recent : SearchContentState()
    object Results : SearchContentState()
    object Empty : SearchContentState()
    object Idle : SearchContentState()
}

class SearchViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val recentSearchesKey = "recent_searches"

    init { loadRecentSearches() }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.isEmpty()) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                    resultsSections = emptyList(),
                    isLoading = false,
                    showSuggestions = false,
                    isSearching = false
                )
            }
            return
        }
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(300)
            if (query.length >= 2) {
                try {
                    val result = YouTubeApi.getSearchSuggestions(query)
                    result.onSuccess { sugg ->
                        _uiState.update { it.copy(suggestions = sugg, showSuggestions = true) }
                    }.onFailure { /* ignore */ }
                } catch (_: Exception) { /* ignore */ }
            } else {
                _uiState.update { it.copy(suggestions = emptyList(), showSuggestions = false) }
            }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        addRecentSearch(query)
        _uiState.update {
            it.copy(
                showSuggestions = false,
                isLoading = true,
                resultsSections = emptyList(),
                isSearching = true
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeApi.search(query)
                result.onSuccess { searchResult ->
                    val items = searchResult.items
                    val sections = listOf(SearchResultSection("Songs", items, false))
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(resultsSections = sections, isLoading = false, isSearching = true)
                        }
                    }
                }.onFailure {
                    val fallback = ArchiveTuneApis.searchMusic(query)
                    fallback.onSuccess { fallbackResults ->
                        val converted = fallbackResults.map { it.toYTSongItem() }
                        val sections = listOf(SearchResultSection("Songs", converted, false))
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(resultsSections = sections, isLoading = false, isSearching = true)
                            }
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isLoading = false, isSearching = true) }
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, isSearching = true) }
                }
            }
        }
    }

    fun clearQuery() {
        _uiState.update {
            it.copy(
                query = "",
                suggestions = emptyList(),
                resultsSections = emptyList(),
                showSuggestions = false,
                isSearching = false,
                isLoading = false
            )
        }
        searchJob?.cancel()
        suggestionJob?.cancel()
    }

    fun onSearchFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
        if (focused && _uiState.value.query.isEmpty()) {
            val hasRecent = _uiState.value.recentSearches.isNotEmpty()
            _uiState.update { it.copy(showRecent = hasRecent) }
        } else {
            _uiState.update { it.copy(showRecent = false) }
        }
    }

    fun onSuggestionClick(suggestion: String) {
        onQueryChange(suggestion)
        performSearch(suggestion)
    }

    fun onRecentSearchClick(query: String) {
        onQueryChange(query)
        performSearch(query)
    }

    fun clearRecentSearches() {
        prefs.edit().remove(recentSearchesKey).apply()
        _uiState.update { it.copy(recentSearches = emptyList(), showRecent = false) }
    }

    private fun addRecentSearch(query: String) {
        val list = _uiState.value.recentSearches.toMutableList()
        list.remove(query)
        list.add(0, query)
        if (list.size > 10) list.removeAt(list.size - 1)
        _uiState.update { it.copy(recentSearches = list) }
        val json = list.joinToString(separator = "|||")
        prefs.edit().putString(recentSearchesKey, json).apply()
    }

    private fun loadRecentSearches() {
        val json = prefs.getString(recentSearchesKey, "") ?: ""
        if (json.isNotBlank()) {
            val list = json.split("|||").filter { it.isNotBlank() }
            _uiState.update { it.copy(recentSearches = list) }
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val resultsSections: List<SearchResultSection> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isFocused: Boolean = false,
    val showSuggestions: Boolean = false,
    val showRecent: Boolean = false,
    val recentSearches: List<String> = emptyList()
)

// -----------------------------------------------------------------------------
// Main Screen – final, polished version
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun YTMusicSearchScreen(
    showBackButton: Boolean = true,
    initialQuery: String? = null,
    isMoodGenreBrowse: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    // IME animation for bottom search bar
    val imeInsets = WindowInsets.ime
    val isImeVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val imeBottom = imeInsets.getBottom(density)
    val searchBarBottomOffset by animateDpAsState(
        targetValue = if (isImeVisible && uiState.isFocused) imeBottom.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SearchBarBottomOffset"
    )

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.performSearch(initialQuery)
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()  // top padding for status bar
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp)
                .padding(bottom = 20.dp) // bottom spacing
        ) {
            // Title row – hidden on results
            AnimatedVisibility(
                visible = contentState !is SearchContentState.Results,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        text = when {
                            uiState.isSearching && uiState.query.isNotBlank() -> "Results"
                            isMoodGenreBrowse && uiState.query.isNotBlank() -> uiState.query
                            else -> "Search"
                        },
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                        fontFamily = SfProFontFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (navController != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(
                                    enabled = true,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { navController.toUI(UI.Settings.Main) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CupertinoIcons.Default.PersonCropCircle,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Top search bar – hidden on results
            AnimatedVisibility(
                visible = contentState !is SearchContentState.Results,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column {
                    SearchBar(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::performSearch,
                        onClear = viewModel::clearQuery,
                        onFocusChanged = viewModel::onSearchFocusChanged,
                        isFocused = uiState.isFocused,
                        focusRequester = focusRequester,
                        placeholder = if (isMoodGenreBrowse) "Search within ${uiState.query}..." else "Search for songs, artists, albums...",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Main content – scrollable and animated
            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
                }
            ) { state ->
                when (state) {
                    SearchContentState.Loading -> LoadingView(Modifier.fillMaxSize())
                    SearchContentState.Suggestions ->
                        SuggestionsContent(
                            suggestions = uiState.suggestions,
                            onSuggestionClick = viewModel::onSuggestionClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    SearchContentState.Recent ->
                        RecentSearchesContent(
                            recentSearches = uiState.recentSearches,
                            onRecentClick = viewModel::onRecentSearchClick,
                            onClearAll = viewModel::clearRecentSearches,
                            modifier = Modifier.fillMaxSize()
                        )
                    SearchContentState.Results ->
                        ResultsContent(
                            sections = uiState.resultsSections,
                            onSongClick = { song ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        MediaController.playOnline(song.videoId, song.title)
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            // optional toast
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    SearchContentState.Empty -> EmptyView(Modifier.fillMaxSize())
                    SearchContentState.Idle -> IdleContent(
                        onCategoryClick = viewModel::performSearch,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom search bar – only on results, floats above content and keyboard
        AnimatedVisibility(
            visible = contentState is SearchContentState.Results,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp) // safe area
                    .offset { IntOffset(0, -searchBarBottomOffset.roundToPx()) }
                    .animateContentSize(animationSpec = spring())
            ) {
                SearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::performSearch,
                    onClear = viewModel::clearQuery,
                    onFocusChanged = viewModel::onSearchFocusChanged,
                    isFocused = uiState.isFocused,
                    focusRequester = focusRequester,
                    placeholder = "Search for songs, artists, albums...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Apple‑style Search Bar (from Figma)
// -----------------------------------------------------------------------------
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .padding(vertical = 8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painterResource(id = R.drawable.ic_uitabbar_search),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isDark) Color(0xFFA0A0A2) else Color(0xFF8E8E93)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (query.isNotBlank()) {
                            onSearch(query)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontSize = 17.sp,
                                fontFamily = SfProFontFamily,
                                color = if (isDark) Color(0xFFA0A0A2) else Color(0xFF8E8E93),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp),
                        tint = if (isDark) Color(0xFFA0A0A2) else Color(0xFF8E8E93)
                    )
                }
            }
        }
        // Red underline (Apple style)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        )
    }
}

// -----------------------------------------------------------------------------
// Idle Content – Browse Categories (scrollable)
// -----------------------------------------------------------------------------
@Composable
private fun IdleContent(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val moods = listOf("Happy", "Energetic", "Relaxed", "Sad", "Romantic", "Focus")
    val genres = listOf("Workout", "Party", "Chill", "Travel", "Jazz", "Hip Hop", "Pop", "Rock", "Classical", "R&B", "Electronic", "Country")
    val sectionBackgrounds = mapOf(
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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp) // avoid now‑playing bar
    ) {
        Text(
            text = "Browse Categories",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Discover music by mood",
            fontSize = 14.sp,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            moods.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { mood ->
                        MoodGenreCard(
                            label = mood,
                            colors = sectionBackgrounds[mood] ?: listOf(Color.Gray, Color.Gray),
                            onClick = { onCategoryClick(mood) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size < 2) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Genres",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(genres) { genre ->
                MoodGenreCard(
                    label = genre,
                    colors = sectionBackgrounds[genre] ?: listOf(Color.Gray, Color.Gray),
                    onClick = { onCategoryClick(genre) },
                    modifier = Modifier.width(130.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MoodGenreCard(label: String, colors: List<Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = Brush.horizontalGradient(colors = colors))
            .clickable(onClick = onClick)
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
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 12.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Recent Searches
// -----------------------------------------------------------------------------
@Composable
private fun RecentSearchesContent(
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
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
                fontWeight = FontWeight.Bold,
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
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            recentSearches.forEach { query ->
                RecentSearchRow(query, onRecentClick)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(query: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(query) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
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
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Suggestions
// -----------------------------------------------------------------------------
@Composable
private fun SuggestionsContent(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionRow(suggestion, onSuggestionClick)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(suggestion) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(12.dp))
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

// -----------------------------------------------------------------------------
// Results Content – LazyColumn with padding for bottom search bar
// -----------------------------------------------------------------------------
@Composable
private fun ResultsContent(
    sections: List<SearchResultSection>,
    onSongClick: (YTSongItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 120.dp) // space for bottom bar
    ) {
        sections.forEach { section ->
            item {
                SectionHeader(title = section.title, seeAll = section.seeAll)
            }
            items(section.items) { item ->
                when (item) {
                    is YTSongItem -> SongRow(item, onSongClick)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, seeAll: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (seeAll) {
            TextButton(onClick = { /* handle see all */ }) {
                Text(
                    text = "See All",
                    fontSize = 14.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SongRow(song: YTSongItem, onClick: (YTSongItem) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(song) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.thumbnailUrl)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .precision(Precision.INEXACT)
                .size(128)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
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
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(start = 10.dp, end = 2.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Loading & Empty
// -----------------------------------------------------------------------------
@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppleLoadingSpinner(modifier = Modifier.size(64.dp))
    }
}

@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No Results",
                fontSize = 17.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Try a different search term",
                fontSize = 14.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------
private fun AudiophileOnlineTrack.toYTSongItem() = YTSongItem(
    videoId = id,
    title = title,
    artists = if (artist != null) listOf(YTArtist(name = artist, id = "")) else emptyList(),
    album = album?.let { YTAlbum(name = it, id = "", thumbnailUrl = null) },
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    playlistId = null
)

class SearchViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}