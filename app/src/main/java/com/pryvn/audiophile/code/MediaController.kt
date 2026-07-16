package com.pryvn.audiophile.code

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.ExtraData
import cn.lyric.getter.api.tools.Tools
import com.blankj.utilcode.util.ResourceUtils.getDrawable
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.MainActivity
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController.mediaControl
import com.pryvn.audiophile.code.MediaController.mediaSession
import com.pryvn.audiophile.code.MediaController.musicPlaying
import com.pryvn.audiophile.code.MediaController.onServiceRunning
import com.pryvn.audiophile.code.MediaController.playingMusicList
import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.archivetune.ArchiveTuneAdapter
import com.pryvn.audiophile.code.api.parseSyncedLyrics
import com.pryvn.audiophile.code.utils.lrc.LyricsProcessor
import com.pryvn.audiophile.code.utils.lrc.TTMLParser
import com.pryvn.audiophile.code.utils.lrc.YosLrcFactory
import com.pryvn.audiophile.code.utils.player.FadeExo
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePause
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePlay
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.MusicLibrary.toMediaItem
import com.pryvn.audiophile.data.libraries.MusicLibrary.toYosMediaItem
import com.pryvn.audiophile.data.libraries.PlayListV1
import com.pryvn.audiophile.data.libraries.PlayStatus
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.uri
import com.pryvn.audiophile.data.objects.MainViewModelObject
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.PlaybackLoadingState
import com.pryvn.audiophile.data.libraries.ListeningHistory
import com.pryvn.audiophile.data.libraries.PlaybackSource

@Stable
object MediaController {
    @Stable
    val mainMusicList: List<YosMediaItem>
        get() = MusicLibrary.songs

    @Stable
    var playingMusicList = mutableStateOf<List<YosMediaItem>?>(null)

    @Stable
    var mediaControl: MediaController? = null

    @Stable
    var musicPlaying = mutableStateOf<YosMediaItem?>(null)

    @Stable
    var mediaSession: MediaSession? = null

