package com.pryvn.audiophile.ui.pages.library.playlists

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.YosMediaItem

/**
 * Auto-generated 2×2 collage of the playlist's first 4 songs, in the order
 * they were added (PRD FR-E-05). The subsquares fill diagonally:
 *
 *   song 1 → top-left, song 2 → bottom-right (the diagonal pair),
 *   song 3 → top-right, song 4 → bottom-left (the last remaining square).
 *   More than 4 songs keep the same 4-cover collage.
 *
 * Fallbacks:
 *   1 song → that song's cover fills the whole square.
 *   2 songs → diagonal pair only; the other two squares stay blank.
 *   A song without artwork gets the default placeholder in its square.
 *
 * Shared between [com.pryvn.audiophile.ui.pages.library.NormalMusic]'s
 * playlist detail header and the Edit Playlist cover carousel.
 */
@Composable
fun PlayListAutoCover(songs: List<YosMediaItem>) {
    val context = LocalContext.current
    val covers = remember(songs) {
        songs.take(4).map { it.thumb }
    }
    if (covers.isEmpty()) {
        Image(
            painter = painterResource(id = R.drawable.placeholder_playlist_default),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    if (covers.size == 1) {
        // A single song's cover fills the whole square.
        CoverCell(
            thumb = covers[0],
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    // Subsquare fill order within the 2×2 grid (row-major 0..3):
    // covers[0] → 0 (top-left), covers[1] → 3 (bottom-right),
    // covers[2] → 1 (top-right), covers[3] → 2 (bottom-left).
    val order = remember(covers) { listOf(0, 3, 1, 2) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CoverCell(
                thumb = covers.getOrNull(order[0]),
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            CoverCell(
                thumb = covers.getOrNull(order[2]),
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CoverCell(
                thumb = covers.getOrNull(order[3]),
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            CoverCell(
                thumb = covers.getOrNull(order[1]),
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CoverCell(thumb: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbRes = thumb
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        if (thumbRes != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbRes).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.placeholder_playlist_default),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
