package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppleLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 40.dp,
    lineCount: Int = 12,
    lineWidth: Dp = 2.dp,
    lineLength: Dp = 12.dp,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val lineWidthPx = with(density) { lineWidth.toPx() }
    val lineLengthPx = with(density) { lineLength.toPx() }
    val center = sizePx / 2f
    val radius = (sizePx - lineLengthPx) / 2f

    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spinnerPulse"
    )

    Canvas(modifier = modifier) {
        for (i in 0 until lineCount) {
            val angle = (2.0 * PI * i / lineCount)
            val phaseOffset = i.toFloat() / lineCount
            val lineAlpha = 0.15f + 0.85f * ((pulse + phaseOffset) % 1f)

            val startX = (center + radius * cos(angle)).toFloat()
            val startY = (center + radius * sin(angle)).toFloat()
            val endX = (center + (radius + lineLengthPx) * cos(angle)).toFloat()
            val endY = (center + (radius + lineLengthPx) * sin(angle)).toFloat()

            drawLine(
                color = color.copy(alpha = lineAlpha * color.alpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = lineWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}