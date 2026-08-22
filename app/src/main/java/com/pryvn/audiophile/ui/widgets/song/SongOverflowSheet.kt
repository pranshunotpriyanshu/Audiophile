package com.pryvn.audiophile.ui.widgets.song

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.YTAlbumSearchItem
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.cache.AudioCacheStore
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.FavPlayListLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.artistsList
import com.pryvn.audiophile.data.libraries.defaultArtistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.code.api.YTArtistSearchItem
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.LyricShareContent
import com.pryvn.audiophile.ui.widgets.basic.AppleActionSheet
import com.pryvn.audiophile.ui.widgets.basic.AppleSheetMenuGroup
import com.pryvn.audiophile.ui.widgets.basic.AppleSheetMenuRow
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.playlist.PlayListPickerContent
import com.pryvn.audiophile.ui.widgets.sleeptimer.SleepTimerContent

private enum class SongOverflowScreen { Menu, AddToPlaylist, SleepTimer, LyricShare, DownloadStatus }

/** True when the item is already-offline media: either flagged by the private
 *  [YosMediaItem.isLocalMedia] tag (Cached library) or backed by a local file. */
private fun YosMediaItem.isLocalMediaItem(): Boolean =
    isLocalMedia || uri?.scheme == "file" || uri?.scheme == "content"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOverflowSheet(
    isOpen: MutableState<Boolean>,
    song: YosMediaItem?,
    navController: NavController? = null,
    /** When provided, adds a "Select" item that switches the hosting list
     * into multi-select mode (used by every playlist screen). */
    onSelect: (() -> Unit)? = null,
    /** Now Playing-only extras: when any is provided the matching row is
     * appended to the menu (Sleep Timer, Refetch Lyrics, Share Lyrics,
     * Download Status). The three pickers switch to an internal sub-screen;
     * [onRefetchLyrics] runs after the sheet dismisses. */
    onPickSleepTimer: (() -> Unit)? = null,
    onRefetchLyrics: (() -> Unit)? = null,
    onPickLyricShare: (() -> Unit)? = null,
    onPickDownloadStatus: (() -> Unit)? = null,
    /** Now Playing-only: replaces the default album/artist navigation so the
     *  hosting screen can minimize the player sheet before routing. When null
     *  the standard navigation is used. */
    onGoToAlbum: ((YosMediaItem, NavController) -> Unit)? = null,
    onGoToArtist: ((YosMediaItem, NavController) -> Unit)? = null,
) {
    if (!isOpen.value || song == null) return

    var screen by remember { mutableStateOf(SongOverflowScreen.Menu) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val onDismiss: () -> Unit = {
        isOpen.value = false
        screen = SongOverflowScreen.Menu
    }

    LaunchedEffect(Unit) {
        Vibrator.click(context)
    }

    AppleActionSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        when (screen) {
            SongOverflowScreen.Menu -> SongOverflowMenuBody(
                song = song,
                navController = navController,
                onSelect = onSelect,
                onAddToPlaylist = { screen = SongOverflowScreen.AddToPlaylist },
                onDismiss = onDismiss,
                onPickSleepTimer = onPickSleepTimer?.let { { screen = SongOverflowScreen.SleepTimer } },
                onRefetchLyrics = onRefetchLyrics,
                onPickLyricShare = onPickLyricShare?.let { { screen = SongOverflowScreen.LyricShare } },
                onPickDownloadStatus = onPickDownloadStatus?.let { { screen = SongOverflowScreen.DownloadStatus } },
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist,
            )

            SongOverflowScreen.AddToPlaylist -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                PlayListPickerContent(
                    songToAdd = song,
                    onDone = onDismiss,
                    appleTheme = true,
                )
            }

            SongOverflowScreen.SleepTimer -> SleepTimerContent(
                onDone = onDismiss,
                onBack = { screen = SongOverflowScreen.Menu },
            )

            SongOverflowScreen.LyricShare -> LyricShareContent(
                song = song,
                onBack = { screen = SongOverflowScreen.Menu },
                onDone = onDismiss,
            )

            SongOverflowScreen.DownloadStatus -> DownloadStatusScreen(
                song = song,
                onBack = { screen = SongOverflowScreen.Menu },
            )
        }
    }
}

