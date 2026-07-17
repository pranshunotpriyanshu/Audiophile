package com.pryvn.audiophile.code

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AnimatedArtworkLibrary
{
    private const val AnimatedArtworkDirectoryName = "anim"
    private const val AnimatedArtworkCacheDirectoryName = "animated_artwork"
    private const val ArtworkSearchEndpoint = "https://artwork.m8tec.top/api/v1/artwork/search"
    private const val NetworkTimeoutMilliseconds = 15000
    private const val DefaultSampleBufferBytes = 1024 * 1024
    private const val MaximumPlaylistBytes = 1024 * 1024
    private const val MaximumArtworkBytes = 50L * 1024L * 1024L
    private const val MaximumExpectedStartOffsetMicroseconds = 500_000L
    private val bandwidthRegex = Regex("BANDWIDTH=(\\d+)")
    private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|]")
    private val hlsMapUriRegex = Regex("URI=\"([^\"]+)\"")
    private val resolutionRegex = Regex("RESOLUTION=(\\d+)x(\\d+)")
    private val artworkMutexes = ConcurrentHashMap<String, Mutex>()

    private data class StreamVariant(
        val url: String,
        val isAvc: Boolean,
        val pixelCount: Int,
        val bandwidth: Long
    )

    suspend fun resolveArtworkFile(context: Context, music: YosMediaItem): File? = withContext(Dispatchers.IO)
    {
        if (!SettingsLibrary.AnimatedAlbumCovers) {return@withContext null}

        val albumName = music.album?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        localArtworkFile(music)?.takeIf { isPlayableVideoFile(it) }?.let { return@withContext it }
        val cachedArtworkFile = cachedArtworkFile(context, music, albumName)
        val artworkMutex = artworkMutexes.getOrPut(cachedArtworkFile.absolutePath) { Mutex() }

        artworkMutex.withLock {
            if (cachedArtworkFile.exists())
            {
                if (cachedArtworkFile.isFile && cachedArtworkFile.length() > 0L && normalizeCachedMp4File(cachedArtworkFile))
                {
                    return@withLock cachedArtworkFile
                }

                cachedArtworkFile.delete()
            }

            if (!SettingsLibrary.AnimatedAlbumCoversUseApi) {return@withLock null}
            if (SettingsLibrary.isAnimatedAlbumCoverBlacklisted(albumName)) {return@withLock null}

            val searchUrl = buildSearchUrl(music, albumName) ?: return@withLock null
            val hlsUrl = fetchArtworkHlsUrl(searchUrl) ?: return@withLock null
            val mp4Url = resolveMp4Url(hlsUrl) ?: return@withLock null

            if (downloadFile(mp4Url, cachedArtworkFile)) {cachedArtworkFile} else null
        }
    }

    suspend fun deleteCachedArtworkFiles(context: Context): Int = withContext(Dispatchers.IO)
    {
        cacheDirectory(context).listFiles()?.count { it.delete() } ?: 0
    }

    suspend fun cachedArtworkFilesSizeBytes(context: Context): Long = withContext(Dispatchers.IO)
    {
        cacheDirectory(context).listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun localArtworkFile(music: YosMediaItem): File?
    {
        val albumName = music.album?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val songPath = music.uri?.path ?: return null
        val songDirectory = File(songPath).parentFile ?: return null
        if (!songDirectory.isDirectory) {return null}

        return animatedArtworkFile(songDirectory, albumName)
    }

    private fun isPlayableVideoFile(artworkFile: File): Boolean
    {
        return artworkFile.isFile && artworkFile.length() > 0L && firstVideoSampleTimeUs(artworkFile) != null
    }

    private fun cacheDirectory(context: Context): File
    {
        return File(context.cacheDir, AnimatedArtworkCacheDirectoryName)
    }

    private fun cachedArtworkFile(context: Context, music: YosMediaItem, albumName: String): File
    {
        val artistName = music.albumArtists?.trim()?.takeIf { it.isNotEmpty() }
            ?: music.artistsName?.trim().orEmpty()

        return animatedArtworkCacheFile(cacheDirectory(context), artistName, albumName)
    }

    internal fun animatedArtworkCacheFile(cacheDirectory: File, artistName: String, albumName: String): File
    {
        val cacheIdentity = "${artistName.lowercase(Locale.ROOT)}\u0000${albumName.lowercase(Locale.ROOT)}"
        val cacheHash = MessageDigest.getInstance("SHA-256")
            .digest(cacheIdentity.toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

        return File(cacheDirectory, "$cacheHash.mp4")
    }

    internal fun animatedArtworkFileName(albumName: String): String
    {
        val safeAlbumName = albumName
            .trim()
            .replace(invalidFileNameCharacters, "_")
            .ifEmpty { "animated_artwork" }

        return "$safeAlbumName.mp4"
    }

    internal fun animatedArtworkFile(songDirectory: File, albumName: String): File
    {
        return File(File(songDirectory, AnimatedArtworkDirectoryName), animatedArtworkFileName(albumName))
    }

    private fun buildSearchUrl(music: YosMediaItem, albumName: String): String?
    {
        val artistName = music.albumArtists?.trim()?.takeIf { it.isNotEmpty() }
            ?: music.artistsName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val queryParameters = mutableListOf(
            "artist=${encodeUrlParameter(artistName)}",
            "album=${encodeUrlParameter(albumName)}"
        )
        music.title?.trim()?.takeIf { it.isNotEmpty() }?.let {
            queryParameters += "title=${encodeUrlParameter(it)}"
        }

        return "$ArtworkSearchEndpoint?${queryParameters.joinToString("&")}"
    }

    private fun encodeUrlParameter(value: String): String
    {
        return URLEncoder.encode(value, "UTF-8")
    }

    private suspend fun fetchArtworkHlsUrl(searchUrl: String): String?
    {
        return try
        {
            val responseText = readUrlText(searchUrl)
            JSONObject(responseText).optString("url").takeIf { it.isNotBlank() }
        }
        catch (cancellationException: CancellationException)
        {
            throw cancellationException
        }
        catch (_: Exception)
        {
            null
        }
    }

    private suspend fun resolveMp4Url(hlsUrl: String): String?
    {
        return try
        {
            val masterPlaylistText = readUrlText(hlsUrl)
            val mediaPlaylistUrl = pickBestStreamUrl(masterPlaylistText, hlsUrl)

            if (mediaPlaylistUrl == null)
            {
                extractMappedMp4Url(masterPlaylistText, hlsUrl)
            }
            else
            {
                extractMappedMp4Url(readUrlText(mediaPlaylistUrl), mediaPlaylistUrl)
            }
        }
        catch (cancellationException: CancellationException)
        {
            throw cancellationException
        }
        catch (_: Exception)
        {
            null
        }
    }

    internal fun pickBestStreamUrl(masterPlaylistText: String, masterPlaylistUrl: String): String?
    {
        val playlistLines = masterPlaylistText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val streamVariants = mutableListOf<StreamVariant>()

        playlistLines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) {return@forEachIndexed}

            val streamPath = playlistLines.getOrNull(index + 1)?.takeIf { !it.startsWith("#") } ?: return@forEachIndexed
            val resolution = resolutionRegex.find(line)?.groupValues
            val pixelCount = if (resolution != null) {resolution[1].toInt() * resolution[2].toInt()} else {0}
            val bandwidth = bandwidthRegex.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

            streamVariants += StreamVariant(
                url = absoluteUrl(masterPlaylistUrl, streamPath),
                isAvc = line.contains("avc1", ignoreCase = true),
                pixelCount = pixelCount,
                bandwidth = bandwidth
            )
        }

        return streamVariants
            .filter { it.isAvc }
            .ifEmpty { streamVariants }
            .maxWithOrNull(compareBy<StreamVariant> { it.pixelCount }.thenBy { it.bandwidth })
            ?.url
    }

    internal fun extractMappedMp4Url(mediaPlaylistText: String, mediaPlaylistUrl: String): String?
    {
        mediaPlaylistText.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXT-X-MAP"))
            {
                val mapUri = hlsMapUriRegex.find(line)?.groupValues?.getOrNull(1) ?: return@forEach
                if (mapUri.endsWith(".mp4", ignoreCase = true)) {return absoluteUrl(mediaPlaylistUrl, mapUri)}
            }
        }

        mediaPlaylistText.lineSequence().map { it.trim() }.forEach { line ->
            if (!line.startsWith("#") && line.endsWith(".mp4", ignoreCase = true))
            {
                return absoluteUrl(mediaPlaylistUrl, line)
            }
        }

        return null
    }

    private fun absoluteUrl(baseUrl: String, path: String): String
    {
        return URL(URL(baseUrl), path).toString()
    }

    private suspend fun readUrlText(url: String): String
    {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = NetworkTimeoutMilliseconds
        connection.readTimeout = NetworkTimeoutMilliseconds
        connection.requestMethod = "GET"

        try
        {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {throw IllegalStateException("HTTP $responseCode")}

            return connection.inputStream.use { readUrlBytes(it, MaximumPlaylistBytes) }.toString(Charsets.UTF_8)
        }
        finally
        {
            connection.disconnect()
        }
    }

    internal suspend fun readUrlBytes(inputStream: InputStream, maximumBytes: Int): ByteArray
    {
        val outputStream = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (true)
        {
            currentCoroutineContext().ensureActive()
            val bytesRead = inputStream.read(buffer)
            if (bytesRead < 0) {break}
            totalBytes += bytesRead
            if (totalBytes > maximumBytes) {throw IOException("Response exceeds $maximumBytes bytes")}
            outputStream.write(buffer, 0, bytesRead)
        }

        return outputStream.toByteArray()
    }

    private suspend fun downloadFile(sourceUrl: String, destinationFile: File): Boolean
    {
        if (destinationFile.exists()) {return false}

        val destinationDirectory = destinationFile.parentFile ?: return false
        if (!destinationDirectory.isDirectory && !destinationDirectory.mkdirs()) {return false}

        val temporaryFile = File(destinationDirectory, ".${destinationFile.name}.tmp")
        val normalizedTemporaryFile = File(destinationDirectory, ".${destinationFile.name}.normalized.tmp")
        var completed = false
        temporaryFile.delete()
        normalizedTemporaryFile.delete()

        var connection: HttpURLConnection? = null

        try
        {
            connection = URL(sourceUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = NetworkTimeoutMilliseconds
            connection.readTimeout = NetworkTimeoutMilliseconds
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {return false}
            if (connection.contentLength > MaximumArtworkBytes) {return false}

            connection.inputStream.use { inputStream ->
                temporaryFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L

                    while (true)
                    {
                        currentCoroutineContext().ensureActive()
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead < 0) {break}
                        totalBytes += bytesRead
                        if (totalBytes > MaximumArtworkBytes) {return false}
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (temporaryFile.length() == 0L) {return false}
            if (!normalizeMp4File(temporaryFile, normalizedTemporaryFile)) {return false}
            if (destinationFile.exists()) {return false}

            completed = normalizedTemporaryFile.renameTo(destinationFile)
            return completed
        }
        catch (cancellationException: CancellationException)
        {
            throw cancellationException
        }
        catch (_: Exception)
        {
            return false
        }
        finally
        {
            connection?.disconnect()
            temporaryFile.delete()
            if (!completed) {normalizedTemporaryFile.delete()}
        }
    }

    private suspend fun normalizeCachedMp4File(artworkFile: File): Boolean
    {
        val firstVideoSampleTimeUs = firstVideoSampleTimeUs(artworkFile) ?: return false
        if (firstVideoSampleTimeUs <= MaximumExpectedStartOffsetMicroseconds) {return true}

        val normalizedTemporaryFile = File(artworkFile.parentFile, ".${artworkFile.name}.normalized.tmp")
        val backupFile = File(artworkFile.parentFile, ".${artworkFile.name}.backup.tmp")
        normalizedTemporaryFile.delete()
        backupFile.delete()

        if (!normalizeMp4File(artworkFile, normalizedTemporaryFile)) {return true}
        if (!artworkFile.renameTo(backupFile))
        {
            normalizedTemporaryFile.delete()
            return true
        }

        if (!normalizedTemporaryFile.renameTo(artworkFile))
        {
            normalizedTemporaryFile.delete()
            backupFile.renameTo(artworkFile)
            return false
        }

        backupFile.delete()
        return true
    }

    private fun firstVideoSampleTimeUs(artworkFile: File): Long?
    {
        val extractor = MediaExtractor()

        try
        {
            extractor.setDataSource(artworkFile.absolutePath)

            for (trackIndex in 0 until extractor.trackCount)
            {
                val format = extractor.getTrackFormat(trackIndex)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mimeType.startsWith("video/")) {continue}

                extractor.selectTrack(trackIndex)
                return extractor.sampleTime.coerceAtLeast(0L)
            }

            return null
        }
        catch (_: Exception)
        {
            return null
        }
        finally
        {
            extractor.release()
        }
    }

    private suspend fun normalizeMp4File(sourceFile: File, destinationFile: File): Boolean
    {
        destinationFile.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var wroteSample = false
        var stopSucceeded = true

        try
        {
            extractor.setDataSource(sourceFile.absolutePath)
            muxer = MediaMuxer(destinationFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val muxerTrackIndexes = addSupportedTracks(extractor, muxer)
            if (muxerTrackIndexes.none { it >= 0 }) {return false}

            val trackStartTimesUs = LongArray(extractor.trackCount) { Long.MIN_VALUE }
            val sampleBuffer = ByteBuffer.allocate(maxSampleInputSize(extractor))
            val bufferInfo = MediaCodec.BufferInfo()

            muxer.start()
            muxerStarted = true

            while (true)
            {
                currentCoroutineContext().ensureActive()
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex < 0) {break}

                val muxerTrackIndex = muxerTrackIndexes[sampleTrackIndex]
                if (muxerTrackIndex < 0)
                {
                    extractor.advance()
                    continue
                }

                sampleBuffer.clear()
                val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) {break}

                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                if (trackStartTimesUs[sampleTrackIndex] == Long.MIN_VALUE)
                {
                    trackStartTimesUs[sampleTrackIndex] = sampleTimeUs
                }

                bufferInfo.set(
                    0,
                    sampleSize,
                    (sampleTimeUs - trackStartTimesUs[sampleTrackIndex]).coerceAtLeast(0L),
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    else
                    {
                        0
                    }
                )
                muxer.writeSampleData(muxerTrackIndex, sampleBuffer, bufferInfo)
                wroteSample = true

                extractor.advance()
            }
        }
        catch (cancellationException: CancellationException)
        {
            throw cancellationException
        }
        catch (_: Exception)
        {
            wroteSample = false
        }
        finally
        {
            extractor.release()
            if (muxerStarted)
            {
                stopSucceeded = runCatching { muxer?.stop() }.isSuccess
            }
            muxer?.release()
        }

        if (!wroteSample || !stopSucceeded || destinationFile.length() == 0L)
        {
            destinationFile.delete()
            return false
        }

        return true
    }

    private fun addSupportedTracks(extractor: MediaExtractor, muxer: MediaMuxer): IntArray
    {
        val muxerTrackIndexes = IntArray(extractor.trackCount) { -1 }

        for (trackIndex in 0 until extractor.trackCount)
        {
            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mimeType.startsWith("video/")) {continue}

            muxerTrackIndexes[trackIndex] = muxer.addTrack(format)
            extractor.selectTrack(trackIndex)
        }

        return muxerTrackIndexes
    }

    private fun maxSampleInputSize(extractor: MediaExtractor): Int
    {
        var maxSampleInputSize = DefaultSampleBufferBytes

        for (trackIndex in 0 until extractor.trackCount)
        {
            val format = extractor.getTrackFormat(trackIndex)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
            {
                maxSampleInputSize = maxOf(
                    maxSampleInputSize,
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtMost(MaximumArtworkBytes.toInt())
                )
            }
        }

        return maxSampleInputSize
    }
}
