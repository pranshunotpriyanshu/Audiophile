package com.pryvn.audiophile.ui.pages

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
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
import com.pryvn.audiophile.code.api.HomeSection
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.data.models.ImageViewModel
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.widgets.basic.Title

private const val PULL_THRESHOLD_DP = 80f

@Composable
fun Home(
    navController: NavController,
    imageViewModel: ImageViewModel
) {
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableFloatStateOf(0f) }

    fun loadHome() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeApi.home()
                result.onSuccess { json ->
                    val parsed = YouTubeApi.parseHomeSections(json)
                    withContext(Dispatchers.Main) {
                        sections = parsed
                        loadError = false
                        isRefreshing = false
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) { loadError = true; isRefreshing = false }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { loadError = true; isRefreshing = false }
            }
        }
    }

    LaunchedEffect(Unit) { loadHome() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullOffset > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullOffset)
                    pullOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && !isRefreshing) {
                    pullOffset = (pullOffset + available.y).coerceAtMost(200f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset > PULL_THRESHOLD_DP) {
                    pullOffset = 0f
                    loadHome()
                } else {
                    pullOffset = 0f
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        Title(
            title = stringResource(id = R.string.page_home_title),
            rightIcon = CupertinoIcons.Default.PersonCropCircle,
            onRightIcon = {
                navController.toUI(UI.Settings.Main)
            },
            content = {
                if (isRefreshing) {
                    item("refresh") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }
                if (loadError) {
                    item("error") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unable to load. Pull down to refresh.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                } else if (sections.isEmpty()) {
                    item("loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    sections.forEach { section ->
                        item("header_${section.title}") {
                            Text(
                                text = section.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                            )
                        }
                        item("carousel_${section.title}") {
                            LazyRow(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(section.items, key = { it.title + (it.videoId ?: it.browseId ?: "") }) { item ->
                                    HomeCard(item = item, onClick = {
                                        item.videoId?.let { vid ->
                                            scope.launch(Dispatchers.IO) {
                                                MediaController.playOnline(vid, item.title)
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        )

        if (pullOffset > 0f && !isRefreshing) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    progress = { (pullOffset / PULL_THRESHOLD_DP).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

@Composable
private fun HomeCard(
    item: com.pryvn.audiophile.code.api.HomeItem,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(item.thumbnailUrl)
                .crossfade(true)
                .error(R.drawable.placeholder_music_default_artwork)
                .fallback(R.drawable.placeholder_music_default_artwork)
                .precision(Precision.INEXACT)
                .size(300)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(150.dp)
                .height(150.dp),
        )
        Text(
            text = item.title,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (item.artists.isNotEmpty()) {
            Text(
                text = item.artists.joinToString(", ") { it.name },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
