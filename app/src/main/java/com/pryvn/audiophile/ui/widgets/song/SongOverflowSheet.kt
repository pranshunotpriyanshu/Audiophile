package com.pryvn.audiophile.ui.widgets.song

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.data.libraries.FavPlayListLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultArtistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.ActionItem
import com.pryvn.audiophile.ui.widgets.basic.ActionSheetBody
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.basic.YosBottomSheetDialog
import com.pryvn.audiophile.ui.widgets.playlist.PlayListPickerContent

private enum class SongOverflowScreen { Menu, AddToPlaylist }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOverflowSheet(
    isOpen: MutableState<Boolean>,
    song: YosMediaItem?,
    navController: NavController? = null,
) {
    if (!isOpen.value || song == null) return

    var screen by remember { mutableStateOf(SongOverflowScreen.Menu) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onDismiss: () -> Unit = {
        isOpen.value = false
        screen = SongOverflowScreen.Menu
    }

    YosBottomSheetDialog(
        bottomSheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        when (screen) {
            SongOverflowScreen.Menu -> SongOverflowMenuBody(
                song = song,
                navController = navController,
                onAddToPlaylist = { screen = SongOverflowScreen.AddToPlaylist },
                onDismiss = onDismiss,
            )

            SongOverflowScreen.AddToPlaylist -> PlayListPickerContent(
                songToAdd = song,
                onDone = onDismiss,
            )
        }
    }
}

@Composable
private fun SongOverflowMenuBody(
    song: YosMediaItem,
    navController: NavController?,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isFav = FavPlayListLibrary.isFavorite(song)

    val items = mutableListOf(
        ActionItem(
            iconRes = R.drawable.ic_action_play_next,
            label = stringResource(R.string.playlist_overflow_play_next),
            showChevron = false,
            onClick = {
                onDismiss()
                MainScope().launch(Dispatchers.IO) {
                    MediaController.playNext(listOf(song))
                }
                Toast.makeText(context, context.getString(R.string.playlist_play_next_toast_one), Toast.LENGTH_SHORT).show()
            },
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_add,
            label = stringResource(R.string.now_playing_overflow_add_to_playlist),
            onClick = onAddToPlaylist,
        ),
        ActionItem(
            iconRes = if (isFav) R.drawable.ic_nowplaying_favorited else R.drawable.ic_nowplaying_favorite,
            label = if (isFav) stringResource(R.string.album_action_unfavorite) else stringResource(R.string.album_action_favorite),
            showChevron = false,
            onClick = {
                if (isFav) {
                    FavPlayListLibrary.removeMusic(song)
                } else {
                    FavPlayListLibrary.addMusic(song)
                }
            },
        ),
    )

    if (song.album != null && navController != null) {
        items.add(
            ActionItem(
                iconRes = R.drawable.ic_library_link_icon_album,
                label = stringResource(R.string.page_library_search_album_tracks),
                showChevron = false,
                onClick = {
                    onDismiss()
                    LibraryObject.setTargetAlbumName(song.album!!)
                    navController.toUI(UI.AlbumInfo)
                },
            )
        )
    }

    if (song.artistsName != null && navController != null) {
        items.add(
            ActionItem(
                iconRes = R.drawable.ic_library_link_icon_artists,
                label = stringResource(R.string.page_library_artists),
                showChevron = false,
                onClick = {
                    onDismiss()
                    LibraryObject.setTargetArtistName(song.artistsName!!)
                    LibraryObject.setArtistSongsSearchOnOpen(false)
                    navController.toUI(UI.ArtistInfo)
                },
            )
        )
    }

    ActionSheetBody(
        header = { SongOverflowHeader(song = song) },
        items = items,
    )
}

@Composable
private fun SongOverflowHeader(song: YosMediaItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = listOfNotNull(song.artistsName ?: defaultArtistsName, song.album).joinToString(" • "),
                fontSize = 13.5.sp,
                modifier = Modifier
                    .alpha(0.6f)
                    .padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}