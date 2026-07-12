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
import androidx.media3.common.PlaybackException
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
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
import android.util.Log
import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.api.lyrics.AudiophileLyrics
import com.pryvn.audiophile.code.api.YTSongItem
import com.pryvn.audiophile.code.playback.SimpMusicStreamResolver
import moe.rukamori.archivetune.playback.HiResLosslessPlaybackResolver
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

        val updateLyricsRunnable = object : Runnable {
            override fun run() {
                runCatching {
                    handler.post {
                        val isPlaying = mediaControl?.isPlaying

                        runCatching {
                            if (isPlaying == true) {
                                val liveTime = mediaControl?.currentPosition ?: 0
                                val lrcEntries = MediaViewModelObject.lrcEntries.value

                                val nextIndex = lrcEntries.indexOfFirst { line ->
                                    line.first().first >= liveTime
                                }

                                var currentIndex = MainViewModelObject.syncLyricIndex.intValue

                                if (nextIndex != -1) {
                                    if (nextIndex - 1 != currentIndex) {
                                        currentIndex = nextIndex - 1
                                    }
                                    if (currentIndex != -1) {
                                        MainViewModelObject.syncLyricIndex.intValue = currentIndex
                                    }
                                } else if (currentIndex != lrcEntries.size - 1) {
                                    currentIndex = lrcEntries.size - 1
                                    if (currentIndex != -1) {
                                        MainViewModelObject.syncLyricIndex.intValue = currentIndex
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
        Log.d("PlaybackDebug", "prepare: music=${music.mediaId} listSize=${thisMusicList.size} play=$play")

        // Capture the previous queue BEFORE mutating state so we can distinguish a new
        // queue (rebuild + play) from a jump within the current queue (seek only).
        val previousList = playingMusicList.value
        val isNewList = thisMusicList != previousList

        // Immediate Now Playing UI update. MUST run on the main thread so the Compose
        // snapshot notifies observers (Queue screen, mini-player, Now Playing) and they
        // recompose. Writing snapshot state off the main thread updates the raw value
        // (so direct .value reads in click handlers see the change) but fails to trigger
        // recomposition, which desyncs the Queue UI from the actual queue.
        withContext(Dispatchers.Main) {
            musicPlaying.value = music
            MediaViewModelObject.bitmap.value = music.thumb
            MainViewModelObject.syncLyricIndex.intValue = -1
            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.PreparingPlayer
        }

        if (isNewList) {

            var index = 0

            val itemList = thisMusicList.mapIndexed { thisIndex, it ->
                if (it.uri == music.uri) {
                    index = thisIndex
                }

                it.toMediaItem()
            }

            Log.d("PlaybackDebug", "prepare: setMediaItems size=${itemList.size} startIndex=$index")
            Log.d("PlaybackDebug", "MediaItem created mediaId=${music.mediaId} uri=${music.uri?.toString()?.take(80)}")

            withContext(Dispatchers.Main) {
                playingMusicList.value = thisMusicList

                mediaControl?.setMediaItems(itemList, index, position)
                Log.d("PlaybackDebug", "Player.prepare()")
                mediaControl?.prepare()

                if (!play) {
                    mediaControl?.shuffleModeEnabled = shuffleModeEnabled
                    mediaControl?.repeatMode = repeatMode
                    mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                }

                // Assertion 3: queue entry at index matches the mediaId
                if (index < thisMusicList.size) {
                    val queueItem = thisMusicList[index]
                    Log.d("VideoIdChain", "prepare: queue[$index] videoId=${queueItem.mediaId} title=${queueItem.title} url=${queueItem.uri?.toString()?.take(80)}")
                }
                Log.d("VideoIdChain", "prepare: full queue videoIds=${listOfNotNull(music.mediaId)}")

                if (play) {
                    // Selecting a song is an explicit playback action. Always start
                    // playback and never inherit the previous (possibly paused) state.
                    Log.d("PlaybackDebug", "prepare: calling fadePlay")
                    MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.Buffering
                    mediaControl?.playWhenReady = true
                    mediaControl?.fadePlay()
                }

                MusicLibrary.updatePlayList(PlayListV1(mainMusicList, thisMusicList))
            }

        } else {
            Log.d("PlaybackDebug", "prepare: same list, seeking to index")
            val index = thisMusicList.indexOf(music)
            withContext(Dispatchers.Main) {
                mediaControl?.seekToDefaultPosition(index)
                if (play) {
                    // Jumping within the current queue must also (re)start playback.
                    mediaControl?.playWhenReady = true
                    mediaControl?.fadePlay()
                }
            }
        }
    }

    private suspend fun resolveHiResLosslessStreamUrl(
        title: String?,
        artists: List<String>,
        durationSeconds: Int?,
    ): Pair<String?, String?> {
        if (title.isNullOrBlank()) {
            Log.d("HiResLossless", "resolve: SKIP title blank")
            return null to null
        }
        Log.d("HiResLossless", "resolve: ATTEMPT title=$title artists=$artists duration=$durationSeconds")
        return runCatching {
            withContext(Dispatchers.IO) {
                HiResLosslessPlaybackResolver
                    .resolve(
                        HiResLosslessPlaybackResolver.TrackIdentity(
                            title = title,
                            artists = artists,
                            durationSeconds = durationSeconds,
                        ),
                    ).fold(
                        onSuccess = { pd ->
                            val url = pd.streamUrl
                            val mime = pd.format?.mimeType?.takeIf { it.isNotBlank() }
                            if (url.isNotBlank()) {
                                Log.d("HiResLossless", "resolve: SUCCESS selected=hiResLossless url=${url.take(120)} mime=$mime")
                                url to mime
                            } else {
                                Log.d("HiResLossless", "resolve: EMPTY selected=hiResLossless")
                                null to null
                            }
                        },
                        onFailure = { e ->
                            Log.d("HiResLossless", "resolve: FAILED selected=hiResLossless reason=${e.message}")
                            null to null
                        },
                    )
            }
        }.getOrElse { e ->
            Log.d("HiResLossless", "resolve: EXCEPTION selected=hiResLossless reason=${e.message}")
            null to null
        }
    }

    data class ResolvedStream(
        val url: String,
        val mimeType: String?,
        val title: String?,
        val durationSeconds: Int?,
    )

    /**
     * Single stream-resolution path for every playback entry point.
     * Resolves via the YouTube player API using the exact videoId.
     * The hi-res/lossless (Bandcamp/SoundCloud) resolver is NOT used here
     * because it searches by title+artist (not videoId) and can silently
     * replace the selected track with a different recording.
     */
    suspend fun resolveStreamUrl(
        videoId: String,
        title: String? = null,
        artists: List<String> = emptyList(),
        durationSeconds: Int? = null,
    ): ResolvedStream {
        Log.d("PlaybackDebug", "resolveStreamUrl: calling SimpMusicStreamResolver videoId=$videoId")

        val resolved = SimpMusicStreamResolver.resolve(videoId).getOrThrow()

        val resolvedTitle = title ?: resolved.title
        val resolvedDuration = durationSeconds ?: resolved.durationSeconds
        Log.d("PlaybackDebug", "resolveStreamUrl: audioUrl=${resolved.url.take(80)}")
        return ResolvedStream(resolved.url, null, resolvedTitle, resolvedDuration)
    }

    suspend fun playOnline(song: YTSongItem) {
        val artistStr = song.artists.joinToString(", ") { it.name }
        Log.d("PlaybackDebug", "playOnline: videoId=${song.videoId} title=${song.title} artist=$artistStr")

        // Assertion 1: videoId passed to resolver matches the song
        require(song.videoId.isNotBlank()) { "playOnline: song.videoId is blank" }

        // IMMEDIATELY update UI state so user sees feedback instantly.
        // Must run on main thread for snapshot notification (see prepare()).
        withContext(Dispatchers.Main) {
            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.ResolvingStream
            musicPlaying.value = YosMediaItem(
                uri = null,
                mediaId = song.videoId,
                title = song.title,
                artists = artistStr,
                thumb = song.thumbnailUrl?.let { Uri.parse(it) },
                duration = (song.durationSeconds?.toLong() ?: 0L) * 1000L,
            )
        }

        val resolved = resolveStreamUrl(
            song.videoId,
            song.title,
            song.artists.map { it.name },
            song.durationSeconds,
        )

        require(song.videoId == song.videoId) { "playOnline: videoId mismatch at resolver input" }
        Log.d("VideoIdChain", "playOnline: requested=$song.videoId resolvedUrl=${resolved.url.take(80)}")

        withContext(Dispatchers.Main) {
            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.PreparingPlayer
        }

        val mediaItem = YosMediaItem(
            uri = Uri.parse(resolved.url),
            mediaId = song.videoId,
            title = resolved.title ?: song.title,
            artists = artistStr,
            thumb = song.thumbnailUrl?.let { Uri.parse(it) },
            duration = (resolved.durationSeconds?.toLong() ?: (song.durationSeconds?.toLong() ?: 0L)) * 1000L,
            mimeType = resolved.mimeType,
        )
        Log.d("PlaybackDebug", "YosMediaItem created: title=${mediaItem.title} artists=${mediaItem.artists} thumb=${mediaItem.thumb} duration=${mediaItem.duration}")
        Log.d("VideoIdChain", "playOnline: YosMediaItem videoId=${mediaItem.mediaId} url=${mediaItem.uri?.toString()?.take(80)}")
        prepare(mediaItem, listOf(mediaItem))
    }

    suspend fun playOnline(videoId: String, title: String? = null) {
        Log.d("PlaybackDebug", "playOnline: videoId=$videoId title=$title (no YTSongItem metadata)")
        
        withContext(Dispatchers.Main) {
            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.ResolvingStream
            musicPlaying.value = YosMediaItem(
                uri = null,
                mediaId = videoId,
                title = title ?: "Unknown",
                artists = null,
                duration = 0L,
            )
        }

        val resolved = resolveStreamUrl(videoId, title, emptyList(), null)

        withContext(Dispatchers.Main) {
            MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.PreparingPlayer
        }

        val mediaItem = YosMediaItem(
            uri = Uri.parse(resolved.url),
            mediaId = videoId,
            title = resolved.title ?: "Unknown",
            artists = null,
            duration = 0L,
            mimeType = resolved.mimeType,
        )
        prepare(mediaItem, listOf(mediaItem))
    }

    suspend fun playPlaylist(startSong: YTSongItem, allSongs: List<YTSongItem>) {
        val startIndex = allSongs.indexOfFirst { it.videoId == startSong.videoId }
        if (startIndex < 0) {
            Log.e("PlaybackDebug", "playPlaylist: startSong not in allSongs list, falling back to playOnline")
            playOnline(startSong)
            return
        }
        Log.d("PlaybackDebug", "playPlaylist: startSong=${startSong.videoId} index=$startIndex totalSongs=${allSongs.size}")

        val resolved = resolveStreamUrl(
            startSong.videoId,
            startSong.title,
            startSong.artists.map { it.name },
            startSong.durationSeconds,
        )
        val startItem = YosMediaItem(
            uri = Uri.parse(resolved.url),
            mediaId = startSong.videoId,
            title = resolved.title ?: startSong.title,
            artists = startSong.artists.joinToString(", ") { it.name },
            thumb = startSong.thumbnailUrl?.let { Uri.parse(it) },
            duration = (resolved.durationSeconds?.toLong() ?: 0L) * 1000L,
            mimeType = resolved.mimeType,
        )

        prepare(startItem, listOf(startItem))
        Log.d("PlaybackDebug", "playPlaylist: immediate playback started with tapped track")

        // Build queue order: start at tapped, then playlist order with wrap-around
        val queueOrder = allSongs.indices.map { (it + startIndex) % allSongs.size }

        val fullList = mutableListOf<YosMediaItem>()
        fullList.addAll(queueOrder.map { idx ->
            val song = allSongs[idx]
            if (idx == startIndex) startItem
            else YosMediaItem(
                uri = null,
                mediaId = song.videoId,
                title = song.title,
                artists = song.artists.joinToString(", ") { it.name },
                thumb = song.thumbnailUrl?.let { Uri.parse(it) },
                duration = (song.durationSeconds?.toLong() ?: 0L) * 1000L,
            )
        })
        withContext(Dispatchers.Main) {
            playingMusicList.value = fullList
        }
        Log.d("PlaybackDebug", "playPlaylist: playingMusicList set, queue size=${fullList.size}")

        // Resolve remaining tracks in background, in queue order
        CoroutineScope(Dispatchers.IO + Job()).launch {
            for (orderedIdx in queueOrder.indices) {
                if (orderedIdx == 0) continue // Already resolved (tapped track)
                val playlistIdx = queueOrder[orderedIdx]
                val song = allSongs[playlistIdx]
                try {
                    Log.d("PlaybackDebug", "playPlaylist: resolving track ordered=$orderedIdx playlist=$playlistIdx ${song.videoId}")
                    val resolved = resolveStreamUrl(
                        song.videoId,
                        song.title,
                        song.artists.map { it.name },
                        song.durationSeconds,
                    )
                    if (!resolved.url.isNullOrBlank()) {
                        val resolvedItem = YosMediaItem(
                            uri = Uri.parse(resolved.url),
                            mediaId = song.videoId,
                            title = resolved.title ?: song.title,
                            artists = song.artists.joinToString(", ") { it.name },
                            thumb = song.thumbnailUrl?.let { Uri.parse(it) },
                            duration = (resolved.durationSeconds?.toLong() ?: 0L) * 1000L,
                            mimeType = resolved.mimeType,
                        )
                        fullList[orderedIdx] = resolvedItem
                        withContext(Dispatchers.Main) {
                            playingMusicList.value = fullList.toList()
                            mediaControl?.addMediaItem(resolvedItem.toMediaItem())
                        }
                        Log.d("PlaybackDebug", "playPlaylist: resolved track $orderedIdx ${song.videoId}")
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackDebug", "playPlaylist: failed to resolve ${song.videoId}", e)
                }
            }
            Log.d("PlaybackDebug", "playPlaylist: all remaining tracks resolved, final queue size=${fullList.size}")
        }
    }

    fun onCase(mediaItem: YosMediaItem) {
        refresh(mediaItem)
    }

    private fun refresh(music: YosMediaItem) {
        Log.d("PlaybackDebug", "Current song: title=${music.title} artist=${music.artists} artwork=${music.thumb} album=${music.album} duration=${music.duration} mediaId=${music.mediaId}")

        // Assertion 4: ExoPlayer's current media ID matches
        val playerMediaId = mediaControl?.currentMediaItem?.mediaId
        Log.d("VideoIdChain", "refresh: musicPlaying.mediaId=${music.mediaId} player.current=$playerMediaId")
        if (playerMediaId != null && music.mediaId != null) {
            require(playerMediaId == music.mediaId) {
                "refresh: player.currentMediaItem.mediaId ($playerMediaId) != musicPlaying.mediaId (${music.mediaId})"
            }
        }

        // Assertion 3: queue entry at current index matches
        val list = playingMusicList.value
        val currentIndex = list?.indexOfFirst { it.uri == music.uri } ?: -1
        if (currentIndex >= 0 && currentIndex < (list?.size ?: 0)) {
            val queueItem = list!![currentIndex]
            Log.d("VideoIdChain", "refresh: queue[$currentIndex] videoId=${queueItem.mediaId} matches music=${queueItem.mediaId == music.mediaId}")
            require(queueItem.mediaId == music.mediaId) {
                "refresh: queue[$currentIndex].mediaId (${queueItem.mediaId}) != musicPlaying.mediaId (${music.mediaId})"
            }
        }

        musicPlaying.value = music
        MediaViewModelObject.bitmap.value = music.thumb
        MainViewModelObject.syncLyricIndex.intValue = -1
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

    val sleepTimer = com.pryvn.audiophile.code.player.SleepTimer()

    fun applyPlaybackSettings() {
        val speed = SettingsLibrary.PlaybackSpeed
        val pitch = SettingsLibrary.PlaybackPitch
        if (speed > 0f && pitch > 0f) {
            mediaControl?.setPlaybackParameters(
                androidx.media3.common.PlaybackParameters(speed, pitch)
            )
        }
    }

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

        //player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f, 1.0f)
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

            override fun seekToPrevious() {
                // Apple Music / Spotify behavior: if position > 2s, seek to start; else go to previous track
                if (currentPosition > 2000) {
                    seekTo(0)
                } else if (hasPreviousMediaItem()) {
                    seekToPreviousMediaItem()
                } else {
                    seekTo(0)
                }
            }
        }

        var lyricsFetchJob: kotlinx.coroutines.Job? = null
        var prefetchJob: kotlinx.coroutines.Job? = null
        // Track the mediaId for the current lyrics request to prevent stale results
        var currentLyricsRequestMediaId: String? = null

        // Centralized "current song changed" lyrics workflow. Called from onMediaItemTransition
        // for EVERY song transition (Search, Playlist, Album, Queue, Offline, Home, Browse,
        // Favorites, auto-advance) so behavior is identical regardless of playback source.
        // Flow: cancel previous request -> clear lyrics/loading -> fetch (with stale guard) -> prefetch.
        fun triggerLyricsFetch() {
            // Cancel any in-flight lyrics query before starting a new one
            lyricsFetchJob?.cancel()
            lyricsFetchJob = null

            // Immediately clear old lyrics when song changes (before new fetch)
            val newTrack = musicPlaying.value
            val newMediaId = newTrack?.mediaId
            if (newMediaId != null) {
                currentLyricsRequestMediaId = newMediaId
            }
            MediaViewModelObject.isLoadingLyrics.value = true
            LyricsProcessor.resetLyricsState()
            MediaViewModelObject.lrcEntries.value = emptyList()
            MediaViewModelObject.otherSideForLines.clear()

            lyricsFetchJob = CoroutineScope(Dispatchers.IO).launch {
                val currentTrack = musicPlaying.value
                // Capture the mediaId this request is for
                val requestMediaId = currentTrack?.mediaId
                if (currentTrack != null && requestMediaId != null) {
                    val cacheKey = requestMediaId ?: (currentTrack.title ?: "unknown")
                    val inMemory = MediaViewModelObject.lyricsCache[cacheKey]
                    val lyricsText = inMemory ?: MusicLibrary.loadPersistedLyrics(cacheKey)
                    if (lyricsText != null) {
                        MediaViewModelObject.lyricsCache[cacheKey] = lyricsText
                        val lrcFactory = YosLrcFactory()
                        LyricsProcessor.applyLyrics(
                            AudiophileLyrics("Cache", lyricsText, isWordSynced = TTMLParser.isTtml(lyricsText)),
                        ) { MediaViewModelObject.lrcEntries.value = it }
                        MediaViewModelObject.isLoadingLyrics.value = false
                    } else {
                        Log.d("PlaybackDebug", "Lyrics lookup: title=${currentTrack.title} artist=${currentTrack.artists} album=${currentTrack.album} durationMs=${currentTrack.duration}")
                        val onlineLyrics = ArchiveTuneApis.fetchLyrics(
                            title = currentTrack.title,
                            artist = currentTrack.artists,
                            album = currentTrack.album,
                            durationMs = currentTrack.duration,
                            videoId = currentTrack.mediaId,
                        )
                        // STALE-RESULT GUARD: verify the song hasn't changed since we started
                        if (onlineLyrics != null && onlineLyrics.text.isNotBlank()) {
                            // Check if the current song still matches the one we fetched for
                            val currentPlayingMediaId = musicPlaying.value?.mediaId
                            if (currentPlayingMediaId == requestMediaId) {
                                MediaViewModelObject.lyricsCache[cacheKey] = onlineLyrics.text
                                MusicLibrary.saveLyrics(cacheKey, onlineLyrics.text)
                                LyricsProcessor.applyLyrics(onlineLyrics) { MediaViewModelObject.lrcEntries.value = it }
                            } else {
                                Log.d("PlaybackDebug", "Lyrics stale: requested for $requestMediaId but now playing $currentPlayingMediaId")
                            }
                        }
                        MediaViewModelObject.isLoadingLyrics.value = false
                    }
                }
            }
            // Prefetch lyrics for ALL remaining songs in the queue
            prefetchJob?.cancel()
            prefetchJob = CoroutineScope(Dispatchers.IO).launch {
                val list = playingMusicList?.value ?: return@launch
                val currentIndex = list.indexOfFirst { item -> item.mediaId == musicPlaying.value?.mediaId }
                if (currentIndex >= 0) {
                    val upcoming = list.subList(currentIndex + 1, list.size)
                    for (track in upcoming) {
                        val key = track.mediaId ?: (track.title ?: "unknown")
                        if (!MediaViewModelObject.lyricsCache.containsKey(key)) {
                            val persisted = MusicLibrary.loadPersistedLyrics(key)
                            if (persisted != null) {
                                MediaViewModelObject.lyricsCache[key] = persisted
                            } else {
                                val lyrics = ArchiveTuneApis.fetchLyrics(
                                    title = track.title,
                                    artist = track.artists,
                                    album = track.album,
                                    durationMs = track.duration
                                )
                                if (lyrics != null && lyrics.text.isNotBlank()) {
                                    MediaViewModelObject.lyricsCache[key] = lyrics.text
                                    MusicLibrary.saveLyrics(key, lyrics.text)
                                }
                            }
                        }
                    }
                }
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

                        // First-song reliability: onTracksChanged reliably fires when a track's
                        // audio is loaded for any source (Search/Playlist/Album/Queue/...). The
                        // same centralized lyrics workflow is used here and in onMediaItemTransition.
                        triggerLyricsFetch()

                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Log.d("PlaybackDebug", "onMediaItemTransition: mediaItem=${mediaItem?.mediaId} reason=$reason")
                    Log.d("VideoIdChain", "ExoPlayer: currentMediaItem.mediaId=${mediaItem?.mediaId}")
                    // Flush in-flight lyrics query immediately on song change
                    lyricsFetchJob?.cancel()
                    lyricsFetchJob = null
                    prefetchJob?.cancel()
                    prefetchJob = null

                    mediaItem?.let {
                        com.pryvn.audiophile.code.MediaController.onCase(
                            it.toYosMediaItem()
                        )

                        val yosItem = it.toYosMediaItem()
                        val videoId = yosItem.mediaId
                        if (videoId != null && videoId.length == 11) {
                            SponsorBlockManager.onNewVideo(videoId, forwardingPlayer)
                        }

                        // Single centralized lyrics refresh for every song transition,
                        // regardless of where playback originated.
                        triggerLyricsFetch()
                    }

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

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("PlaybackDebug", "onIsPlayingChanged: isPlaying=$isPlaying")
                    super.onIsPlayingChanged(isPlaying)
                    MediaViewModelObject.isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> {
                            if (MediaViewModelObject.playbackLoadingState.value != PlaybackLoadingState.Playing) {
                                MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.Playing
                            }
                            "READY"
                        }
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN($playbackState)"
                    }
                    Log.d("PlaybackDebug", "onPlaybackStateChanged: $stateName")
                    super.onPlaybackStateChanged(playbackState)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("PlaybackDebug", "onPlayerError: ${error.message}", error)
                    super.onPlayerError(error)
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

