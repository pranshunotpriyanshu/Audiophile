package com.pryvn.audiophile.code

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.TagLib
import java.io.File
import java.nio.charset.Charset

object AudioMetadataUtils {
    fun loadLrcFile(context: Context, filePath: String): String? {
        return try {
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charset.defaultCharset())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadEmbeddedLyrics(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            val pfd = when (uri.scheme) {
                "content" -> context.contentResolver.openFileDescriptor(uri, "r")
                else -> {
                    val path = uri.path ?: return null
                    val file = File(path)
                    if (!file.exists()) return null
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
            var lyrics: String? = null
            pfd?.use { fd ->
                val metadata = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val map = metadata?.propertyMap
                lyrics = map?.get("SYNCEDLYRICS")?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: map?.get("LYRICS")?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: map?.get("UNSYNCEDLYRICS")?.firstOrNull()?.takeIf { it.isNotBlank() }
            }
            lyrics
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getQualityInfos(filePath: String): Pair<Int, Int> {
        val songFile = File(filePath)
        var bitrate: Int
        var sampleRate: Int

        println("quality: Taglib implementation")

        ParcelFileDescriptor.open(songFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd(), AudioPropertiesReadStyle.Fast)
            bitrate = audioProperties?.bitrate ?: -1
            sampleRate = audioProperties?.sampleRate ?: -1
        }

        if (bitrate == -1 || sampleRate == -1) {
            val extractor = MediaExtractor()
            try {
                println("quality: MediaExtractor implementation")
                extractor.setDataSource(filePath)
                val format = extractor.getTrackFormat(0)
                if (bitrate == -1) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (sampleRate == -1) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extractor.release()
            }
        }

        return Pair(bitrate, sampleRate)
    }

}