    fun onServiceRunning() {
        val handler by lazy { Handler(Looper.getMainLooper()) }
        val lyricAPI by lazy { API() }
        var lastLyric = listOf<Pair<Float, String>>()
        val base64 = Tools.drawableToBase64(getDrawable(R.drawable.audiophile_icon_notification)!!)
        var statusBarLyricEnabled: Boolean
        var hooked = false

        val checkHookStatusRunnable = object : Runnable {
            override fun run() {
                hooked = lyricAPI.hasEnable
                SettingsLibrary.StatusBarLyricHooked = hooked
                handler.postDelayed(this, 350)
            }
        }

        val updateLyricsRunnable = object : Runnable {
            override fun run() {
                runCatching {
                    var currentLyricIndex: Int
                    var isPlaying: Boolean?
                    var liveTime: Long

                    handler.post {
                        isPlaying = mediaControl?.isPlaying

                        runCatching {
                            currentLyricIndex = MainViewModelObject.syncLyricIndex.intValue

                            if (isPlaying == true) {
                                liveTime = mediaControl?.currentPosition ?: 0

                                val lrcEntries = MediaViewModelObject.lrcEntries.value

                                val nextIndex = lrcEntries.indexOfFirst { line ->
                                    line.first().first >= liveTime
                                }

                                val sendLyric = fun() {
                                    try {
                                        MainViewModelObject.syncLyricIndex.intValue =
                                            currentLyricIndex
                                        statusBarLyricEnabled =
                                            SettingsLibrary.StatusBarLyricEnabled


                                        val line = lrcEntries[currentLyricIndex]
                                        if (line == lastLyric) {
                                            return
                                        }

                                        val lyric = StringBuffer("")
                                        line.forEachIndexed { charIndex, char ->
                                            if (charIndex >= line.size - 1) return@forEachIndexed
                                            lyric.append(char.second)
                                        }

                                        val lyricResult = lyric.toString()

                                        if (statusBarLyricEnabled && hooked) {
                                            lyricAPI.sendLyric(
                                                lyricResult,
                                                extra = ExtraData().apply {
                                                    customIcon = true
                                                    base64Icon = base64
                                                }
                                            )
                                        }

                                        // YosPlaybackService().sendLyricTicker(lyricResult)

                                        lastLyric = line
                                    } catch (_: Exception) {
                                    }
                                }

                                if (nextIndex != -1) {
                                    if (nextIndex - 1 != currentLyricIndex) {
                                        currentLyricIndex = nextIndex - 1
                                    }
                                    if (currentLyricIndex != -1) {
                                        sendLyric()
                                    }
                                } else if (currentLyricIndex != lrcEntries.size - 1) {
                                    currentLyricIndex = lrcEntries.size - 1
                                    if (currentLyricIndex != -1) {
                                        sendLyric()
                                    }
                                }
                            }
                        }
                    }

                    handler.postDelayed(this, 70)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            handler.post(checkHookStatusRunnable)
            handler.post(updateLyricsRunnable)
        }
    }



    suspend fun prepare(
        music: YosMediaItem,
        thisMusicList: List<YosMediaItem>,
        position: Long = 0L,
        shuffleModeEnabled: Boolean = false,
        repeatMode: Int = REPEAT_MODE_ALL,
        play: Boolean = true
    ) {
        println("prepare $music")
        if (thisMusicList != playingMusicList.value) {

            var index = 0

            val itemList = thisMusicList.mapIndexed { thisIndex, it ->
                if (it.uri == music.uri) {
                    index = thisIndex
                }

                it.toMediaItem()
            }


            withContext(Dispatchers.Main) {
                mediaControl?.setMediaItems(itemList, index, position)
                mediaControl?.prepare()
            }

            println("prepare 调用切列表")
            if (!play && playingMusicList.value == null) {
                playingMusicList.value = thisMusicList
                //refresh(music)
                withContext(Dispatchers.Main) {
                    mediaControl?.shuffleModeEnabled = shuffleModeEnabled
                    mediaControl?.repeatMode = repeatMode
                    mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                }
            } else {
                playingMusicList.value = thisMusicList
            }

            if (play) {
                withContext(Dispatchers.Main) {
                    mediaControl?.fadePlay()
                }
            }

            // 播放列表切换事件
            println("prepare 尝试保存播放列表")
            if (mainMusicList != null && playingMusicList.value != null) {
                println("prepare 保存播放列表")
                MusicLibrary.updatePlayList(
                    PlayListV1(
                        mainMusicList,
                        playingMusicList.value
                    )
                )
            }

        } else {
            println("prepare 调用非切列表")
            val index = thisMusicList.indexOf(music)
            withContext(Dispatchers.Main) {
                mediaControl?.seekToDefaultPosition(index)
                mediaControl?.fadePlay()
            }
        }

        prefetchNext(music, thisMusicList)
    }

    private fun prefetchNext(current: YosMediaItem, playlist: List<YosMediaItem>) {
        if (playlist.size <= 1) return
        val currentIndex = playlist.indexOf(current)
        if (currentIndex < 0) return
        val nextIndex = currentIndex + 1
        if (nextIndex >= playlist.size) return
        val next = playlist[nextIndex]
        val nextVideoId = next.mediaId ?: return
        ArchiveTuneAdapter.prefetch(nextVideoId)
    }

    var lyricsFetchJob: Job? = null

    private fun cancelLyricsFetch() {
        lyricsFetchJob?.cancel()
        lyricsFetchJob = null
    }

    fun newLyricsFetchJob(): Job {
        lyricsFetchJob?.cancel()
        val job = Job()
        lyricsFetchJob = job
        return job
    }

    private fun clearLyricsState() {
        MediaViewModelObject.lrcEntries.value = emptyList()
        MediaViewModelObject.otherSideForLines.clear()
        MediaViewModelObject.onlineLyrics.value = null
        MediaViewModelObject.translatedLyrics.value = null
        MediaViewModelObject.lyricsSource.value = null
        MediaViewModelObject.wordSyncedLines.value = emptyList()
        MediaViewModelObject.hasWordSyncedLyrics.value = false
        MediaViewModelObject.isLoadingLyrics.value = true
        MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.ResolvingStream
        MainViewModelObject.syncLyricIndex.intValue = -1
    }

    suspend fun playOnline(videoId: String, title: String? = null) {
        cancelLyricsFetch()
        clearLyricsState()
        withContext(Dispatchers.Main.immediate) { mediaControl?.stop() }
        musicPlaying.value = YosMediaItem(
            uri = Uri.EMPTY,
            mediaId = videoId,
            title = title ?: videoId,
        )
        MediaViewModelObject.bitmap.value = null

        val resolved = ArchiveTuneAdapter.resolve(videoId)
        if (resolved.url.isBlank()) throw Exception("Empty stream URL received.")
        val mediaItem = YosMediaItem(
            uri = Uri.parse(resolved.url),
            mediaId = videoId,
            title = title ?: resolved.title,
            artists = resolved.artists,
            duration = (resolved.durationSeconds?.toLong() ?: 0L) * 1000L
        )
        prepare(mediaItem, listOf(mediaItem))
    }

    suspend fun playOnline(song: com.pryvn.audiophile.code.api.YTSongItem) {
        cancelLyricsFetch()
        clearLyricsState()
        withContext(Dispatchers.Main.immediate) { mediaControl?.stop() }
        val thumbUri = song.thumbnailUrl?.let { Uri.parse(it) }
        musicPlaying.value = YosMediaItem(
            uri = Uri.EMPTY,
            mediaId = song.videoId,
            title = song.title,
            artists = song.artists.joinToString(", ") { it.name },
            album = song.album?.name,
            thumb = thumbUri,
            duration = (song.durationSeconds?.toLong() ?: 0L) * 1000L,
        )
        MediaViewModelObject.bitmap.value = thumbUri

        val resolved = ArchiveTuneAdapter.resolve(song.videoId)
        if (resolved.url.isBlank()) throw Exception("Empty stream URL received.")
        val mediaItem = YosMediaItem(
            uri = Uri.parse(resolved.url),
            mediaId = song.videoId,
            title = song.title,
            artists = song.artists.joinToString(", ") { it.name },
            album = song.album?.name,
            thumb = song.thumbnailUrl?.let { Uri.parse(it) },
            duration = (resolved.durationSeconds?.toLong() ?: song.durationSeconds?.toLong() ?: 0L) * 1000L,
            mimeType = resolved.mimeType
        )
        prepare(mediaItem, listOf(mediaItem))
    }

    suspend fun playPlaylist(firstSong: com.pryvn.audiophile.code.api.YTSongItem, songs: List<com.pryvn.audiophile.code.api.YTSongItem>) {
        cancelLyricsFetch()
        clearLyricsState()
        withContext(Dispatchers.Main.immediate) { mediaControl?.stop() }
        val thumbUri = firstSong.thumbnailUrl?.let { Uri.parse(it) }
        musicPlaying.value = YosMediaItem(
            uri = Uri.EMPTY,
            mediaId = firstSong.videoId,
            title = firstSong.title,
            artists = firstSong.artists.joinToString(", ") { it.name },
            album = firstSong.album?.name,
            thumb = thumbUri,
            duration = (firstSong.durationSeconds?.toLong() ?: 0L) * 1000L,
        )
        MediaViewModelObject.bitmap.value = thumbUri

        val resolved = resolveStreamUrl(
            videoId = firstSong.videoId,
            title = firstSong.title,
            artists = firstSong.artists.map { it.name },
            durationSeconds = firstSong.durationSeconds,
        )
        if (resolved.url.isBlank()) throw Exception("Empty stream URL received.")
        val mediaItems = songs.map { song ->
            if (song.videoId == firstSong.videoId) {
                YosMediaItem(
                    uri = Uri.parse(resolved.url),
                    mediaId = song.videoId,
                    title = resolved.title ?: song.title,
                    artists = song.artists.joinToString(", ") { it.name },
                    duration = (resolved.durationSeconds?.toLong() ?: 0L) * 1000L,
                    thumb = song.thumbnailUrl?.let { Uri.parse(it) },
                    mimeType = resolved.mimeType,
                )
            } else {
                YosMediaItem(
                    uri = Uri.EMPTY,
                    mediaId = song.videoId,
                    title = song.title,
                    artists = song.artists.joinToString(", ") { it.name },
                    duration = (song.durationSeconds?.toLong() ?: 0L) * 1000L,
                    thumb = song.thumbnailUrl?.let { Uri.parse(it) },
                )
            }
        }
        prepare(mediaItems.firstOrNull() ?: return, mediaItems)
    }

    fun manualNext() {
        val list = playingMusicList.value ?: return
        val currentIdx = mediaControl?.currentMediaItemIndex ?: return
        val nextIdx = currentIdx + 1
        if (nextIdx >= list.size) return
        val nextItem = list[nextIdx]

        cancelLyricsFetch()
        clearLyricsState()
        musicPlaying.value = nextItem
        MediaViewModelObject.bitmap.value = nextItem.thumb

        mediaControl?.seekToNextMediaItem()
    }

    fun manualPrevious() {
        val list = playingMusicList.value ?: return
        val currentIdx = mediaControl?.currentMediaItemIndex ?: return
        val prevIdx = currentIdx - 1
        if (prevIdx < 0) return
        val prevItem = list[prevIdx]

        cancelLyricsFetch()
        clearLyricsState()
        musicPlaying.value = prevItem
        MediaViewModelObject.bitmap.value = prevItem.thumb

        mediaControl?.seekToPrevious()
    }

    fun onCase(mediaItem: YosMediaItem) {
        CoroutineScope(Dispatchers.IO).launch {
            refresh(mediaItem)
        }
    }

    private var refreshJob: CompletableJob? = null

    private fun refresh(music: YosMediaItem) {
        refreshJob?.cancel()
        refreshJob = Job()

        val scope = CoroutineScope(Dispatchers.IO + refreshJob!!)

        scope.launch {
            println("prepare 刷新UI状态 $music")
            musicPlaying.value = music
            println(musicPlaying.value)
        }

        scope.launch {
            // val bitmap: MutableState<String?> = MediaViewModelObject.bitmap
            // bitmap.value = music.thumb
            MediaViewModelObject.bitmap.value = music.thumb
        }

        scope.launch {
            MainViewModelObject.syncLyricIndex.intValue = -1
        }
    }

    data class ResolvedStream(
        val url: String,
        val mimeType: String?,
        val title: String?,
        val durationSeconds: Int?,
        val artists: String? = null,
        val thumbnailUrl: String? = null,
        val album: String? = null,
        val timingLog: String = "",
    )

    suspend fun resolveStreamUrl(
        videoId: String,
        title: String? = null,
        artists: List<String> = emptyList(),
        durationSeconds: Int? = null,
    ): ResolvedStream {
        return runCatching { ArchiveTuneAdapter.resolve(videoId) }
            .fold(
                onSuccess = { resolved ->
                    ResolvedStream(
                        url = resolved.url,
                        mimeType = resolved.mimeType,
                        title = title ?: resolved.title,
                        durationSeconds = durationSeconds ?: resolved.durationSeconds,
                        artists = resolved.artists,
                        thumbnailUrl = resolved.thumbnailUrl,
                        album = resolved.album,
                    )
                },
                onFailure = { e ->
                    Log.e("MediaController", "Failed to resolve stream for $videoId", e)
                    throw e
                }
            )
    }
}

class YosPlaybackService : MediaSessionService() {
    private val notificationID = 1145
    private val channelID = "YosMediaControllerChannel"

    private val shuffleMode = "shuffle_mode"
    private val repeatMode = "repeat_mode"

    companion object {
        private const val FLAG_ALWAYS_SHOW_TICKER = 0x1000000
        private const val FLAG_ONLY_UPDATE_TICKER = 0x2000000
    }

    @OptIn(UnstableApi::class)
    private fun setCustomButtons(player: ForwardingPlayer) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (player.shuffleModeEnabled) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    fun setCustomButtons(player: MediaController) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (player.shuffleModeEnabled) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    /*fun sendLyricTicker(lyric: String) {
        val notification = NotificationCompat.Builder(this, channelID).apply {
            setTicker(lyric)
            setSmallIcon(R.drawable.audiophile_icon_notification)
        }.build().also {
            it.extras.putInt("ticker_icon", R.drawable.audiophile_icon_notification)
            it.extras.putBoolean("ticker_icon_switch", true)
            it.flags = it.flags.or(FLAG_ALWAYS_SHOW_TICKER).or(FLAG_ONLY_UPDATE_TICKER)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(notificationID, notification)
    }*/

    private var saveJob: Job? = null

    fun saveDataWithDelay() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.IO).launch {
            delay(200)
            withContext(Dispatchers.Main) {
                saveData()
            }
        }
    }

