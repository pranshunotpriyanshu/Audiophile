package com.pryvn.audiophile.np.transition

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

enum class SharedElementRole { Source, Target }

fun applySharedTransform(
    scope: GraphicsLayerScope,
    bounds: SharedElementBounds,
    progress: Float,
    targetSide: Boolean
) {
    val f = progress.coerceIn(0f, 1f)
    val active = f > 0.001f && f < 0.999f
    if (!active) {
        scope.translationX = 0f; scope.translationY = 0f
        scope.scaleX = 1f; scope.scaleY = 1f
        scope.transformOrigin = TransformOrigin(0.5f, 0.5f)
        scope.alpha = if (targetSide) 0f else 1f
        return
    }
    val scx = bounds.source.center.x; val scy = bounds.source.center.y
    val tcx = bounds.target.center.x; val tcy = bounds.target.center.y
    scope.translationX = (scx - tcx) * (1f - f)
    scope.translationY = (scy - tcy) * (1f - f)
    val sw = bounds.source.width; val sh = bounds.source.height
    val tw = bounds.target.width; val th = bounds.target.height
    if (targetSide) {
        scope.scaleX = (sw / tw - 1f) * f + 1f
        scope.scaleY = (sh / th - 1f) * f + 1f
        scope.alpha = f
    } else {
        scope.scaleX = (tw / sw - 1f) * f + 1f
        scope.scaleY = (th / sh - 1f) * f + 1f
        scope.alpha = 1f - f
    }
    scope.transformOrigin = TransformOrigin(0.5f, 0.5f)
}

private const val CAPTURE_GATE_MIN = 0.05f
private const val CAPTURE_GATE_MAX = 0.95f

fun Modifier.sharedElementCapture(
    registry: SharedElementRegistry,
    key: String,
    role: SharedElementRole,
    progressProvider: () -> Float
): Modifier = this.then(
    Modifier.onGloballyPositioned { coords ->
        val p = progressProvider()
        if (p > CAPTURE_GATE_MIN && p < CAPTURE_GATE_MAX) return@onGloballyPositioned
        val pos = coords.positionInRoot()
        val rect = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
        when (role) {
            SharedElementRole.Source -> registry.registerSource(key, rect)
            SharedElementRole.Target -> registry.registerTarget(key, rect)
        }
    }
)

fun Modifier.sharedElementTransform(
    registry: SharedElementRegistry,
    key: String,
    role: SharedElementRole,
    progress: State<Float>
): Modifier = this.then(
    Modifier.graphicsLayer {
        val bounds = registry.lookup(key)
        if (bounds != null) {
            applySharedTransform(this, bounds, progress.value, role == SharedElementRole.Target)
        } else {
            alpha = if (role == SharedElementRole.Target) 0f else 1f
        }
    }
)
