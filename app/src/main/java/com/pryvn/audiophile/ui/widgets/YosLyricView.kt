package com.pryvn.audiophile.ui.widgets

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pryvn.audiophile.code.utils.lrc.YosMediaEvent
import com.pryvn.audiophile.code.utils.lrc.YosUIConfig
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.MainViewModelObject
import com.pryvn.audiophile.code.player.MediaControlPlayerAdapter

import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


val yosEasing = CubicBezierEasing(0.75f, 0.0f, 0.25f, 1.0f)

// Strong ease-out for the automatic lyric scroll: the list accelerates briefly
// then decelerates sharply as the anchored line arrives (Apple Music feel).
// Only the container's scroll movement uses this curve — the anchored/active
// lines keep their exact anchor positions.
val lyricScrollEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private const val LRC_LEAD_MS = 300L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

// Gap-dots AnimatedVisibility exit tween is 340ms; wait past it (plus the
// 30ms settle delay and a small margin) before measuring the scroll target
// so the collapsed dots row doesn't skew the target line's offset.
private const val GAP_DOTS_COLLAPSE_WAIT_MS = 420L

/**
 * YosLyricView main widget
 * @param lrcEntriesLambda Processed LRC text (each entry is List<Pair<Float, String>>)
 * @param liveTimeLambda Current song progress (milliseconds)
 * @param mediaEvent YosLyricView media event
 * @param translationLambda Whether to enable translation
 * @param blurLambda Whether to enable blur effect
 * @param uiConfig YosLyricView UI config
 */
