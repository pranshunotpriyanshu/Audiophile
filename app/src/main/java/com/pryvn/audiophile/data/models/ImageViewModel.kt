package com.pryvn.audiophile.data.models

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.pryvn.audiophile.data.libraries.YosMediaItem

class ImageViewModel : ViewModel() {
    val recommendMusicList: MutableState<List<YosMediaItem>> = mutableStateOf(emptyList())
}
