package com.pryvn.audiophile.ui.widgets.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint

private const val MAX_LAYER_SIDE_PX = 440f

@Composable
fun Modifier.overlayEffect() = this.drawWithCache {
    val overlayPaint = Paint().apply {
        blendMode = BlendMode.Plus
    }
    val rect = Rect(0f, 0f, size.width, size.height)
    val canAffordLayer = size.width <= MAX_LAYER_SIDE_PX && size.height <= MAX_LAYER_SIDE_PX

    if (canAffordLayer) {
        onDrawWithContent {
            val canvas = this.drawContext.canvas

            canvas.saveLayer(rect, overlayPaint)

            drawContent()

            canvas.restore()
        }
    } else {
        onDrawWithContent {
            drawContent()
        }
    }
}
