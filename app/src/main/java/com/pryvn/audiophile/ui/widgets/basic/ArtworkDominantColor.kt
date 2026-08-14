package com.pryvn.audiophile.ui.widgets.basic

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads [url] and extracts its dominant color with AndroidX Palette so a screen can
 * paint an artwork-derived hero background. Falls back to a neutral dark tone when the
 * artwork is missing or extraction fails. All bitmap work happens off the main thread.
 */
@Composable
fun rememberArtworkDominantColor(url: String?): Color {
    var color by remember(url) { mutableStateOf(Color(0xFF1A1A1A)) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            color = Color(0xFF1A1A1A)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context).data(url).build()
                val bitmap = loader.execute(request).drawable?.toBitmap()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    bitmap.recycle()
                    val rgb = palette?.dominantSwatch?.rgb
                        ?: palette?.vibrantSwatch?.rgb
                        ?: palette?.darkVibrantSwatch?.rgb
                    if (rgb != null) color = Color(rgb)
                }
            } catch (_: Exception) {
                // keep previous color on failure
            }
        }
    }

    return color
}

/**
 * Darkens a [Color] toward black by [factor] (0f = unchanged, 1f = black) for readable
 * gradient scrims built from the artwork-derived hero color.
 */
fun Color.darken(factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - f),
        green = green * (1f - f),
        blue = blue * (1f - f),
        alpha = alpha,
    )
}
