package com.pryvn.audiophile.code

import androidx.media3.common.Player
import com.pryvn.audiophile.code.api.SBSegment
import com.pryvn.audiophile.code.api.SponsorBlockClient
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import kotlinx.coroutines.*
import kotlin.math.abs

object SponsorBlockManager {
    private var currentVideoId: String? = null
    private var segments: List<SBSegment> = emptyList()
    private var trackingJob: Job? = null
    private var playerRef: Player? = null

    private var lastSkippedSegment: String? = null

    fun onNewVideo(videoId: String?, player: Player?) {
        if (videoId == null || videoId == currentVideoId) return
        playerRef = player
        currentVideoId = videoId
        segments = emptyList()
        lastSkippedSegment = null
        trackingJob?.cancel()
        if (!SettingsLibrary.SponsorBlockEnabled) return
        CoroutineScope(Dispatchers.IO).launch {
            val cats = SettingsLibrary.sponsorBlockEnabledCategories
            if (cats.isEmpty()) return@launch
            val result = SponsorBlockClient.fetchSegments(videoId, cats)
            if (result.isSuccess) {
                segments = result.getOrDefault(emptyList())
                if (segments.isNotEmpty()) {
                    startTracking()
                }
            }
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val player = playerRef ?: break
                if (!player.isPlaying) {
                    delay(500)
                    continue
                }
                val position = player.currentPosition.toDouble() / 1000.0
                checkAndSkip(position)
                delay(250)
            }
        }
    }

    private fun checkAndSkip(positionSec: Double) {
        val player = playerRef ?: return
        for (segment in segments) {
            val seg = segment.segment
            if (seg.size < 2) continue
            val start = seg[0]
            val end = seg[1]
            if (positionSec in start..end) {
                val segKey = "${segment.UUID ?: ""}_${start}_${end}"
                if (segKey == lastSkippedSegment) return
                lastSkippedSegment = segKey
                player.seekTo((end * 1000).toLong())
                return
            }
        }
    }

    fun clear() {
        trackingJob?.cancel()
        trackingJob = null
        segments = emptyList()
        currentVideoId = null
        playerRef = null
        lastSkippedSegment = null
    }
}
