package com.pryvn.audiophile.ui.pages.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Simple list of artists from a single song. Clicking an artist resolves
 * their browseId via YouTube search and navigates to the online artist page.
 */
@Composable
fun SongArtistsList(navController: NavController) {
    val (title, artistItems) = LibraryObject.getTargetListWithTitle()

    val resolvedArtists = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistItems) {
        isLoading = true
        val results = withContext(Dispatchers.IO) {
            artistItems.mapNotNull { item ->
                val name = item.title ?: return@mapNotNull null
                val result = withTimeoutOrNull(10_000L) {
                    YouTubeApi.search(name, "artist")
                }
                val artists = result?.getOrNull()?.sections
                    ?.firstOrNull { it.title == "Artists" }
                    ?.artists.orEmpty()
                val matched = artists.firstOrNull { a ->
                    a.name.equals(name, ignoreCase = true)
                } ?: artists.firstOrNull()
                if (matched != null) Pair(name, matched.browseId) else null
            }
        }
        resolvedArtists.value = results
        isLoading = false
    }

    Title(
        title = title.ifEmpty { "Artists" },
        onBack = { navController.popBackStack() },
    ) {
        if (isLoading) {
            item("loading") {
                AppleLoadingSpinner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp)
                )
            }
        } else if (resolvedArtists.value.isEmpty()) {
            item("empty") {
                Text(
                    text = "No artists found",
                    color = Color.Black.copy(alpha = 0.5f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 60.dp)
                )
            }
        } else {
            itemsIndexed(
                resolvedArtists.value,
                key = { index, _ -> resolvedArtists.value[index].first }
            ) { index, artist ->
                val artistName = artist.first
                val browseId = artist.second
                SongArtistRow(
                    artistName = artistName,
                    onClick = {
                        LibraryObject.setTargetBrowseId(browseId)
                        navController.toUI(UI.OnlineArtistInfo)
                    }
                )
                if (index < resolvedArtists.value.size - 1) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 76.dp)
                            .height(0.5.dp)
                            .background(Color.Black.copy(alpha = 0.08f))
                    )
                }
            }
            item("spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SongArtistRow(
    artistName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = Color(0xFFE8E8E8),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artistName.take(1).uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black.copy(alpha = 0.6f),
            )
        }

        Text(
            text = artistName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SfProFontFamily,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )
    }
}
