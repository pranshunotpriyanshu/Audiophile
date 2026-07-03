package com.pryvn.audiophile.ui.pages.ytmusic

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pryvn.audiophile.R
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
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
                    }.onFailure { }
                } catch (_: Exception) { }
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

// ─── Apple Music Search Page ───────────────────────────────────────────────

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
    val isSearching = uiState.isFocused || uiState.isSearching

    val imeInsets = WindowInsets.ime
    val imeBottom by remember { derivedStateOf { imeInsets.getBottom(density) } }
    val keyboardHeight by animateDpAsState(
        targetValue = if (uiState.isFocused && imeBottom > 0) imeBottom.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "keyboardHeight"
    )

    val searchBarAnimOffset by animateDpAsState(
        targetValue = if (uiState.isFocused && keyboardHeight > 0.dp) keyboardHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "searchBarOffset"
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

    BackHandler(
        enabled = !showBackButton && contentState != SearchContentState.Idle
    ) {
        viewModel.clearQuery()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Main scrollable content ──
        Box(modifier = Modifier.fillMaxSize()) {
            // When idle: show full page with categories
            // When searching: show scrollable results
            if (uiState.isFocused || uiState.isSearching) {
                // Results area
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (uiState.isFocused && keyboardHeight > 0.dp) (keyboardHeight + 60.dp) else 220.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
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
                                        MediaController.playOnline(song.videoId, song.title)
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
            } else {
                // Idle page with categories
                IdlePage(
                    onCategoryClick = viewModel::performSearch,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Search bar overlay ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp)
        ) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton || isMoodGenreBrowse) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onBackClick?.invoke() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = "Search",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SfProFontFamily,
                    letterSpacing = 0.3.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { navController?.toUI(UI.Settings.Main) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CupertinoIcons.Default.PersonCropCircle,
                        contentDescription = "Account",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Search bar
            AppleSearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::performSearch,
                onClear = viewModel::clearQuery,
                onFocusChanged = viewModel::onSearchFocusChanged,
                isFocused = uiState.isFocused,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }

        // ── Floating mini player ──
        if (!uiState.isFocused) {
            AppleMiniPlayer(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }

    // Auto-focus and keyboard handling
    LaunchedEffect(uiState.isFocused) {
        if (uiState.isFocused) {
            focusRequester.requestFocus()
        }
    }
}

// ─── Apple Search Bar ──────────────────────────────────────────────────────

@Composable
private fun AppleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
        ) {
            Icon(
                painterResource(id = R.drawable.ic_uitabbar_search),
                contentDescription = "Search",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(8.dp))
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
                                text = "Artists, Songs, Lyrics, and More",
                                fontSize = 17.sp,
                                fontFamily = SfProFontFamily,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ─── Idle Page ─────────────────────────────────────────────────────────────

@Composable
private fun IdlePage(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(top = 120.dp)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 200.dp)
    ) {
        Text(
            text = "Browse Categories",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val categories = listOf(
            CategoryData("Pop", Color(0xFFEC4899), Color(0xFF8B5CF6)),
            CategoryData("Hip-Hop", Color(0xFFF97316), Color(0xFFFACC15)),
            CategoryData("Rock", Color(0xFF6B7280), Color(0xFF374151)),
            CategoryData("Electronic", Color(0xFF06B6D4), Color(0xFF3B82F6)),
            CategoryData("R&B", Color(0xFF7C3AED), Color(0xFF6D28D9)),
            CategoryData("Jazz", Color(0xFF8B5CF6), Color(0xFFEC4899)),
            CategoryData("Classical", Color(0xFFD4A574), Color(0xFFF5E6CC)),
            CategoryData("Country", Color(0xFFD97706), Color(0xFFFBBF24)),
            CategoryData("Alternative", Color(0xFF0ABD8B), Color(0xFF0052D4)),
            CategoryData("Indie", Color(0xFFA18CD1), Color(0xFFFBC2EB)),
            CategoryData("Metal", Color(0xFF374151), Color(0xFF6B7280)),
            CategoryData("Blues", Color(0xFF1E90FF), Color(0xFF00D2FF)),
            CategoryData("Folk", Color(0xFF96E6A1), Color(0xFFD4FC79)),
            CategoryData("Latin", Color(0xFFFF6B6B), Color(0xFFEE5A24)),
            CategoryData("Reggae", Color(0xFF0ABD8B), Color(0xFFF9D423)),
            CategoryData("Soul", Color(0xFFF093FB), Color(0xFFF5576C)),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { category ->
                        CategoryCard(
                            label = category.label,
                            colors = listOf(category.color1, category.color2),
                            onClick = { onCategoryClick(category.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size < 2) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class CategoryData(val label: String, val color1: Color, val color2: Color)

@Composable
private fun CategoryCard(
    label: String,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.horizontalGradient(colors = colors),
                shape = RoundedCornerShape(10.dp)
            )
            .scale(if (isPressed) 0.97f else 1f)
            .graphicsLayer {
                shadowElevation = if (isPressed) 0f else 2f
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    awaitPointerEvent()
                    isPressed = true
                    try {
                        awaitPointerEvent()
                    } finally {
                        isPressed = false
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
        )
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp)
        )
    }
}

// ─── Suggestions ───────────────────────────────────────────────────────────

@Composable
private fun SuggestionsList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(suggestions) { suggestion ->
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
            .padding(vertical = 14.dp, horizontal = 24.dp),
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
            .padding(horizontal = 24.dp)
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
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
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
            .padding(horizontal = 24.dp, vertical = 6.dp),
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

// ─── Apple Mini Player ─────────────────────────────────────────────────────

@Composable
private fun AppleMiniPlayer(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = "Not Playing",
                    fontSize = 15.sp,
                    fontFamily = SfProFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to start listening",
                    fontSize = 12.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { }) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
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
            fontWeight = FontWeight.SemiBold,
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

// ─── Helper ────────────────────────────────────────────────────────────────

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
