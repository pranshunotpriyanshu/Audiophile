package com.pryvn.audiophile.data.objects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.pryvn.audiophile.data.libraries.Folder
import com.pryvn.audiophile.data.libraries.YosMediaItem

@Stable
object LibraryObject {
    @Stable
    private val targetAlbumName = mutableStateOf("")
    fun setTargetAlbumName(name: String) {
        targetAlbumName.value = name
    }

    fun getTargetAlbumName(): String {
        return targetAlbumName.value
    }

    @Stable
    private val targetPlaylistId = mutableStateOf("")
    fun setTargetPlaylistId(id: String) { targetPlaylistId.value = id }
    fun getTargetPlaylistId(): String { return targetPlaylistId.value }

    @Stable
    private val targetBrowseId = mutableStateOf("")
    fun setTargetBrowseId(id: String) { targetBrowseId.value = id }
    fun getTargetBrowseId(): String { return targetBrowseId.value }

    @Stable
    private val targetArtistName = mutableStateOf<String?>(null)
    fun setTargetArtistName(name: String?) { targetArtistName.value = name }
    fun getTargetArtistName(): String? { return targetArtistName.value }

    @Stable
    private val artistSongsSearchOnOpen = mutableStateOf(false)
    fun setArtistSongsSearchOnOpen(open: Boolean) { artistSongsSearchOnOpen.value = open }
    fun consumeArtistSongsSearchOnOpen(): Boolean {
        val value = artistSongsSearchOnOpen.value
        artistSongsSearchOnOpen.value = false
        return value
    }

    @Stable
    private val targetList: MutableState<List<YosMediaItem>> = mutableStateOf(emptyList())
    @Stable
    private val targetListTitle = mutableStateOf("")
    fun setTargetListWithTitle(title: String, list: List<YosMediaItem>, playListId: String? = null) {
        targetListTitle.value = title
        targetList.value = list
        if (playListId != null) {
            targetPlaylistId.value = playListId
        }
    }
    fun getTargetListWithTitle(): Pair<String, List<YosMediaItem>> {
        return Pair(targetListTitle.value, targetList.value)
    }
}