@file:Suppress("SameParameterValue")

package com.pryvn.audiophile.data.libraries

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.funny.data_saver.core.mutableDataSaverListStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import uk.akane.libphonograph.constructor.ItemConstructor
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.reader.ReaderConfiguration
import uk.akane.libphonograph.reader.ReaderResult
import com.pryvn.audiophile.UriTypeAdapter
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.data.NormalSaver
import com.pryvn.audiophile.data.SongListSaver

/*@Parcelize
@Stable
data class Music(
    val title: String,
    val artist: List<String>,
    val album: String,
    val path: String,
    val date: Long,
    val id: Long,
    var thumb: String?,
    val duration: Long = 0,
    val bitrate: Int,
    val samplingRate: Int
) : Parcelable {
    fun artistsToString(): String {
        return artist.joinToString("、")
    }
}*/

@Stable
data class Time(
    val min: String,
    val sec: String
)
@Parcelize
@Stable
data class PlayListV1(
    val mainMusicList: List<YosMediaItem>? = null,
    val playingMusicList: List<YosMediaItem>? = null,
    val nextInQueueMusicList: List<YosMediaItem>? = null,
    val historyMusicList: List<YosMediaItem>? = null,
    val musicPlaying: YosMediaItem? = null,
    val shuffleModeEnabled: Boolean = false,
    val playingMusicUris: List<String>? = null,
    val nextInQueueMusicUris: List<String>? = null,
    val historyMusicUris: List<String>? = null,
    val musicPlayingUri: String? = null,
) : Parcelable

@Parcelize
@Stable
data class PlayStatus(
    val music: YosMediaItem?,
    val position: Long,
    val shuffleModeEnabled: Boolean,
    val repeatMode: Int
) : Parcelable

@Parcelize
@Stable
data class Folder(val name: String, val path: String, val songs: List<YosMediaItem>) : Parcelable

@Parcelize
@Stable
data class YosMediaItem(
    val uri: Uri? = null,
    val mediaId: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
    val writer: String? = null,
    val compilation: String? = null,
    val composer: String? = null,
    val artists: String? = null,
    val album: String? = null,
    val albumArtists: String? = null,
    val thumb: Uri? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val genre: String? = null,
    val recordingDay: Int? = null,
    val recordingMonth: Int? = null,
    val recordingYear: Int? = null,
    val releaseYear: Int? = null,
    val artistId: Long? = null,
    val albumId: Long? = null,
    val genreId: Long? = null,
    val author: String? = null,
    val addDate: Long? = null,
    val duration: Long = 0L,
    val modifiedDate: Long? = null,
    val cdTrackNumber: Int? = null
) : Parcelable

@Stable
@Parcelize
data class YosStringWrapper(val value: String) : Parcelable

@Stable
object MusicLibrary {
    // yos_player_core 负责歌曲列表 V1、播放状态记录

    private const val mmkvID = "yos_player_core"
    private const val playListKey = "yos_play_list_v1"
    private const val playStatusKey = "yos_player_play_status"

    private const val lyricsMmkvID = "yos_lyrics_cache"
    private const val lyricsPrefix = "lyrics_"
    private val lyricsMmkv by lazy { MMKV.mmkvWithID(lyricsMmkvID) }

    fun saveLyrics(key: String, text: String) {
        lyricsMmkv.encode(lyricsPrefix + key, text)
    }

    fun loadPersistedLyrics(key: String): String? {
        return lyricsMmkv.decodeString(lyricsPrefix + key)
    }

    private val cacheMmkv by lazy { MMKV.mmkvWithID("yos_playback_cache") }
    private const val visitorDataKey = "cached_visitor_data"
    private const val cookieKey = "cached_cookie"
    private const val dataSyncIdKey = "cached_data_sync_id"
    private const val potokenPlayerKey = "cached_potoken_player"
    private const val potokenGvsKey = "cached_potoken_gvs"
    private const val potokenExpiryKey = "cached_potoken_expiry"

    fun saveCachedVisitorData(value: String) {
        cacheMmkv.encode(visitorDataKey, value)
    }

