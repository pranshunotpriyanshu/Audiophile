package com.pryvn.audiophile.ui.pages.ytmusic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.YTPlaylist
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTMusicPlaylistsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<YTPlaylist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastSyncTime by remember { mutableLongStateOf(SettingsLibrary.YtMusicLastSyncTime) }
    val isLoggedIn = SettingsLibrary.isYtMusicLoggedIn

    fun loadPlaylists() {
        if (!isLoggedIn) return
        scope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeApi.library()
                result.onSuccess { json ->
                    val parsed = YouTubeApi.parseLibraryPlaylists(json)
                    withContext(Dispatchers.Main) {
                        playlists = parsed
                        isLoading = false
                        isRefreshing = false
                        val now = System.currentTimeMillis()
                        SettingsLibrary.YtMusicLastSyncTime = now
                        lastSyncTime = now
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) { isLoading = false; isRefreshing = false }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isLoading = false; isRefreshing = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            loadPlaylists()
            while (SettingsLibrary.YtMusicSyncEnabled) {
                delay(30000)
                if (!SettingsLibrary.YtMusicSyncEnabled) break
                loadPlaylists()
            }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ytmusic_playlists)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        loadPlaylists()
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.ytmusic_sync),
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (!isLoggedIn) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ytmusic_not_logged_in))
            }
        } else if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tip_no_lyrics))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (lastSyncTime > 0) {
                    item("sync_info") {
                        val minutes = (System.currentTimeMillis() - lastSyncTime) / 60000
                        val label = if (minutes < 1) "Just now"
                            else if (minutes < 60) "${minutes}m ago"
                            else "${minutes / 60}h ${minutes % 60}m ago"
                        Text(
                            text = "Synced $label",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
                if (isRefreshing) {
                    item("refresh_indicator") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                items(playlists) { playlist ->
                    PlaylistRow(playlist = playlist, onClick = {
                        LibraryObject.setTargetPlaylistId(playlist.id)
                        navController.toUI(UI.OnlinePlaylist)
                    })
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: YTPlaylist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedArtworkImage(
            url = playlist.thumbnailUrl,
            contentDescription = null,
            size = 128,
            modifier = Modifier
                .width(48.dp)
                .height(48.dp),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = playlist.title,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                playlist.songCount?.let { append("$it songs") }
                playlist.author?.let {
                    if (isNotEmpty()) append(" • ")
                    append(it)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
}
