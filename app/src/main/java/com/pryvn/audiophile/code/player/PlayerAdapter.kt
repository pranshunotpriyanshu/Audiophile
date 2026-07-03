package com.pryvn.audiophile.code.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

interface PlayerAdapter {
    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
    val currentMediaItem: MediaItem?
    val currentMediaItemIndex: Int
    val mediaItemCount: Int
    val audioSessionId: Int
    var volume: Float
    var shuffleModeEnabled: Boolean
    var repeatMode: Int
    val hasNextMediaItem: Boolean
    val hasPreviousMediaItem: Boolean

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekTo(index: Int, positionMs: Long)
    fun seekToNext()
    fun seekToPrevious()
    fun prepare()
    fun playWhenReady(playWhenReady: Boolean)
    fun setMediaItem(mediaItem: MediaItem)
    fun setMediaItems(mediaItems: List<MediaItem>, index: Int, positionMs: Long)
    fun addMediaItem(index: Int, mediaItem: MediaItem)
    fun addMediaItem(mediaItem: MediaItem)
    fun removeMediaItem(index: Int)
    fun moveMediaItem(fromIndex: Int, toIndex: Int)
    fun clearMediaItems()
    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)
    fun release()
}
