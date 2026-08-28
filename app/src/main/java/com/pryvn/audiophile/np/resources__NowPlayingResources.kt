package com.pryvn.audiophile.np.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

interface DrawablePainterSource {
    @Composable
    fun forDrawable(name: String): Painter
}

interface StringSource {
    @Composable
    fun string(name: String): String

    fun plain(name: String): String
}

object NpPainters {
    lateinit var source: DrawablePainterSource

    @Composable
    fun forDrawable(name: String): Painter = source.forDrawable(name)
}

object NpValues {
    lateinit var stringSource: StringSource

    @Composable
    fun string(name: String): String = stringSource.string(name)

    fun plain(name: String): String = stringSource.plain(name)
}

object NpDrawables {
    const val IC_EARPHONE = "ic_earphone"
    const val IC_NOWPLAYING_AIRPLAY = "ic_nowplaying_airplay"
    const val IC_NOWPLAYING_FAVORITE = "ic_nowplaying_favorite"
    const val IC_NOWPLAYING_FAVORITED = "ic_nowplaying_favorited"
    const val IC_NOWPLAYING_FFORWARD = "ic_nowplaying_fforward"
    const val IC_NOWPLAYING_LYRICS = "ic_nowplaying_lyrics"
    const val IC_NOWPLAYING_LYRICSON = "ic_nowplaying_lyricson"
    const val IC_NOWPLAYING_MORE = "ic_nowplaying_more"
    const val IC_NOWPLAYING_MORE_FILL = "ic_nowplaying_more_fill"
    const val IC_NOWPLAYING_MP_PAUSE = "ic_nowplaying_mp_pause"
    const val IC_NOWPLAYING_MP_PLAY = "ic_nowplaying_mp_play"
    const val IC_NOWPLAYING_PAUSE = "ic_nowplaying_pause"
    const val IC_NOWPLAYING_PLAY = "ic_nowplaying_play"
    const val IC_NOWPLAYING_QUEUE = "ic_nowplaying_queue"
    const val IC_NOWPLAYING_QUEUEON = "ic_nowplaying_queueon"
    const val IC_NOWPLAYING_REPEAT = "ic_nowplaying_repeat"
    const val IC_NOWPLAYING_REPEATONE = "ic_nowplaying_repeatone"
    const val IC_NOWPLAYING_REWIND = "ic_nowplaying_rewind"
    const val IC_NOWPLAYING_SHUFFLE = "ic_nowplaying_shuffle"
    const val IC_NOWPLAYING_TRANSLATE = "ic_nowplaying_translate"
    const val IC_NOWPLAYING_TRANSLATEON = "ic_nowplaying_translateon"
    const val IC_NOWPLAYING_VOCAL = "ic_nowplaying_vocal"
    const val IC_NOWPLAYING_VOLUME = "ic_nowplaying_volume"
    const val IC_NOWPLAYING_VOLUME_FULL = "ic_nowplaying_volume_full"
    const val IC_SWIPE_QUEUE = "ic_swipe_queue"
    const val IC_UITABBAR_LIBRARY = "ic_uitabbar_library"
}

object NpStrings {
    const val NOW_PLAYING_MSS_TOGGLE = "now_playing_mss_toggle"
    const val NOW_PLAYING_MSS_FAILED = "now_playing_mss_failed"
    const val NOW_PLAYING_MSS_UNAVAILABLE = "now_playing_mss_unavailable"
    const val NOWPLAYING_MORE_ADD_TO_FAVORITE = "nowplaying_more_add_to_favorite"
    const val NOWPLAYING_MORE_ADD_TO_PLAYLIST = "nowplaying_more_add_to_playlist"
    const val NOWPLAYING_MORE_PLAYLIST_BACK = "nowplaying_more_playlist_back"
    const val NOWPLAYING_MORE_PLAYLIST_EMPTY = "nowplaying_more_playlist_empty"
    const val PLAYLIST_UNAVAILABLE_TITLE = "playlist_unavailable_title"
    const val QUEUE_ADDED_TOAST = "queue_added_toast"
}

