package com.pryvn.audiophile.ui.widgets.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint

/**
 * Implements Add blend effect in Compose.
 *
 * Note: if using .alpha() for transparency, it must be placed after this Modifier, or use .graphicLayer { this.alpha = 0.5f } instead.
 *
 * -- By pryvn
 */
@Composable
fun Modifier.overlayEffect() = this.drawWithCache {
    val overlayPaint = Paint().apply {
        blendMode = BlendMode.Plus
    }
    val rect = Rect(0f, 0f, size.width, size.height)

    onDrawWithContent {
        val canvas = this.drawContext.canvas

        canvas.saveLayer(rect, overlayPaint)

        drawContent()

        canvas.restore()
    }
}