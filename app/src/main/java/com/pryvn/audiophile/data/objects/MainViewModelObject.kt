package com.pryvn.audiophile.data.objects

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

@Stable
object MainViewModelObject {
    val syncLyricIndex = mutableIntStateOf(-1)

    // val nowPage = mutableStateOf(NowPlayingPage.Album)
}