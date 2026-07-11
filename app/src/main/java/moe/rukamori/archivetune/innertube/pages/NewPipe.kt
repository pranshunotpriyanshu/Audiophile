/*
* ArchiveTune (2026)
* © Rukamori — github.com/rukamori
* GPL-3.0 License | Contributors: see git history
* Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
*/

package moe.rukamori.archivetune.innertube

import android.util.Log
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

private class NewPipeDownloaderImpl(
    proxy: Proxy?,
) : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy ?: Proxy.NO_PROXY)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, dataToSend?.toRequestBody())
                .url(url)

        var hasUserAgent = false
        headers.forEach { (headerName, headerValueList) ->
            if (headerName.equals("User-Agent", ignoreCase = true) && headerValueList.isNotEmpty()) {
                hasUserAgent = true
            }

            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        if (!hasUserAgent) {
            requestBuilder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl(YouTube.streamProxy))
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> =
        runCatching {
            withRefreshedJavaScriptPlayerCacheOnExtractorFailure {
                YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
            }
        }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<String> =
        runCatching {
            val directUrl = format.url
            if (directUrl != null) {
                Log.d("PipeInvestigate", "getStreamUrl: directUrl exists, checking n-param")
                val resolvedDirectUrl =
                    if (directUrl.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true) {
                        runCatching {
                            getUrlWithThrottlingParameterDeobfuscated(videoId, directUrl)
                        }.getOrElse { directUrl }
                    } else {
                        directUrl
                    }

                return@runCatching YouTube.appendGvsPoToken(
                    url = resolvedDirectUrl,
                    client = client,
                    authState = authState,
                )
            }

            Log.d("PipeInvestigate", "getStreamUrl: no directUrl, using signatureCipher")
            val cipherString = format.signatureCipher ?: format.cipher
            if (cipherString == null) {
                Log.e("PipeInvestigate", "getStreamUrl: cipherString is null, throwing")
                throw ParsingException("Could not find format url")
            }

            Log.d("PipeInvestigate", "getStreamUrl: cipherString=$cipherString")
            val params = parseQueryString(cipherString)
            val obfuscatedSignature = params["s"]
            val signatureParam = params["sp"]
            val urlString = params["url"]
            Log.d("PipeInvestigate", "getStreamUrl: parsed params s=${obfuscatedSignature?.take(30)} sp=$signatureParam url=${urlString?.take(60)}")

            if (obfuscatedSignature == null) throw ParsingException("Could not parse cipher signature")
            if (signatureParam == null) throw ParsingException("Could not parse cipher signature parameter")
            if (urlString == null) throw ParsingException("Could not parse cipher url")

            val urlBuilder = URLBuilder(urlString)
            Log.d("PipeInvestigate", "getStreamUrl: about to deobfuscateSignature")

            val deobfuscatedSig =
                withRefreshedJavaScriptPlayerCacheOnExtractorFailure {
                    Log.d("PipeInvestigate", "getStreamUrl: calling YoutubeJavaScriptPlayerManager.deobfuscateSignature")
                    val result = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
                    Log.d("PipeInvestigate", "getStreamUrl: deobfuscatedSig=${result?.take(20)}")
                    result
                }

            urlBuilder.parameters[signatureParam] = deobfuscatedSig
            val builtUrl = urlBuilder.buildString()
            Log.d("PipeInvestigate", "getStreamUrl: builtUrl before PoToken=${builtUrl.take(100)}")

            val finalUrl = YouTube.appendGvsPoToken(
                url = builtUrl,
                client = client,
                authState = authState,
            )
            Log.d("PipeInvestigate", "getStreamUrl: finalUrl=${finalUrl.take(100)}")
            finalUrl
        }.onFailure { error ->
            Log.e("PipeInvestigate", "getStreamUrl: FAILED for videoId=$videoId itag=${format.itag}", error)
        }

    private fun getUrlWithThrottlingParameterDeobfuscated(
        videoId: String,
        url: String,
    ): String =
        withRefreshedJavaScriptPlayerCacheOnExtractorFailure {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }

    private inline fun <T> withRefreshedJavaScriptPlayerCacheOnExtractorFailure(block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            runCatching { YoutubeJavaScriptPlayerManager.clearAllCaches() }
            block()
        }

    enum class ExternalAudioService {
        BANDCAMP,
        SOUNDCLOUD,
    }

    data class ExternalAudioQuery(
        val title: String,
        val artists: List<String>,
        val durationSeconds: Int?,
    )

    data class ExternalAudioStream(
        val bitrate: Int,
        val averageBitrate: Int,
        val estimatedBitrate: Int,
        val itag: Int,
        val source: ExternalAudioService,
        val streamUrl: String,
        val mimeType: String?,
        val quality: String?,
        val durationSeconds: Long?,
    )

    fun getHiResLosslessAudioStream(query: ExternalAudioQuery): Result<ExternalAudioStream> =
        runCatching {
            val searchText =
                buildString {
                    append(query.title)
                    if (query.artists.isNotEmpty()) append(" ").append(query.artists.joinToString(" "))
                }

            val candidates =
                listOf(
                    NewPipe.getService("Bandcamp") to ExternalAudioService.BANDCAMP,
                    NewPipe.getService("SoundCloud") to ExternalAudioService.SOUNDCLOUD,
                )

            var lastError: Throwable? = null
            for ((service, source) in candidates) {
                try {
                    val searchExtractor = service.getSearchExtractor(searchText)
                    val searchInfo = SearchInfo.getInfo(searchExtractor)
                    val items = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                    for (item in items) {
                        val streamInfo = StreamInfo.getInfo(service, item.url)
                        val audio =
                            streamInfo.audioStreams
                                .filterIsInstance<AudioStream>()
                                .maxByOrNull { it.averageBitrate }
                        if (audio != null) {
                            return@runCatching ExternalAudioStream(
                                bitrate = audio.bitrate,
                                averageBitrate = audio.averageBitrate,
                                estimatedBitrate = audio.averageBitrate,
                                itag = audio.itag,
                                source = source,
                                streamUrl = audio.url ?: "",
                                mimeType = null,
                                quality = null,
                                durationSeconds = streamInfo.duration,
                            )
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            throw lastError ?: Exception("No hi-res/lossless audio stream found for '${query.title}'")
        }
}
