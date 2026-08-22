package com.pryvn.audiophile.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun AmlLyricsView(
    player: PlayerAdapter,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    lyricsSyncOffset: Int = 0,
    onBackgroundClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    val syncedLyrics by MediaViewModelObject.parsedSyncedLyrics
    val isLoadingLyrics by MediaViewModelObject.isLoadingLyrics

    // Track transition state: 0 = showing spinner, 1 = showing lyrics
    val transitionProgress = remember { Animatable(0f) }
    var wasLoading by remember { mutableStateOf(true) }

    LaunchedEffect(isLoadingLyrics, syncedLyrics) {
        if (syncedLyrics == null && isLoadingLyrics) {
            wasLoading = true
            transitionProgress.snapTo(0f)
        } else if (syncedLyrics != null && wasLoading) {
            wasLoading = false
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 200f,
                ),
            )
        } else if (syncedLyrics != null) {
            wasLoading = false
            transitionProgress.snapTo(1f)
        }
    }

    if (syncedLyrics == null) {
        if (isLoadingLyrics) {
            Box(modifier = modifier.fillMaxSize()) {
                val alpha = 1f - transitionProgress.value
                val scale = 1f - transitionProgress.value * 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    AppleLoadingSpinner()
                }
            }
        }
        return
    }

    var currentPositionMs by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val pos = player.currentPosition
            val newPos = (pos + lyricsSyncOffset).coerceAtLeast(0).toInt()
            if (newPos != currentPositionMs) currentPositionMs = newPos
            delay(50)
        }
    }

    // Poll settings for instant reactivity (SettingsLibrary uses DataSaver, not Compose snapshot)
    var fontSize by remember { mutableStateOf(SettingsLibrary.LyricFontSize) }
    var fontWeightStr by remember { mutableStateOf(SettingsLibrary.LyricFontWeight) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val newFontSize = SettingsLibrary.LyricFontSize
            val newWeight = SettingsLibrary.LyricFontWeight
            if (newFontSize != fontSize) fontSize = newFontSize
            if (newWeight != fontWeightStr) fontWeightStr = newWeight
            delay(100)
        }
    }

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

    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 40f

    LaunchedEffect(syncedLyrics) {
        listState.scrollToItem(0)
    }

    // Lyrics: fade in + scale up on transition, fully visible otherwise
    val lyricsAlpha = transitionProgress.value
    val lyricsScale = 0.85f + transitionProgress.value * 0.15f

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = lyricsAlpha
                scaleX = lyricsScale
                scaleY = lyricsScale
            }
    ) {
        KaraokeLyricsView(
            listState = listState,
            lyrics = syncedLyrics!!,
            currentPosition = { currentPositionMs },
            onLineClicked = { line: ISyncedLine ->
                player.seekTo(line.start.toLong())
            },
            onLinePressed = { },
            modifier = Modifier
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
}
