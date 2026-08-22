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