@Composable
private fun SongOverflowMenuBody(
    song: YosMediaItem,
    navController: NavController?,
    onSelect: (() -> Unit)?,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
    onPickSleepTimer: (() -> Unit)?,
    onRefetchLyrics: (() -> Unit)?,
    onPickLyricShare: (() -> Unit)?,
    onPickDownloadStatus: (() -> Unit)?,
    onGoToAlbum: ((YosMediaItem, NavController) -> Unit)?,
    onGoToArtist: ((YosMediaItem, NavController) -> Unit)?,
) {
    val context = LocalContext.current
    val isFav = FavPlayListLibrary.isFavorite(song)

    val isCurrentSong = song.mediaId != null && song.mediaId == MediaController.musicPlaying.value?.mediaId
    val isInNextQueue = song.mediaId != null &&
        MediaController.nextInQueueMusicList.value.any { it.mediaId == song.mediaId }

    val isLocal = song.isLocalMediaItem()
    val isAlreadyCached = AudioCacheStore.getCachedUri(song.mediaId) != null
    val isDownloading = AudioCacheStore.progressOf(song.mediaId) != null
    val isDownloaded = isLocal || isAlreadyCached
    val rowTint = Color.Black withNight Color.White

    Column(modifier = Modifier.fillMaxWidth()) {
        SongOverflowHeader(song = song)
        Spacer(modifier = Modifier.height(14.dp))

        AppleSheetMenuGroup {
            var first = true

            @Composable
            fun row(
                text: String,
                iconRes: Int,
                onClick: () -> Unit,
                enabled: Boolean = true,
                tint: Color? = null,
            ) {
                val showTopDivider = !first
                first = false
                AppleSheetMenuRow(
                    text = text,
                    icon = iconRes,
                    onClick = onClick,
                    enabled = enabled,
                    tint = tint ?: rowTint,
                    showTopDivider = showTopDivider,
                )
            }

            if (!isCurrentSong && !isInNextQueue) {
                row(
                    text = stringResource(R.string.playlist_overflow_play_next),
                    iconRes = R.drawable.ic_action_play_next,
                    onClick = {
                        onDismiss()
                        MainScope().launch(Dispatchers.IO) {
                            MediaController.playNext(listOf(song))
                        }
                        Toast.makeText(context, context.getString(R.string.playlist_play_next_toast_one), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (onSelect != null) {
                row(
                    text = stringResource(R.string.song_menu_select),
                    iconRes = R.drawable.ic_action_check,
                    onClick = {
                        onDismiss()
                        onSelect()
                    },
                )
            }

            row(
                text = stringResource(R.string.now_playing_overflow_add_to_playlist),
                iconRes = R.drawable.ic_action_add,
                onClick = onAddToPlaylist,
            )

            row(
                text = if (isFav) stringResource(R.string.song_action_unfavorite) else stringResource(R.string.song_action_favorite),
                iconRes = if (isFav) R.drawable.ic_nowplaying_favorited else R.drawable.ic_nowplaying_favorite,
                onClick = {
                    if (isFav) {
                        FavPlayListLibrary.removeMusic(song)
                    } else {
                        FavPlayListLibrary.addMusic(song)
                    }
                },
            )

            when {
                isDownloaded -> row(
                    text = stringResource(R.string.song_menu_download_local_already),
                    iconRes = R.drawable.ic_action_recover,
                    enabled = false,
                    onClick = {},
                )

                isDownloading -> row(
                    text = stringResource(R.string.download_status_downloading),
                    iconRes = R.drawable.ic_action_recover,
                    enabled = false,
                    onClick = {},
                )

                else -> row(
                    text = stringResource(R.string.song_menu_download),
                    iconRes = R.drawable.ic_action_recover,
                    onClick = {
                        onDismiss()
                        MediaController.forceDownloadSong(song)
                    },
                )
            }

            row(
                text = stringResource(R.string.song_menu_add_to_queue),
                iconRes = R.drawable.ic_nowplaying_queue,
                onClick = {
                    onDismiss()
                    MainScope().launch(Dispatchers.IO) {
                        MediaController.addToQueue(song)
                    }
                    Toast.makeText(context, context.getString(R.string.queue_added_toast), Toast.LENGTH_SHORT).show()
                },
            )

            if (song.album != null && navController != null) {
                row(
                    text = stringResource(R.string.song_menu_go_to_album),
                    iconRes = R.drawable.ic_library_link_icon_album,
                    onClick = {
                        onDismiss()
                        if (onGoToAlbum != null) {
                            onGoToAlbum(song, navController)
                        } else {
                            goToAlbum(song, navController)
                        }
                    },
                )
            }

            if (song.artistsName != null && navController != null) {
                row(
                    text = stringResource(R.string.song_menu_see_artists),
                    iconRes = R.drawable.ic_library_link_icon_artists,
                    onClick = {
                        onDismiss()
                        if (onGoToArtist != null) {
                            onGoToArtist(song, navController)
                        } else {
                            goToArtist(song, navController)
                        }
                    },
                )
            }
        }

        val hasExtras = onPickSleepTimer != null || onRefetchLyrics != null ||
            onPickLyricShare != null || onPickDownloadStatus != null
        if (hasExtras) {
            Spacer(modifier = Modifier.height(12.dp))

            AppleSheetMenuGroup {
                var first = true

                @Composable
                fun extraRow(
                    text: String,
                    iconRes: Int,
                    onClick: () -> Unit,
                ) {
                    val showTopDivider = !first
                    first = false
                    AppleSheetMenuRow(
                        text = text,
                        icon = iconRes,
                        onClick = onClick,
                        tint = rowTint,
                        showTopDivider = showTopDivider,
                    )
                }

                if (onPickSleepTimer != null) {
                    extraRow(
                        text = stringResource(R.string.now_playing_overflow_sleep_timer),
                        iconRes = R.drawable.ic_setting_moon,
                        onClick = onPickSleepTimer,
                    )
                }

                if (onRefetchLyrics != null) {
                    extraRow(
                        text = stringResource(R.string.now_playing_overflow_refetch_lyrics),
                        iconRes = R.drawable.ic_refresh,
                        onClick = {
                            onDismiss()
                            onRefetchLyrics()
                        },
                    )
                }

                if (onPickLyricShare != null) {
                    extraRow(
                        text = stringResource(R.string.now_playing_overflow_share_lyrics),
                        iconRes = R.drawable.ic_action_share,
                        onClick = onPickLyricShare,
                    )
                }

                if (onPickDownloadStatus != null) {
                    extraRow(
                        text = stringResource(R.string.now_playing_overflow_download_status),
                        iconRes = R.drawable.ic_action_recover,
                        onClick = onPickDownloadStatus,
                    )
                }
            }
        }
    }
}

internal fun goToAlbum(song: YosMediaItem, navController: NavController) {
    val albumName = song.album ?: return
    if (song.isLocalMediaItem()) {
        LibraryObject.setTargetAlbumName(albumName)
        navController.toUI(UI.AlbumInfo)
    } else {
        MainScope().launch(Dispatchers.IO) {
            val query = listOfNotNull(albumName, song.artistsName).joinToString(" ")
            val result = withTimeoutOrNull(12_000L) { YouTubeApi.search(query, "album") }
            val albums = result?.getOrNull()?.items?.filterIsInstance<YTAlbumSearchItem>().orEmpty()
            val album = albums.firstOrNull { it.title.equals(albumName, ignoreCase = true) }
                ?: albums.firstOrNull()
            if (album != null) {
                LibraryObject.setTargetBrowseId(album.browseId)
                navController.toUI(UI.OnlineAlbumInfo)
            }
        }
    }
}

internal fun goToArtist(song: YosMediaItem, navController: NavController) {
    val artists = song.artistsList
    if (song.isLocalMediaItem() || artists == null || artists.isEmpty()) {
        // Offline / unknown artist — fall back to local artist page
        LibraryObject.setTargetArtistName(song.artistsName ?: return)
        LibraryObject.setArtistSongsSearchOnOpen(false)
        navController.toUI(UI.ArtistInfo)
        return
    }

    // Single artist: go directly to their online page
    if (artists.size == 1) {
        resolveAndGoToOnlineArtist(artists.first(), navController)
        return
    }

    LibraryObject.setTargetListWithTitle(
        title = "Artists",
        list = artists.map { name ->
            YosMediaItem(
                title = name,
                artists = name,
            )
        },
    )
    navController.toUI(UI.SongArtistsList)
}

private fun resolveAndGoToOnlineArtist(artistName: String, navController: NavController) {
    MainScope().launch(Dispatchers.IO) {
        val result = withTimeoutOrNull(10_000L) {
            YouTubeApi.search(artistName, "artist")
        }
        val artists = result?.getOrNull()?.sections
            ?.firstOrNull { it.title == "Artists" }
            ?.artists.orEmpty()
        val matched = artists.firstOrNull { name ->
            name.name.equals(artistName, ignoreCase = true)
        } ?: artists.firstOrNull()

        withContext(Dispatchers.Main) {
            if (matched != null) {
                LibraryObject.setTargetBrowseId(matched.browseId)
                navController.toUI(UI.OnlineArtistInfo)
            } else {
                // Fallback: open local artist page
                LibraryObject.setTargetArtistName(artistName)
                LibraryObject.setArtistSongsSearchOnOpen(false)
                navController.toUI(UI.ArtistInfo)
            }
        }
    }
}

@Composable
private fun SongOverflowHeader(song: YosMediaItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowImageWithCache(
            dataLambda = { song.thumb },
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            cornerRadius = 6.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.LOW,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = song.title ?: defaultTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black withNight Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = listOfNotNull(song.artistsName ?: defaultArtistsName, song.album).joinToString(" • "),
                fontSize = 13.5.sp,
                color = Color.Black withNight Color.White,
                modifier = Modifier
                    .alpha(0.6f)
                    .padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
