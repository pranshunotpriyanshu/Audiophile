package com.pryvn.audiophile.ui.pages.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.api.YTArtistSearchItem
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.theme.userFontWeight
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.SearchTextField
import com.pryvn.audiophile.ui.widgets.basic.TitleWithLazyVerticalGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OnlineArtistsList(navController: NavController) {
    val searchText = remember("OnlineArtists_searchText") {
        mutableStateOf("")
    }
    var artists by remember { mutableStateOf<List<YTArtistSearchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    val useSearch = remember { derivedStateOf { searchText.value.isNotEmpty() } }

    LaunchedEffect(searchText.value) {
        if (searchText.value.length >= 2) {
            withContext(Dispatchers.IO) {
                isLoading = true
                try {
                    val result = YouTubeApi.search(searchText.value, "artist")
                    result.onSuccess { searchResult ->
                        artists = searchResult.sections
                            .firstOrNull { it.title == "Artists" }
                            ?.artists ?: emptyList()
                        hasSearched = true
                    }.onFailure {
                        artists = emptyList()
                    }
                } catch (_: Exception) {
                    artists = emptyList()
                }
                isLoading = false
            }
        } else {
            artists = emptyList()
            hasSearched = false
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    TitleWithLazyVerticalGrid(
        title = "Artists",
        onBack = { navController.popBackStack() },
        columns = { 2 }
    ) {
        // Search field
        item("search", span = { GridItemSpan(2) }) {
            SearchTextField(
                text = searchText.value,
                placeholder = "Search artists...",
                onValueChange = { searchText.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 5.dp, bottom = 8.dp),
                onSearch = {
                    if (searchText.value.isNotEmpty()) {
                        keyboardController?.hide()
                    }
                },
            )
        }

        // Loading state
        if (isLoading && artists.isEmpty()) {
            item("loading", span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppleLoadingSpinner()
                }
            }
        }

        // Empty state
        if (!isLoading && hasSearched && artists.isEmpty()) {
            item("empty", span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No artists found",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontFamily = SfProFontFamily,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Try a different search term",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontFamily = SfProFontFamily,
                    )
                }
            }
        }

        // Artist grid items
        itemsIndexed(
            artists,
            key = { _, artist -> artist.browseId }
        ) { index, artist ->
            ArtistGridItem(
                artist = artist,
                onClick = {
                    LibraryObject.setTargetBrowseId(artist.browseId)
                    navController.toUI(UI.OnlineArtistInfo)
                }
            )
        }
    }
}

@Composable
private fun ArtistGridItem(
    artist: YTArtistSearchItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = artist.name,
            fontSize = 15.sp,
            fontWeight = userFontWeight(),
            fontFamily = SfProFontFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
