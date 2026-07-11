package com.pryvn.audiophile.ui.pages.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.ui.widgets.basic.PullToRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.HomeItem
import com.pryvn.audiophile.code.api.HomeSection
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.theme.SfProFontFamily

@Composable
fun Browse(
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableIntStateOf(0) }

    val tabs = listOf("New", "Charts", "Moods")

    fun loadBrowse() {
        if (isLoading) return
        isLoading = true
        loadError = false
        scope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeApi.explore()
                result.onSuccess { json ->
                    val parsed = YouTubeApi.parseHomeSections(json)
                    withContext(Dispatchers.Main) {
                        sections = parsed
                        loadError = false
                        isLoading = false
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) { loadError = true; isLoading = false }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { loadError = true; isLoading = false }
            }
        }
    }

    LaunchedEffect(Unit) { loadBrowse() }

    val listState = rememberLazyListState()

    PullToRefreshLayout(
        isRefreshing = isLoading,
        onRefresh = { loadBrowse() },
        listState = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item("header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 40.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.page_browse_title),
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                        fontFamily = SfProFontFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { navController.toUI(UI.Settings.Main) }
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
        }

        // Section tabs
        item("tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedSection == index
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { selectedSection = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tab,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = SfProFontFamily,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (isLoading) {
            item("loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppleLoadingSpinner()
                }
            }
        } else if (loadError) {
            item("error") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to load content",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontFamily = SfProFontFamily,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Check your internet connection and try again.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontFamily = SfProFontFamily,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { loadBrowse() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Retry",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = SfProFontFamily,
                        )
                    }
                }
            }
        } else if (sections.isNotEmpty()) {
            // Featured section — large hero card
            val featuredSection = sections.firstOrNull()
            if (featuredSection != null && featuredSection.items.isNotEmpty()) {
                item("featured") {
                    FeaturedCard(item = featuredSection.items.first(), onClick = {
                        val first = featuredSection.items.first()
                        if (first.playlistId != null) {
                            LibraryObject.setTargetPlaylistId(first.playlistId)
                            navController.toUI(UI.OnlinePlaylist)
                        } else if (first.browseId?.startsWith("VL") == true || first.browseId?.startsWith("PL") == true) {
                            LibraryObject.setTargetPlaylistId(first.browseId)
                            navController.toUI(UI.OnlinePlaylist)
                        } else if (first.browseId != null) {
                            LibraryObject.setTargetBrowseId(first.browseId)
                            navController.toUI(UI.OnlineAlbumInfo)
                        } else {
                            first.videoId?.let { vid ->
                                scope.launch(Dispatchers.IO) {
                                    MediaController.playOnline(vid, first.title)
                                }
                            }
                        }
                    })
                }
            }

            // Remaining sections
            sections.forEachIndexed { idx, section ->
                item("header_${idx}") {
                    Text(
                        text = section.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontFamily = SfProFontFamily,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                item("carousel_${idx}") {
                    LazyRow(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.items.take(10), key = { it.title + (it.videoId ?: it.browseId ?: "${idx}_${it.title}") }) { item ->
                            BrowseCard(item = item, onClick = {
                                if (item.playlistId != null) {
                                    LibraryObject.setTargetPlaylistId(item.playlistId)
                                    navController.toUI(UI.OnlinePlaylist)
                                } else if (item.browseId?.startsWith("VL") == true || item.browseId?.startsWith("PL") == true) {
                                    LibraryObject.setTargetPlaylistId(item.browseId)
                                    navController.toUI(UI.OnlinePlaylist)
                                } else if (item.browseId != null) {
                                    LibraryObject.setTargetBrowseId(item.browseId)
                                    navController.toUI(UI.OnlineAlbumInfo)
                                } else {
                                    item.videoId?.let { vid ->
                                        scope.launch(Dispatchers.IO) {
                                            MediaController.playOnline(vid, item.title)
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun FeaturedCard(
    item: HomeItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        CachedArtworkImage(
            url = item.thumbnailUrl,
            contentDescription = null,
            size = 600,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SfProFontFamily,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.artists.isNotEmpty()) {
                Text(
                    text = item.artists.joinToString(", ") { it.name },
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BrowseCard(
    item: HomeItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CachedArtworkImage(
            url = item.thumbnailUrl,
            contentDescription = null,
            size = 300,
            modifier = Modifier
                .width(150.dp)
                .height(150.dp),
        )
        Text(
            text = item.title,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = SfProFontFamily,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (item.artists.isNotEmpty()) {
            Text(
                text = item.artists.joinToString(", ") { it.name },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = SfProFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
