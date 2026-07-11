package com.pryvn.audiophile.code.playback.extractor

import android.util.Log
import dev.maxrave.pipepipe.extractor.NewPipe as PipePipe
import dev.maxrave.pipepipe.extractor.ServiceList as PipeServiceList
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
    }

    fun logIn(cookie: String?) {
        PipeServiceList.YouTube.tokens = cookie ?: ""
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        try {
            val streamInfo = PipeStreamInfo.getInfo(PipeServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
            val pipeResult = streamInfo.audioStreams.mapNotNull { (it.itagItem?.id ?: return@mapNotNull null) to it.content }
            if (pipeResult.isNotEmpty()) return pipeResult
            Log.d(TAG, "PipePipe no audio for $videoId, falling back to BravePipe")
        } catch (e: Throwable) {
            Log.w(TAG, "PipePipe extractor failed for $videoId: ${e.message}, falling back to BravePipe")
        }

        return runCatching {
            val streamInfo = BraveStreamInfo.getInfo(BraveServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            streamInfo.audioStreams.mapNotNull { (it.itagItem?.id ?: return@mapNotNull null) to it.content }
        }.onFailure {
            Log.w(TAG, "BravePipe extractor failed for $videoId: ${it.message}")
        }.getOrElse { emptyList() }
    }
}
