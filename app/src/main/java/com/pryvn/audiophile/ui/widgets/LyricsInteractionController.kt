package com.pryvn.audiophile.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * Single source of truth for lyrics interaction state.
 * 
 * This controller ensures that lyrics are interactive IF AND ONLY IF
 * the user has explicitly opened the full-screen Lyrics view.
 * 
 * On the main Now Playing screen (inline/background lyrics), interaction
 * is ALWAYS disabled - lyrics are purely visual elements.
 * 
 * Only when the user navigates to the Lyrics tab/view does interaction
 * become enabled (scrolling, tapping to seek, word selection, etc.).
 * 
 * ARCHITECTURAL GUARANTEE: This state cannot be accidentally overridden
 * by UI changes, animation changes, or parameter passing errors. All
 * lyric composables read directly from this CompositionLocal.
 */
@Stable
object LyricsInteractionController {

    /**
     * Current interaction state. Read this in composables to determine
     * if interaction modifiers should be applied.
     * 
     * Usage:
     * val isInteractive = LyricsInteractionController.isInteractive
     * if (isInteractive) Modifier.nestedScroll(...) else Modifier
     */
    @Composable
    fun isInteractive(): Boolean = LocalLyricsInteractive.current

    /**
     * Provides the interaction state for a subtree.
     * 
     * Should ONLY be called from the NowPlaying screen when the
     * Lyrics view is actively displayed (nowPage == Lyric).
     * 
     * @param isLyricsViewOpen true only when the full-screen Lyrics view is active
     */
    @Composable
    fun Provider(isLyricsViewOpen: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalLyricsInteractive provides isLyricsViewOpen,
            content = content
        )
    }

    /**
     * Modifier extension that conditionally applies interaction modifiers
     * based on the global lyrics interaction state.
     * 
     * Usage:
     * Modifier
     *     .fillMaxSize()
     *     .lyricsInteractive { Modifier.nestedScroll(connection) }
     *     .lyricsInteractive { Modifier.clickable { onClick() } }
     * 
     * This ensures ALL interaction modifiers in the codebase derive
     * from the single source of truth.
     */
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

/**
 * CompositionLocal that provides whether lyrics should be interactive.
 * 
 * Default is false (non-interactive) for safety - if not explicitly
 * provided by a parent, lyrics will be non-interactive.
 */
internal val LocalLyricsInteractive = compositionLocalOf { false }