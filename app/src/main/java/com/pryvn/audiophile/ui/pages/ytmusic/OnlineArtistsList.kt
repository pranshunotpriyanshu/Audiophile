package com.pryvn.audiophile.ui.pages.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.SearchTextField
import com.pryvn.audiophile.ui.widgets.basic.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OnlineArtistsList(navController: NavController) {
    Column(
        Modifier.fillMaxSize()
    ) {
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

        Title(
            title = "Artists",
            onBack = {
                navController.popBackStack()
            },
        ) {
            item {
                SearchTextField(
                    text = searchText.value,
                    placeholder = "Search artists...",
                    onValueChange = {
                        searchText.value = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 5.dp, bottom = 12.dp),
                    onSearch = {
                        if (searchText.value.isNotEmpty()) {
                            keyboardController?.hide()
                        }
                    },
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppleLoadingSpinner()
                    }
                }
            } else if (hasSearched && artists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No artists found",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            itemsIndexed(
                artists,
                key = { _, artist -> artist.browseId }
            ) { index, artist ->
                OnlineArtistItem(
                    artist = artist,
                    onClick = {
                        LibraryObject.setTargetBrowseId(artist.browseId)
                        navController.toUI(UI.OnlineArtistInfo)
                    }
                )

                key(index) {
                    if (index < artists.size - 1) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 81.dp)
                                .alpha(0.15f)
                                .height(0.5.dp)
                                .background(Color.Black withNight Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.OnlineArtistItem(
    artist: YTArtistSearchItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(data = artist.thumbnailUrl)
                    .crossfade(true)
                    .error(R.drawable.songcredits_monogram_person)
                    .placeholder(R.drawable.songcredits_monogram_person)
                    .fallback(R.drawable.songcredits_monogram_person)
                    .build(),
                contentDescription = "Artist_Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(15.dp))
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = artist.name,
                fontSize = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_action_next),
            contentDescription = null,
            modifier = Modifier
                .height(12.dp)
                .padding(end = 8.dp)
                .alpha(0.3f),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}
