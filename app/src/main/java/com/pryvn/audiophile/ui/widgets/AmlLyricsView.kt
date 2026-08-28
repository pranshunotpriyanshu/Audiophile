package com.pryvn.audiophile.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeBreathingDotsDefaults
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.pryvn.audiophile.code.player.PlayerAdapter
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Bridge composable that adapts the current project's raw lyrics data
 * into AMLL's SyncedLyrics model and renders with KaraokeLyricsView.
 *
 * Consumes [MediaViewModelObject.parsedSyncedLyrics] which is pre-parsed
 * by AutoParser in [com.pryvn.audiophile.code.utils.lrc.LyricsProcessor.applyLyrics]
 * and cached — avoids reparsing on every recomposition.
 *
 * User-configurable style options are read from [SettingsLibrary]:
 * - LyricFontSize / LyricFontWeight → text styles
 * - LyricBlurEffect → useBlurEffect
 * - LyricBlendMode → blendMode
 */
@Composable
fun AmlLyricsView(
    player: PlayerAdapter,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    lyricsSyncOffset: Int = 0,
    onBackgroundClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Consume pre-parsed SyncedLyrics from the cache (parsed in LyricsProcessor)
    val syncedLyrics by MediaViewModelObject.parsedSyncedLyrics

    // Show loading spinner while lyrics are being fetched from online sources
    val isLoadingLyrics by MediaViewModelObject.isLoadingLyrics

    if (syncedLyrics == null) {
        if (isLoadingLyrics) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                AppleLoadingSpinner()
            }
        }
        // No synced lyrics available — caller handles empty state
        return
    }

    // Current playback position in ms — polled because player.currentPosition
    // is a plain getter (not Compose State), so snapshotFlow never re-emits.
    var currentPositionMs by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val pos = player.currentPosition
            val newPos = (pos + lyricsSyncOffset).coerceAtLeast(0).toInt()
            if (newPos != currentPositionMs) currentPositionMs = newPos
            delay(50)
        }
    }

    // Read user-configurable style options from SettingsLibrary
    val fontSize = SettingsLibrary.LyricFontSize
    val fontWeightStr = SettingsLibrary.LyricFontWeight
    val fontWeight = when (fontWeightStr) {
        "Thin" -> FontWeight.Thin
        "ExtraLight" -> FontWeight.ExtraLight
        "Light" -> FontWeight.Light
        "Regular" -> FontWeight.Normal
        "Medium" -> FontWeight.Medium
        "SemiBold" -> FontWeight.SemiBold
        "Bold" -> FontWeight.Bold
        "ExtraBold" -> FontWeight.ExtraBold
        "Black" -> FontWeight.Black
        else -> FontWeight.Bold
    }

    val blendMode = when (SettingsLibrary.LyricBlendMode) {
        "Multiply" -> BlendMode.Multiply
        "Screen" -> BlendMode.Screen
        "SrcOver" -> BlendMode.SrcOver
        else -> BlendMode.Plus
    }

    val textBaseStyle = TextStyle(
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        fontFamily = SfProFontFamily,
        textMotion = TextMotion.Animated,
    )

    val accompanimentStyle = TextStyle(
        fontSize = (fontSize * 0.65f).sp,
        fontWeight = fontWeight,
        fontFamily = SfProFontFamily,
        textMotion = TextMotion.Animated,
    )

    // ---- Swipe-down to show player controls ----
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 40f

    // ---- Instant refresh on song change ----
    LaunchedEffect(syncedLyrics) {
        listState.scrollToItem(0)
    }

    KaraokeLyricsView(
        listState = listState,
        lyrics = syncedLyrics!!,
        currentPosition = { currentPositionMs },
        onLineClicked = { line: ISyncedLine ->
            player.seekTo(com.pryvn.audiophile.code.utils.player.FlamingoBehavior.seekTargetFromLyricClick(line.start.toLong(), 0))
        },
        onLinePressed = { /* Long press - no-op for now */ },
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onBackgroundClick,
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragAccumulator > swipeThreshold) onBackgroundClick()
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) dragAccumulator += dragAmount
                        else dragAccumulator = 0f
                    },
                )
            }
            .padding(horizontal = 12.dp),
        normalLineTextStyle = textBaseStyle,
        accompanimentLineTextStyle = accompanimentStyle,
        textColor = textColor,
        breathingDotsDefaults = KaraokeBreathingDotsDefaults(
            breathingDotsColor = textColor,
        ),
        blendMode = blendMode,
        useBlurEffect = SettingsLibrary.LyricBlurEffect,
        offset = 32.dp,
    )
}

