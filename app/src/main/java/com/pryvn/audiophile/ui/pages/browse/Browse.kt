package com.pryvn.audiophile.ui.pages.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.HomeItem
import com.pryvn.audiophile.code.api.HomeSection
import com.pryvn.audiophile.code.api.YouTubeApi
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
    var isLoading by remember { mutableStateOf(true) }

    fun loadBrowse() {
        if (isLoading) return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeApi.home()
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

    LazyColumn(
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
                Icon(
                    painterResource(id = R.drawable.ic_uitabbar_search),
                    contentDescription = "Search",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            // Navigate to search tab
                            navController.toUI(UI.HomePage)
                        },
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            enabled = true,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { navController.toUI(UI.Settings.Main) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CupertinoIcons.Default.PersonCropCircle,
                        contentDescription = "Account",
                        modifier = Modifier.fillMaxSize().size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isLoading) {
            item("loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (loadError) {
            item("error") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Unable to load. Pull down to refresh.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        } else if (sections.isNotEmpty()) {
            // Featured section (first section gets a large card)
            val featuredSection = sections.firstOrNull()
            if (featuredSection != null) {
                item("featured_header") {
                    Text(
                        text = featuredSection.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontFamily = SfProFontFamily,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                item("featured_card") {
                    if (featuredSection.items.isNotEmpty()) {
                        FeaturedCard(
                            item = featuredSection.items.first(),
                            onClick = {
                                featuredSection.items.first().videoId?.let { vid ->
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.playOnline(vid, featuredSection.items.first().title)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Remaining sections
            sections.drop(1).forEach { section ->
                item("header_${section.title}") {
                    Text(
                        text = section.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontFamily = SfProFontFamily,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                item("list_${section.title}") {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        section.items.take(5).forEach { item ->
                            BrowseSongRow(
                                item = item,
                                onClick = {
                                    item.videoId?.let { vid ->
                                        scope.launch(Dispatchers.IO) {
                                            MediaController.playOnline(vid, item.title)
                                        }
                                    }
                                }
                            )
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
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(item.thumbnailUrl)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .precision(Precision.INEXACT)
                .size(600)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
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
private fun BrowseSongRow(
    item: HomeItem,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(item.thumbnailUrl)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .precision(Precision.INEXACT)
                .size(128)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(48.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.artists.isNotEmpty()) {
                Text(
                    text = item.artists.joinToString(", ") { it.name },
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.durationSeconds != null) {
            val min = item.durationSeconds / 60
            val sec = item.durationSeconds % 60
            Text(
                text = "%d:%02d".format(min, sec),
                fontSize = 13.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
