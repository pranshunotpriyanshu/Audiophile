package com.pryvn.audiophile.data.libraries

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverListStateOf
import kotlinx.parcelize.Parcelize
import com.pryvn.audiophile.data.PlayListSaver
import java.util.UUID

@Stable
@Parcelize
data class PlayList(
    val listID: String,
    val name: String,
    val songDataList: List<Uri>,
    val description: String? = null,
    /** Custom cover image URI (content:// or file://) chosen by the user.
     *  When null the playlist falls back to the auto-generated collage
     *  built from [songDataList]. */
    val coverUri: String? = null,
    /** Pin state — controls placement in the Library playlist list. */
    val isPinned: Boolean = false,
    /** Sort key within the pinned block. Lower = higher in the list.
     *  Only meaningful when [isPinned] is true. Gaps are tolerated;
     *  unpin clears the value. */
    val pinOrder: Int? = null,
) : Parcelable

@Stable
object PlayListLibrary {

    @Stable
    var playList by mutableDataSaverListStateOf(
        dataSaverInterface = PlayListSaver,
        key = "yos_play_list",
        initialValue = listOf<PlayList>()
    )
        private set

    /**
     * Replace [old] with [new] in-place. Preserves the list's overall
     * order (the original position of [old]) rather than appending the
     * replacement to the end.
     */
    private fun replace(old: PlayList, new: PlayList) {
        val idx = playList.indexOfFirst { it.listID == old.listID }
        if (idx < 0) {
            playList = playList + new
        } else {
            playList = playList.toMutableList().also { it[idx] = new }
        }
    }

    fun PlayList.addMusic(music: YosMediaItem) {
        val uri = music.uri ?: return
        replace(this, copy(songDataList = songDataList + uri))
    }

    fun PlayList.removeMusic(music: YosMediaItem) {
        val idx = songDataList.indexOfFirst { it == music.uri }
        if (idx < 0) return
        replace(this, copy(songDataList = songDataList.toMutableList().also { it.removeAt(idx) }))
    }

    fun PlayList.rename(name: String) = replace(this, copy(name = name))

    fun PlayList.applyEdits(
        name: String,
        description: String?,
        coverUri: String?,
        songs: List<Uri>,
    ) = replace(
        this,
        copy(
            name = name,
            description = description,
            coverUri = coverUri,
            songDataList = songs,
        ),
    )

    /** Pin places the playlist at the bottom of the pinned block (max
     *  pinOrder + 1). */
    fun PlayList.pin() {
        if (isPinned) return
        val nextOrder = (playList.mapNotNull { it.pinOrder }.maxOrNull() ?: -1) + 1
        replace(this, copy(isPinned = true, pinOrder = nextOrder))
    }

    /** Unpin clears [pinOrder]; remaining pins keep theirs. */
    fun PlayList.unpin() {
        if (!isPinned) return
        replace(this, copy(isPinned = false, pinOrder = null))
    }

    /**
     * Bulk-set pin order for the long-press reorder gesture. [ordering] is
     * the desired top-to-bottom order of pinned playlists' listIDs;
     * pinOrder is reassigned to that index. Non-pinned playlists untouched.
     */
    fun reorderPins(ordering: List<String>) {
        val orderMap = ordering.withIndex().associate { (i, id) -> id to i }
        playList = playList.map { pl ->
            if (pl.isPinned && orderMap.containsKey(pl.listID)) {
                pl.copy(pinOrder = orderMap[pl.listID])
            } else pl
        }
    }

    fun create(name: String) {
        if (!playList.any { it.name == name }) {
            playList = playList + PlayList(UUID.randomUUID().toString(), name, listOf())
        }
    }

    fun remove(list: PlayList) {
        playList = playList.filterNot { it.listID == list.listID }
    }

    /**
     * Re-insert a previously-removed playlist at its original index (used
     * by the delete-undo flow). When [originalIndex] is out of range the
     * playlist is appended to the end.
     */
    fun restore(list: PlayList, originalIndex: Int) {
        if (playList.any { it.listID == list.listID }) return
        val safeIndex = originalIndex.coerceIn(0, playList.size)
        playList = playList.toMutableList().also { it.add(safeIndex, list) }
    }
}

@Stable
object FavPlayListLibrary {
    @Stable
    var favPlayList by mutableDataSaverListStateOf(
        dataSaverInterface = PlayListSaver,
        key = "yos_fav_play_list",
        initialValue = listOf<YosMediaItem>()
    )
        private set

    fun addMusic(music: YosMediaItem) {
        if (!favPlayList.any { it.uri == music.uri }) {
            favPlayList += music
        }
    }

    fun removeMusic(music: YosMediaItem) {
        favPlayList -= music
    }

    fun isFavorite(music: YosMediaItem): Boolean = favPlayList.any { it.uri == music.uri }
}