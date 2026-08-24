package com.pryvn.audiophile.ui.widgets.song

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.cache.AudioCacheStore
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultArtistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.ui.theme.withNight

/**
 * Sub-screen of the Now Playing overflow menu: shows the per-song download
 * state of the current song plus every song currently being cached in the
 * background, with live progress bars.
 */
@Composable
fun DownloadStatusScreen(
    song: YosMediaItem?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val accent = Color(0xFF1E88E5)
    // Snapshot read: recomposes while downloads stream in or finish.
    val activeDownloads = AudioCacheStore.activeDownloads

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Vibrator.click(context)
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(0.6f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.now_playing_overflow_download_status),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        DownloadStatusDivider()

        // Current song: per-song Download indicator. Already-offline media
        // (cached songs and device-local files) counts as downloaded.
        if (song != null) {
            DownloadProgressRow(
                title = song.title ?: defaultTitle,
                subtitle = song.artistsName ?: defaultArtistsName,
                progress = AudioCacheStore.progressOf(song.mediaId),
                downloaded = AudioCacheStore.getCachedUri(song.mediaId) != null ||
                    song.isLocalMedia ||
                    song.uri?.scheme == "file" ||
                    song.uri?.scheme == "content",
                accent = accent,
                song = song,
                videoId = song.mediaId,
            )
            DownloadStatusDivider()
        }

        // Background downloads EXCLUDING the current song, which is already
        // shown in its own row above (otherwise it would be duplicated).
        val currentVideoId = song?.mediaId
        val otherDownloads = activeDownloads.filterKeys { it != currentVideoId }
        if (otherDownloads.isEmpty()) {
            Text(
                text = stringResource(R.string.download_status_empty),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .alpha(0.45f),
            )
        } else {
            otherDownloads.forEach { (videoId, progress) ->
                DownloadProgressRow(
                    title = AudioCacheStore.titleFor(videoId) ?: videoId,
                    subtitle = null,
                    progress = progress,
                    downloaded = false,
                    accent = accent,
                    videoId = videoId,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun DownloadStatusDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
}

@Composable
private fun DownloadProgressRow(
    title: String,
    subtitle: String?,
    progress: AudioCacheStore.DownloadProgress?,
    downloaded: Boolean = false,
    accent: Color,
    song: YosMediaItem? = null,
    videoId: String? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_recover),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (downloaded || progress != null) accent else accent.copy(alpha = 0.35f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .alpha(0.5f),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            val statusText = when {
                downloaded -> stringResource(R.string.download_status_local_already)
                progress != null -> if (progress.fraction >= 0f) {
                    stringResource(R.string.download_status_percent, (progress.fraction * 100).toInt())
                } else {
                    stringResource(R.string.download_status_downloading)
                }
                else -> stringResource(R.string.download_status_not_downloaded)
            }
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (downloaded || progress != null) {
                    accent
                } else {
                    (Color.Black withNight Color.White).copy(alpha = 0.4f)
                },
            )
            // 3-dot menu: only shown when a force download is actually possible
            // (song known, not already cached, and no download in flight).
            if (song != null && progress == null && !downloaded) {
                val menuOpen = remember(song.mediaId) { mutableStateOf(false) }
                Spacer(modifier = Modifier.width(6.dp))
                Box {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_more),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                Vibrator.click(context)
                                menuOpen.value = true
                            }
                            .padding(6.dp),
                        tint = (Color.Black withNight Color.White).copy(alpha = 0.45f),
                    )
                    DropdownMenu(
                        expanded = menuOpen.value,
                        onDismissRequest = { menuOpen.value = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.download_status_force_download),
                                    fontSize = 14.5.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_action_recover),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = accent,
                                )
                            },
                            onClick = {
                                menuOpen.value = false
                                MediaController.forceDownloadSong(song)
                            },
                        )
                    }
                }
            }
        }
        if (progress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Cancel: an 'x' sitting in front of the progress bar stops
                // the in-flight download for this song.
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            Vibrator.click(context)
                            AudioCacheStore.cancelDownload(videoId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_close),
                        contentDescription = stringResource(R.string.download_status_cancel_download),
                        modifier = Modifier.size(16.dp),
                        tint = (Color.Black withNight Color.White).copy(alpha = 0.5f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val fraction = progress.fraction
                if (fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.18f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.18f),
                    )
                }
            }
        }
    }
}
