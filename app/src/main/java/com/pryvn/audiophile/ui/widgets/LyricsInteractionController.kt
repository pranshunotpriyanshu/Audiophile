package com.pryvn.audiophile.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@Stable
object LyricsInteractionController {

    @Composable
    fun isInteractive(): Boolean = LocalLyricsInteractive.current

    @Composable
    fun Provider(isLyricsViewOpen: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalLyricsInteractive provides isLyricsViewOpen,
            content = content
        )
    }

    @Composable
    fun Modifier.lyricsInteractive(
        interactiveModifier: @Composable () -> Modifier
    ): Modifier {
        return if (isInteractive()) {
            this.then(interactiveModifier())
        } else {
            this
        }
    }
}

internal val LocalLyricsInteractive = compositionLocalOf { false }
