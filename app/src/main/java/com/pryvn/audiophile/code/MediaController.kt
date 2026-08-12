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
import androidx.media3.common.PlaybackException
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import com.pryvn.audiophile.MainActivity
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController.mediaControl
import com.pryvn.audiophile.code.MediaController.mediaSession
import com.pryvn.audiophile.code.MediaController.musicPlaying
import com.pryvn.audiophile.code.MediaController.onServiceRunning
import com.pryvn.audiophile.code.MediaController.playingMusicList
import com.pryvn.audiophile.code.api.YTPlayerUtils
import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.api.AudiophileLyrics
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.YouTube
import com.pryvn.audiophile.archivetune.ArchiveTuneAdapter
import com.pryvn.audiophile.code.SmartRadioQueue
import com.pryvn.audiophile.code.utils.lrc.LyricsProcessor
import com.pryvn.audiophile.code.utils.lrc.TTMLParser
import com.pryvn.audiophile.code.utils.lrc.YosLrcFactory
import com.pryvn.audiophile.code.utils.player.FadeExo
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePause
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePlay
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.MusicLibrary.toMediaItem
import com.pryvn.audiophile.data.libraries.MusicLibrary.toYosMediaItem
import com.pryvn.audiophile.data.libraries.artistsList
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
    var nextInQueueMusicList = mutableStateOf<List<YosMediaItem>>(emptyList())

    @Stable
    var historyMusicList = mutableStateOf<List<YosMediaItem>>(emptyList())

    @Stable
    var orderedPlayingMusicList = mutableStateOf<List<YosMediaItem>>(emptyList())

    @Stable
    var queueShuffleEnabled = mutableStateOf(false)

    @Stable
    var mediaSession: MediaSession? = null

    // The real, in-process session player (ExoPlayer-backed). Unlike the
    // client-side `mediaControl` MediaController, its timeline mutations are
    // synchronous, so queue reads/writes against it never observe a stale,
    // lagging timeline.
    @Stable
    var realPlayer: Player? = null

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
        Log.d("QueueTap", "prepare() called: music.title=${music.title}, thisMusicList.size=${thisMusicList.size}, thisMusicList.hash=${thisMusicList.hashCode()}, playingMusicList.hash=${playingMusicList.value?.hashCode()}, sameRef=${thisMusicList === playingMusicList.value}, sameValue=${thisMusicList == playingMusicList.value}")
        if (thisMusicList != playingMusicList.value) {

            var index = 0

            val itemList = thisMusicList.mapIndexed { thisIndex, it ->
                val resolved = if (it.uri?.scheme == "ytmusic") {
                    val videoId = it.uri.host ?: it.mediaId ?: ""
                    val response = YTPlayerUtils.resolvePlayable(videoId)
                    if (response.isSuccess) {
                        it.copy(uri = Uri.parse(response.getOrThrow().streamUrl))
                    } else it
                } else it

                if ((music.mediaId != null && resolved.mediaId != null && resolved.mediaId == music.mediaId) ||
                    (resolved.uri != null && resolved.uri == music.uri)
                ) {
                    index = thisIndex
                }

                resolved.toMediaItem()
            }

            Log.d("QueueTap", "prepare() FULL path taken: resolved list size=${itemList.size}, target index=$index, about to setMediaItems+prepare+fadePlay")
            withContext(Dispatchers.Main) {
                mediaControl?.setMediaItems(itemList, index, position)
                mediaControl?.prepare()
            }

            println("prepare: switching playlist")
            orderedPlayingMusicList.value = thisMusicList
            nextInQueueMusicList.value = emptyList()
            historyMusicList.value = emptyList()
            queueShuffleEnabled.value = shuffleModeEnabled

            if (!play && playingMusicList.value == null) {
                playingMusicList.value = thisMusicList
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

            // Playlist switch event
            println("prepare: attempting to save playlist")
            if (mainMusicList != null && playingMusicList.value != null) {
                println("prepare: saving playlist")
                MusicLibrary.updatePlayList(
                    PlayListV1(
                        mainMusicList = mainMusicList,
                        playingMusicList = playingMusicList.value,
                    )
                )
            }

        } else {
            Log.d("QueueTap", "prepare() ELSE branch taken: indexOf(music)=${thisMusicList.indexOf(music)}, music.mediaId=${music.mediaId}, music.uri=${music.uri}")
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

    // ── Auto-Queue (Smart Radio) ──────────────────────────────────────────
    private var autoQueueJob: Job? = null
    private var queueResolveJob: Job? = null
    private val autoQueueLock = Any()

    private const val AUTO_QUEUE_TARGET_SIZE = 20
    private const val AUTO_QUEUE_REFILL_THRESHOLD = 5
    private const val AUTO_QUEUE_REFILL_PAGES = 3
    private const val AUTO_QUEUE_PARALLEL_CHUNK = 8
    private const val AUTO_QUEUE_REPLACEMENT_ATTEMPTS = 5
    private val autoQueueResolveSemaphore = Semaphore(4)

    // Process-lifetime set of every video ID Auto-Queue has ever inserted.
    private val generatedAutoQueueIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Session-lifetime cache of successfully resolved stream URLs.
    private val resolvedStreamUrls = ConcurrentHashMap<String, String>()

    // IDs whose stream resolution failed, so they are never attempted again
    // or re-inserted during this process lifetime.
    private val failedResolveIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // IDs currently being resolved, so no video is resolved concurrently
    // by overlapping Auto-Queue jobs.
    private val activeResolveIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Candidate pool kept after insertion, used to replace failed items.
    private val pendingAutoQueueCandidates = ConcurrentLinkedDeque<SongItem>()

    private data class PlayableQueueItem(
        val appItem: YosMediaItem,
        val mediaItem: YosMediaItem,
    )

    /**
     * Checks if the queue is nearing its end and, if so and auto-queue is enabled,
     * fetches related/suggested songs from YouTube and appends them to the player queue.
     *
     * Called when near the end of the queue or when a song ends with no next item.
     */
    fun maybeAutoQueue(currentVideoId: String? = null) {
        if (!SettingsLibrary.AutoQueueEnabled) return
        if (currentVideoId == null) return
        if (currentVideoId == lastAutoQueuedVideoId) return

        // The real session player is authoritative and synchronous; fall back
        // to the client controller only if the service hasn't published it yet.
        val player = realPlayer ?: mediaControl ?: return

        // Don't refill while repeat-one keeps replaying the same song.
        if (player.repeatMode == REPEAT_MODE_ONE) return

        // Threshold: auto-queue kicks in when <= 2 items remain after current
        val queueSize = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        val remaining = queueSize - currentIndex - 1
        if (remaining > 2) return

        // Don't auto-queue if the current song is local
        val currentSong = musicPlaying.value ?: return
        if (currentSong.uri?.scheme?.let { it == "file" || it == "content" } == true) return

        autoQueueJob?.cancel()
        autoQueueJob = CoroutineScope(Dispatchers.IO).launch {
            lastAutoQueuedVideoId = currentVideoId
            val seedArtistNames = currentSong.artistsList.orEmpty()

            Log.d("AutoQueue", "Auto-queue triggered. Seed=$currentVideoId")
            val pool = runCatching {
                SmartRadioQueue.fetchRecommendations(
                    seedVideoId = currentVideoId,
                    seedArtistIds = emptyList(),
                    seedArtistNames = seedArtistNames,
                )
            }.getOrElse { e ->
                Log.e("AutoQueue", "Recommendation fetch failed: ${e.message}")
                emptyList()
            }

            val excludedIds = buildAutoQueueExcludedIds(player, currentVideoId)
            val candidates = pool.filter {
                it.id.isNotBlank() &&
                    it.id !in failedResolveIds &&
                    excludedIds.add(it.id)
            }
            if (candidates.isEmpty()) return@launch

            // Queue items are created immediately from the recommendations.
            // Streams are resolved lazily afterwards; the queue never waits.
            val insertedSongs = candidates.take(AUTO_QUEUE_TARGET_SIZE)
            val queueItems = insertedSongs.map { toYosMediaItemFromSongItem(it) }
            val mediaItems = queueItems.map { it.toMediaItem() }

            generatedAutoQueueIds.addAll(insertedSongs.map { it.id })
            candidates.drop(AUTO_QUEUE_TARGET_SIZE).forEach { pendingAutoQueueCandidates.add(it) }
            Log.d("AutoQueue", "Generated ${insertedSongs.size} recommendations (${candidates.size} usable)")

            withContext(Dispatchers.Main) {
                // A cancelled/stale job must not mutate the timeline or UI state.
                ensureActive()

                val inserted = player.runCatching {
                    addMediaItems(mediaItemCount, mediaItems)
                }.isSuccess
                if (!inserted) return@withContext

                // Immediately publish the new queue to the UI & persistence.
                // Use the real player so the snapshot reflects the just-added
                // items synchronously — the client MediaController lags.
                syncQueueStateFromController(player, musicPlaying.value)
                saveQueueState()
                Log.d(
                    "AutoQueue",
                    "Inserted ${mediaItems.size} items; queue UI state synchronized " +
                        "(playingMusicList=${playingMusicList.value?.size}, nextInQueue=${nextInQueueMusicList.value.size})"
                )

                // If the previous song already ended while we were fetching,
                // nudge playback into the freshly appended items.
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToNextMediaItem()
                    player.play()
                }
            }

            // Background, bounded stream resolution AFTER insertion — this
            // never blocks the queue, the UI, or the current song.
            startBackgroundQueueResolution(player)
        }
    }

    /**
     * Collects every ID that must never be auto-queued: the current song, the
     * Media3 timeline, the mirrored queue state, previously generated IDs and
     * known-failed IDs.
     */
    private suspend fun buildAutoQueueExcludedIds(
        controller: Player,
        currentVideoId: String,
    ): MutableSet<String> {
        val ids = mutableSetOf(currentVideoId)
        ids.addAll(generatedAutoQueueIds)
        ids.addAll(failedResolveIds)
        withContext(Dispatchers.Main) {
            for (index in 0 until controller.mediaItemCount) {
                runCatching { controller.getMediaItemAt(index).mediaId }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ids.add(it) }
            }
        }
        currentQueueSnapshot(controller)
            .mapNotNull { it.mediaId }
            .forEach { ids.add(it) }
        return ids
    }

    /**
     * Resolves one recommendation's playable stream. Returns null after logging
     * when resolution fails, so a single failure discards only that candidate
     * and can never abort the whole Auto-Queue job.
     */
    private suspend fun resolvePlayableCandidate(song: SongItem): PlayableQueueItem? {
        // Never resolve the same video ID concurrently across overlapping jobs.
        if (!activeResolveIds.add(song.id)) return null
        return try {
            val resolved = resolveStreamUrl(
                song.id,
                song.title,
                song.artists.map { it.name },
                song.duration,
            )
            resolvedStreamUrls[song.id] = resolved.url
            Log.d("AutoQueue", "Resolved stream for ${song.id}: ${resolved.url.take(80)}")
            PlayableQueueItem(
                appItem = toYosMediaItemFromSongItem(song),
                mediaItem = toResolvedYosMediaItem(song, resolved.url),
            )
        } catch (e: Exception) {
            failedResolveIds.add(song.id)
            Log.e("AutoQueue", "Stream resolution failed for ${song.id} (${song.title}): ${e.message}")
            null
        } finally {
            activeResolveIds.remove(song.id)
        }
    }

    /**
     * Background resolution for upcoming queued items. Runs in bounded
     * parallel batches (Semaphore(4)); resolved items are swapped into the
     * Media3 timeline with their real stream URL — except the current item,
     * which the playback path updates itself. Failed items are removed and
     * replaced from the pending candidate pool.
     */
    private fun startBackgroundQueueResolution(controller: Player) {
        queueResolveJob?.cancel()
        queueResolveJob = CoroutineScope(Dispatchers.IO).launch {
            val items = snapshotUpcomingUnresolvedItems(controller)
            if (items.isEmpty()) return@launch
            Log.d("AutoQueue", "Background resolution started for ${items.size} upcoming items")
            // Resolve the next few songs first, then work through the rest.
            val prioritized = items.take(3) + items.drop(3)
            var resolvedCount = 0
            for (chunk in prioritized.chunked(AUTO_QUEUE_PARALLEL_CHUNK)) {
                if (!coroutineContext.isActive) return@launch
                coroutineScope {
                    chunk.map { (index, videoId) ->
                        async(Dispatchers.IO) {
                            autoQueueResolveSemaphore.withPermit {
                                if (resolveQueueItemInBackground(controller, index, videoId)) resolvedCount++
                            }
                        }
                    }.awaitAll()
                }
            }
            Log.d("AutoQueue", "Background resolution finished: $resolvedCount resolved")
        }
    }

    /**
     * Snapshots (index, videoId) of every upcoming item whose stream is still
     * unresolved (`ytmusic://` scheme).
     */
    private suspend fun snapshotUpcomingUnresolvedItems(controller: Player): List<Pair<Int, String>> {
        return withContext(Dispatchers.Main) {
            val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
            (currentIndex + 1 until controller.mediaItemCount).mapNotNull { index ->
                val item = runCatching { controller.getMediaItemAt(index) }.getOrNull() ?: return@mapNotNull null
                if (item.localConfiguration?.uri?.scheme != "ytmusic") return@mapNotNull null
                item.mediaId.takeIf { it.isNotBlank() }?.let { index to it }
            }
        }
    }

    /**
     * Resolves one upcoming queue item in the background. Returns true when it
     * ended up with a playable URL (cached or freshly resolved). Also swaps
     * the resolved URI into the Media3 timeline for non-current items, and
     * removes + replaces the item when resolution fails.
     */
    private suspend fun resolveQueueItemInBackground(
        controller: Player,
        targetIndex: Int,
        videoId: String,
    ): Boolean {
        if (videoId in failedResolveIds) return false
        if (resolvedStreamUrls.containsKey(videoId)) return true

        val resolved = if (activeResolveIds.add(videoId)) {
            try {
                val stream = resolveStreamUrl(videoId)
                resolvedStreamUrls[videoId] = stream.url
                stream
            } catch (e: Exception) {
                failedResolveIds.add(videoId)
                Log.e("AutoQueue", "Background resolution failed for $videoId: ${e.message}")
                withContext(Dispatchers.Main) { replaceFailedQueueItem(controller, videoId) }
                return false
            } finally {
                activeResolveIds.remove(videoId)
            }
        } else {
            return false
        }

        withContext(Dispatchers.Main) {
            val index = indexOfVideoId(controller, videoId)
            if (index == null || index == controller.currentMediaItemIndex) return@withContext
            val item = runCatching { controller.getMediaItemAt(index) }.getOrNull() ?: return@withContext
            if (item.localConfiguration?.uri?.scheme != "ytmusic") return@withContext
            controller.replaceMediaItem(index, item.buildUpon().setUri(Uri.parse(resolved.url)).build())
            Log.d("AutoQueue", "Timeline item $videoId updated with resolved stream")
        }
        return true
    }

    /**
     * Called from the playback service when the current item changes. If the
     * new current item is still unresolved, gives it high-priority resolution
     * so playback continues without user intervention.
     */
    fun onCurrentItemChanged(player: Player) {
        runCatching {
            val current = player.currentMediaItem ?: return
            val uri = current.localConfiguration?.uri
            if (uri?.scheme != "ytmusic") {
                // Current song is playable; keep the next few warm.
                proactivelyResolveNextItems(player)
                return
            }
            val videoId = current.mediaId.takeIf { it.isNotBlank() } ?: return
            if (videoId in failedResolveIds) {
                Log.d("AutoQueue", "Current item $videoId previously failed; replacing it")
                currentItemResolveJob?.cancel()
                currentItemResolveJob = CoroutineScope(Dispatchers.Main).launch {
                    handleFailedCurrentItem(player, videoId)
                }
                return
            }
            val cached = resolvedStreamUrls[videoId]
            if (cached != null) {
                Log.d("AutoQueue", "Current item $videoId already resolved; applying cached stream")
                CoroutineScope(Dispatchers.Main).launch {
                    applyResolvedUriToCurrent(player, videoId, cached)
                }
            } else {
                startHighPriorityCurrentResolution(player, videoId)
            }
        }
    }

    private var currentItemResolveJob: Job? = null

    private fun startHighPriorityCurrentResolution(player: Player, videoId: String) {
        if (!activeResolveIds.add(videoId)) return
        currentItemResolveJob?.cancel()
        currentItemResolveJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AutoQueue", "High-priority resolution for current item $videoId")
                val stream = resolveStreamUrl(videoId)
                resolvedStreamUrls[videoId] = stream.url
                withContext(Dispatchers.Main) {
                    applyResolvedUriToCurrent(player, videoId, stream.url)
                }
            } catch (e: Exception) {
                failedResolveIds.add(videoId)
                Log.e("AutoQueue", "High-priority resolution FAILED for $videoId: ${e.message}")
                withContext(Dispatchers.Main) {
                    handleFailedCurrentItem(player, videoId)
                }
            } finally {
                activeResolveIds.remove(videoId)
            }
        }
    }

    /**
     * Replaces the resolved URI into the current Media3 item and restarts
     * playback, so the song plays without the user pressing Next again.
     */
    private suspend fun applyResolvedUriToCurrent(player: Player, videoId: String, url: String) {
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return
        val current = runCatching { player.getMediaItemAt(index) }.getOrNull() ?: return
        if (current.mediaId != videoId || current.localConfiguration?.uri?.scheme != "ytmusic") return
        player.replaceMediaItem(index, current.buildUpon().setUri(Uri.parse(url)).build())
        Log.d("AutoQueue", "Current item $videoId updated with resolved stream; resuming playback")
        player.prepare()
        player.fadePlayIfAvailable()
    }

    /**
     * Called from the playback service on ExoPlayer errors while an unresolved
     * item is current: routes back into high-priority resolution.
     */
    fun onPlaybackFailed(player: Player, error: PlaybackException) {
        runCatching {
            val current = player.currentMediaItem ?: return
            if (current.localConfiguration?.uri?.scheme != "ytmusic") return
            val videoId = current.mediaId.takeIf { it.isNotBlank() } ?: return
            Log.d("AutoQueue", "Playback error on unresolved item $videoId (${error.errorCodeName}); resolving")
            onCurrentItemChanged(player)
        }
    }

    /**
     * Removes a failed item from the timeline and replaces it with the next
     * playable recommendation when one is available. Runs on the main thread.
     */
    private suspend fun handleFailedCurrentItem(player: Player, videoId: String) {
        val controller = realPlayer ?: mediaControl ?: return
        val index = indexOfVideoId(controller, videoId) ?: return
        if (controller.currentMediaItemIndex != index) return

        Log.d("AutoQueue", "Removing failed current item $videoId at index $index")
        runCatching { controller.removeMediaItem(index) }
        syncQueueStateFromController(controller, musicPlaying.value)
        saveQueueState()

        val replacement = fetchResolvedReplacement()
        if (replacement != null) {
            withContext(Dispatchers.Main) {
                val insertAt = index.coerceAtMost(controller.mediaItemCount)
                controller.addMediaItems(insertAt, listOf(replacement.mediaItem.toMediaItem()))
                generatedAutoQueueIds.add(replacement.appItem.mediaId.orEmpty())
                syncQueueStateFromController(controller, musicPlaying.value)
                saveQueueState()
                Log.d("AutoQueue", "Replaced failed item $videoId with ${replacement.appItem.mediaId}")
                if (controller.playbackState != Player.STATE_ENDED) {
                    controller.prepare()
                    controller.fadePlayIfAvailable()
                }
            }
        } else {
            // No replacement could be resolved. Never leave the player stuck on
            // the removed/errored item — advance so the valid following items
            // already in the queue keep playing.
            Log.d("AutoQueue", "No replacement available for failed current item $videoId; advancing to keep playback alive")
            withContext(Dispatchers.Main) {
                if (controller.mediaItemCount > 0) {
                    controller.seekToNextMediaItem()
                    controller.fadePlayIfAvailable()
                }
            }
        }

        // Keep the queue topped up after the removal/insertion.
        maybeAutoQueue(musicPlaying.value?.mediaId)
    }

    /**
     * Removes a failed upcoming item and replaces it at the same index with
     * another playable recommendation. Runs on the main thread.
     */
    private suspend fun replaceFailedQueueItem(controller: Player, videoId: String) {
        val index = indexOfVideoId(controller, videoId) ?: return
        if (controller.currentMediaItemIndex == index) {
            handleFailedCurrentItem(controller, videoId)
            return
        }

        Log.d("AutoQueue", "Removing failed queue item $videoId at index $index")
        runCatching { controller.removeMediaItem(index) }
        syncQueueStateFromController(controller, musicPlaying.value)
        saveQueueState()

        val replacement = fetchResolvedReplacement()
        if (replacement != null) {
            val insertAt = index.coerceAtMost(controller.mediaItemCount)
            controller.addMediaItems(insertAt, listOf(replacement.mediaItem.toMediaItem()))
            generatedAutoQueueIds.add(replacement.appItem.mediaId.orEmpty())
            syncQueueStateFromController(controller, musicPlaying.value)
            saveQueueState()
            Log.d("AutoQueue", "Replaced failed item $videoId with ${replacement.appItem.mediaId}")
        } else {
            Log.d("AutoQueue", "No replacement available for failed item $videoId")
        }
    }

    /**
     * Pops a playable replacement from the pending candidate pool, refilling
     * the pool from a fresh related fetch when it runs dry. Returns null when
     * no candidate can be resolved.
     */
    private suspend fun fetchResolvedReplacement(): PlayableQueueItem? {
        var attempts = 0
        while (attempts < AUTO_QUEUE_REPLACEMENT_ATTEMPTS) {
            attempts++
            var candidate = pendingAutoQueueCandidates.pollFirst()
            if (candidate == null) {
                refillPendingCandidates()
                candidate = pendingAutoQueueCandidates.pollFirst() ?: return null
            }
            val resolved = resolvePlayableCandidate(candidate)
            if (resolved != null) return resolved
        }
        return null
    }

    private suspend fun refillPendingCandidates() {
        val seedId = musicPlaying.value?.mediaId ?: return
        runCatching {
            SmartRadioQueue.fetchRecommendations(seedId, emptyList(), musicPlaying.value?.artistsList.orEmpty())
        }.getOrElse {
            Log.e("AutoQueue", "Replacement pool fetch failed: ${it.message}")
            emptyList()
        }.forEach { song ->
            if (song.id.isNotBlank() &&
                song.id !in failedResolveIds &&
                song.id !in generatedAutoQueueIds
            ) {
                pendingAutoQueueCandidates.add(song)
            }
        }
        Log.d("AutoQueue", "Replacement pool refilled from seed $seedId")
    }

    /**
     * Resolves the next few upcoming items ahead of playback so transition to
     * them is seamless.
     */
    private fun proactivelyResolveNextItems(player: Player) {
        queueResolveJob?.cancel()
        queueResolveJob = CoroutineScope(Dispatchers.IO).launch {
            val next = snapshotUpcomingUnresolvedItems(player).take(3)
            if (next.isEmpty()) return@launch
            coroutineScope {
                next.map { (index, videoId) ->
                    async(Dispatchers.IO) {
                        autoQueueResolveSemaphore.withPermit {
                            resolveQueueItemInBackground(player, index, videoId)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun indexOfVideoId(player: Player, videoId: String): Int? {
        for (index in 0 until player.mediaItemCount) {
            if (runCatching { player.getMediaItemAt(index).mediaId }.getOrNull() == videoId) return index
        }
        return null
    }

    private fun Player.fadePlayIfAvailable() {
        if (this is ExoPlayer) {
            this.fadePlay()
        } else if (this is androidx.media3.session.MediaController) {
            this.fadePlay()
        } else {
            this.play()
        }
    }

    /**
     * Triggered when a song ends (STATE_ENDED) to ensure next-song playback
     * and auto-queue generation happen promptly.
     */
    fun onSongEnded() {
        if (!SettingsLibrary.AutoQueueEnabled) return
        // Use the authoritative, synchronous session player for end-of-queue
        // detection so a lagging client controller can never wrongly skip a
        // needed Auto-Queue refill (which would stall playback).
        val controller = realPlayer ?: mediaControl ?: return
        val currentVideoId = controller.currentMediaItem?.mediaId
        val queueSize = controller.mediaItemCount
        val currentIndex = controller.currentMediaItemIndex
        val remaining = queueSize - currentIndex - 1

        // If there are remaining items, playback will continue naturally.
        // Only trigger auto-queue if this was the last meaningful item.
        if (remaining > 0) return

        currentVideoId?.let { maybeAutoQueue(it) }
    }

    /**
     * Converts an InnerTube [SongItem] into a [YosMediaItem] using the
     * deferred `ytmusic://` URI scheme so stream URLs are resolved lazily
     * by [prepare].
     */
    private fun toYosMediaItemFromSongItem(song: SongItem): YosMediaItem {
        val thumbnailUrl = song.thumbnail
        return YosMediaItem(
            uri = Uri.parse("ytmusic://${song.id}"),
            mediaId = song.id,
            title = song.title,
            artists = song.artists.joinToString(", ") { it.name },
            album = song.album?.name,
            thumb = thumbnailUrl?.let { Uri.parse(it) },
            duration = (song.duration?.toLong() ?: 0L) * 1000L,
            mimeType = "audio/mp4",
        )
    }

    /**
     * Same shape as [toYosMediaItemFromSongItem] but carrying the real playable
     * stream URL, so the item can be handed to Media3 without deferred
     * `ytmusic://` resolution. Never insert an unresolved URI as a queue item.
     */
    private fun toResolvedYosMediaItem(song: SongItem, streamUrl: String): YosMediaItem {
        val thumbnailUrl = song.thumbnail
        return YosMediaItem(
            uri = Uri.parse(streamUrl),
            mediaId = song.id,
            title = song.title,
            artists = song.artists.joinToString(", ") { it.name },
            album = song.album?.name,
            thumb = thumbnailUrl?.let { Uri.parse(it) },
            duration = (song.duration?.toLong() ?: 0L) * 1000L,
            mimeType = "audio/mp4",
        )
    }

    var lyricsFetchJob: Job? = null
    var playbackJob: Job? = null

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

    private fun cancelPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        lyricsFetchJob?.cancel()
        lyricsFetchJob = null
    }

    private fun startPlayback(body: suspend CoroutineScope.() -> Unit): Job {
        cancelPlayback()
        val job = CoroutineScope(Dispatchers.IO).launch { body() }
        playbackJob = job
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

    suspend fun playOnline(videoId: String, title: String? = null) = startPlayback {
        cancelLyricsFetch()
        clearLyricsState()
        withContext(Dispatchers.Main.immediate) { mediaControl?.stop() }
        ensureActive()
        musicPlaying.value = YosMediaItem(
            uri = Uri.EMPTY,
            mediaId = videoId,
            title = title ?: videoId,
        )
        MediaViewModelObject.bitmap.value = null
        MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.ResolvingStream

        val resolved = ArchiveTuneAdapter.resolve(videoId)
        ensureActive()
        if (resolved.url.isBlank()) throw Exception("Empty stream URL received.")
        val mediaItem = YosMediaItem(
            uri = Uri.parse(resolved.url),
            mediaId = videoId,
            title = title ?: resolved.title,
            artists = resolved.artists,
            duration = (resolved.durationSeconds?.toLong() ?: 0L) * 1000L
        )
        ensureActive()
        prepare(mediaItem, listOf(mediaItem))
    }

    suspend fun playOnline(song: com.pryvn.audiophile.code.api.YTSongItem) = startPlayback {
        cancelLyricsFetch()
        clearLyricsState()
        withContext(Dispatchers.Main.immediate) { mediaControl?.stop() }
        ensureActive()
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
        MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.ResolvingStream

        val resolved = ArchiveTuneAdapter.resolve(song.videoId)
        ensureActive()
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
        ensureActive()
        prepare(mediaItem, listOf(mediaItem))
    }

    suspend fun playPlaylist(firstSong: com.pryvn.audiophile.code.api.YTSongItem, songs: List<com.pryvn.audiophile.code.api.YTSongItem>) = startPlayback {
        cancelLyricsFetch()
        clearLyricsState()
        withContext(Dispatchers.Main.immediate) { mediaControl?.stop() }
        ensureActive()
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
        MediaViewModelObject.playbackLoadingState.value = PlaybackLoadingState.ResolvingStream

        val resolved = resolveStreamUrl(
            videoId = firstSong.videoId,
            title = firstSong.title,
            artists = firstSong.artists.map { it.name },
            durationSeconds = firstSong.durationSeconds,
        )
        ensureActive()
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
                    uri = Uri.parse("ytmusic://${song.videoId}"),
                    mediaId = song.videoId,
                    title = song.title,
                    artists = song.artists.joinToString(", ") { it.name },
                    duration = (song.durationSeconds?.toLong() ?: 0L) * 1000L,
                    thumb = song.thumbnailUrl?.let { Uri.parse(it) },
                )
            }
        }
        ensureActive()
        prepare(mediaItems.firstOrNull() ?: return@startPlayback, mediaItems)
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

        CoroutineScope(Dispatchers.Main).launch {
            mediaControl?.seekToNextMediaItem()
            mediaControl?.fadePlay()
        }
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

        CoroutineScope(Dispatchers.Main).launch {
            mediaControl?.seekToPrevious()
            mediaControl?.fadePlay()
        }
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
            println("prepare: refreshing UI state $music")
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

    suspend fun addToQueue(music: YosMediaItem): Boolean {
        return addToQueue(listOf(music))
    }

    suspend fun addToQueue(musicList: List<YosMediaItem>): Boolean {
        if (musicList.isEmpty()) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)

        if (currentQueue.isEmpty()) {
            prepare(
                musicList.first(),
                musicList,
                play = false,
                shuffleModeEnabled = queueShuffleEnabled.value
            )
            return true
        }

        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val currentNextInQueue = nextInQueueMusicList.value
        val insertAt = (currentIndex + 1 + currentNextInQueue.size).coerceAtMost(currentQueue.size)

        val updatedQueue = currentQueue.toMutableList().also {
            it.addAll(insertAt, musicList)
        }

        withContext(Dispatchers.Main) {
            controller.addMediaItems(insertAt, musicList.map { it.toMediaItem() })
        }

        nextInQueueMusicList.value = currentNextInQueue + musicList
        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    suspend fun playNext(musicList: List<YosMediaItem>): Boolean {
        return addToQueue(musicList)
    }

    suspend fun toggleShuffleMode(): Boolean {
        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)

        if (currentQueue.isEmpty()) {
            queueShuffleEnabled.value = !queueShuffleEnabled.value
            withContext(Dispatchers.Main) {
                controller.shuffleModeEnabled = false
                YosPlaybackService().setCustomButtons(controller)
            }
            saveQueueState()
            return true
        }

        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val updatedShuffleEnabled = !queueShuffleEnabled.value
        val history = currentQueue.take(currentIndex)
        val currentMusic = currentQueue.getOrNull(currentIndex) ?: return false
        val currentNextInQueue = nextInQueueMusicList.value
        val upcoming = currentQueue.drop(currentIndex + 1 + currentNextInQueue.size)
        val updatedQueue = if (updatedShuffleEnabled) {
            buildList {
                addAll(history)
                add(currentMusic)
                addAll(currentNextInQueue)
                addAll(upcoming.shuffled())
            }
        } else {
            currentQueue
        }

        withContext(Dispatchers.Main) {
            controller.shuffleModeEnabled = false
            if (upcoming.isNotEmpty()) {
                controller.replaceMediaItems(
                    currentIndex + 1 + currentNextInQueue.size,
                    controller.mediaItemCount,
                    updatedQueue.drop(currentIndex + 1 + currentNextInQueue.size).map { it.toMediaItem() }
                )
            }
            YosPlaybackService().setCustomButtons(controller)
        }

        orderedPlayingMusicList.value = updatedQueue
        queueShuffleEnabled.value = updatedShuffleEnabled
        syncQueueState(updatedQueue, history.size, currentMusic, false)
        saveQueueState()

        return true
    }

    suspend fun restoreQueueState(
        music: YosMediaItem,
        upcomingMusicList: List<YosMediaItem>,
        nextInQueue: List<YosMediaItem>,
        historyQueue: List<YosMediaItem>,
        position: Long,
        shuffleModeEnabled: Boolean,
        repeatMode: Int,
        play: Boolean = false,
    ) {
        val restoredQueue = buildList {
            addAll(historyQueue)
            add(music)
            addAll(nextInQueue)
            addAll(upcomingMusicList)
        }

        if (restoredQueue.isEmpty()) {
            return
        }

        val currentQueueIndex = historyQueue.size.coerceAtMost(restoredQueue.lastIndex)

        withContext(Dispatchers.Main) {
            mediaControl?.setMediaItems(
                restoredQueue.map { it.toMediaItem() },
                currentQueueIndex,
                position
            )
            mediaControl?.prepare()
            mediaControl?.repeatMode = repeatMode
            mediaControl?.shuffleModeEnabled = false
            mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
        }

        nextInQueueMusicList.value = nextInQueue
        orderedPlayingMusicList.value = restoredQueue
        queueShuffleEnabled.value = shuffleModeEnabled
        syncQueueState(restoredQueue, currentQueueIndex, music, false)

        if (play) {
            withContext(Dispatchers.Main) {
                mediaControl?.fadePlay()
            }
        }

        saveQueueState()
    }

    suspend fun skipToNextInQueueItem(index: Int): Boolean {
        Log.d("QueueTap", "skipToNextInQueueItem called: index=$index")
        val controller = mediaControl ?: return false
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }

        return skipToQueueIndex(controller, currentIndex + 1 + index)
    }

    suspend fun skipToUpNextItem(index: Int): Boolean {
        Log.d("QueueTap", "skipToUpNextItem called: index=$index")
        val controller = mediaControl ?: return false
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val targetIndex = currentIndex + 1 + nextInQueueMusicList.value.size + index

        return skipToQueueIndex(controller, targetIndex)
    }

    suspend fun moveUpNextToNextQueue(index: Int): Boolean {
        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val currentNextInQueue = nextInQueueMusicList.value
        val fromIndex = currentIndex + 1 + currentNextInQueue.size + index
        val insertAt = currentIndex + 1 + currentNextInQueue.size

        if (fromIndex !in currentQueue.indices) {
            return false
        }

        val movedMusic = currentQueue[fromIndex]
        val updatedQueue = currentQueue.toMutableList().also {
            it.removeAt(fromIndex)
            it.add(insertAt, movedMusic)
        }

        withContext(Dispatchers.Main) {
            controller.moveMediaItem(fromIndex, insertAt)
        }

        nextInQueueMusicList.value = currentNextInQueue + movedMusic
        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    suspend fun removeNextInQueueItem(index: Int): Boolean {
        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val currentNextInQueue = nextInQueueMusicList.value
        val targetIndex = currentIndex + 1 + index

        if (index !in currentNextInQueue.indices || targetIndex !in currentQueue.indices) {
            return false
        }

        val updatedNextInQueue = currentNextInQueue.toMutableList().also {
            it.removeAt(index)
        }
        val updatedQueue = currentQueue.toMutableList().also {
            it.removeAt(targetIndex)
        }

        withContext(Dispatchers.Main) {
            controller.removeMediaItem(targetIndex)
        }

        nextInQueueMusicList.value = updatedNextInQueue
        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    suspend fun clearNextInQueue(): Boolean {
        val controller = mediaControl ?: return false
        val currentNextInQueue = nextInQueueMusicList.value

        if (currentNextInQueue.isEmpty()) { return false }

        val currentQueue = currentQueueSnapshot(controller)
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val fromIndex = currentIndex + 1
        val toIndex = fromIndex + currentNextInQueue.size

        if (fromIndex !in currentQueue.indices || toIndex > currentQueue.size) { return false }

        val updatedQueue = currentQueue.toMutableList().also {
            it.subList(fromIndex, toIndex).clear()
        }

        withContext(Dispatchers.Main) {
            controller.removeMediaItems(fromIndex, toIndex)
        }

        nextInQueueMusicList.value = emptyList()
        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    suspend fun removeUpNextItem(index: Int): Boolean {
        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val targetIndex = currentIndex + 1 + nextInQueueMusicList.value.size + index

        if (targetIndex !in currentQueue.indices) {
            return false
        }

        val updatedQueue = currentQueue.toMutableList().also {
            it.removeAt(targetIndex)
        }

        withContext(Dispatchers.Main) {
            controller.removeMediaItem(targetIndex)
        }

        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    suspend fun moveNextInQueueItem(fromIndex: Int, toIndex: Int): Boolean {
        val currentNextInQueue = nextInQueueMusicList.value

        if (fromIndex !in currentNextInQueue.indices || toIndex !in currentNextInQueue.indices) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val fromQueueIndex = currentIndex + 1 + fromIndex
        val toQueueIndex = currentIndex + 1 + toIndex
        val updatedNextInQueue = currentNextInQueue.moved(fromIndex, toIndex)
        val updatedQueue = currentQueue.moved(fromQueueIndex, toQueueIndex)

        withContext(Dispatchers.Main) {
            controller.moveMediaItem(fromQueueIndex, toQueueIndex)
        }

        nextInQueueMusicList.value = updatedNextInQueue
        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    fun moveNextInQueueItemDuringDrag(fromIndex: Int, toIndex: Int): Boolean {
        val currentNextInQueue = nextInQueueMusicList.value

        if (fromIndex !in currentNextInQueue.indices || toIndex !in currentNextInQueue.indices) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = orderedPlayingMusicList.value.takeIf { it.isNotEmpty() } ?: return false
        val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val fromQueueIndex = currentIndex + 1 + fromIndex
        val toQueueIndex = currentIndex + 1 + toIndex

        if (fromQueueIndex !in currentQueue.indices || toQueueIndex !in currentQueue.indices) {
            return false
        }

        val updatedNextInQueue = currentNextInQueue.moved(fromIndex, toIndex)
        val updatedQueue = currentQueue.moved(fromQueueIndex, toQueueIndex)

        nextInQueueMusicList.value = updatedNextInQueue
        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        controller.moveMediaItem(fromQueueIndex, toQueueIndex)

        return true
    }

    suspend fun moveUpNextItem(fromIndex: Int, toIndex: Int): Boolean {
        val currentUpNext = playingMusicList.value ?: emptyList()

        if (fromIndex !in currentUpNext.indices || toIndex !in currentUpNext.indices) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)
        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val upNextStartIndex = currentIndex + 1 + nextInQueueMusicList.value.size
        val fromQueueIndex = upNextStartIndex + fromIndex
        val toQueueIndex = upNextStartIndex + toIndex
        val updatedQueue = currentQueue.moved(fromQueueIndex, toQueueIndex)

        withContext(Dispatchers.Main) {
            controller.moveMediaItem(fromQueueIndex, toQueueIndex)
        }

        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        saveQueueState()

        return true
    }

    fun moveUpNextItemDuringDrag(fromIndex: Int, toIndex: Int): Boolean {
        val currentUpNext = playingMusicList.value ?: emptyList()

        if (fromIndex !in currentUpNext.indices || toIndex !in currentUpNext.indices) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = orderedPlayingMusicList.value.takeIf { it.isNotEmpty() } ?: return false
        val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val upNextStartIndex = currentIndex + 1 + nextInQueueMusicList.value.size
        val fromQueueIndex = upNextStartIndex + fromIndex
        val toQueueIndex = upNextStartIndex + toIndex

        if (fromQueueIndex !in currentQueue.indices || toQueueIndex !in currentQueue.indices) {
            return false
        }

        val updatedQueue = currentQueue.moved(fromQueueIndex, toQueueIndex)

        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex, consumeNextInQueue = false)
        controller.moveMediaItem(fromQueueIndex, toQueueIndex)

        return true
    }

    private suspend fun skipToQueueIndex(
        controller: androidx.media3.session.MediaController,
        targetIndex: Int,
    ): Boolean {
        val currentQueue = currentQueueSnapshot(controller)
        Log.d("QueueTap", "skipToQueueIndex: targetIndex=$targetIndex, currentQueue.size=${currentQueue.size}, valid=${targetIndex in currentQueue.indices}")

        if (targetIndex !in currentQueue.indices) {
            return false
        }

        withContext(Dispatchers.Main) {
            controller.seekTo(targetIndex, 0L)
        }

        syncQueueState(currentQueue, targetIndex, currentQueue[targetIndex])
        saveQueueState()

        return true
    }

    /**
     * Plays the queue item identified by its stable [mediaId] by locating it in
     * the CURRENT Media3 timeline at click time. The index is computed from the
     * live (real, synchronous) player, never from a stale positional snapshot
     * captured at composition — so clicking a displayed song always plays that
     * exact song regardless of how many tracks have been skipped or how the
     * queue has shifted. Lazy stream resolution is preserved: seeking to a
     * still-unresolved `ytmusic://` item triggers [onCurrentItemChanged], which
     * resolves and plays that exact item.
     */
    suspend fun playQueueItemByMediaId(mediaId: String?) {
        if (mediaId.isNullOrBlank()) return
        val controller = realPlayer ?: mediaControl ?: return

        val targetIndex = withContext(Dispatchers.Main) {
            (0 until controller.mediaItemCount).firstOrNull { index ->
                runCatching { controller.getMediaItemAt(index).mediaId }.getOrNull() == mediaId
            }
        }

        if (targetIndex == null) {
            Log.d("AutoQueue", "playQueueItemByMediaId: mediaId=$mediaId not found in current timeline")
            return
        }

        Log.d("AutoQueue", "playQueueItemByMediaId: mediaId=$mediaId -> timeline index $targetIndex")
        withContext(Dispatchers.Main) {
            controller.seekTo(targetIndex, 0L)
            controller.fadePlayIfAvailable()
            // Sync queue state on the main thread — this reads the Player
            // timeline (getMediaItemAt/getMediaItemCount) and must never run
            // on a background dispatcher.
            syncQueueStateFromController(controller, musicPlaying.value)
            saveQueueState()
        }
    }

    private suspend fun currentQueueSnapshot(controller: Player): List<YosMediaItem> {
        orderedPlayingMusicList.value.takeIf { it.isNotEmpty() }?.let {
            return it
        }

        return withContext(Dispatchers.Main) {
            List(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).toYosMediaItem()
                    .withPersistentIdentity()
            }
        }
    }

    private fun syncQueueState(
        orderedQueue: List<YosMediaItem>,
        currentQueueIndex: Int,
        currentMusic: YosMediaItem? = null,
        consumeNextInQueue: Boolean = true,
    ) {
        orderedPlayingMusicList.value = orderedQueue

        if (orderedQueue.isEmpty()) {
            historyMusicList.value = emptyList()
            playingMusicList.value = emptyList()
            nextInQueueMusicList.value = emptyList()
            musicPlaying.value = null
            return
        }

        val boundedIndex = currentQueueIndex.coerceIn(0, orderedQueue.lastIndex)
        val resolvedMusic = currentMusic ?: orderedQueue[boundedIndex]
        val pendingQueue = orderedQueue.drop(boundedIndex + 1)
        val previousQueueIndex = historyMusicList.value.size
        val consumedNextInQueueCount = if (consumeNextInQueue) {
            (boundedIndex - previousQueueIndex).coerceAtLeast(0)
        } else {
            0
        }
        val shiftedNextInQueue = nextInQueueMusicList.value.drop(consumedNextInQueueCount)
        val remainingNextInQueue = pendingQueue.matchingQueuePrefix(shiftedNextInQueue)

        historyMusicList.value = orderedQueue.take(boundedIndex)
        nextInQueueMusicList.value = remainingNextInQueue
        playingMusicList.value = pendingQueue.drop(remainingNextInQueue.size)
        musicPlaying.value = resolvedMusic
        MediaViewModelObject.bitmap.value = resolvedMusic.thumb
        MainViewModelObject.syncLyricIndex.intValue = -1
    }

    fun syncQueueStateFromController(controller: Player, currentMusic: YosMediaItem? = null) {
        val orderedQueue = if (
            orderedPlayingMusicList.value.isNotEmpty() &&
            orderedPlayingMusicList.value.size == controller.mediaItemCount
        ) {
            orderedPlayingMusicList.value
        } else {
            List(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).toYosMediaItem()
                    .withPersistentIdentity()
            }
        }

        syncQueueState(orderedQueue, controller.currentMediaItemIndex.coerceAtLeast(0), currentMusic)
    }

    /**
     * Called from the playback service on every media item transition
     * (natural, manual Next/Previous, auto-queue and queue skips). Media3's
     * timeline is the source of truth when the mirrored queue diverges.
     * Guarded so a queue-state sync problem can never break playback.
     */
    fun syncQueueAfterTransition(player: Player, mediaItem: MediaItem?) {
        runCatching {
            if (player.mediaItemCount == 0) return
            syncQueueStateFromController(player, mediaItem?.toYosMediaItem())
        }
    }

    /**
     * Normalizes a timeline-derived item so online songs always keep their
     * canonical `ytmusic://<videoId>` identity in the app queue state, even
     * when the Media3 timeline already holds a resolved (expiring) stream URL.
     * Persistence and restore rely on that identity.
     */
    private fun YosMediaItem.withPersistentIdentity(): YosMediaItem {
        val scheme = uri?.scheme
        if ((scheme == "http" || scheme == "https") && !mediaId.isNullOrBlank()) {
            return copy(uri = Uri.parse("ytmusic://$mediaId"))
        }
        return this
    }

    fun saveQueueState() {
        MusicLibrary.updatePlayList(
            PlayListV1(
                playingMusicUris = playingMusicList.value.orEmpty().mapNotNull { it.uri?.toString() },
                nextInQueueMusicUris = nextInQueueMusicList.value.mapNotNull { it.uri?.toString() },
                historyMusicUris = historyMusicList.value.mapNotNull { it.uri?.toString() },
                musicPlayingUri = musicPlaying.value?.uri?.toString(),
                shuffleModeEnabled = queueShuffleEnabled.value,
            )
        )
    }
    private fun List<YosMediaItem>.moved(fromIndex: Int, toIndex: Int): List<YosMediaItem> {
        return toMutableList().also {
            val movedMusic = it.removeAt(fromIndex)
            it.add(toIndex, movedMusic)
        }
    }

    private fun List<YosMediaItem>.matchingQueuePrefix(
        candidateQueue: List<YosMediaItem>,
    ): List<YosMediaItem> {
        val maxPrefixSize = candidateQueue.size.coerceAtMost(size)

        for (prefixSize in maxPrefixSize downTo 0) {
            val candidatePrefix = candidateQueue.take(prefixSize)

            if (take(prefixSize).queueMatches(candidatePrefix)) {
                return candidatePrefix
            }
        }

        return emptyList()
    }

    private fun List<YosMediaItem>.queueMatches(candidateQueue: List<YosMediaItem>): Boolean {
        if (size != candidateQueue.size) {
            return false
        }

        return indices.all { index ->
            this[index].queueIdentityMatches(candidateQueue[index])
        }
    }

    private fun YosMediaItem.queueIdentityMatches(other: YosMediaItem): Boolean {
        return uri == other.uri && mediaId == other.mediaId
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
        println("persist: attempting to save playback state")
        if (musicPlaying.value != null && mediaControl != null) {
            println("persist: saving playback state")
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

                        println("quality: using built-in implementation")
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
                            println("failed to get lyrics metadata, will read: $lrcPath")
                            AudioMetadataUtils.loadLrcFile(this@YosPlaybackService, lrcPath) ?: ""
                        } else {
                            lrcContent
                        }

                        val lrcFactory = YosLrcFactory()
                        lrcEntries.value = lrcFactory.formatLrcEntries(finalLrcContent)

                        if (thisPath != null) {
                            // MediaViewModelObject.isDolby.value = thisPath.endsWith(".m4a")
                            // Changed to JOC check

                            if (samplingRate == 0 || bitrate == 0) {
                                try {
                                    val audioInfo = AudioMetadataUtils.getQualityInfos(thisPath)
                                    if (samplingRate == 0) {
                                        samplingRate = audioInfo.second
                                    } else {
                                        bitrate = audioInfo.first
                                    }
                                } catch (_: Exception) {
                                    println("quality analysis failed (not a local file), skipping")
                                }
                            }
                        }

                        MediaViewModelObject.isDolby.value = haveJOC
                        MediaViewModelObject.samplingRate.intValue = samplingRate
                        MediaViewModelObject.bitrate.intValue = bitrate

                        println("quality: sample rate: ${MediaViewModelObject.samplingRate.intValue}, bitrate: ${MediaViewModelObject.bitrate.intValue}")

                        // Fetch lyrics with cancellation guard. Embedded lyrics have
                        // top priority: if the current song has them, never hit the API.
                        MediaViewModelObject.isLoadingLyrics.value = true
                        LyricsProcessor.resetLyricsState()
                        val lyricsJob = com.pryvn.audiophile.code.MediaController.newLyricsFetchJob()
                        CoroutineScope(Dispatchers.IO + lyricsJob).launch {
                            val currentTrack = musicPlaying.value
                            val videoIdAtFetch = currentTrack?.mediaId
                            try {
                                if (currentTrack != null) {
                                    val embeddedLyrics = try {
                                        AudioMetadataUtils.loadEmbeddedLyrics(
                                            this@YosPlaybackService,
                                            currentTrack.uri
                                        )
                                    } catch (e: Exception) {
                                        println("Embedded lyrics read failed: ${e.message}")
                                        null
                                    }
                                    if (embeddedLyrics != null && embeddedLyrics.isNotBlank()) {
                                        if (musicPlaying.value?.mediaId != videoIdAtFetch) return@launch
                                        ensureActive()
                                        LyricsProcessor.applyLyrics(
                                            AudiophileLyrics("Embedded", embeddedLyrics, isWordSynced = TTMLParser.isTtml(embeddedLyrics)),
                                            { lrcEntries.value = it }
                                        )
                                        if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                            MediaViewModelObject.isLoadingLyrics.value = false
                                        }
                                        return@launch
                                    }

                                    val cacheKey = videoIdAtFetch ?: (currentTrack.title ?: "unknown")
                                    val cached = MediaViewModelObject.lyricsCache[cacheKey]
                                    if (cached != null) {
                                        if (musicPlaying.value?.mediaId != videoIdAtFetch) return@launch
                                        ensureActive()
                                        LyricsProcessor.applyLyrics(
                                            AudiophileLyrics("Cache", cached, isWordSynced = TTMLParser.isTtml(cached)),
                                            { lrcEntries.value = it }
                                        )
                                        if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                            MediaViewModelObject.isLoadingLyrics.value = false
                                        }
                                    } else {
                                        val onlineLyrics = try {
                                            ArchiveTuneApis.fetchLyrics(
                                                title = currentTrack.title,
                                                artist = currentTrack.artists,
                                                album = currentTrack.album,
                                                durationMs = currentTrack.duration,
                                                videoId = currentTrack.mediaId,
                                            )
                                        } catch (e: Exception) {
                                            println("Lyrics fetch failed: ${e.message}")
                                            null
                                        }
                                        if (musicPlaying.value?.mediaId != videoIdAtFetch) {
                                            MediaViewModelObject.isLoadingLyrics.value = false
                                            return@launch
                                        }
                                        ensureActive()
                                        if (onlineLyrics != null && onlineLyrics.text.isNotBlank()) {
                                            MediaViewModelObject.lyricsCache[cacheKey] = onlineLyrics.text
                                            if (MediaViewModelObject.lyricsCache.size > 20) {
                                                val keys = MediaViewModelObject.lyricsCache.keys.toList()
                                                for (i in 0 until (MediaViewModelObject.lyricsCache.size - 20)) {
                                                    MediaViewModelObject.lyricsCache.remove(keys[i])
                                                }
                                            }
                                            LyricsProcessor.applyLyrics(onlineLyrics, { lrcEntries.value = it })
                                        } else {
                                            println("Lyrics fetch returned null or blank for: ${currentTrack.title}")
                                            // Set empty lyrics state to show "not found" UI
                                            if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                                lrcEntries.value = emptyList()
                                            }
                                        }
                                        if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                            MediaViewModelObject.isLoadingLyrics.value = false
                                        }
                                    }
                                }
                            } finally {
                                // Ensure loading is always cleared even if coroutine is cancelled
                                if (musicPlaying.value?.mediaId == videoIdAtFetch) {
                                    MediaViewModelObject.isLoadingLyrics.value = false
                                }
                            }
                        }
                        // Prefetch lyrics for upcoming songs (tied to same lyrics job for cancellation)
                        CoroutineScope(Dispatchers.IO + lyricsJob).launch {
                            val list = playingMusicList?.value ?: return@launch
                            val currentIndex = list.indexOfFirst { item -> item.mediaId == musicPlaying.value?.mediaId }
                            if (currentIndex >= 0) {
                                val upcoming = list.subList(currentIndex + 1, kotlin.math.min(currentIndex + 16, list.size))
                                for (track in upcoming) {
                                    ensureActive()
                                    val key = track.mediaId ?: (track.title ?: "unknown")
                                    if (!MediaViewModelObject.lyricsCache.containsKey(key)) {
                                        val embeddedLyrics = try {
                                            AudioMetadataUtils.loadEmbeddedLyrics(
                                                this@YosPlaybackService,
                                                track.uri
                                            )
                                        } catch (e: Exception) {
                                            null
                                        }
                                        if (embeddedLyrics != null && embeddedLyrics.isNotBlank()) {
                                            MediaViewModelObject.lyricsCache[key] = embeddedLyrics
                                        } else {
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
                    Log.d("QueueTap", "onMediaItemTransition: mediaId=${mediaItem?.mediaId}, reason=$reason")
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

                    println("updating $mediaItem")
                    // Keep Audiophile's mirrored queue state aligned with Media3's
                    // actual timeline on every transition (natural, manual, automatic).
                    com.pryvn.audiophile.code.MediaController.syncQueueAfterTransition(player, mediaItem)
                    // Ensure the new current item has a playable stream, resolving
                    // it now if it was not ready when added to the queue.
                    com.pryvn.audiophile.code.MediaController.onCurrentItemChanged(player)
                    // Trigger auto-queue when nearing end of current queue
                    com.pryvn.audiophile.code.MediaController.maybeAutoQueue(mediaItem?.mediaId)
                    super.onMediaItemTransition(mediaItem, reason)
                }

                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    com.pryvn.audiophile.code.MediaController.onPlaybackFailed(player, error)
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
                    Log.d("QueueTap", "onPlaybackStateChanged: state=$playbackState, currentId=$currentId, musicPlaying.mediaId=${musicPlaying.value?.mediaId}, match=${currentId != null && currentId == musicPlaying.value?.mediaId}")
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
                            // Trigger auto-queue when a song ends
                            com.pryvn.audiophile.code.MediaController.onSongEnded()
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

        com.pryvn.audiophile.code.MediaController.realPlayer = forwardingPlayer

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
