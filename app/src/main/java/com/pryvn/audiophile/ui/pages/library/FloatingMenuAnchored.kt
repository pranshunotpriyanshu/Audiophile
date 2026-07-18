package com.pryvn.audiophile.ui.pages.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pryvn.audiophile.ui.theme.withNight

@Composable
fun FloatingMenuAnchored(
    expandedLambda: () -> Boolean,
    expandedOnChanged: (Boolean) -> Unit,
    buttonPosition: Offset = Offset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current

    if (expandedLambda()) {
        Popup(
            alignment = Alignment.TopStart,
            properties = PopupProperties(focusable = true),
            onDismissRequest = { expandedOnChanged(false) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expandedOnChanged(false) },
                    ),
            ) {
                val topDp: Dp = with(density) { buttonPosition.y.toDp() }
                AnimatedVisibility(
                    visible = true,
                    modifier = Modifier
                        .padding(top = topDp + 10.dp)
                        .padding(start = with(density) { buttonPosition.x.toDp() }),
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.618f,
                        transformOrigin = TransformOrigin(0.95f, 0f),
                    ),
                    exit = fadeOut() + scaleOut(
                        targetScale = 0.618f,
                        transformOrigin = TransformOrigin(0.95f, 0f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color(0xFFE9E9E9) withNight Color(0xFF161616))
                            .padding(vertical = 4.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
