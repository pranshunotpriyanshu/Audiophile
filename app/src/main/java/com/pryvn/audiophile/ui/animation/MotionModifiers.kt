package com.pryvn.audiophile.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

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
