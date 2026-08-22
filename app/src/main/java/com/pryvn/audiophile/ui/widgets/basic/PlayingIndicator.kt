package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Apple Music-style animated playing bars (3 bars bouncing).
 * Shows when a song is currently playing.
 */
@Composable
fun PlayingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFC3C44),
    barCount: Int = 3,
) {
    val transition = rememberInfiniteTransition(label = "playingBars")

    val heights = (0 until barCount).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + (index * 100),
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar_$index",
        )
    }

    Canvas(modifier = modifier.size(width = 18.dp, height = 16.dp)) {
        val barWidth = size.width / (barCount * 2 - 1)
        val gap = barWidth
        val cornerRadius = CornerRadius(barWidth / 2)

        heights.forEach { anim ->
            val barHeight = size.height * anim.value
            val x = (heights.indexOf(anim) * (barWidth + gap))

            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}