    fun loadCachedVisitorData(): String? {
        return cacheMmkv.decodeString(visitorDataKey)
    }

    fun saveCachedCookie(value: String) {
        cacheMmkv.encode(cookieKey, value)
    }

    fun loadCachedCookie(): String? {
        return cacheMmkv.decodeString(cookieKey)
    }

    fun saveCachedDataSyncId(value: String) {
        cacheMmkv.encode(dataSyncIdKey, value)
    }

    fun loadCachedDataSyncId(): String? {
        return cacheMmkv.decodeString(dataSyncIdKey)
    }

    fun saveCachedPoToken(playerToken: String, gvsToken: String, expiryMs: Long) {
        cacheMmkv.encode(potokenPlayerKey, playerToken)
        cacheMmkv.encode(potokenGvsKey, gvsToken)
        cacheMmkv.encode(potokenExpiryKey, expiryMs)
    }

    fun loadCachedPoToken(): Triple<String?, String?, Long> {
        val player = cacheMmkv.decodeString(potokenPlayerKey)
        val gvs = cacheMmkv.decodeString(potokenGvsKey)
        val expiry = cacheMmkv.decodeLong(potokenExpiryKey, 0L)
        return Triple(player, gvs, expiry)
    }

    var hideSongs by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver,
        key = "hide_songs",
        initialValue = listOf<YosMediaItem>()
    )
        private set

    var folders by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver,
        key = "folders",
        initialValue = listOf<Folder>()
    )
        private set

    private var hideFoldersSaver by mutableDataSaverListStateOf(
        dataSaverInterface = NormalSaver, key = "hide_folders", initialValue = listOf<YosStringWrapper>()
    )

    val hideFolders: List<String>
        get() = hideFoldersSaver.map { it.value }

    private var songSaver by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver, key = "songs", initialValue = listOf<YosMediaItem>()
    )

    val songs: List<YosMediaItem>
        get() = songSaver
            .filter { it !in hideSongs && it.uri !in folders.filter { thisFolders -> thisFolders.path in hideFolders }
                .flatMap { thisFlatMap -> thisFlatMap.songs }
                .map { thisMap -> thisMap.uri } }

    val artists
        get() = songs/*.distinctBy { it.artist }.map { it.artist }*/.flatMap {
            it.artistsList ?: defaultArtists
        }
            .distinct()

    val albums
        get() = songs.distinctBy { it.album ?: defaultAlbum }.map { it.album ?: defaultAlbum }

    @Stable
    object Album {
        operator fun get(albumName: String) =
            songs.filter { (it.album ?: defaultAlbum) == albumName }
    }

    @Stable
    object Artist {
        operator fun get(artistName: String) =
            songs.filter { (it.artistsList ?: defaultArtists).contains(artistName) }
    }

    fun updatePlayList(playListV1: PlayListV1) {
        updateData(playListKey, playListV1)
        println("updatePlayList $playListV1")
    }

    fun loadPlayList(): PlayListV1 {
        val loadedData = loadData(playListKey) ?: PlayListV1(null, null)
        println("loadPlayList $loadedData")
        return loadedData
    }

    fun MediaItem.toYosMediaItem(): YosMediaItem {
        return YosMediaItem(
            uri = this.localConfiguration?.uri,
            mediaId = this.mediaId,
            mimeType = this.localConfiguration?.mimeType,
            title = this.title,
            writer = this.writer,
            compilation = this.compilation,
            composer = this.composer,
            artists = this.artistsName,
            album = this.album,
            albumArtists = this.albumArtists,
            thumb = this.thumb,
            trackNumber = this.trackNumber,
            discNumber = this.discNumber,
            genre = this.genre,
            recordingDay = this.recordingDay,
            recordingMonth = this.recordingMonth,
            recordingYear = this.recordingYear,
            releaseYear = this.releaseYear,
            artistId = this.artistId,
            albumId = this.albumId,
            genreId = this.genreId,
            author = this.author,
            addDate = this.addDate,
            duration = this.duration,
            modifiedDate = this.modifiedDate,
            cdTrackNumber = this.cdTrackNumber
            //samplingRate = this.samplingRate,
            //bitrate = this.bitrate
        )
    }

    fun YosMediaItem.toMediaItem(): MediaItem {
        Log.d("PlaybackDebug", "MediaMetadata: title=${this.title} artist=${this.artists} artwork=${this.thumb} album=${this.album} duration=${this.duration}")
        return MediaItem.Builder()
            .setUri(this.uri)
            .setMediaId(this.mediaId!!)
            .setMimeType(this.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setWriter(this.writer)
                    .setCompilation(this.compilation)
                    .setComposer(this.composer)
                    .setArtist(this.artists)
                    .setAlbumTitle(this.album)
                    .setAlbumArtist(this.albumArtists)
                    .setArtworkUri(this.thumb)
                    .setTrackNumber(this.trackNumber)
                    .setDiscNumber(this.discNumber)
                    .setGenre(this.genre)
                    .setRecordingDay(this.recordingDay)
                    .setRecordingMonth(this.recordingMonth)
                    .setRecordingYear(this.recordingYear)
                    .setReleaseYear(this.releaseYear)
                    .setExtras(Bundle().apply {
                        this@toMediaItem.artistId?.let { putLong("ArtistId", it) }
                        this@toMediaItem.albumId?.let { putLong("AlbumId", it) }
                        this@toMediaItem.genreId?.let { putLong("GenreId", it) }
                        putString("Author", this@toMediaItem.author)
                        this@toMediaItem.addDate?.let { putLong("AddDate", it) }
                        putLong("Duration", this@toMediaItem.duration)
                        this@toMediaItem.modifiedDate?.let { putLong("ModifiedDate", it) }
                        this@toMediaItem.cdTrackNumber?.let { putInt("CdTrackNumber", it) }
                        //this@toMediaItem.samplingRate?.let { putInt("SamplingRate", it) }
                        //this@toMediaItem.bitrate?.let { putInt("Bitrate", it) }
                    })
                    .build()
            )
            .build()
    }

    fun updatePlayStatus(playStatus: PlayStatus) {
        updateData(playStatusKey, playStatus)
    }

    fun loadPlayStatus(): PlayStatus {
        return loadData(playStatusKey) ?: PlayStatus(null, 0L, false, 0)
    }

    private inline fun <reified T> updateData(key: String, value: T) {
        val gson = GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
        val mmkv = MMKV.mmkvWithID(mmkvID)
        val json = gson.toJson(value)
        mmkv.encode(key, json)
    }

    private inline fun <reified T> loadData(key: String): T? {
        val gson = GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
        val mmkv = MMKV.mmkvWithID(mmkvID)
        val json = mmkv.decodeString(key)
        return json?.let {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(it, type)
        }
    }

    fun hideFolder(folder: Folder) {
        updateFolderVisibility(folder, hide = true)
    }

    fun unHideFolder(folder: Folder) {
        updateFolderVisibility(folder, hide = false)
    }

    fun hideSong(song: YosMediaItem) {
        hideSongs = hideSongs + song
    }

    fun unHideSong(song: YosMediaItem) {
        hideSongs = hideSongs - song
    }

    /*fun removeSong(song: YosMediaItem) {
        folders = folders.map {
            if (it.songs.contains(song)) {
                return@map it.copy(
                    name = it.name,
                    songs = it.songs.toMutableList().apply { remove(song) })
            }
            it
        }
        hideSongs = hideSongs - song
    }*/

    private fun updateFolderVisibility(folder: Folder, hide: Boolean) {
        println("文件夹 显示状态更改")
        if (hide) {
            println("文件夹 将隐藏 $folder")
            if (folders.any { it.path == folder.path }) {
                // folders = folders - folder
                println("文件夹 匹配成功")
                println("文件夹 已将 ${folder.path} 隐藏")
                hideFoldersSaver = hideFoldersSaver.plus(YosStringWrapper(folder.path))
            }
        } else {
            println("文件夹 将显示 $folder")
            if (hideFolders.any { it == folder.path }) {
                // folders = folders + folder
                println("文件夹 匹配成功")
                println("文件夹 已将 ${folder.path} 显示")
                hideFoldersSaver = hideFoldersSaver.minus(YosStringWrapper(folder.path))
            }
        }
    }

    private val readerConfiguration = ReaderConfiguration(
        ItemConstructor { uri, mediaId, mimeType, title, writer, compilation,
                          composer, artist, albumTitle, albumArtist, artworkUri,
                          cdTrackNumber, trackNumber, discNumber, genre,
                          recordingDay, recordingMonth, recordingYear, releaseYear,
                          artistId, albumId, genreId, author, addDate,
                          duration, modifiedDate ->
            //val audioProperties = getAudioProperties(uri.path!!)
            return@ItemConstructor MediaItem
                .Builder()
                .setUri(uri)
                .setMediaId(mediaId.toString())
                .setMimeType(mimeType)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setTitle(title)
                        .setWriter(writer)
                        .setCompilation(compilation)
                        .setComposer(composer)
                        .setArtist(artist)
                        .setAlbumTitle(albumTitle)
                        .setAlbumArtist(albumArtist)
                        .setArtworkUri(artworkUri)
                        .setTrackNumber(trackNumber)
                        .setDiscNumber(discNumber)
                        .setGenre(genre)
                        .setRecordingDay(recordingDay)
                        .setRecordingMonth(recordingMonth)
                        .setRecordingYear(recordingYear)
                        .setReleaseYear(releaseYear)
                        .setExtras(Bundle().apply {
                            if (artistId != null) {
                                putLong("ArtistId", artistId)
                            }
                            if (albumId != null) {
                                putLong("AlbumId", albumId)
                            }
                            if (genreId != null) {
                                putLong("GenreId", genreId)
                            }
                            putString("Author", author)
                            if (addDate != null) {
                                putLong("AddDate", addDate)
                            }
                            if (duration != null) {
                                putLong("Duration", duration)
                            }
                            if (modifiedDate != null) {
                                putLong("ModifiedDate", modifiedDate)
                            }
                            cdTrackNumber?.toIntOrNull()
                                ?.let { it1 -> putInt("CdTrackNumber", it1) }
                            /*audioProperties?.let {
                                putInt("SamplingRate", it.first)
                                putInt("Bitrate", it.second)
                            }*/
                        })
                        .build(),
                ).build()
        },
        shouldFetchPlaylist = true,
        shouldIncludeExtraFormat = true
    )

    suspend fun scanMedia(context: Context): ReaderResult<MediaItem> {
        return withContext(Dispatchers.IO) {
            val result = Reader.readFromMediaStore(
                context,
                readerConfiguration
            )

            // 有层级结构的result.folderStructure.folderList[""].folderList
            // val folderList = result.folderStructure.folderList

            songSaver = result.songList.fastMap {
                it.toYosMediaItem()
            }

            result.shallowFolder.folderList.map {
                val name = it.key
                val path = it.value.songList.first().uri?.path?.substringBeforeLast("/")?:""
                val songs = it.value.songList.fastMap { thisSong ->
                    thisSong.toYosMediaItem()
                }
                Folder(name, path, songs)
            }.let {
                folders = it
            }

            /*folders = folderList.map { (path, fileNode) ->
                Folder(
                    path,
                    fileNode.songList.toList()
                )
            }.filter { folder ->
                folder !in hideFolders
            }*/

            println("基本扫描: ${result.songList}")
            println("文件夹: $folders")
            println("SongSaver: $songSaver")
            println("Songs: $songs")

            println("prepare 媒体库扫描完毕，尝试保存播放列表")
            println("prepare 媒体库扫描完毕，保存播放列表")

            updatePlayList(
                PlayListV1(
                    MediaController.mainMusicList,
                    MediaController.playingMusicList.value ?: emptyList()
                )
            )

            result
        }
    }
}