@Composable
fun YosLyricView(
    lrcEntriesLambda: () -> List<List<Pair<Float, String>>>,
    liveTimeLambda: () -> Int,
    mediaEvent: YosMediaEvent,
    translationLambda: () -> Boolean = { true },
    blurLambda: () -> Boolean = { false },
    uiConfig: YosUIConfig = YosUIConfig(),
    weightLambda: () -> Boolean,
    wordSyncedLambda: () -> Boolean = { false },
    pollingEnabled: () -> Boolean = { true },
    modifier: Modifier,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val mainTextBasicColor = Color(uiConfig.mainTextBasicColor)
    val subTextBasicColor = Color(uiConfig.subTextBasicColor)
    val otherSideForLines = MediaViewModelObject.otherSideForLines
    val lrcEntries = lrcEntriesLambda()

    // Read interaction state from single source of truth
    val interactive = LocalLyricsInteractive.current

    // ---- Word-synced lyrics: delegate to ArchiveTune renderers ----
    // Priority: syllable-level -> word-level -> line-sync -> plain blocks.
    // Single unified renderer: LyricsV2 (liquid fill / glow / bounce) handles
    // both word-synced and syllable-synced lyrics.
    val hasWordSynced = wordSyncedLambda()
    val wordSyncedLinesExist = MediaViewModelObject.wordSyncedLines.value.isNotEmpty()
    if (hasWordSynced && wordSyncedLinesExist) {
        val dominantBackground = MediaViewModelObject.paletteDarkVibrantColor.value
        val lyricTextColor =
            if (dominantBackground.luminance() < 0.4f) Color.White
            else Color.Black

        // During a lyric refetch, show the loading state so the action gives
        // visible feedback even for word-synced songs.
        if (MediaViewModelObject.isLoadingLyrics.value) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                    .fillMaxWidth()
                    .then(
                        if (interactive) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onBackClick() }
                        } else {
                            Modifier
                        },
                    )
            ) {
                LyricsSpinnerContent(color = lyricTextColor)
            }
        } else {
            LyricsV2(
                player = MediaControlPlayerAdapter,
                sliderPositionProvider = { null },
                lyricsSyncOffset = 0,
                modifier = modifier,
                textColorOverride = lyricTextColor,
                lyricsLineBlurOverride = SettingsLibrary.LyricBlurEffect,
                pollingEnabled = pollingEnabled,
                onBackgroundClick = onBackClick,
            )
        }
        return
    }

    // ---- Empty / Loading state ----
    // isLoading is hoisted so the loading state also shows during "Refetch
    // lyrics" (entries are still populated then — we keep them until a better
    // result arrives, but the user should still see the fetching feedback).
    val isLoading = MediaViewModelObject.isLoadingLyrics.value
    if (isLoading || lrcEntries.isEmpty() || otherSideForLines.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                .fillMaxWidth()
                .then(
                    if (interactive) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onBackClick() }
                    } else {
                        Modifier
                    },
                )
        ) {
            if (isLoading) {
                LyricsSpinnerContent(color = mainTextBasicColor)
            } else {
                Text(
                    text = "Lyrics couldn't be loaded",
                    fontSize = 16.sp,
                    color = mainTextBasicColor.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    // ---- Main content ----
    val scrollState = rememberLazyListState()
    val currentLyricIndex = remember { MainViewModelObject.syncLyricIndex }

    // Direction of the last driven lyric advance (+1 forward / -1 backward), so
    // the per-line settle animation lags the surrounding lines in the correct
    // direction without ever moving the anchored/active lines.
    val lyricScrollDirection = remember { mutableIntStateOf(1) }
    val enableLyricScroll = remember { mutableStateOf(true) }

    // Shared snapshot position so snapshotFlow collectors re-emit (liveTimeLambda is not snapshot state).
    val liveTimeState = remember { mutableIntStateOf(liveTimeLambda()) }

    val height = rememberSaveable { mutableIntStateOf(0) }
    val space = 0.dp

    val measurer = rememberTextMeasurer(cacheSize = 32)

    val visibleItems = derivedStateOf { scrollState.layoutInfo.visibleItemsInfo }
    val nowFirst = derivedStateOf { scrollState.firstVisibleItemIndex }

    val supportBlur = rememberSaveable {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    // ---- User scrolling detection ----
    val isUserScrolling = remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                isUserScrolling.value = true
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isUserScrolling.value = false
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(isUserScrolling.value) {
        if (isUserScrolling.value) {
            enableLyricScroll.value = false
        } else {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            enableLyricScroll.value = true
        }
    }

    // ---- LazyColumn ----
    LazyColumn(
        state = scrollState,
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier
            .fillMaxSize()
            .then(
                if (interactive) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onBackClick() }
                } else {
                    Modifier
                },
            )
            .then(
                if (interactive) Modifier.nestedScroll(nestedScrollConnection) else Modifier,
            )
            .onSizeChanged {
                if (height.intValue == 0 && it.height != 0) {
                    height.intValue = it.height
                }
            }
    ) {
        blankSpacer(uiConfig.blankHeight.dp)

        itemsIndexed(
            items = lrcEntries,
            key = { _, lines -> lines }
        ) { index, lines ->
            val isCurrent = derivedStateOf { index == currentLyricIndex.intValue }
            val isTop = derivedStateOf { index == currentLyricIndex.intValue - 1 }

            val showStateAnimation = derivedStateOf {
                (currentLyricIndex.intValue in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 }
                        && currentLyricIndex.intValue >= 0 && enableLyricScroll.value)
            }

            val isLyricEmpty = rememberSaveable(lines) {
                mutableStateOf(lines.all { it.second.isBlank() })
            }

            key(lines) {
                val translation = remember(index) {
                    lines.last().second.ifBlank { null }
                }

                val blur = derivedStateOf {
                    if (!showStateAnimation.value || index == currentLyricIndex.intValue || !blurLambda() || !supportBlur) {
                        0f
                    } else {
                        (abs(index - currentLyricIndex.intValue) * 2.5f).coerceAtMost(8f)
                    }
                }

                val otherSide = remember(index) {
                    otherSideForLines.getOrElse(index) { false }
                }

                val thisWordSyncedWords = derivedStateOf {
                    if (MediaViewModelObject.hasWordSyncedLyrics.value) {
                        val syncedLines = MediaViewModelObject.wordSyncedLines.value
                        if (index < syncedLines.size) {
                            syncedLines[index].words.map { word ->
                                Triple(word.startTimeMs.toFloat(), word.endTimeMs.toFloat(), word.isBackground)
                            }
                        } else emptyList()
                    } else emptyList()
                }

                // ---- Per-line inertial settle ----
                // The auto-scroll moves the whole list with a strong ease-out
                // (line anchored at the golden point, active line untouched).
                // These surrounding lines briefly resist the movement, then
                // settle back with a restrained spring, so the list reads as
                // having inertia and weight. The active line (index == current)
                // and the line pinned at the anchor (index == current + 1) are
                // never displaced. Retargets smoothly on successive changes.
                val settleOffset = remember { Animatable(0f) }
                val settleDensity = LocalDensity.current.density
                LaunchedEffect(currentLyricIndex.intValue) {
                    if (!enableLyricScroll.value) return@LaunchedEffect
                    val current = currentLyricIndex.intValue
                    if (index == current || index == current + 1) return@LaunchedEffect
                    if (abs(index - current) > 3) return@LaunchedEffect
                    val amp = lyricScrollDirection.intValue * 7f * settleDensity
                    settleOffset.animateTo(amp, tween(durationMillis = 110, easing = yosEasing))
                    settleOffset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 520f))
                }

                Box(
                    Modifier.graphicsLayer {
                        translationY = settleOffset.value
                    }
                ) {
                    LyricItem(
                        isCurrentLambda = { isCurrent.value },
                    isTopLambda = { isTop.value },
                    mainLyric = lines.dropLast(1),
                    translation = translation,
                    showTranslation = translationLambda(),
                    subTextSize = uiConfig.subTextSize,
                    blur = { blur.value },
                    mainTextBasicColor = mainTextBasicColor,
                    subTextBasicColor = subTextBasicColor,
                    otherSide = otherSide,
                    liveTimeLambda = { liveTimeState.intValue },
                    measurer = measurer,
                    isLyricEmpty = { isLyricEmpty.value },
                    nextTime = {
                        if (index + 1 > lrcEntries.size - 1) 0f else lrcEntries[index + 1].first().first
                    },
                    prevLineEndMs = {
                        if (index == 0) 0f
                        else {
                            val prev = lrcEntries[index - 1]
                            prev.drop(1).dropLast(1).lastOrNull()?.first ?: prev.first().first
                        }
                    },
                    wordSyncedWords = thisWordSyncedWords.value,
                    onClick = {
                        Vibrator.doubleClick(context)
                        lyricScrollDirection.intValue = if (index > currentLyricIndex.intValue) 1 else -1
                        currentLyricIndex.intValue = index
                        mediaEvent.onSeek(lines.first().first.toInt())
                    }
                )
                    }
            }

            // ---- Spacer animation for each item ----
            key(index) {
                val show = derivedStateOf { !isLyricEmpty.value || isCurrent.value }

                val thisTargetHeight = remember { mutableStateOf(space) }

                LaunchedEffect(currentLyricIndex.intValue) {
                    if (visibleItems.value.isEmpty()) return@LaunchedEffect
                    if (index >= currentLyricIndex.intValue - 1 && showStateAnimation.value && show.value) {
                        val segment = 1f - ((index - nowFirst.value).toFloat() / visibleItems.value.size.toFloat())
                        delay((350 * (1f - segment)).toLong())
                        thisTargetHeight.value = (3.dp * segment) + space
                        delay(100)
                        thisTargetHeight.value = space
                    } else if (show.value) {
                        thisTargetHeight.value = space
                    } else {
                        thisTargetHeight.value = 0.dp
                    }
                }

                val offset = animateDpAsState(
                    targetValue = thisTargetHeight.value,
                    animationSpec = tween(durationMillis = 250, easing = yosEasing)
                )
                Spacer(modifier = Modifier.height(offset.value))
            }
        }

        blankSpacer(uiConfig.blankHeight.dp)
        item("extra_blank") { Spacer(Modifier.height(500.dp)) }
    }

    // ---- Auto‑scroll to current line ----
    LaunchedEffect(currentLyricIndex.intValue, translationLambda()) {
        try {
            if (!enableLyricScroll.value) return@LaunchedEffect
            val targetIdx = currentLyricIndex.intValue + 1

            val skip = try {
                targetIdx - 1 >= 0 &&
                        lrcEntries[targetIdx - 1][1].second.isBlank()
            } catch (_: Exception) { false }
            if (skip) return@LaunchedEffect

            // When the line we are scrolling away from is a blank gap-dots line,
            // its AnimatedVisibility exit (~340ms fade/scale collapse) is still
            // running while this effect fires. Measuring targetItem.offset
            // mid-collapse yields a stale value and the target line lands
            // off-anchor (the reported "line jumps after the gap dots" bug).
            // Wait for the collapse to finish before measuring, then re-verify
            // the index hasn't advanced again.
            val leavingGapDots = try {
                targetIdx - 2 >= 0 &&
                        lrcEntries[targetIdx - 2][1].second.isBlank()
            } catch (_: Exception) { false }

            delay(if (leavingGapDots) GAP_DOTS_COLLAPSE_WAIT_MS else 30)

            if (currentLyricIndex.intValue + 1 != targetIdx) return@LaunchedEffect

            // The gap-dots slot also collapses via animateContentSize (a spring)
            // that outlives the 340ms exit tween, so a fixed delay can still
            // measure the target mid-collapse — the reported "next line
            // overshoots the anchor after the gap dots" bug. Poll the target's
            // offset until it settles across frames; only then is the layout
            // final and the scroll exact, so the line can never overshoot.
            if (leavingGapDots) {
                var lastOffset = Float.NaN
                repeat(30) {
                    if (currentLyricIndex.intValue + 1 != targetIdx) return@LaunchedEffect
                    val currentOffset = (visibleItems.value.find { it.index == targetIdx }?.offset ?: return@LaunchedEffect).toFloat()
                    if (lastOffset.isNaN()) {
                        lastOffset = currentOffset
                    } else if (abs(currentOffset - lastOffset) < 0.5f) {
                        return@LaunchedEffect
                    } else {
                        lastOffset = currentOffset
                    }
                    withFrameNanos { }
                }
            }

            if (currentLyricIndex.intValue + 1 != targetIdx) return@LaunchedEffect

            val targetOffset = height.intValue * 0.0618f
            val targetItem = visibleItems.value.find { it.index == targetIdx }
            if (targetItem != null) {
                scrollState.animateScrollBy(
                    targetItem.offset - targetOffset,
                    animationSpec = tween(
                        durationMillis = 550,
                        easing = lyricScrollEaseOut
                    )
                )
            } else {
                scrollState.animateScrollToItem(
                    index = targetIdx.coerceAtLeast(0),
                    scrollOffset = -targetOffset.toInt(),
                )
            }
        } catch (_: Exception) { }
    }

    // ---- Live time updater for current index ----
    val lyricLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        var stableIdx = currentLyricIndex.intValue
        var stableCount = 0
        while (isActive) {
            if (!lyricLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !pollingEnabled()) {
                delay(500)
                continue
            }
            val liveTime = liveTimeLambda()
            liveTimeState.intValue = liveTime
            val targetPos = liveTime + LRC_LEAD_MS + LYRIC_VISUAL_TUNING_OFFSET_MS
            val nextIdx = if (lrcEntries.isEmpty()) -1 else {
                var lo = 0
                var hi = lrcEntries.lastIndex
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (lrcEntries[mid].first().first <= targetPos) {
                        lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                if (lo > lrcEntries.lastIndex) -1 else lo
            }
            val newIdx = when {
                nextIdx == -1 -> lrcEntries.size - 1
                nextIdx == 0 -> 0
                else -> nextIdx - 1
            }
            if (newIdx == stableIdx) {
                stableCount++
                if (stableCount >= 3 && newIdx != currentLyricIndex.intValue) {
                    lyricScrollDirection.intValue = if (newIdx > currentLyricIndex.intValue) 1 else -1
                    currentLyricIndex.intValue = newIdx
                }
            } else {
                stableIdx = newIdx
                stableCount = 0
            }
            delay(100)
        }
    }
}

