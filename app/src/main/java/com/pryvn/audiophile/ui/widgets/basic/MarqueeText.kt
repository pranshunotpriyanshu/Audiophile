package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    edgeGradientWidth: Dp = 14.dp,
    initialDelayMillis: Long = 1200L,
    endDelayMillis: Long = 1200L,
    velocityDpPerSec: Int = 30,
    enabled: Boolean = true
) {
    if (!enabled) {
        Text(
            text = text,
            style = style.copy(
                color = if (color != Color.Unspecified) color else style.color,
                fontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize,
                fontWeight = fontWeight ?: style.fontWeight,
                letterSpacing = if (letterSpacing != TextUnit.Unspecified) letterSpacing else style.letterSpacing,
                textAlign = textAlign ?: style.textAlign,
                lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else style.lineHeight
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    val mergedStyle = style.copy(
        color = if (color != Color.Unspecified) color else style.color,
        fontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize,
        fontWeight = fontWeight ?: style.fontWeight,
        letterSpacing = if (letterSpacing != TextUnit.Unspecified) letterSpacing else style.letterSpacing,
        textAlign = textAlign ?: style.textAlign,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else style.lineHeight
    )

    SubcomposeLayout(
        modifier = modifier.clipToBounds()
    ) { constraints ->
        val containerWidth = constraints.maxWidth

        val mainMeasurable = subcompose(MarqueeSlots.MainText) {
            Text(
                text = text,
                style = mergedStyle,
                maxLines = 1,
                softWrap = false
            )
        }.first()

        val mainPlaceable = mainMeasurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))
        val textWidth = mainPlaceable.width

        if (textWidth <= containerWidth || containerWidth <= 0) {
            // Text fits inside container cleanly: render static single line
            layout(mainPlaceable.width, mainPlaceable.height) {
                mainPlaceable.placeRelative(0, 0)
            }
        } else {
            // Text overflows: render animated scrolling marquee
            val marqueeMeasurable = subcompose(MarqueeSlots.MarqueeContent) {
                MarqueeContent(
                    text = text,
                    style = mergedStyle,
                    textWidth = textWidth,
                    containerWidth = containerWidth,
                    edgeGradientWidth = edgeGradientWidth,
                    initialDelayMillis = initialDelayMillis,
                    endDelayMillis = endDelayMillis,
                    velocityDpPerSec = velocityDpPerSec
                )
            }.first()

            val marqueePlaceable = marqueeMeasurable.measure(constraints)

            layout(containerWidth, marqueePlaceable.height) {
                marqueePlaceable.placeRelative(0, 0)
            }
        }
    }
}

private enum class MarqueeSlots {
    MainText,
    MarqueeContent
}

@Composable
private fun MarqueeContent(
    text: String,
    style: TextStyle,
    textWidth: Int,
    containerWidth: Int,
    edgeGradientWidth: Dp,
    initialDelayMillis: Long,
    endDelayMillis: Long,
    velocityDpPerSec: Int
) {
    val scrollState = rememberScrollState()
    val maxScroll = (textWidth - containerWidth).coerceAtLeast(0)

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(text, maxScroll) {
        if (maxScroll <= 0) return@LaunchedEffect

        val distance = maxScroll.toFloat()
        val durationMillis = ((distance / velocityDpPerSec) * 30f).roundToInt().coerceAtLeast(1500)

        while (true) {
            if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
                delay(250)
                continue
            }
            scrollState.scrollTo(0)
            delay(initialDelayMillis)

            scrollState.animateScrollTo(
                value = maxScroll,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing)
            )

            delay(endDelayMillis)

            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 500, easing = LinearEasing)
            )
        }
    }

    val edgeGradientPx = edgeGradientWidth.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()

                val currentScroll = scrollState.value
                val isScrolledFromStart = currentScroll > 4
                val isScrolledBeforeEnd = currentScroll < (maxScroll - 4)

                if (isScrolledFromStart && edgeGradientPx > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = 0f,
                            endX = edgeGradientPx * density
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }

                if (isScrolledBeforeEnd && edgeGradientPx > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = size.width - (edgeGradientPx * density),
                            endX = size.width
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
            .horizontalScroll(scrollState, enabled = false)
    ) {
        Row {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
