package com.pryvn.audiophile.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pryvn.audiophile.code.player.PlayerAdapter
import com.pryvn.audiophile.code.utils.lrc.TTMLParser
import com.pryvn.audiophile.code.utils.lyrics.LyricsEntryBridge
import com.pryvn.audiophile.data.objects.LyricsEntry
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.headline
import com.pryvn.audiophile.ui.theme.isAudiophileInDarkMode
import com.pryvn.audiophile.ui.theme.primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

private val SmoothDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
private const val LyricsWordSyncLeadMs = 300L

val LyricsPreviewTime = 2.seconds

private const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"

private val NoSpaceAfterChars: Set<Char> = setOf('(', '[', '{', '\u00ab', '\u2039', '\u201c', '\u2018')

enum class LyricsAnimationStyle { NONE, APPLE, KARAOKE, FADE, GLOW, SLIDE }

enum class LyricsPosition { LEFT, CENTER, RIGHT }

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ArchiveLyrics(
    player: PlayerAdapter,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val landscapeOffset = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lyricsTextPosition = LyricsPosition.LEFT
    val lyricsAnimationStyle = LyricsAnimationStyle.APPLE
    val lyricsTextSize = 26f
    val lyricsLineSpacing = 1.3f
    val lyricsLineBlur = true
    val animationsDisabled = false
    val lyricsFontFamily = SfProFontFamily

    val verticalLineSpacing = with(LocalDensity.current) {
        (lyricsTextSize.sp * (lyricsLineSpacing - 1f)).toDp().coerceAtLeast(0.dp)
    }
    val changeLyrics = true
    val scrollLyrics = true
    val scope = rememberCoroutineScope()

    val lyrics by MediaViewModelObject.onlineLyrics

    val isCustomBackground = false
    val useDarkTheme = isAudiophileInDarkMode()

    val lines = remember(lyrics, player.duration) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            LyricsEntryBridge.fromRawLyrics(lyrics, player.duration)
        }
    }

    val isSynced = remember(lyrics) {
        val l = lyrics
        !l.isNullOrEmpty() && (TTMLParser.isLineSyncedLrc(l) || TTMLParser.isTtml(l))
    }

    val lyricsBaseColor = if (useDarkTheme || isCustomBackground) Color.White else Color.Black
    val lyricsGlowColor = if (useDarkTheme || isCustomBackground) Color.White else Color.Black

    val wordSyncLeadMs = remember(lyrics) {
        val l = lyrics
        if (l != null && TTMLParser.isTtml(l)) 0L else LyricsWordSyncLeadMs
    }
    val lineSyncLeadMs = remember(lyrics) {
        val l = lyrics
        if (l != null && TTMLParser.isTtml(l)) 0L else LyricsWordSyncLeadMs
    }

    var currentLineIndex by remember { mutableIntStateOf(-1) }
    var deferredCurrentLineIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }
    var previousLineIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastPreviewTime by rememberSaveable { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    var shouldScrollToFirstLine by rememberSaveable { mutableStateOf(true) }
    var isAppMinimized by rememberSaveable { mutableStateOf(false) }

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    val maxSelectionLimit = 5

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast.makeText(
                context, "Maximum selection limit: $maxSelectionLimit", Toast.LENGTH_SHORT,
            ).show()
            showMaxSelectionToast = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val isCurrentLineVisible = visibleItemsInfo.any { it.index == currentLineIndex }
                if (isCurrentLineVisible) {
                    initialScrollDone = false
                }
                isAppMinimized = true
            } else if (event == Lifecycle.Event.ON_START) {
                isAppMinimized = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lines) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    var isManualScrolling by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lyrics, lines, isAppMinimized) {
        val l = lyrics
        if (l.isNullOrEmpty() || (!TTMLParser.isLineSyncedLrc(l) && !TTMLParser.isTtml(l))) {
            currentLineIndex = -1
            currentPlaybackPosition = 0L
            return@LaunchedEffect
        }

        val isTtmlLyrics = TTMLParser.isTtml(l)
        while (isActive) {
            if (isAppMinimized) {
                delay(250L)
                continue
            }
            if (isTtmlLyrics) {
                withFrameNanos { }
            } else {
                delay(50L)
            }
            val sliderPosition = sliderPositionProvider()
            val seekingNow = sliderPosition != null
            if (isSeeking != seekingNow) {
                isSeeking = seekingNow
            }
            val position = sliderPosition ?: player.currentPosition
            val syncedPosition = (position + wordSyncLeadMs + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
            if (currentPlaybackPosition != syncedPosition) {
                currentPlaybackPosition = syncedPosition
            }
            val newLineIndex = findCurrentLineIndex(lines, position + lyricsSyncOffset.toLong(), leadMs = lineSyncLeadMs)
            if (currentLineIndex != newLineIndex) {
                currentLineIndex = newLineIndex
            }
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            isManualScrolling = false
            lastPreviewTime = 0L
        }
    }

    LaunchedEffect(currentLineIndex, lastPreviewTime, initialScrollDone) {
        fun calculateOffset() = with(density) {
            if (currentLineIndex < 0 || currentLineIndex >= lines.size) return@with 0
            val currentItem = lines[currentLineIndex]
            val totalNewLines = currentItem.text.count { it == '\n' }
            val dpValue = if (landscapeOffset) 16.dp else 20.dp
            dpValue.toPx().toInt() * totalNewLines
        }

        if (!isSynced) return@LaunchedEffect

        suspend fun performSmoothPageScroll(targetIndex: Int, isSeek: Boolean = false) {
            try {
                val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                if (itemInfo != null) {
                    val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                    val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)
                    val itemCenter = itemInfo.offset + itemInfo.size / 2
                    val offset = itemCenter - center

                    if (abs(offset) > 5) {
                        lazyListState.animateScrollBy(
                            value = offset.toFloat(),
                            animationSpec = if (isSeek) {
                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                            } else {
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                            },
                        )
                    }
                } else {
                    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
                    val distance = abs(targetIndex - firstVisibleIndex)

                    if (distance > 15) {
                        lazyListState.scrollToItem(targetIndex)
                    } else {
                        lazyListState.animateScrollToItem(index = targetIndex, scrollOffset = 0)
                    }
                }
            } catch (_: Exception) { }
        }

        if ((currentLineIndex == 0 && shouldScrollToFirstLine) || !initialScrollDone) {
            shouldScrollToFirstLine = false
            val initialCenterIndex = kotlin.math.max(0, currentLineIndex)
            performSmoothPageScroll(initialCenterIndex)
            if (!isAppMinimized) {
                initialScrollDone = true
            }
        } else if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (isSeeking) {
                val seekCenterIndex = kotlin.math.max(0, currentLineIndex)
                performSmoothPageScroll(seekCenterIndex, isSeek = true)
            } else if ((lastPreviewTime == 0L || currentLineIndex != previousLineIndex) && scrollLyrics && !isManualScrolling) {
                if (currentLineIndex != previousLineIndex) {
                    val centerTargetIndex = kotlin.math.max(0, currentLineIndex)
                    performSmoothPageScroll(centerTargetIndex)
                }
            }
        }
        if (currentLineIndex > 0) {
            shouldScrollToFirstLine = true
        }
        previousLineIndex = currentLineIndex
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize().padding(bottom = 12.dp),
    ) {
        if (lyrics == LYRICS_NOT_FOUND) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Lyrics not found",
                    fontSize = 20.sp,
                    color = headline,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(0.5f),
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Top)
                    .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                    .asPaddingValues(),
                modifier = Modifier
                    .smoothFadingEdge(vertical = 72.dp)
                    .nestedScroll(
                        remember {
                            var lastScrollTime = 0L
                            object : NestedScrollConnection {
                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource,
                                ): Offset {
                                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastScrollTime > 50) {
                                            lastPreviewTime = currentTime
                                            isManualScrolling = true
                                            lastScrollTime = currentTime
                                        }
                                    }
                                    return super.onPostScroll(consumed, available, source)
                                }

                                override suspend fun onPostFling(
                                    consumed: Velocity,
                                    available: Velocity,
                                ): Velocity {
                                    if (!isSelectionModeActive) {
                                        lastPreviewTime = System.currentTimeMillis()
                                        isManualScrolling = true
                                    }
                                    return super.onPostFling(consumed, available)
                                }
                            }
                        },
                    ),
            ) {
                val displayedCurrentLineIndex =
                    if (isSeeking || isSelectionModeActive) deferredCurrentLineIndex else currentLineIndex

                if (lyrics == null) {
                    item {
                        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                        val shimmerAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "shimmerAlpha",
                        )
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                                    .alpha(shimmerAlpha),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(20.dp)
                                        .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = lines,
                        key = { index, item -> "${index}_${item.time}_${item.text.hashCode()}" },
                        contentType = { _, _ -> "lyric_line" },
                    ) { index, item ->
                        val isSelected = selectedIndices.contains(index)

                        val distance = abs(index - displayedCurrentLineIndex)

                        val targetAlpha = when {
                            !isSynced || (isSelectionModeActive && isSelected) -> 1f
                            isManualScrolling -> when {
                                index == displayedCurrentLineIndex -> 1f
                                distance == 1 -> 0.72f
                                distance == 2 -> 0.56f
                                distance == 3 -> 0.40f
                                else -> 0.28f
                            }
                            index == displayedCurrentLineIndex -> 1f
                            distance == 1 -> 0.52f
                            distance == 2 -> 0.30f
                            distance == 3 -> 0.18f
                            else -> 0.10f
                        }

                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = 400, easing = SmoothDecelerateEasing),
                            label = "lyricAlpha",
                        )

                        val targetScale = 1f

                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                            label = "lyricScale",
                        )

                        val targetBlur = when {
                            !isSynced || index == displayedCurrentLineIndex ||
                                (isSelectionModeActive && isSelected) || isManualScrolling -> 0f
                            distance == 1 -> 2f
                            distance == 2 -> 5f
                            else -> 12f
                        }

                        val animatedBlur by animateFloatAsState(
                            targetValue = targetBlur,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            label = "lyricBlur",
                        )

                        val itemModifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = true,
                                onClick = {
                                    if (isSelectionModeActive) {
                                        if (isSelected) {
                                            selectedIndices.remove(index)
                                            if (selectedIndices.isEmpty()) {
                                                isSelectionModeActive = false
                                            }
                                        } else {
                                            if (selectedIndices.size < maxSelectionLimit) {
                                                selectedIndices.add(index)
                                            } else {
                                                showMaxSelectionToast = true
                                            }
                                        }
                                    } else if (isSynced && changeLyrics) {
                                        player.seekTo(item.time)
                                        scope.launch {
                                            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                            if (itemInfo != null) {
                                                val viewportHeight = lazyListState.layoutInfo.viewportEndOffset -
                                                    lazyListState.layoutInfo.viewportStartOffset
                                                val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)
                                                val itemCenter = itemInfo.offset + itemInfo.size / 2
                                                val offset = itemCenter - center

                                                if (abs(offset) > 10) {
                                                    lazyListState.animateScrollBy(
                                                        value = offset.toFloat(),
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessVeryLow,
                                                        ),
                                                    )
                                                }
                                            } else {
                                                lazyListState.animateScrollToItem(index)
                                            }
                                        }
                                        lastPreviewTime = 0L
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionModeActive) {
                                        isSelectionModeActive = true
                                        selectedIndices.add(index)
                                    } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                        selectedIndices.add(index)
                                    } else if (!isSelected) {
                                        showMaxSelectionToast = true
                                    }
                                },
                            ).background(
                                color = if (isSelected && isSelectionModeActive) {
                                    primary.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp),
                            ).padding(horizontal = 24.dp, vertical = 8.dp)
                            .then(
                                if (lyricsLineBlur) {
                                    Modifier.blur(
                                        radiusX = animatedBlur.dp,
                                        radiusY = animatedBlur.dp,
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                    )
                                } else {
                                    Modifier
                                },
                            ).alpha(animatedAlpha)
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }

                        val baseLayoutDirection = LocalLayoutDirection.current
                        val lineIsRtl = remember(item.text) { isRtlText(item.text) }
                        val lineLayoutDirection = remember(lineIsRtl, baseLayoutDirection) {
                            if (lineIsRtl) LayoutDirection.Rtl else baseLayoutDirection
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                            CompositionLocalProvider(
                                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = lyricsFontFamily),
                            ) {
                                Column(
                                    modifier = itemModifier,
                                    horizontalAlignment = when (lyricsTextPosition) {
                                        LyricsPosition.LEFT -> Alignment.Start
                                        LyricsPosition.CENTER -> Alignment.CenterHorizontally
                                        LyricsPosition.RIGHT -> Alignment.End
                                    },
                                ) {
                                    val isActiveLine = index == displayedCurrentLineIndex && isSynced
                                    val lineColor = remember(isActiveLine, lyricsBaseColor) {
                                        if (isActiveLine) lyricsBaseColor else lyricsBaseColor.copy(alpha = 0.52f)
                                    }
                                    val alignment = remember(lyricsTextPosition) {
                                        when (lyricsTextPosition) {
                                            LyricsPosition.LEFT -> TextAlign.Start
                                            LyricsPosition.CENTER -> TextAlign.Center
                                            LyricsPosition.RIGHT -> TextAlign.End
                                        }
                                    }

                                    val hasWordTimings = remember(item.words) { item.words?.isNotEmpty() == true }

                                    val effectiveAnimationStyle = if (animationsDisabled) {
                                        LyricsAnimationStyle.NONE
                                    } else {
                                        lyricsAnimationStyle
                                    }

                                    val reduceMotionDuringScroll = isSelectionModeActive

                                    if (effectiveAnimationStyle == LyricsAnimationStyle.KARAOKE && hasWordTimings) {
                                        val isCjk = remember(item.text) {
                                            isChinese(item.text) || isJapanese(item.text) || isKorean(item.text)
                                        }

                                        val wordsToRender = remember(
                                            item.words, item.text, item.time, lines.size, index, lineIsRtl, isCjk,
                                        ) {
                                            if (hasWordTimings && item.words != null) {
                                                val baseWords = item.words.filter { it.text.isNotBlank() }
                                                baseWords.flatMapIndexed { idx, word ->
                                                    val prevText = baseWords.getOrNull(idx - 1)?.text
                                                    val nextText = baseWords.getOrNull(idx + 1)?.text
                                                    val includeSpace = if (isCjk) {
                                                        val currEdge = if (lineIsRtl) word.text.firstOrNull() else word.text.lastOrNull()
                                                        val neighborEdge = if (lineIsRtl) prevText?.lastOrNull() else nextText?.firstOrNull()
                                                        val neighbor = if (lineIsRtl) prevText else nextText
                                                        currEdge != null && neighborEdge != null && neighbor != null &&
                                                            (currEdge.code < 0x3000 || neighborEdge.code < 0x3000) &&
                                                            shouldAppendWordSpace(
                                                                if (lineIsRtl) neighbor else word.text,
                                                                if (lineIsRtl) word.text else neighbor,
                                                            )
                                                    } else if (lineIsRtl) {
                                                        prevText != null && shouldAppendWordSpace(prevText, word.text)
                                                    } else {
                                                        nextText != null && shouldAppendWordSpace(word.text, nextText)
                                                    }

                                                    val wordStartMs = (word.startTime * 1000).toLong()
                                                    val wordEndMs = (word.endTime * 1000).toLong()
                                                    val wordDuration = wordEndMs - wordStartMs

                                                    if (isCjk && word.text.length > 3) {
                                                        val chars = word.text.toList()
                                                        chars.mapIndexed { charIdx, char ->
                                                            val charStartMs = wordStartMs + (wordDuration * charIdx / chars.size)
                                                            val charEndMs = wordStartMs + (wordDuration * (charIdx + 1) / chars.size)
                                                            val charText = when {
                                                                includeSpace && !lineIsRtl && charIdx == chars.lastIndex -> "$char "
                                                                includeSpace && lineIsRtl && charIdx == 0 -> " $char"
                                                                else -> char.toString()
                                                            }
                                                            Triple(charText, charStartMs to charEndMs, word.isBackground)
                                                        }
                                                    } else {
                                                        val displayText = when {
                                                            !includeSpace -> word.text
                                                            lineIsRtl -> " ${word.text}"
                                                            else -> "${word.text} "
                                                        }
                                                        listOf(Triple(displayText, wordStartMs to wordEndMs, word.isBackground))
                                                    }
                                                }
                                            } else {
                                                val nextLineTime = lines.getOrNull(index + 1)?.time
                                                    ?: (item.time + 5000L).coerceAtLeast(item.time + 1000L)
                                                val lineDuration = (nextLineTime - item.time).coerceAtLeast(100L)

                                                val splitWords = if (isCjk) {
                                                    item.text.map { it.toString() }
                                                } else {
                                                    item.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                                                }

                                                val lengths = splitWords.mapIndexed { idx, wordText ->
                                                    val prevText = splitWords.getOrNull(idx - 1)
                                                    val nextText = splitWords.getOrNull(idx + 1)
                                                    val includeSpace = if (isCjk) {
                                                        val currEdge = if (lineIsRtl) wordText.firstOrNull() else wordText.lastOrNull()
                                                        val neighborEdge = if (lineIsRtl) prevText?.lastOrNull() else nextText?.firstOrNull()
                                                        val neighbor = if (lineIsRtl) prevText else nextText
                                                        currEdge != null && neighborEdge != null && neighbor != null &&
                                                            (currEdge.code < 0x3000 || neighborEdge.code < 0x3000) &&
                                                            shouldAppendWordSpace(
                                                                if (lineIsRtl) neighbor else wordText,
                                                                if (lineIsRtl) wordText else neighbor,
                                                            )
                                                    } else if (lineIsRtl) {
                                                        prevText != null && shouldAppendWordSpace(prevText, wordText)
                                                    } else {
                                                        nextText != null && shouldAppendWordSpace(wordText, nextText)
                                                    }
                                                    wordText.length + if (includeSpace) 1 else 0
                                                }
                                                val totalLength = lengths.sum().coerceAtLeast(1)

                                                var currentOffset = 0L
                                                splitWords.mapIndexed { idx, wordText ->
                                                    val wordDuration = (lineDuration * (lengths[idx].toDouble() / totalLength)).toLong()
                                                    val startTime = item.time + currentOffset
                                                    val endTime = startTime + wordDuration
                                                    currentOffset += wordDuration

                                                    val prevText = splitWords.getOrNull(idx - 1)
                                                    val nextText = splitWords.getOrNull(idx + 1)
                                                    val includeSpace = if (isCjk) {
                                                        val currEdge = if (lineIsRtl) wordText.firstOrNull() else wordText.lastOrNull()
                                                        val neighborEdge = if (lineIsRtl) prevText?.lastOrNull() else nextText?.firstOrNull()
                                                        val neighbor = if (lineIsRtl) prevText else nextText
                                                        currEdge != null && neighborEdge != null && neighbor != null &&
                                                            (currEdge.code < 0x3000 || neighborEdge.code < 0x3000) &&
                                                            shouldAppendWordSpace(
                                                                if (lineIsRtl) neighbor else wordText,
                                                                if (lineIsRtl) wordText else neighbor,
                                                            )
                                                    } else if (lineIsRtl) {
                                                        prevText != null && shouldAppendWordSpace(prevText, wordText)
                                                    } else {
                                                        nextText != null && shouldAppendWordSpace(wordText, nextText)
                                                    }
                                                    val displayText = when {
                                                        !includeSpace -> wordText
                                                        lineIsRtl -> " $wordText"
                                                        else -> "$wordText "
                                                    }
                                                    Triple(displayText, startTime to endTime, false)
                                                }
                                            }
                                        }

                                        val horizontalSpacing = 0.dp
                                        val karaokeCurrentTimeProvider: () -> Long = {
                                            if (isActiveLine && !reduceMotionDuringScroll) currentPlaybackPosition else Long.MIN_VALUE
                                        }

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = when (lyricsTextPosition) {
                                                LyricsPosition.LEFT -> Arrangement.spacedBy(horizontalSpacing, Alignment.Start)
                                                LyricsPosition.CENTER -> Arrangement.spacedBy(horizontalSpacing, Alignment.CenterHorizontally)
                                                LyricsPosition.RIGHT -> Arrangement.spacedBy(horizontalSpacing, Alignment.End)
                                            },
                                            verticalArrangement = Arrangement.spacedBy(verticalLineSpacing),
                                        ) {
                                            wordsToRender.forEach { (text, timings, isBg) ->
                                                val (wordStartMs, wordEndMs) = timings
                                                KaraokeWord(
                                                    text = text,
                                                    startTime = wordStartMs,
                                                    endTime = wordEndMs,
                                                    currentTimeProvider = karaokeCurrentTimeProvider,
                                                    isRtl = lineIsRtl,
                                                    fontSize = lyricsTextSize.sp,
                                                    textColor = lyricsBaseColor,
                                                    inactiveAlpha = if (isActiveLine) 0.35f else 0.7f,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    isBackground = isBg,
                                                    nudgeEnabled = isActiveLine && !reduceMotionDuringScroll,
                                                )
                                            }
                                        }
                                    } else if (hasWordTimings && item.words != null &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.APPLE
                                    ) {
                                        if (!isActiveLine || reduceMotionDuringScroll) {
                                            Text(
                                                text = item.text,
                                                fontSize = lyricsTextSize.sp,
                                                color = lineColor,
                                                textAlign = alignment,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        } else {
                                            val styledText = buildAnnotatedString {
                                                item.words.forEachIndexed { wordIndex, word ->
                                                    val wordStartMs = (word.startTime * 1000).toLong()
                                                    val wordEndMs = (word.endTime * 1000).toLong()
                                                    val wordDuration = wordEndMs - wordStartMs

                                                    val isWordActive = isActiveLine &&
                                                        currentPlaybackPosition >= wordStartMs &&
                                                        currentPlaybackPosition <= wordEndMs
                                                    val hasWordPassed = isActiveLine && currentPlaybackPosition > wordEndMs

                                                    val transitionProgress = when {
                                                        !isActiveLine -> 0f
                                                        hasWordPassed -> 1f
                                                        isWordActive && wordDuration > 0 -> {
                                                            val elapsed = currentPlaybackPosition - wordStartMs
                                                            val linear = (elapsed.toFloat() / wordDuration).coerceIn(0f, 1f)
                                                            linear * linear * (3f - 2f * linear)
                                                        }
                                                        else -> 0f
                                                    }

                                                    val wordAlpha = when {
                                                        !isActiveLine -> 0.7f
                                                        hasWordPassed -> 1f
                                                        isWordActive -> 0.5f + (0.5f * transitionProgress)
                                                        else -> 0.35f
                                                    }

                                                    val effectiveAlpha = if (word.isBackground) wordAlpha * 0.6f else wordAlpha
                                                    val wordColor = lyricsBaseColor.copy(alpha = effectiveAlpha)

                                                    val wordWeight = when {
                                                        !isActiveLine -> FontWeight.Bold
                                                        hasWordPassed -> FontWeight.Bold
                                                        isWordActive -> FontWeight.ExtraBold
                                                        else -> FontWeight.Medium
                                                    }

                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = wordColor,
                                                            fontWeight = wordWeight,
                                                            fontSize = if (word.isBackground) lyricsTextSize.sp * 0.7f else TextUnit.Unspecified,
                                                        ),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                }
                                            }

                                            Text(
                                                text = styledText,
                                                fontSize = lyricsTextSize.sp,
                                                textAlign = alignment,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        }
                                    } else if (hasWordTimings && item.words != null &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.FADE
                                    ) {
                                        if (!isActiveLine || reduceMotionDuringScroll) {
                                            Text(
                                                text = item.text,
                                                fontSize = lyricsTextSize.sp,
                                                color = lineColor,
                                                textAlign = alignment,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        } else {
                                            val styledText = buildAnnotatedString {
                                                item.words.forEachIndexed { wordIndex, word ->
                                                    val wordStartMs = (word.startTime * 1000).toLong()
                                                    val wordEndMs = (word.endTime * 1000).toLong()
                                                    val wordDuration = wordEndMs - wordStartMs

                                                    val isWordActive = isActiveLine &&
                                                        currentPlaybackPosition >= wordStartMs &&
                                                        currentPlaybackPosition <= wordEndMs
                                                    val hasWordPassed = isActiveLine && currentPlaybackPosition > wordEndMs

                                                    val fadeProgress = if (isWordActive && wordDuration > 0) {
                                                        val timeElapsed = currentPlaybackPosition - wordStartMs
                                                        val linear = (timeElapsed.toFloat() / wordDuration.toFloat()).coerceIn(0f, 1f)
                                                        linear * linear * (3f - 2f * linear)
                                                    } else if (hasWordPassed) {
                                                        1f
                                                    } else {
                                                        0f
                                                    }

                                                    val wordAlpha = if (isActiveLine) {
                                                        0.35f + (0.65f * fadeProgress)
                                                    } else {
                                                        0.65f
                                                    }

                                                    val effectiveAlpha = if (word.isBackground) wordAlpha * 0.6f else wordAlpha
                                                    val wordColor = lyricsBaseColor.copy(alpha = effectiveAlpha)

                                                    val wordWeight = when {
                                                        !isActiveLine -> FontWeight.Bold
                                                        hasWordPassed -> FontWeight.Bold
                                                        isWordActive -> FontWeight.ExtraBold
                                                        else -> FontWeight.Medium
                                                    }

                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = wordColor,
                                                            fontWeight = wordWeight,
                                                            fontSize = if (word.isBackground) lyricsTextSize.sp * 0.85f else TextUnit.Unspecified,
                                                        ),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                }
                                            }

                                            Text(
                                                text = styledText,
                                                fontSize = lyricsTextSize.sp,
                                                textAlign = alignment,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        }
                                    } else if (hasWordTimings && item.words != null &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.GLOW
                                    ) {
                                        if (!isActiveLine || reduceMotionDuringScroll) {
                                            Text(
                                                text = item.text,
                                                fontSize = lyricsTextSize.sp,
                                                color = lineColor,
                                                textAlign = alignment,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        } else {
                                            val styledText = buildAnnotatedString {
                                                item.words.forEachIndexed { wordIndex, word ->
                                                    val wordStartMs = (word.startTime * 1000).toLong()
                                                    val wordEndMs = (word.endTime * 1000).toLong()
                                                    val wordDuration = wordEndMs - wordStartMs

                                                    val isWordActive = isActiveLine &&
                                                        currentPlaybackPosition in wordStartMs..wordEndMs
                                                    val hasWordPassed = isActiveLine && currentPlaybackPosition > wordEndMs

                                                    val fillProgress = if (isWordActive && wordDuration > 0) {
                                                        val linear = ((currentPlaybackPosition - wordStartMs).toFloat() / wordDuration)
                                                            .coerceIn(0f, 1f)
                                                        linear * linear * (3f - 2f * linear)
                                                    } else if (hasWordPassed) {
                                                        1f
                                                    } else {
                                                        0f
                                                    }

                                                    val glowIntensity = fillProgress * fillProgress
                                                    val brightness = 0.45f + (0.55f * fillProgress)

                                                    val baseWordColor = when {
                                                        !isActiveLine -> lyricsBaseColor.copy(alpha = 0.5f)
                                                        isWordActive || hasWordPassed -> lyricsBaseColor.copy(alpha = brightness)
                                                        else -> lyricsBaseColor.copy(alpha = 0.35f)
                                                    }

                                                    val wordColor = if (word.isBackground) {
                                                        baseWordColor.copy(alpha = baseWordColor.alpha * 0.6f)
                                                    } else {
                                                        baseWordColor
                                                    }

                                                    val wordWeight = when {
                                                        !isActiveLine -> FontWeight.Bold
                                                        isWordActive -> FontWeight.ExtraBold
                                                        hasWordPassed -> FontWeight.Bold
                                                        else -> FontWeight.Medium
                                                    }

                                                    val floatOffset = if (isWordActive && fillProgress > 0.1f) {
                                                        val floatAmount = sin(fillProgress * Math.PI).toFloat() * 0.5f
                                                        Offset(0f, -floatAmount)
                                                    } else {
                                                        Offset.Zero
                                                    }

                                                    val wordShadow = if (isWordActive && glowIntensity > 0.05f) {
                                                        Shadow(
                                                            color = lyricsGlowColor.copy(alpha = 0.5f + (0.3f * glowIntensity)),
                                                            offset = floatOffset,
                                                            blurRadius = 16f + (12f * glowIntensity),
                                                        )
                                                    } else if (hasWordPassed) {
                                                        Shadow(
                                                            color = lyricsGlowColor.copy(alpha = 0.25f),
                                                            offset = Offset.Zero,
                                                            blurRadius = 8f,
                                                        )
                                                    } else {
                                                        null
                                                    }

                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = wordColor,
                                                            fontWeight = wordWeight,
                                                            shadow = wordShadow,
                                                            fontSize = if (word.isBackground) lyricsTextSize.sp * 0.7f else TextUnit.Unspecified,
                                                        ),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                }
                                            }

                                            Text(
                                                text = styledText,
                                                fontSize = lyricsTextSize.sp,
                                                textAlign = alignment,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        }
                                    } else if (hasWordTimings && item.words != null &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.SLIDE
                                    ) {
                                        val firstWordStartMs = (item.words.firstOrNull()?.startTime?.times(1000))?.toLong() ?: 0L
                                        val lastWordEndMs = (item.words.lastOrNull()?.endTime?.times(1000))?.toLong() ?: 0L
                                        val lineDuration = lastWordEndMs - firstWordStartMs

                                        val isLineActive = isActiveLine &&
                                            currentPlaybackPosition >= firstWordStartMs &&
                                            currentPlaybackPosition <= lastWordEndMs
                                        val hasLinePassed = isActiveLine && currentPlaybackPosition > lastWordEndMs

                                        if (isLineActive && lineDuration > 0) {
                                            val timeElapsed = currentPlaybackPosition - firstWordStartMs
                                            val linearProgress = (timeElapsed.toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
                                            val fillProgress = linearProgress

                                            val breatheValue = (timeElapsed % 3000) / 3000f
                                            val breatheEffect = (sin(breatheValue * Math.PI.toFloat() * 2f) * 0.03f).coerceIn(0f, 0.03f)
                                            val glowIntensity = (0.3f + fillProgress * 0.7f + breatheEffect).coerceIn(0f, 1.1f)

                                            val slideBrush = rtlAwareHorizontalGradient(
                                                isRtl = lineIsRtl,
                                                0.0f to lyricsBaseColor,
                                                (fillProgress * 0.95f).coerceIn(0f, 1f) to lyricsBaseColor,
                                                fillProgress to lyricsBaseColor.copy(alpha = 0.9f),
                                                (fillProgress + 0.02f).coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.5f),
                                                (fillProgress + 0.08f).coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.35f),
                                                1.0f to lyricsBaseColor.copy(alpha = 0.35f),
                                            )

                                            val styledText = buildAnnotatedString {
                                                withStyle(
                                                    style = SpanStyle(brush = slideBrush, fontWeight = FontWeight.ExtraBold),
                                                ) {
                                                    append(item.text)
                                                }
                                            }

                                            Text(
                                                text = styledText,
                                                fontSize = lyricsTextSize.sp,
                                                textAlign = alignment,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        } else if (hasLinePassed) {
                                            Text(
                                                text = item.text,
                                                fontSize = lyricsTextSize.sp,
                                                color = lyricsBaseColor,
                                                textAlign = alignment,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        } else {
                                            Text(
                                                text = item.text,
                                                fontSize = lyricsTextSize.sp,
                                                color = if (!isActiveLine) lineColor else lyricsBaseColor.copy(alpha = 0.35f),
                                                textAlign = alignment,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                            )
                                        }
                                    } else if (hasWordTimings && item.words != null &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.KARAOKE
                                    ) {
                                        val styledText = buildAnnotatedString {
                                            item.words.forEachIndexed { wordIndex, word ->
                                                val wordStartMs = (word.startTime * 1000).toLong()
                                                val wordEndMs = (word.endTime * 1000).toLong()
                                                val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

                                                val isWordActive = isActiveLine &&
                                                    currentPlaybackPosition >= wordStartMs &&
                                                    currentPlaybackPosition < wordEndMs
                                                val hasWordPassed = (isActiveLine && currentPlaybackPosition >= wordEndMs) ||
                                                    (!isActiveLine && index < displayedCurrentLineIndex)
                                                val isUpcoming = isActiveLine && currentPlaybackPosition < wordStartMs

                                                if (isWordActive && wordDuration > 0) {
                                                    val timeElapsed = currentPlaybackPosition - wordStartMs
                                                    val linearProgress = (timeElapsed.toFloat() / wordDuration.toFloat()).coerceIn(0f, 1f)
                                                    val fillProgress = linearProgress * linearProgress * (3f - 2f * linearProgress)

                                                    val breatheCycleDuration = wordDuration.toFloat().coerceIn(400f, 2000f)
                                                    val breathePhase = (timeElapsed % breatheCycleDuration) / breatheCycleDuration
                                                    val breatheEffect = (sin(breathePhase * Math.PI.toFloat()) * 0.05f).coerceIn(0f, 0.05f)
                                                    val glowIntensity = (fillProgress + breatheEffect).coerceIn(0f, 1.0f)

                                                    val wordBrush = rtlAwareHorizontalGradient(
                                                        isRtl = lineIsRtl,
                                                        0.0f to lyricsBaseColor,
                                                        (fillProgress * 0.85f).coerceIn(0f, 0.99f) to lyricsBaseColor,
                                                        fillProgress.coerceIn(0.01f, 0.99f) to lyricsBaseColor.copy(alpha = 0.85f),
                                                        (fillProgress + 0.02f).coerceIn(0.01f, 1f) to lyricsBaseColor.copy(alpha = 0.45f),
                                                        (fillProgress + 0.08f).coerceIn(0.01f, 1f) to lyricsBaseColor.copy(alpha = 0.3f),
                                                        1.0f to lyricsBaseColor.copy(alpha = 0.3f),
                                                    )

                                                    withStyle(
                                                        style = SpanStyle(brush = wordBrush, fontWeight = FontWeight.ExtraBold),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                } else if (hasWordPassed) {
                                                    withStyle(
                                                        style = SpanStyle(color = lyricsBaseColor, fontWeight = FontWeight.Bold),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                } else if (isUpcoming && isActiveLine) {
                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = lyricsBaseColor.copy(alpha = 0.3f),
                                                            fontWeight = FontWeight.Medium,
                                                        ),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                } else {
                                                    val wordColor = if (!isActiveLine) lineColor else lyricsBaseColor.copy(alpha = 0.3f)
                                                    withStyle(
                                                        style = SpanStyle(color = wordColor, fontWeight = FontWeight.Medium),
                                                    ) {
                                                        append(word.text)
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            textAlign = alignment,
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    } else if (hasWordTimings && item.words != null &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.APPLE
                                    ) {
                                        val styledText = buildAnnotatedString {
                                            item.words.forEachIndexed { wordIndex, word ->
                                                val wordStartMs = (word.startTime * 1000).toLong()
                                                val wordEndMs = (word.endTime * 1000).toLong()
                                                val wordDuration = wordEndMs - wordStartMs

                                                val isWordActive = isActiveLine &&
                                                    currentPlaybackPosition >= wordStartMs &&
                                                    currentPlaybackPosition < wordEndMs
                                                val hasWordPassed = (isActiveLine && currentPlaybackPosition >= wordEndMs) ||
                                                    (!isActiveLine && index < displayedCurrentLineIndex)

                                                val rawProgress = if (isWordActive && wordDuration > 0) {
                                                    val elapsed = currentPlaybackPosition - wordStartMs
                                                    (elapsed.toFloat() / wordDuration).coerceIn(0f, 1f)
                                                } else if (hasWordPassed) {
                                                    1f
                                                } else {
                                                    0f
                                                }

                                                val smoothProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)

                                                val wordAlpha = when {
                                                    !isActiveLine -> 0.55f
                                                    hasWordPassed -> 1f
                                                    isWordActive -> 0.55f + (0.45f * smoothProgress)
                                                    else -> 0.35f
                                                }

                                                val wordColor = lyricsBaseColor.copy(alpha = wordAlpha)

                                                val wordWeight = when {
                                                    !isActiveLine -> FontWeight.SemiBold
                                                    hasWordPassed -> FontWeight.Bold
                                                    isWordActive -> FontWeight.ExtraBold
                                                    else -> FontWeight.Normal
                                                }

                                                withStyle(
                                                    style = SpanStyle(color = wordColor, fontWeight = wordWeight),
                                                ) {
                                                    append(word.text)
                                                }
                                            }
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            textAlign = alignment,
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    } else if (isActiveLine &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.GLOW &&
                                        !reduceMotionDuringScroll
                                    ) {
                                        val styledText = buildAnnotatedString {
                                            withStyle(
                                                style = SpanStyle(
                                                    shadow = Shadow(
                                                        color = lyricsGlowColor.copy(alpha = 0.8f),
                                                        offset = Offset(0f, 0f),
                                                        blurRadius = 30f,
                                                    ),
                                                ),
                                            ) {
                                                append(item.text)
                                            }
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            color = lyricsBaseColor,
                                            textAlign = alignment,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    } else if (isActiveLine &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.SLIDE &&
                                        !reduceMotionDuringScroll
                                    ) {
                                        val popInScale = remember { Animatable(0.95f) }
                                        val fillProgress = remember { Animatable(0f) }

                                        LaunchedEffect(index) {
                                            popInScale.snapTo(0.95f)
                                            popInScale.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                            )
                                            fillProgress.snapTo(0f)
                                            fillProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                                            )
                                        }

                                        val fill = fillProgress.value

                                        val slideBrush = rtlAwareHorizontalGradient(
                                            isRtl = lineIsRtl,
                                            0.0f to lyricsBaseColor.copy(alpha = 0.3f),
                                            (fill * 0.7f).coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.9f),
                                            fill to lyricsBaseColor,
                                            (fill + 0.1f).coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.7f),
                                            1.0f to lyricsBaseColor.copy(alpha = if (fill >= 1f) 1f else 0.3f),
                                        )

                                        val styledText = buildAnnotatedString {
                                            withStyle(style = SpanStyle(brush = slideBrush)) {
                                                append(item.text)
                                            }
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            textAlign = alignment,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    } else if (isActiveLine &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.KARAOKE &&
                                        !reduceMotionDuringScroll
                                    ) {
                                        val nextLineTime = remember(index, lines.size) {
                                            lines.getOrNull(index + 1)?.time ?: (item.time + 5000L).coerceAtLeast(item.time + 1000L)
                                        }
                                        val lineDuration = (nextLineTime - item.time).coerceAtLeast(100L)
                                        val timeElapsed = (currentPlaybackPosition - item.time).coerceAtLeast(0L)
                                        val fillProgress = (timeElapsed.toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)

                                        val slideBrush = rtlAwareHorizontalGradient(
                                            isRtl = lineIsRtl,
                                            0.0f to lyricsBaseColor,
                                            (fillProgress * 0.95f).coerceIn(0f, 1f) to lyricsBaseColor,
                                            fillProgress.coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.9f),
                                            (fillProgress + 0.02f).coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.5f),
                                            (fillProgress + 0.08f).coerceIn(0f, 1f) to lyricsBaseColor.copy(alpha = 0.35f),
                                            1.0f to lyricsBaseColor.copy(alpha = 0.35f),
                                        )

                                        val styledText = buildAnnotatedString {
                                            withStyle(
                                                style = SpanStyle(brush = slideBrush, fontWeight = FontWeight.ExtraBold),
                                            ) {
                                                append(item.text)
                                            }
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            textAlign = alignment,
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    } else if (isActiveLine &&
                                        effectiveAnimationStyle == LyricsAnimationStyle.APPLE &&
                                        !reduceMotionDuringScroll
                                    ) {
                                        val popInScale = remember { Animatable(0.96f) }

                                        LaunchedEffect(index) {
                                            popInScale.snapTo(0.96f)
                                            popInScale.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                            )
                                        }

                                        val styledText = if (item.words != null) {
                                            buildAnnotatedString {
                                                item.words.forEachIndexed { idx, word ->
                                                    if (word.isBackground) {
                                                        withStyle(SpanStyle(fontSize = lyricsTextSize.sp * 0.7f)) {
                                                            append(word.text)
                                                        }
                                                    } else {
                                                        append(word.text)
                                                    }
                                                }
                                            }
                                        } else {
                                            AnnotatedString(item.text)
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            color = lyricsBaseColor,
                                            textAlign = alignment,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    } else {
                                        val popInScale = remember { Animatable(1f) }

                                        LaunchedEffect(isActiveLine, reduceMotionDuringScroll) {
                                            if (isActiveLine && !reduceMotionDuringScroll) {
                                                popInScale.snapTo(0.96f)
                                                popInScale.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                                )
                                            }
                                        }

                                        val styledText = if (item.words != null) {
                                            buildAnnotatedString {
                                                item.words.forEachIndexed { idx, word ->
                                                    if (word.isBackground) {
                                                        withStyle(SpanStyle(fontSize = lyricsTextSize.sp * 0.7f)) {
                                                            append(word.text)
                                                        }
                                                    } else {
                                                        append(word.text)
                                                    }
                                                }
                                            }
                                        } else {
                                            AnnotatedString(item.text)
                                        }

                                        Text(
                                            text = styledText,
                                            fontSize = lyricsTextSize.sp,
                                            color = lineColor,
                                            textAlign = alignment,
                                            fontWeight = if (isActiveLine) {
                                                FontWeight.ExtraBold
                                            } else if (index > displayedCurrentLineIndex) {
                                                FontWeight.Light
                                            } else {
                                                FontWeight.Bold
                                            },
                                            lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isManualScrolling && scrollLyrics && !isSelectionModeActive,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it * 2 },
                ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    targetOffsetY = { it * 2 },
                ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .background(color = Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp))
                        .clickable {
                            isManualScrolling = false
                            lastPreviewTime = 0L
                            if (currentLineIndex >= 0) {
                                scope.launch { lazyListState.animateScrollToItem(index = currentLineIndex, scrollOffset = 0) }
                            }
                        }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Resume autoscroll",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (isSelectionModeActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(color = Color.Black.copy(alpha = 0.3f), shape = CircleShape)
                                .clickable {
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (selectedIndices.isNotEmpty()) {
                                        Color.White.copy(alpha = 0.9f)
                                    } else {
                                        Color.White.copy(alpha = 0.5f)
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                ).clickable(enabled = selectedIndices.isNotEmpty()) {
                                    if (selectedIndices.isNotEmpty()) {
                                        val sortedIndices = selectedIndices.sorted()
                                        val selectedLyricsText = sortedIndices
                                            .mapNotNull { lines.getOrNull(it)?.text }
                                            .joinToString("\n")
                                        if (selectedLyricsText.isNotBlank()) {
                                            shareLyricsAsText(context, selectedLyricsText)
                                        }
                                        isSelectionModeActive = false
                                        selectedIndices.clear()
                                    }
                                }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Share",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KaraokeWord(
    text: String,
    startTime: Long,
    endTime: Long,
    currentTimeProvider: () -> Long,
    isRtl: Boolean,
    fontSize: TextUnit,
    textColor: Color,
    inactiveAlpha: Float,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    isBackground: Boolean = false,
    nudgeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val duration = endTime - startTime
    val glowPadding = 10.dp

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val glowPaddingPx = glowPadding.roundToPx()
                val looseConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
                val placeable = measurable.measure(looseConstraints)
                val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)
                layout(coreWidth, coreHeight) { placeable.place(-glowPaddingPx, -glowPaddingPx) }
            }.graphicsLayer {
                clip = false
                val currentTime = currentTimeProvider()
                val maxShift = 5f
                val attackDuration = 120L
                val decayDuration = 250L
                val totalImpulseTime = attackDuration + decayDuration

                val shift = if (nudgeEnabled && currentTime >= startTime && currentTime < startTime + totalImpulseTime) {
                    val timeSinceStart = currentTime - startTime
                    if (timeSinceStart < attackDuration) {
                        val progress = timeSinceStart.toFloat() / attackDuration.toFloat()
                        androidx.compose.ui.util.lerp(0f, maxShift, progress)
                    } else {
                        val decayProgress = (timeSinceStart - attackDuration).toFloat() / decayDuration.toFloat()
                        androidx.compose.ui.util.lerp(maxShift, 0f, decayProgress)
                    }
                } else {
                    0f
                }

                translationX = if (isRtl) -shift else shift
            },
    ) {
        val effectiveFontSize = if (isBackground) fontSize * 0.7f else fontSize
        val effectiveAlpha = if (isBackground) 0.6f else 1f

        Text(
            text = text,
            fontSize = effectiveFontSize,
            color = textColor.copy(alpha = inactiveAlpha * effectiveAlpha),
            fontWeight = fontWeight,
            modifier = Modifier.padding(glowPadding),
        )

        Text(
            text = text,
            fontSize = effectiveFontSize,
            color = textColor.copy(alpha = effectiveAlpha),
            fontWeight = fontWeight,
            modifier = Modifier
                .padding(glowPadding)
                .drawWithContent {
                    val currentTime = currentTimeProvider()
                    val isDone = currentTime >= endTime
                    if (isDone) {
                        drawContent()
                    }
                },
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    val currentTime = currentTimeProvider()
                    val fadeDuration = 200L
                    if (currentTime >= endTime) {
                        val timeSinceEnd = currentTime - endTime
                        val fadeProgress = (timeSinceEnd.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
                        alpha = 1f - fadeProgress
                    } else {
                        alpha = 1f
                    }
                }.drawWithContent {
                    val currentTime = currentTimeProvider()
                    val progress = if (duration > 0) {
                        val elapsed = currentTime - startTime
                        (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else if (currentTime >= endTime) {
                        1f
                    } else {
                        0f
                    }

                    val fadeDuration = 200L
                    val isFading = currentTime >= endTime && currentTime < (endTime + fadeDuration)

                    if ((progress > 0f && progress < 1f) || isFading) {
                        drawContent()
                        val fadeWidth = 20f
                        val totalWidth = size.width
                        val paddingPx = glowPadding.toPx()
                        val textWidth = totalWidth - (paddingPx * 2)
                        val fillWidth = textWidth * progress
                        val endFraction = (paddingPx + fillWidth + fadeWidth) / totalWidth
                        val solidFraction = (paddingPx + fillWidth) / totalWidth

                        val softFillBrush = if (!isRtl) {
                            Brush.horizontalGradient(
                                0f to Color.Black,
                                solidFraction.coerceAtLeast(0f) to Color.Black,
                                endFraction.coerceAtMost(1f) to Color.Transparent,
                            )
                        } else {
                            val solidStartX = (paddingPx + (textWidth - fillWidth)).coerceIn(0f, totalWidth)
                            val fadeStartX = (solidStartX - fadeWidth).coerceIn(0f, totalWidth)
                            val fadeStartFraction = (fadeStartX / totalWidth).coerceIn(0f, 1f)
                            val solidStartFraction = (solidStartX / totalWidth).coerceIn(0f, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                fadeStartFraction to Color.Transparent,
                                solidStartFraction to Color.Black,
                                1f to Color.Black,
                            )
                        }

                        drawRect(brush = softFillBrush, blendMode = BlendMode.DstIn)
                    }
                }.padding(glowPadding),
        ) {
            Text(
                text = text,
                fontSize = effectiveFontSize,
                color = textColor.copy(alpha = effectiveAlpha),
                fontWeight = fontWeight,
            )
        }
    }
}

private fun shareLyricsAsText(context: Context, lyricsText: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, lyricsText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics"))
    } catch (e: Exception) {
        Log.e("ArchiveLyrics", "Error sharing lyrics", e)
    }
}

private fun Modifier.smoothFadingEdge(vertical: Dp): Modifier = this.drawWithContent {
    drawContent()
    val edgePx = vertical.toPx()
    if (edgePx > 0f && size.height > edgePx * 2) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                (edgePx / size.height) to Color.Black,
                (1f - edgePx / size.height) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            -> return false
        }
    }
    return false
}

private fun rtlAwareHorizontalGradient(
    isRtl: Boolean,
    vararg colorStops: Pair<Float, Color>,
): Brush {
    val stops = if (isRtl) {
        colorStops.map { (f, c) -> (1f - f).coerceIn(0f, 1f) to c }.sortedBy { it.first }
    } else {
        colorStops.toList()
    }
    return Brush.horizontalGradient(*stops.toTypedArray())
}

private fun findCurrentLineIndex(
    lines: List<LyricsEntry>,
    position: Long,
    leadMs: Long = 300L,
): Int {
    if (lines.isEmpty()) return -1
    val target = position + leadMs
    var low = 0
    var high = lines.lastIndex
    while (low <= high) {
        val mid = (low + high).ushr(1)
        val midTime = lines[mid].time
        if (midTime < target) {
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return high.coerceIn(0, lines.lastIndex)
}

private fun isChinese(c: Char): Boolean = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF ||
    c.code in 0xF900..0xFAFF || c.code in 0x2F800..0x2FA1F

private fun isJapanese(c: Char): Boolean = c.code in 0x3040..0x309F || c.code in 0x30A0..0x30FF ||
    c.code in 0xFF66..0xFF9F

private fun isKorean(c: Char): Boolean = c.code in 0xAC00..0xD7AF || c.code in 0x1100..0x11FF ||
    c.code in 0x3130..0x318F || c.code in 0xA960..0xA97C || c.code in 0xD7B0..0xD7FF

private fun isChinese(text: String): Boolean = text.any(::isChinese)
private fun isJapanese(text: String): Boolean = text.any(::isJapanese)
private fun isKorean(text: String): Boolean = text.any(::isKorean)

private fun shouldAppendWordSpace(current: String, next: String): Boolean {
    if (current.isEmpty() || next.isEmpty()) return false
    val last = current.last()
    val first = next.first()
    if (last.isWhitespace() || first.isWhitespace()) return false
    if (!first.isLetterOrDigit()) return false
    return last !in NoSpaceAfterChars
}
