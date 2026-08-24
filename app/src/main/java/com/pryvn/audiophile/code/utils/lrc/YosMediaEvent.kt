package com.pryvn.audiophile.code.utils.lrc

/**
 * YosLyricView media event interface
 *
 * More features may be added to this interface in the future
 */
interface YosMediaEvent {
    /**
     * Seek event
     * @param position Target position to seek to
     */
    fun onSeek(position: Int)
}