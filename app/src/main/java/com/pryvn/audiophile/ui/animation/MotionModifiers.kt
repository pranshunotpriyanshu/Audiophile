package com.pryvn.audiophile.ui.animation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Apple-style press feedback: scales down to [pressedScale] on touch-down using a spring,
 * returns to 1.0 on release. Interruptible by design — the spring naturally re-targets.
 */
fun Modifier.pressableScale(
    interactionSource: InteractionSource,
    pressedScale: Float = MotionTokens.Scale.pressMin,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MotionTokens.pressSpring(),
    )
    this.then(Modifier.scale(scale))
}

/**
 * Pressable scale without needing an InteractionSource.
 * Use for combinedClickable or programmatic press states.
 */
fun Modifier.pressableScaleNoSource(
    isPressed: Boolean,
    pressedScale: Float = MotionTokens.Scale.pressMin,
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MotionTokens.pressSpring(),
    )
    this.then(Modifier.scale(scale))
}

/**
 * Apple-style press opacity: dims the element on press for tactile feedback.
 * Pairs with pressableScale for a richer feel.
 */
fun Modifier.pressableAlpha(
    interactionSource: InteractionSource,
    pressedAlpha: Float = MotionTokens.Opacity.subtleDim,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) pressedAlpha else 1f,
        animationSpec = MotionTokens.pressSpring(),
    )
    this.then(Modifier.graphicsLayer { this.alpha = alpha })
}

/**
 * Combined press scale + alpha for the richest tactile feedback.
 * Scale down + dim on press — like iOS list rows.
 */
fun Modifier.pressableFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
    pressedAlpha: Float = 0.92f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MotionTokens.pressSpring(),
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) pressedAlpha else 1f,
        animationSpec = MotionTokens.pressSpring(),
    )
    this.then(
        Modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
    )
}

/**
 * Stagger entrance animation for list items.
 * Returns a delay offset based on index for staggered entry.
 */
fun staggerDelay(index: Int, baseDelayMs: Long = MotionTokens.itemEnterDelayMs.toLong(), staggerMs: Long = MotionTokens.itemEnterStaggerMs.toLong()): Long {
    return baseDelayMs + (index * staggerMs)
}

/**
 * Apple-style item entrance: fades in + slides up slightly with a spring.
 * Use with LaunchedEffect to trigger on screen entry.
 */
@Composable
fun Modifier.itemEntrance(
    index: Int = 0,
    delayPerItemMs: Long = 35L,
    initiallyHidden: Boolean = true,
): Modifier {
    var started by remember { mutableStateOf(!initiallyHidden) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 280f,
        ),
    )
    LaunchedEffect(Unit) {
        delay(index * delayPerItemMs)
        started = true
    }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24f
    }
}

/**
 * Shimmer loading effect — animated gradient sweep.
 * Apply to a placeholder shape while data is loading.
 */
fun Modifier.shimmer(
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    this.then(
        Modifier.drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color.Transparent,
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                        androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    start = Offset(translateX, 0f),
                    end = Offset(translateX + 300f, size.height),
                )
            )
        }
    )
}

/**
 * Scale-on-view: animates from [fromScale] to 1.0 when [visible] becomes true.
 * Great for card/image entrance with a spring.
 */
@Composable
fun Modifier.scaleOnView(
    visible: Boolean = true,
    fromScale: Float = MotionTokens.Scale.cardEnter,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else fromScale,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 300f,
        ),
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 200f,
        ),
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
