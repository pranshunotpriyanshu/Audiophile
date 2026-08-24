package com.pryvn.audiophile.code.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerAdapter(private val player: ExoPlayer) : PlayerAdapter {
    override val currentPosition: Long get() = player.currentPosition
    override val duration: Long get() = player.duration
    override val isPlaying: Boolean get() = player.isPlaying
    override val currentMediaItem: MediaItem? get() = player.currentMediaItem
    override val currentMediaItemIndex: Int get() = player.currentMediaItemIndex
    override val mediaItemCount: Int get() = player.mediaItemCount
    override val audioSessionId: Int get() = player.audioSessionId
    override var volume: Float
        get() = player.volume
        set(value) { player.volume = value }
    override var shuffleModeEnabled: Boolean
        get() = player.shuffleModeEnabled
        set(value) { player.shuffleModeEnabled = value }
    override var repeatMode: Int
        get() = player.repeatMode
        set(value) { player.repeatMode = value }
    override val hasNextMediaItem: Boolean get() = player.hasNextMediaItem()
    override val hasPreviousMediaItem: Boolean get() = player.hasPreviousMediaItem()

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun stop() = player.stop()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun seekTo(index: Int, positionMs: Long) = player.seekTo(index, positionMs)
    override fun seekToNext() = player.seekToNext()
    override fun seekToPrevious() = player.seekToPrevious()
    override fun prepare() = player.prepare()
    override fun playWhenReady(playWhenReady: Boolean) { player.playWhenReady = playWhenReady }
    override fun setMediaItem(mediaItem: MediaItem) = player.setMediaItem(mediaItem)
    override fun setMediaItems(mediaItems: List<MediaItem>, index: Int, positionMs: Long) = player.setMediaItems(mediaItems, index, positionMs)
    override fun addMediaItem(index: Int, mediaItem: MediaItem) = player.addMediaItem(index, mediaItem)
    override fun addMediaItem(mediaItem: MediaItem) = player.addMediaItem(mediaItem)
    override fun removeMediaItem(index: Int) = player.removeMediaItem(index)
    override fun moveMediaItem(fromIndex: Int, toIndex: Int) = player.moveMediaItem(fromIndex, toIndex)
    override fun clearMediaItems() = player.clearMediaItems()
    override fun addListener(listener: Player.Listener) = player.addListener(listener)
    override fun removeListener(listener: Player.Listener) = player.removeListener(listener)
    override fun release() = player.release()
}