    private fun saveData() {
        println("持久化 尝试保存播放状态")
        if (musicPlaying.value != null && mediaControl != null) {
            println("持久化 保存播放状态")
            MusicLibrary.updatePlayStatus(
                PlayStatus(
                    musicPlaying.value,
                    mediaControl?.currentPosition ?: 0,
                    mediaControl?.shuffleModeEnabled ?: false,
                    mediaControl?.repeatMode ?: REPEAT_MODE_ALL
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(
            this,
            YosRenderFactory(this)
                .setEnableAudioFloatOutput(
                    SettingsLibrary.AudioFloatOutput
                )
                .setEnableDecoderFallback(true)
                .setEnableAudioTrackPlaybackParams(
                    SettingsLibrary.HardwareAudioTrackPlayBackParams
                )
                .setExtensionRendererMode(
                    when (SettingsLibrary.Codec) {
                        "Auto" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        "System" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    }
                )
        )
            .setAudioAttributes(
                audioAttributes,
                SettingsLibrary.AudioAttributes
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                player.fadePlay()
            }

            override fun pause() {
                player.fadePause()
            }

            override fun isPlaying(): Boolean {
                return FadeExo.targetStatus != 0
            }
        }

        forwardingPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    runCatching {

                        if (tracks.isEmpty) return@runCatching

                        val lrcEntries: MutableState<List<List<Pair<Float, String>>>> =
                            MediaViewModelObject.lrcEntries
                        var lrcContent: String? = null


                        val path = player.currentMediaItem?.uri

                        println("质量分析 内置实现获取")
                        var samplingRate = 0
                        var bitrate = 0
                        var haveJOC = false

                        for (i in tracks.groups) {
                            for (j in 0 until i.length) {
                                if (!i.isTrackSelected(j)) continue
                                val trackFormat = i.getTrackFormat(j)
                                samplingRate = trackFormat.sampleRate
                                bitrate = trackFormat.bitrate / 1000
                                haveJOC =
                                    trackFormat.sampleMimeType?.contains("-joc", ignoreCase = true)
                                        ?: false
                                break
                            }
                        }

                        val thisPath = path?.path

                        val finalLrcContent = if (lrcContent == null) {
                            val lrcPath = "${thisPath?.substringBeforeLast(".")}.lrc"
                            println("获取歌词元数据失败，将读取：$lrcPath")
                            AudioMetadataUtils.loadLrcFile(this@YosPlaybackService, lrcPath) ?: ""
                        } else {
                            lrcContent
                        }

                        val lrcFactory = YosLrcFactory()
                        lrcEntries.value = lrcFactory.formatLrcEntries(finalLrcContent)

                        if (thisPath != null) {
                            // MediaViewModelObject.isDolby.value = thisPath.endsWith(".m4a")
                            // 改为 JOC 判断

                            if (samplingRate == 0 || bitrate == 0) {
                                val audioInfo = AudioMetadataUtils.getQualityInfos(thisPath)
                                if (samplingRate == 0) {
                                    samplingRate = audioInfo.second
                                } else {
                                    bitrate = audioInfo.first
                                }
                            }
                        }

                        MediaViewModelObject.isDolby.value = haveJOC
                        MediaViewModelObject.samplingRate.intValue = samplingRate
                        MediaViewModelObject.bitrate.intValue = bitrate

                        println("质量分析 采样率：${MediaViewModelObject.samplingRate.intValue}，比特率：${MediaViewModelObject.bitrate.intValue}")

                        // Fetch online lyrics with cancellation + stale-update guard
                        val lyricsJob = com.pryvn.audiophile.code.MediaController.newLyricsFetchJob()
                        MediaViewModelObject.isLoadingLyrics.value = true
                        LyricsProcessor.resetLyricsState()
                        CoroutineScope(Dispatchers.IO + lyricsJob).launch {
                            val currentTrack = musicPlaying.value
                            val videoIdAtFetch = currentTrack?.mediaId
                            if (currentTrack != null) {
                                val cacheKey = videoIdAtFetch ?: (currentTrack.title ?: "unknown")
                                val cached = MediaViewModelObject.lyricsCache[cacheKey]
                                if (cached != null) {
                                    if (musicPlaying.value?.mediaId != videoIdAtFetch) return@launch
                                    LyricsProcessor.applyLyrics(
                                        AudiophileLyrics("Cache", cached, isWordSynced = TTMLParser.isTtml(cached)),
                                        { lrcEntries.value = it }
                                    )
                                    if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                        MediaViewModelObject.isLoadingLyrics.value = false
                                    }
                                } else {
                                    val onlineLyrics = ArchiveTuneApis.fetchLyrics(
                                        title = currentTrack.title,
                                        artist = currentTrack.artists,
                                        album = currentTrack.album,
                                        durationMs = currentTrack.duration,
                                        videoId = currentTrack.mediaId,
                                    )
                                    if (musicPlaying.value?.mediaId != videoIdAtFetch) return@launch
                                    if (onlineLyrics != null && onlineLyrics.text.isNotBlank()) {
                                        MediaViewModelObject.lyricsCache[cacheKey] = onlineLyrics.text
                                        if (MediaViewModelObject.lyricsCache.size > 20) {
                                            val keys = MediaViewModelObject.lyricsCache.keys.toList()
                                            for (i in 0 until (MediaViewModelObject.lyricsCache.size - 20)) {
                                                MediaViewModelObject.lyricsCache.remove(keys[i])
                                            }
                                        }
                                        LyricsProcessor.applyLyrics(onlineLyrics, { lrcEntries.value = it })
                                    }
                                    if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                        MediaViewModelObject.isLoadingLyrics.value = false
                                    }
                                }
                            }
                        }
                        // Prefetch lyrics for upcoming songs (tied to same Job for cancellation)
                        CoroutineScope(Dispatchers.IO + lyricsJob).launch {
                            val list = playingMusicList?.value ?: return@launch
                            val currentIndex = list.indexOfFirst { item -> item.mediaId == musicPlaying.value?.mediaId }
                            if (currentIndex >= 0) {
                                val upcoming = list.subList(currentIndex + 1, kotlin.math.min(currentIndex + 16, list.size))
                                for (track in upcoming) {
                                    val key = track.mediaId ?: (track.title ?: "unknown")
                                    if (!MediaViewModelObject.lyricsCache.containsKey(key)) {
                                        val lyrics = ArchiveTuneApis.fetchLyrics(
                                            title = track.title,
                                            artist = track.artists,
                                            album = track.album,
                                            durationMs = track.duration,
                                            videoId = track.mediaId
                                        )
                                        if (lyrics != null && lyrics.text.isNotBlank()) {
                                            MediaViewModelObject.lyricsCache[key] = lyrics.text
                                        }
                                        if (MediaViewModelObject.lyricsCache.size > 20) {
                                            val keys = MediaViewModelObject.lyricsCache.keys.toList()
                                            for (i in 0 until (MediaViewModelObject.lyricsCache.size - 20)) {
                                                MediaViewModelObject.lyricsCache.remove(keys[i])
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    /*mediaSession?.let { MediaController.sendNotification(it,context) }*/
                    mediaItem?.let {
                        val yosItem = it.toYosMediaItem()
                        com.pryvn.audiophile.code.MediaController.onCase(yosItem)
                        yosItem.mediaId?.let { videoId ->
                            val source = if (yosItem.uri?.scheme?.let { it == "file" || it == "content" } == true) {
                                PlaybackSource.LOCAL
                            } else {
                                PlaybackSource.ONLINE
                            }
                            ListeningHistory.record(
                                videoId = videoId,
                                title = yosItem.title.orEmpty(),
                                artists = yosItem.artists,
                                thumbnailUrl = yosItem.thumb?.toString(),
                                source = source,
                            )
                        }
                    }

                    println("更新 $mediaItem")
                    super.onMediaItemTransition(mediaItem, reason)
                }

                /*override fun onIsPlayingChanged(isPlaying: Boolean) {
                    saveData()
                    super.onIsPlayingChanged(isPlaying)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    saveData()
                    super.onRepeatModeChanged(repeatMode)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    saveData()
                    super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_BUFFERING) {
                        saveData()
                    }
                    super.onPlaybackStateChanged(playbackState)
                }*/

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    val currentId = player.currentMediaItem?.mediaId
                    if (currentId == null || currentId != musicPlaying.value?.mediaId) return
                    when (playbackState) {
                        Player.STATE_READY -> {
                            MediaViewModelObject.playbackLoadingState.value =
                                if (player.playWhenReady) PlaybackLoadingState.Playing
                                else PlaybackLoadingState.Paused
                        }
                        Player.STATE_BUFFERING -> {
                            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.Buffering
                        }
                        Player.STATE_ENDED -> {
                            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.Idle
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    MediaViewModelObject.isPlaying.value = isPlaying
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    super.onEvents(player, events)

                    if (events.containsAny(
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                        )
                    ) {
                        saveDataWithDelay()
                    }
                }

            }
        )

        /*val repeatButton = CommandButton.Builder()
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(SAVE_TO_FAVORITES, Bundle()))
            .build()*/

        @Suppress("DEPRECATION")
        class YosMediaSessionCallback : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands =
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(shuffleMode, Bundle.EMPTY))
                        .add(SessionCommand(repeatMode, Bundle.EMPTY))
                        .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == shuffleMode) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    setCustomButtons(forwardingPlayer)
                } else if (customCommand.customAction == repeatMode) {
                    when (player.repeatMode) {
                        REPEAT_MODE_OFF -> {
                            player.repeatMode = REPEAT_MODE_ALL
                        }

                        REPEAT_MODE_ALL -> {
                            player.repeatMode = REPEAT_MODE_ONE
                        }

                        else -> {
                            player.repeatMode = REPEAT_MODE_OFF
                        }
                    }
                    setCustomButtons(forwardingPlayer)
                }
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
            /*override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.fadePlay()
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.fadePause()
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            player.seekToNextMediaItem()
                        }

                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            player.seekToPreviousMediaItem()
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }*/
        }

        mediaSession =
            MediaSession
                .Builder(this, forwardingPlayer)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setShowPlayButtonIfPlaybackIsSuppressed(true)
                .setCallback(YosMediaSessionCallback())
                .build()
        /*
                val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
                val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                mediaButtonIntent.component = mediaButtonReceiver
                val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                mediaSession.setMediaButtonReceiver(pendingIntent)
        */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Audiophile Media Control"
            val descriptionText = "Audiophile Media Control Notification Channel"
            val importance = NotificationManager.IMPORTANCE_NONE
            val channel = NotificationChannel(channelID, name, importance).apply {
                description = descriptionText
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(null, null)
            }
            val notificationManager: NotificationManager =
                ContextCompat.getSystemService(
                    this,
                    NotificationManager::class.java
                ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(notificationID)
                .setChannelId(channelID)
                .build()

        /*DefaultMediaNotificationProvider(
            this,
            {
                notificationID
            },
            channelID,
            notificationID
        )*/

        notificationProvider.setSmallIcon(R.drawable.audiophile_icon_notification)

        this.setMediaNotificationProvider(notificationProvider)

        setCustomButtons(forwardingPlayer)

        onServiceRunning()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}

