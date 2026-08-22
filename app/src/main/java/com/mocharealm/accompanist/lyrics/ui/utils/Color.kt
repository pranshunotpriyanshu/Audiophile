package com.mocharealm.accompanist.lyrics.ui.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

fun Color.copyHsl(
    hue: Float? = null,
    saturation: Float? = null,
    lightness: Float? = null,
    alpha: Float? = null
): Color {
    val (h, s, l) = this.toHsl()

    // Ensure hue wraps around correctly (0-360 degrees) and other values are clamped.
    val newHue = hue?.coerceIn(0f, 360f) ?: h
    val newSaturation = saturation?.coerceIn(0f, 1f) ?: s
    val newLightness = lightness?.coerceIn(0f, 1f) ?: l
    val newAlpha = alpha?.coerceIn(0f, 1f) ?: this.alpha

    return hslToColor(newHue, newSaturation, newLightness, newAlpha)
}

// --- Helper Functions ---

private fun Color.toHsl(): FloatArray {
    val r = this.red
    val g = this.green
    val b = this.blue

    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min

    var h = 0f
    var s = 0f
    val l = (max + min) / 2f

    if (max != min) {
        s = if (l < 0.5f) delta / (max + min) else delta / (2f - max - min)
        h = when (max) {
            r -> (g - b) / delta + (if (g < b) 6f else 0f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        h *= 60f
    }

    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, a: Float = 1.0f): Color {
    if (s == 0f) {
        // If saturation is 0, it's a shade of gray.
        return Color(red = l, green = l, blue = l, alpha = a)
    }

    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q

    val hueNormalized = h / 360f
    val r = hueToRgbComponent(p, q, hueNormalized + 1f / 3f)
    val g = hueToRgbComponent(p, q, hueNormalized)
    val b = hueToRgbComponent(p, q, hueNormalized - 1f / 3f)

    return Color(red = r, green = g, blue = b, alpha = a)
}

private fun hueToRgbComponent(p: Float, q: Float, t: Float): Float {
    var tempT = t
    if (tempT < 0f) tempT += 1f
    if (tempT > 1f) tempT -= 1f
    return when {
        tempT < 1f / 6f -> p + (q - p) * 6f * tempT
        tempT < 1f / 2f -> q
        tempT < 2f / 3f -> p + (q - p) * (2f / 3f - tempT) * 6f
        else -> p
    }
}
