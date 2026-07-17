package com.pryvn.audiophile.ui.widgets.basic

import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pryvn.audiophile.code.AnimatedArtworkLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import java.io.File

@Stable
class AnimatedAlbumCoverState internal constructor(
    internal val player: ExoPlayer?,
    internal val isPlaying: Boolean,
    internal val videoPrepared: Boolean
)

@Composable
fun rememberAnimatedAlbumCoverState(
    music: YosMediaItem?,
    isPlaying: Boolean,
    active: Boolean
): AnimatedAlbumCoverState
{
    val context = LocalContext.current
    val animatedAlbumCovers = SettingsLibrary.AnimatedAlbumCovers
    val animatedAlbumCoversUseApi = SettingsLibrary.AnimatedAlbumCoversUseApi
    val animatedAlbumCoverBlacklist = SettingsLibrary.AnimatedAlbumCoverBlacklist
    var animatedArtworkFile by remember(music?.uri, music?.album, active, animatedAlbumCovers, animatedAlbumCoversUseApi, animatedAlbumCoverBlacklist) {
        mutableStateOf<File?>(null)
    }

    LaunchedEffect(music?.uri, music?.album, active, animatedAlbumCovers, animatedAlbumCoversUseApi, animatedAlbumCoverBlacklist)
    {
        animatedArtworkFile = if (!active || music == null)
        {
            null
        }
        else
        {
            AnimatedArtworkLibrary.resolveArtworkFile(context.applicationContext, music)
        }
    }

    val artworkFile = animatedArtworkFile
    if (artworkFile == null) {return AnimatedAlbumCoverState(null, isPlaying, false)}

    val artworkUri = remember(artworkFile) { Uri.fromFile(artworkFile) }
    val latestIsPlaying = rememberUpdatedState(isPlaying)
    var videoPrepared by remember(artworkFile) { mutableStateOf(false) }
    val player = remember(artworkFile) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(artworkUri))
            prepare()
        }
    }

    DisposableEffect(player)
    {
        val listener = object : Player.Listener
        {
            override fun onRenderedFirstFrame()
            {
                videoPrepared = true
            }

            override fun onPlayerError(error: PlaybackException)
            {
                videoPrepared = false
            }

            override fun onPlaybackStateChanged(playbackState: Int)
            {
                if (playbackState == Player.STATE_ENDED && latestIsPlaying.value)
                {
                    player.seekTo(0L)
                    player.play()
                }
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isPlaying)
    {
        player.playWhenReady = isPlaying
        if (isPlaying) {player.play()} else {player.pause()}
    }

    return AnimatedAlbumCoverState(player, isPlaying, videoPrepared)
}

@Composable
fun BoxScope.AnimatedAlbumCoverOverlay(state: AnimatedAlbumCoverState)
{
    val player = state.player ?: return
    var textureView by remember(player) { mutableStateOf<TextureView?>(null) }
    val videoAlpha by animateFloatAsState(
        targetValue = if (state.videoPrepared) {1f} else {0f},
        animationSpec = tween(durationMillis = 200),
        label = "AnimatedAlbumCoverAlpha"
    )

    DisposableEffect(player)
    {
        onDispose {
            textureView?.let { player.clearVideoTextureView(it) }
        }
    }

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            if (view.tag != player)
            {
                view.tag = player
                textureView = view
                player.setVideoTextureView(view)
            }

            player.playWhenReady = state.isPlaying
            if (state.isPlaying) {player.play()} else {player.pause()}
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = videoAlpha
            }
    )
}