private fun LazyListScope.blankSpacer(height: Dp) {
    item { Box(Modifier.height(height)) }
}

// ---- Helper function to convert Float to Dp ----
@Composable
fun Float.toDp(): Dp {
    val density = LocalDensity.current
    return (this / density.density).dp
}

// ---- Line drawing composable with custom draw ----
@Composable
private fun LazyItemScope.Line(
    lines: List<Pair<Float, String>>,
    style: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
    viewAlign: Alignment.Horizontal,
    draw: CacheDrawScope.(Constraints, TextLayoutResult) -> DrawResult
) {
    val styledString = remember(style, lines) {
        buildString {
            lines.forEach { if (it.second.isNotEmpty()) append(it.second) }
        }
    }

    Column(
        horizontalAlignment = viewAlign,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    ) {
        SubcomposeLayout(modifier = modifier) { constraints ->
            val measureResult = measurer.measure(
                text = styledString,
                style = style,
                constraints = Constraints(minWidth = 0, maxWidth = constraints.maxWidth),
                layoutDirection = LayoutDirection.Ltr
            )

            val height = style.lineHeight * measureResult.lineCount
            val width = runCatching {
                (0 until measureResult.lineCount).maxOf {
                    measureResult.getBoundingBox(measureResult.getLineEnd(it, visibleEnd = true) - 1).right
                }
            }.getOrDefault(constraints.maxWidth.toFloat())

            val content = subcompose(lines) {
                Spacer(Modifier.fillMaxSize().drawWithCache { draw(constraints, measureResult) })
            }.first()

            val placeable = content.measure(Constraints.fixed(width.roundToInt(), height.roundToPx()))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }
}

// ---- Easing ----
val easing: Easing = EaseInOutQuad

// ---- Lyric item composable ----
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LazyItemScope.LyricItem(
    isCurrentLambda: () -> Boolean,
    isTopLambda: () -> Boolean,
    mainLyric: List<Pair<Float, String>>,
    translation: String?,
    showTranslation: Boolean,
    subTextSize: Int,
    blur: () -> Float,
    mainTextBasicColor: Color,
    subTextBasicColor: Color,
    measurer: TextMeasurer,
    isLyricEmpty: () -> Boolean,
    nextTime: () -> Float,
    prevLineEndMs: () -> Float = { 0f },
    otherSide: Boolean,
    liveTimeLambda: () -> Int,
    wordSyncedWords: List<Triple<Float, Float, Boolean>> = emptyList(),
    onClick: () -> Unit
) {
    val viewAlign = if (otherSide) Alignment.End else Alignment.Start
    val interactive = LocalLyricsInteractive.current

    val focusedColor = Color.White
    val unfocusedColor = Color(0x2EFFFFFF)
    val unfocusedSolidBrush = SolidColor(unfocusedColor)

    val isNotOneByOne = rememberSaveable(mainLyric) {
        mutableStateOf(mainLyric.all { it.first == mainLyric.firstOrNull()?.first })
    }

    val liveTime = remember(mainLyric) { mutableIntStateOf(liveTimeLambda()) }

    // Update liveTime via snapshotFlow instead of polling loop
    LaunchedEffect(liveTimeLambda) {
        snapshotFlow { liveTimeLambda() }
            .distinctUntilChanged()
            .collect { liveTime.intValue = it }
    }

    Column(
        Modifier.padding(horizontal = 9.dp),
        horizontalAlignment = viewAlign
    ) {
        val otherSideTransformOrigin = if (otherSide) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)

        val tweenSpecWithDelay = TweenSpec<Float>(durationMillis = 270, easing = yosEasing, delay = 110)
        val tweenSpecWithoutDelay = TweenSpec<Float>(durationMillis = 300, easing = yosEasing, delay = 45)

        val scale = animateFloatAsState(
            targetValue = if (isCurrentLambda()) 1.1f else 1f,
            animationSpec = if (isCurrentLambda()) tweenSpecWithDelay else tweenSpecWithoutDelay
        )

        val cardPadding = if (otherSide) Modifier.padding(start = 28.dp) else Modifier.padding(end = 28.dp)

        val otherSideAnimate = if (otherSide) TransformOrigin(1f, 0.25f) else TransformOrigin(0f, 0.25f)

        if (isLyricEmpty()) {
            // ---- Countdown gap dots ----
            // The dots live in the empty line's own slot: the countdown anchors
            // on the LATER of the slot start and the END of the line above (its
            // last sung word), so the dots can never start while the line above
            // is still finishing. And when the gap to the next line is too
            // small, the dots never appear at all.
            Column(Modifier.animateContentSize()) {
                val percent = remember(mainLyric) {
                    derivedStateOf {
                        val m = mainLyric.first().first
                        val start = maxOf(m, prevLineEndMs())
                        ((liveTime.intValue - start).coerceAtLeast(0f) / (nextTime() - start)).coerceAtMost(1f)
                    }
                }
                val gapMs = remember(mainLyric) {
                    derivedStateOf {
                        val m = mainLyric.first().first
                        val start = maxOf(m, prevLineEndMs())
                        (nextTime() - start).coerceAtLeast(0f)
                    }
                }
                val show = remember {
                    derivedStateOf {
                        isLyricEmpty() && isCurrentLambda() && percent.value != 0f && gapMs.value >= 5000f
                    }
                }

                AnimatedVisibility(
                    visible = show.value,
                    enter = fadeIn(animationSpec = tween(550, easing = yosEasing, delayMillis = 300)) +
                            scaleIn(initialScale = 0.85f, transformOrigin = otherSideAnimate,
                                animationSpec = tween(550, easing = yosEasing, delayMillis = 300)),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = otherSideAnimate,
                        animationSpec = tween(340, easing = yosEasing))
                ) {
                    LyricCard(
                        scale = { scale.value },
                        cardPadding = cardPadding,
                        otherSideTransformOrigin = otherSideTransformOrigin,
                        viewAlign = viewAlign
                    ) {
                        Column(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
                            horizontalAlignment = viewAlign
                        ) {
                            GapDotsAnim(progress = { percent.value }, colorLambda = { mainTextBasicColor })
                        }
                    }
                }
            }
        } else {
            // ---- Regular lyric line ----
            LyricCard(
                scale = { scale.value },
                cardPadding = cardPadding,
                otherSideTransformOrigin = otherSideTransformOrigin,
                viewAlign = viewAlign
            ) {
                val blurValue = animateDpAsState(
                    targetValue = blur().dp,
                    animationSpec = tween(durationMillis = 0, delayMillis = if (isTopLambda()) 260 else 0)
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (blur() == 0f) Modifier
                            else Modifier.blur(blurValue.value, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        ),
                    horizontalAlignment = viewAlign
                ) {
                    val textAlign = if (otherSide) TextAlign.End else TextAlign.Start

                    val alphaTweenWithDelay = TweenSpec<Float>(durationMillis = 350, easing = yosEasing, delay = 145)
                    val alphaTweenWithoutDelay = TweenSpec<Float>(durationMillis = 350, easing = yosEasing, delay = 80)

                    val thisAlphaAnimated = animateFloatAsState(
                        targetValue = if (isCurrentLambda()) 1f else 0.14f,
                        animationSpec = if (isCurrentLambda()) alphaTweenWithDelay else alphaTweenWithoutDelay
                    )

                    val thisAlpha = remember(mainLyric) {
                        derivedStateOf {
                            if (isNotOneByOne.value) thisAlphaAnimated.value else 1f
                        }
                    }

                    val otherSidePadding = remember(mainLyric) {
                        derivedStateOf {
                            if (otherSide) {
                                Modifier.padding(
                                    start = 20.dp,
                                    end = if (mainLyric.last().second.endsWith("：")) 3.dp else 20.dp
                                )
                            } else {
                                Modifier.padding(start = 20.dp, end = 20.dp)
                            }
                        }
                    }

                    val showHighLight = remember(mainLyric) {
                        derivedStateOf {
                            if (isNotOneByOne.value) true
                            else liveTime.intValue >= mainLyric[mainLyric.size - (if (translation != null) 3 else 1)].first
                        }
                    }

                    val charStyle = if (otherSide) mainTextStyle().copy(textAlign = TextAlign.End) else mainTextStyle()

                    Line(
                        lines = mainLyric,
                        style = if (otherSide) mainTextStyle().copy(textAlign = TextAlign.End) else mainTextStyle(),
                        measurer = measurer,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = thisAlpha.value
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .padding(vertical = 4.dp)
                            .then(otherSidePadding.value)
                            .then(
                                if (interactive) {
                                    Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onClick() }
                                } else {
                                    Modifier
                                },
                            ),
                        viewAlign = viewAlign
                    ) { _, measureResult ->
                        // ---- Drawing logic ----
                        if (isNotOneByOne.value) {
                            // Full line highlight (not word-synced)
                            return@Line onDrawBehind {
                                drawText(textLayoutResult = measureResult, color = focusedColor)
                            }
                        }

                        if (!isCurrentLambda()) {
                            // Past or future line
                            if (showHighLight.value) {
                                return@Line onDrawBehind {
                                    drawText(textLayoutResult = measureResult, color = focusedColor, topLeft = Offset(0f, -4f))
                                }
                            } else {
                                return@Line onDrawBehind {
                                    drawText(textLayoutResult = measureResult, color = unfocusedColor)
                                }
                            }
                        }

                        // ---- Word‑synced highlighting ----
                        var sum = 0
                        var lastTime = mainLyric.first().first
                        val wordsToDraw = arrayListOf<DrawWord>()

                        mainLyric.fastForEachIndexed { wordIndex, word ->
                            val thisWord = word.second
                            if (thisWord.isEmpty()) return@fastForEachIndexed

                            val wordStartTime = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                wordSyncedWords[wordIndex].first
                            } else lastTime
                            val wordEndTime = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                wordSyncedWords[wordIndex].second
                            } else word.first

                            val avgTime = (wordEndTime - wordStartTime) / thisWord.length.coerceAtLeast(1)

                            val groupLastTime = if (wordIndex - 1 < 0) mainLyric.first().first else mainLyric[wordIndex - 1].first
                            val groupPercent = if ((wordEndTime - groupLastTime) == 0f) 0f else
                                ((liveTime.intValue - groupLastTime).coerceAtLeast(0f) / (wordEndTime - groupLastTime)).coerceIn(0f, 1f)
                            val easedPercent = easing.transform(groupPercent.coerceIn(0f, 1f))
                            val topLeftWeight = 4 * easedPercent

                            thisWord.forEach { char ->
                                val charWord = char.toString()
                                val layout = measurer.measure(
                                    text = charWord,
                                    style = charStyle,
                                    constraints = measureResult.layoutInput.constraints
                                )

                                val currentPercent = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                    val wordDur = (wordEndTime - wordStartTime).coerceAtLeast(1f)
                                    ((liveTime.intValue - wordStartTime).coerceIn(0f, wordDur) / wordDur)
                                } else {
                                    ((liveTime.intValue - lastTime) / avgTime)
                                }

                                wordsToDraw += DrawWord(
                                    time = lastTime + avgTime,
                                    word = charWord,
                                    layout = layout,
                                    topLeft = measureResult.getBoundingBox(
                                        sum.coerceAtMost(mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0)
                                    ).topLeft.minus(Offset(0f, topLeftWeight)),
                                    brush = { px, percent ->
                                        if (thisWord == " ") return@DrawWord unfocusedSolidBrush

                                        val beforeColor = if (percent <= -0.5f) {
                                            unfocusedColor
                                        } else {
                                            focusedColor
                                        }

                                        val afterColor = if (percent >= 1f) {
                                            focusedColor
                                        } else {
                                            unfocusedColor
                                        }

                                        Brush.horizontalGradient(
                                            0f to beforeColor,
                                            (percent - px).coerceIn(
                                                0f,
                                                1f
                                            ) to beforeColor,
                                            (percent + px).coerceIn(
                                                0f,
                                                1f
                                            ) to afterColor
                                        )
                                    },
                                    percent = { if (thisWord == " ") 0f else currentPercent }
                                ).also {
                                    sum += charWord.length
                                    lastTime += avgTime
                                }
                            }
                        }

                        onDrawBehind {
                            wordsToDraw.fastForEach { drawWord ->
                                drawText(
                                    textLayoutResult = drawWord.layout,
                                    topLeft = drawWord.topLeft,
                                    brush = drawWord.brush(0.3f, drawWord.percent())
                                )
                            }
                        }
                    }

                    // ---- Translation ----
                    AnimatedVisibility(showTranslation && translation != null) {
                        translation?.let {
                            val translationAlpha = animateFloatAsState(
                                targetValue = if (isCurrentLambda()) 0.5f else 0.14f,
                                animationSpec = if (isCurrentLambda()) alphaTweenWithDelay else alphaTweenWithoutDelay
                            )
                            Text(
                                text = it,
                                fontSize = (subTextSize * (SettingsLibrary.LyricFontSize / 30.5f)).sp,
                                color = subTextBasicColor,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = translationAlpha.value
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                    .padding(start = 20.dp, end = 20.dp, top = 5.dp),
                                lineHeight = ((subTextSize + 5) * (SettingsLibrary.LyricFontSize / 30.5f)).sp,
                                letterSpacing = 0.3.sp,
                                textAlign = textAlign
                            )
}
                    }
                    }
                }
            }
        }
        }

