package com.pryvn.audiophile.ui.widgets.effects

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

enum class ShadowType(val blur: Dp, val offsetY: Float, val offsetX: Float, val areaWeight: Float) {
    Large(16.dp, 0.08f, 0f, 0.94f),
    Medium(18.dp, 0.08f, 0f, 0.94f),
    Small(16.dp, 0.11f, 0f, 0.94f)
}

/**
 * Blurred drop shadow that is rendered ONCE into a cached bitmap (per node size) and then
 * composited as a plain texture on every redraw. This turns the per-frame GPU blur used by a
 * scrolling list into a single one-time cost per visible item, which keeps fling/scroll frames
 * cheap.
 *
 * The bitmap carries a margin around the node so the glow never gets clipped, exactly like the
 * old per-frame draw on the unlimited canvas.
 */
@Composable
fun Modifier.dropShadow(
    shape: Shape,
    shadowAlpha: Float,
    shadowType: ShadowType,
    overlay: Boolean = false
): Modifier = if (shadowAlpha == 0f) this else this.drawWithCache {
    val height = size.height
    val width = size.width

    val shadowSize = Size(width * shadowType.areaWeight, height * shadowType.areaWeight)
    val shadowOutline = shape.createOutline(shadowSize, layoutDirection, this)

    val blurPx = shadowType.blur.toPx()

    val offsetX = shadowType.offsetX * width + (width * (1f - shadowType.areaWeight)) / 2
    val offsetY = shadowType.offsetY * height + (height * (1f - shadowType.areaWeight)) / 2

    // Margin so the blur glow is not clipped at the bitmap edge.
    val margin = if (blurPx > 0) ceil(blurPx * 2f).toInt() else 1
    val bmpWidth = max((width + margin * 2).toInt(), 1)
    val bmpHeight = max((height + margin * 2).toInt(), 1)

    val shadowBitmap = renderShadowBitmap(
        outline = shadowOutline,
        color = Color(0xFF000000).copy(alpha = shadowAlpha),
        overlay = overlay,
        blurPx = blurPx,
        offsetX = offsetX + margin.toFloat(),
        offsetY = offsetY + margin.toFloat(),
        width = bmpWidth,
        height = bmpHeight
    )

    onDrawBehind {
        drawImage(
            image = shadowBitmap,
            topLeft = Offset(-margin.toFloat(), -margin.toFloat())
        )
    }
}

private fun renderShadowBitmap(
    outline: androidx.compose.ui.graphics.Outline,
    color: Color,
    overlay: Boolean,
    blurPx: Float,
    offsetX: Float,
    offsetY: Float,
    width: Int,
    height: Int
): ImageBitmap {
    val androidBitmap = android.graphics.Bitmap.createBitmap(
        width, height, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(androidBitmap.asImageBitmap())
    val paint = Paint().apply {
        this.color = color
        if (overlay) {
            this.blendMode = BlendMode.Overlay
        }
        if (blurPx > 0) {
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    canvas.save()
    canvas.translate(offsetX, offsetY)
    canvas.drawOutline(outline, paint)
    canvas.restore()
    return androidBitmap.asImageBitmap()
}