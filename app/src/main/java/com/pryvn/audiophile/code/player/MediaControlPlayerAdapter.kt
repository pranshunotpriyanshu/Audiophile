package com.pryvn.audiophile.code.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.pryvn.audiophile.code.MediaController

object MediaControlPlayerAdapter : PlayerAdapter {
    private val mc get() = MediaController.mediaControl

    override val currentPosition: Long
        get() = mc?.currentPosition ?: 0L
    override val duration: Long
        get() = mc?.duration ?: 0L
    override val isPlaying: Boolean
        get() = mc?.isPlaying ?: false
    override val currentMediaItem: MediaItem?
        get() = mc?.currentMediaItem
    override val currentMediaItemIndex: Int
        get() = mc?.currentMediaItemIndex ?: 0
    override val mediaItemCount: Int
        get() = mc?.mediaItemCount ?: 0
    override val audioSessionId: Int
        get() = 0
    override var volume: Float
        get() = mc?.volume ?: 1f
        set(value) { mc?.let { it.volume = value } }
    override var shuffleModeEnabled: Boolean
        get() = mc?.shuffleModeEnabled ?: false
        set(value) { mc?.let { it.shuffleModeEnabled = value } }
    override var repeatMode: Int
        get() = mc?.repeatMode ?: Player.REPEAT_MODE_OFF
        set(value) { mc?.let { it.repeatMode = value } }
    override val hasNextMediaItem: Boolean
        get() = mc?.hasNextMediaItem() ?: false
    override val hasPreviousMediaItem: Boolean
        get() = mc?.hasPreviousMediaItem() ?: false

    override fun play() { mc?.play() }
    override fun pause() { mc?.pause() }
    override fun stop() { mc?.stop() }
    override fun seekTo(positionMs: Long) { mc?.seekTo(positionMs) }
    override fun seekTo(index: Int, positionMs: Long) { mc?.seekTo(index, positionMs) }
    override fun seekToNext() { mc?.seekToNext() }
    override fun seekToPrevious() { mc?.seekToPrevious() }
    override fun prepare() { mc?.prepare() }
    override fun playWhenReady(playWhenReady: Boolean) { mc?.playWhenReady = playWhenReady }
    override fun setMediaItem(mediaItem: MediaItem) { mc?.setMediaItem(mediaItem) }
    override fun setMediaItems(mediaItems: List<MediaItem>, index: Int, positionMs: Long) { mc?.setMediaItems(mediaItems, index, positionMs) }
    override fun addMediaItem(index: Int, mediaItem: MediaItem) { mc?.addMediaItem(index, mediaItem) }
    override fun addMediaItem(mediaItem: MediaItem) { mc?.addMediaItem(mediaItem) }
    override fun removeMediaItem(index: Int) { mc?.removeMediaItem(index) }
    override fun moveMediaItem(fromIndex: Int, toIndex: Int) { mc?.moveMediaItem(fromIndex, toIndex) }
    override fun clearMediaItems() { mc?.clearMediaItems() }
    override fun addListener(listener: Player.Listener) { mc?.addListener(listener) }
    override fun removeListener(listener: Player.Listener) { mc?.removeListener(listener) }
    override fun release() { mc?.release() }
}