// ---- LyricCard wrapper ----
@Composable
private fun LyricCard(
    scale: () -> Float,
    cardPadding: Modifier,
    otherSideTransformOrigin: TransformOrigin,
    viewAlign: Alignment.Horizontal,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                transformOrigin = otherSideTransformOrigin
            }
            .fillMaxWidth()
            .then(cardPadding)
            .padding(top = 9.dp, bottom = 9.dp),
        horizontalAlignment = viewAlign
    ) {
        content()
    }
}

// ---- Gap dots: 3 dots, sequential opacity fill ----
@Composable
fun GapDotsAnim(progress: () -> Float, colorLambda: () -> Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 5.dp)
    ) {
        for (i in 0 until 3) {
            val segmentStart = i / 3f
            val segmentEnd = (i + 1) / 3f
            val raw = (progress() - segmentStart) / (segmentEnd - segmentStart)
            val dotAlpha = (0.2f + 0.8f * raw.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(11.dp)
                    .background(colorLambda().copy(alpha = dotAlpha), shape = CircleShape)
            )
        }
    }
}

// ---- Main text style ----
@Composable
fun mainTextStyle(): TextStyle {
    val fontWeight = SettingsLibrary.LyricFontWeight
    // Line-synced lyrics follow the user's font-size setting too (default 30.5 sp),
    // with line height scaled by the same factor to preserve the current ratio.
    val lyricFontSize = SettingsLibrary.LyricFontSize
    val lineBalance = SettingsLibrary.LyricLineBalance
    return TextStyle(
        fontFamily = SfProFontFamily,
        fontSize = lyricFontSize.sp,
        lineHeight = (lyricFontSize * (40.5f / 30.5f)).sp,
        fontWeight = when (fontWeight) {
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
        },
        letterSpacing = 0.05.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        ),
        lineBreak = LineBreak(
            strategy = if (lineBalance) LineBreak.Strategy.Balanced else LineBreak.Strategy.Simple,
            strictness = LineBreak.Strictness.Default,
            wordBreak = LineBreak.WordBreak.Default
        )
    )
}

@Composable
private fun LyricsSpinnerContent(color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppleLoadingSpinner(
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Loading lyrics...",
            fontSize = 14.sp,
            fontFamily = SfProFontFamily,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.5f)
        )
    }
}

// ---- Data class for drawing words ----
@Stable
private data class DrawWord(
    val time: Float,
    val word: String,
    val layout: TextLayoutResult,
    val topLeft: Offset,
    val brush: (px: Float, percent: Float) -> Brush,
    val percent: () -> Float
)