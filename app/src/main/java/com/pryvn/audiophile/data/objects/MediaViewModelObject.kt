package com.pryvn.audiophile.data.objects

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics

data class WordSyncedWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isBackground: Boolean = false
)

data class WordSyncedLine(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<WordSyncedWord> = emptyList(),
    val agent: String? = null,
    val transliteration: String? = null,
    val subtitle: String? = null
)

enum class PlaybackLoadingState {
    Idle,
    ResolvingStream,
    PreparingPlayer,
    Buffering,
    Playing,
    Paused,
    Error
}

@Stable
object MediaViewModelObject {
    val lrcEntries: MutableState<List<List<Pair<Float, String>>>> = mutableStateOf(listOf())
    val otherSideForLines = mutableStateListOf<Boolean>()

    val backgroundLines = mutableStateListOf<Boolean>()

    val bitmap: MutableState<Uri?> = mutableStateOf(null)

    val isPlaying: MutableState<Boolean> = mutableStateOf(false)
    val isBuffering: MutableState<Boolean> = mutableStateOf(false)

    val bitrate = mutableIntStateOf(0)
    val samplingRate = mutableIntStateOf(0)
    val isDolby = mutableStateOf(false)

    val paletteVibrantColor: MutableState<Color> = mutableStateOf(Color.Black)
    val paletteDarkVibrantColor: MutableState<Color> = mutableStateOf(Color.Black)
    val paletteDarkMutedColor: MutableState<Color> = mutableStateOf(Color.Black)

    val nextPaletteSongId: MutableState<String?> = mutableStateOf(null)
    val nextPaletteVibrantColor: MutableState<Color> = mutableStateOf(Color.Black)
    val nextPaletteDarkVibrantColor: MutableState<Color> = mutableStateOf(Color.Black)
    val nextPaletteDarkMutedColor: MutableState<Color> = mutableStateOf(Color.Black)

    val isUnsyncedLyrics: MutableState<Boolean> = mutableStateOf(false)

    val onlineLyrics: MutableState<String?> = mutableStateOf(null)
    val translatedLyrics: MutableState<String?> = mutableStateOf(null)
    val lyricsSource: MutableState<String?> = mutableStateOf(null)
    val currentVideoId: MutableState<String?> = mutableStateOf(null)
    val isLoadingLyrics: MutableState<Boolean> = mutableStateOf(false)

    val lyricsCache: MutableMap<String, String> = mutableMapOf()

    val parsedSyncedLyrics: MutableState<SyncedLyrics?> = mutableStateOf(null)

    val wordSyncedLines: MutableState<List<WordSyncedLine>> = mutableStateOf(emptyList())
    val hasWordSyncedLyrics: MutableState<Boolean> = mutableStateOf(false)

    val lyricLineTransliterations = mutableStateListOf<String?>()
    val lyricLineSubtitles = mutableStateListOf<String?>()

    val playbackLoadingState: MutableState<PlaybackLoadingState> = mutableStateOf(PlaybackLoadingState.Idle)

    fun clearWordSync() {
        hasWordSyncedLyrics.value = false
        wordSyncedLines.value = emptyList()
        parsedSyncedLyrics.value = null
        lyricLineTransliterations.clear()
        lyricLineSubtitles.clear()
        backgroundLines.clear()
    }
}
