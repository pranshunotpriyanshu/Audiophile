package com.pryvn.audiophile.code.playback.extractor

import android.util.Log
import dev.maxrave.pipepipe.extractor.NewPipe as PipePipe
import dev.maxrave.pipepipe.extractor.ServiceList as PipeServiceList
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import dev.maxrave.pipepipe.extractor.stream.StreamInfo as PipeStreamInfo
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo

private const val TAG = "Extractor"

class Extractor {
    private var newPipeDownloader = NewPipeDownloaderImpl(proxy = null)
    private var braveNewPipeDownloader = BraveNewPipeDownloaderImpl(proxy = null)

    fun init() {
        PipePipe.init(newPipeDownloader)
        BraveNewPipe.init(braveNewPipeDownloader)
        warmUpJavaScriptPlayerManager()
    }

    private fun warmUpJavaScriptPlayerManager() {
        Thread {
            try {
                Log.d(TAG, "Warming up PipePipe JavaScript player manager...")
                YoutubeJavaScriptPlayerManager.getSignatureTimestamp("dQw4w9WgXcQ")
                Log.d(TAG, "PipePipe JavaScript player manager warmed up successfully")
            } catch (e: Throwable) {
                Log.w(TAG, "PipePipe JavaScript player warm-up failed (will retry lazily): ${e.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    fun logIn(cookie: String?) {
        PipeServiceList.YouTube.tokens = cookie ?: ""
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        Log.d(TAG, "newPipePlayer: resolving videoId=$videoId")
        try {
            val streamInfo = PipeStreamInfo.getInfo(PipeServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val pipeResult = streamsList.mapNotNull { (it.itagItem?.id ?: return@mapNotNull null) to it.content }
            if (pipeResult.isNotEmpty()) {
                Log.d(TAG, "newPipePlayer: PipePipe success videoId=$videoId streams=${pipeResult.size}")
                return pipeResult
            }
            Log.d(TAG, "PipePipe no streams for $videoId, falling back to BravePipe")
        } catch (e: Throwable) {
            Log.w(TAG, "PipePipe extractor failed for $videoId: ${e.message}, falling back to BravePipe")
        }

        return runCatching {
            val streamInfo = BraveStreamInfo.getInfo(BraveServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val result = streamsList.mapNotNull { (it.itagItem?.id ?: return@mapNotNull null) to it.content }
            Log.d(TAG, "newPipePlayer: BravePipe success videoId=$videoId streams=${result.size}")
            result
        }.onFailure {
            Log.w(TAG, "BravePipe extractor failed for $videoId: ${it.message}")
        }.getOrElse { emptyList() }
    }
}
