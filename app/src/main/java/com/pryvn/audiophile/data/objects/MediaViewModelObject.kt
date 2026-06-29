package com.pryvn.audiophile.data.objects

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

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
    val words: List<WordSyncedWord> = emptyList()
)

@Stable
object MediaViewModelObject {
    val lrcEntries: MutableState<List<List<Pair<Float, String>>>> = mutableStateOf(listOf())
    val otherSideForLines = mutableStateListOf<Boolean>()

    // var mainLyricLines = mutableStateListOf<AnnotatedString>()

    val bitmap: MutableState<Uri?> = mutableStateOf(null)

    val isPlaying: MutableState<Boolean> = mutableStateOf(false)

    val bitrate = mutableIntStateOf(0)
    val samplingRate = mutableIntStateOf(0)
    val isDolby = mutableStateOf(false)

    val paletteVibrantColor: MutableState<Color> = mutableStateOf(Color.Black)
    val paletteDarkVibrantColor: MutableState<Color> = mutableStateOf(Color.Black)
    val paletteDarkMutedColor: MutableState<Color> = mutableStateOf(Color.Black)

    val onlineLyrics: MutableState<String?> = mutableStateOf(null)
    val translatedLyrics: MutableState<String?> = mutableStateOf(null)
    val lyricsSource: MutableState<String?> = mutableStateOf(null)
    val currentVideoId: MutableState<String?> = mutableStateOf(null)
    val isLoadingLyrics: MutableState<Boolean> = mutableStateOf(false)

    val lyricsCache: MutableMap<String, String> = mutableMapOf()

    val wordSyncedLines: MutableState<List<WordSyncedLine>> = mutableStateOf(emptyList())
    val hasWordSyncedLyrics: MutableState<Boolean> = mutableStateOf(false)
}