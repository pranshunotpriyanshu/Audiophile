package com.pryvn.audiophile.ui.pages.library.artists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.ArtistLibrary
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.TitleWithLazyVerticalGrid

@Composable
fun ArtistAlbums(navController: NavController) {
    val artistName = rememberSaveable(key = "ArtistAlbums_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val sections = remember(artistName.value, MusicLibrary.songs) {
        ArtistLibrary.sectionsForArtist(artistName.value ?: "")
    }
    val releases = sections.albums

    if (artistName.value.isNullOrEmpty() || releases.isEmpty()) {
        Title(
            title = stringResource(id = R.string.page_library_albums),
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("ArtistAlbums_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.tip_no_song),
                        fontSize = 18.sp,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
            }
        }
        return
    }

    TitleWithLazyVerticalGrid(
        title = stringResource(id = R.string.page_library_albums),
        onBack = {
            navController.popBackStack()
        },
    ) {
        itemsIndexed(
            releases,
            key = { _, release -> release.albumName },
        ) { _, release ->
            ArtistReleaseGridCard(
                release = release,
                onClick = {
                    LibraryObject.setTargetAlbumName(release.albumName)
                    navController.toUI(UI.AlbumInfo)
                },
            )
        }
    }
}
