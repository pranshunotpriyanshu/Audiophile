package com.pryvn.audiophile.ui.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

object MotionTokens {

    // How long the Now Playing background takes to mix into the next song's
    // colors — the blurred artwork crossfade and the palette shifts share it.
    const val BackgroundMixDurationMs = 2600L

    object Duration {
        val press = 80
        val instant = 120
        val fast = 200
        val normal = 280
        val slow = 380
        val themeChange = 400
    }

    object Spring {
        val pressStiffness = 1200f
        val pressDamping = 0.85f

        val appearStiffness = 350f
        val appearDamping = 0.82f

        val transitionStiffness = 500f
        val transitionDamping = 0.80f

        val colorStiffness = 220f
        val colorDamping = 0.88f

        val seekbarStiffness = 1000f
        val seekbarDamping = 0.78f

        val liftStiffness = 400f
        val liftDamping = 0.80f

        val snapStiffness = 900f
        val snapDamping = 0.78f
    }

    object Scale {
        val pressMin = 0.97f
        val pressMax = 1.0f
        val cardEnter = 0.96f
        val selected = 1.03f
        val floatingMax = 1.04f
        val appearMin = 0.97f
    }

    object Opacity {
        val hidden = 0f
        val visible = 1f
        val dimmed = 0.6f
        val subtleDim = 0.85f
        val disabled = 0.38f
    }

    object Translation {
        val liftUp = (-8).dp
        val enterFromBottom = 16.dp
        val cardLift = (-4).dp
    }

    inline fun <reified T> pressSpring(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.pressStiffness,
        dampingRatio = Spring.pressDamping,
    )

    inline fun <reified T> appearSpring(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.appearStiffness,
        dampingRatio = Spring.appearDamping,
    )

    inline fun <reified T> transitionSpring(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.transitionStiffness,
        dampingRatio = Spring.transitionDamping,
    )

    inline fun <reified T> colorSpring(): FiniteAnimationSpec<T> = tween(
        durationMillis = 700,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )

    // Slow, smooth color mix used by the Now Playing background: when the song
    // changes, the palette shifts completely into the next song's colors over
    // this duration instead of changing instantly.
    inline fun <reified T> backgroundMix(): FiniteAnimationSpec<T> = tween(
        durationMillis = BackgroundMixDurationMs.toInt(),
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )

    inline fun <reified T> seekbarSpring(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.seekbarStiffness,
        dampingRatio = Spring.seekbarDamping,
    )

    inline fun <reified T> liftSpring(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.liftStiffness,
        dampingRatio = Spring.liftDamping,
    )

    inline fun <reified T> snapSpring(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.snapStiffness,
        dampingRatio = Spring.snapDamping,
    )

    inline fun <reified T> appearAlpha(): FiniteAnimationSpec<T> = spring(
        stiffness = Spring.appearStiffness,
        dampingRatio = Spring.appearDamping,
    )

    // Non-spring tween for shared-element transitions: smooth, direct, ~500 ms.
    // No bounce, no overshoot, no physics — just A → easing → B.
    inline fun <reified T> sharedTransition(): FiniteAnimationSpec<T> = tween(
        durationMillis = 500,
        easing = FastOutSlowInEasing
    )

    fun fastFadeIn() = tween<Float>(durationMillis = Duration.fast)

    val itemEnterDelayMs = 30
    val itemEnterStaggerMs = 45
}
