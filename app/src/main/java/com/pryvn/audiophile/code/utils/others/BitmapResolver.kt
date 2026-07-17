package com.pryvn.audiophile.code.utils.others

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import com.pryvn.audiophile.data.libraries.SettingsLibrary.NowplayingBackgroundEffect

@Stable
object BitmapResolver {
    fun bitmapCompress(bitmap: Bitmap, lowQuality: Boolean = false): Bitmap {
        val px = if (lowQuality) 4 else (if (NowplayingBackgroundEffect) 96 else 32)
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var compressedBitmap = bitmap

        val size = minOf(originalWidth, originalHeight)
        val xOffset = (originalWidth - size) / 2
        val yOffset = (originalHeight - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        if (size > px) {
            val scaleFactor = size / px
            val scaledSize = size / scaleFactor
            compressedBitmap = Bitmap.createScaledBitmap(squareBitmap, scaledSize, scaledSize, true)
        }

        val config = Bitmap.Config.RGB_565
        return compressedBitmap.copy(config, false)
    }

    fun blurBitmap(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val r = radius.coerceAtMost(25)
        val windowSize = r * 2 + 1
        val `in` = IntArray(width * height)
        val `out` = IntArray(width * height)

        for (y in 0 until height) {
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (x in -r..r) {
                val px = pixels[(y * width) + (x.coerceIn(0, width - 1))]
                sumR += (px shr 16) and 0xff
                sumG += (px shr 8) and 0xff
                sumB += px and 0xff
            }
            for (x in 0 until width) {
                val idx = y * width + x
                `in`[idx] = (sumR / windowSize shl 16) or (sumG / windowSize shl 8) or (sumB / windowSize)
                val left = (x - r).coerceAtLeast(0)
                val right = (x + r + 1).coerceAtMost(width - 1)
                val leftPx = pixels[(y * width) + left]
                val rightPx = pixels[(y * width) + right]
                sumR += ((rightPx shr 16) and 0xff) - ((leftPx shr 16) and 0xff)
                sumG += ((rightPx shr 8) and 0xff) - ((leftPx shr 8) and 0xff)
                sumB += (rightPx and 0xff) - (leftPx and 0xff)
            }
        }

        for (x in 0 until width) {
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (y in -r..r) {
                val px = `in`[(y.coerceIn(0, height - 1) * width) + x]
                sumR += (px shr 16) and 0xff
                sumG += (px shr 8) and 0xff
                sumB += px and 0xff
            }
            for (y in 0 until height) {
                val idx = y * width + x
                `out`[idx] = (sumR / windowSize shl 16) or (sumG / windowSize shl 8) or (sumB / windowSize)
                val top = (y - r).coerceAtLeast(0)
                val bottom = (y + r + 1).coerceAtMost(height - 1)
                val topPx = `in`[(top * width) + x]
                val bottomPx = `in`[(bottom * width) + x]
                sumR += ((bottomPx shr 16) and 0xff) - ((topPx shr 16) and 0xff)
                sumG += ((bottomPx shr 8) and 0xff) - ((topPx shr 8) and 0xff)
                sumB += (bottomPx and 0xff) - (topPx and 0xff)
            }
        }

        val result = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(`out`, 0, width, 0, 0, width, height)
        return result
    }
}