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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt


val yosEasing = CubicBezierEasing(0.75f, 0.0f, 0.25f, 1.0f)

private const val LRC_LEAD_MS = 300L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

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

    val interactive = LocalLyricsInteractive.current

    var lyricBlurEffect by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricBlurEffect)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsLibrary.LyricBlurEffect }
            .distinctUntilChanged()
            .collect { lyricBlurEffect = it }
    }

    val hasWordSynced = wordSyncedLambda()
    val wordSyncedLinesExist = MediaViewModelObject.wordSyncedLines.value.isNotEmpty()
    if (hasWordSynced && wordSyncedLinesExist) {
        val dominantBackground = MediaViewModelObject.paletteDarkVibrantColor.value
        val lyricTextColor =
            if (dominantBackground.luminance() < 0.4f) Color.White
            else Color.Black

        AmlLyricsView(
            player = MediaControlPlayerAdapter,
            textColor = lyricTextColor,
            modifier = modifier,
            onBackgroundClick = onBackClick,
        )
        return
    }

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

    val overlapHeldIndices = remember { mutableStateOf<Set<Int>>(emptySet()) }

    val enableLyricScroll = remember(MediaViewModelObject.isUnsyncedLyrics.value) {
        mutableStateOf(!MediaViewModelObject.isUnsyncedLyrics.value)
    }

    // Shared snapshot position so snapshotFlow collectors re-emit (liveTimeLambda is not snapshot state).
    val liveTimeState = remember { mutableIntStateOf(liveTimeLambda()) }

    val height = rememberSaveable { mutableIntStateOf(0) }
    val space = 0.dp

    val measurer = rememberTextMeasurer(cacheSize = 32)

    val visibleItems = derivedStateOf { scrollState.layoutInfo.visibleItemsInfo }

    val targetOffset = rememberSaveable(height.intValue) {
        lyricAnchorOffsetPx(height.intValue).toFloat()
    }
    val targetItem = derivedStateOf {
        visibleItems.value.find { it.index == currentLyricIndex.intValue }
    }
    val currentOffset = derivedStateOf {
        targetItem.value?.offset ?: targetOffset.toInt()
    }
    val scrollDistance = derivedStateOf {
        currentOffset.value - targetOffset
    }

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
            val isCurrent = derivedStateOf {
                MediaViewModelObject.isUnsyncedLyrics.value ||
                    index == currentLyricIndex.intValue ||
                    index in overlapHeldIndices.value
            }
            val isTop = derivedStateOf { index == currentLyricIndex.intValue - 1 }

            val showStateAnimation = derivedStateOf {
                !MediaViewModelObject.isUnsyncedLyrics.value &&
                    (currentLyricIndex.intValue in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 }
                        && currentLyricIndex.intValue >= 0 && enableLyricScroll.value)
            }

            val isLyricEmpty = rememberSaveable(lines) {
                mutableStateOf(lines.all { it.second.isBlank() })
            }

            if (index == 0 && !MediaViewModelObject.isUnsyncedLyrics.value) {
                val firstStart = try {
                    lines.first().first
                } catch (_: Exception) {
                    0f
                }
                if (firstStart >= 3000f) {
                    val introActive = liveTimeState.intValue < firstStart
                    val introOffset = remember(lines) { Animatable(0f) }
                    val introAlpha = remember(lines) { Animatable(0f) }
                    var introShown by remember(lines) { mutableStateOf(false) }
                    var introHiding by remember(lines) { mutableStateOf(false) }
                    val introDensity = LocalDensity.current.density

                    LaunchedEffect(introActive) {
                        if (introActive) {
                            introShown = true
                            introHiding = false
                            introOffset.snapTo(-GAP_DOTS_SLIDE_DP)
                            introAlpha.snapTo(0f)
                            launch {
                                introOffset.animateTo(
                                    0f,
                                    animationSpec = tween(
                                        GAP_DOTS_REVEAL_MS.toInt(),
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                            launch {
                                introAlpha.animateTo(
                                    1f,
                                    animationSpec = tween(
                                        (GAP_DOTS_REVEAL_MS * 0.8f).toInt(),
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        } else if (introShown) {
                            introShown = false
                            introHiding = true
                            coroutineScope {
                                launch {
                                    introOffset.animateTo(
                                        GAP_DOTS_SLIDE_DP,
                                        animationSpec = tween(
                                            GAP_DOTS_HIDE_MS.toInt(),
                                            easing = LinearOutSlowInEasing
                                        )
                                    )
                                }
                                launch {
                                    introAlpha.animateTo(
                                        0f,
                                        animationSpec = tween(
                                            GAP_DOTS_HIDE_MS.toInt(),
                                            easing = LinearOutSlowInEasing
                                        )
                                    )
                                }
                            }
                            introHiding = false
                        }
                    }
                    val introVisible = introActive || introHiding

                    Box(
                        Modifier.animateContentSize(
                            animationSpec =
                                tween(durationMillis = GAP_ROW_ANIM_MS.toInt(), easing = yosEasing)
                        )
                    ) {
                        if (introVisible) {
                            val fill = (liveTimeState.intValue / firstStart).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = introAlpha.value
                                        translationY = introOffset.value * introDensity
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement =
                                    if (otherSideForLines.getOrElse(0) { false }) {
                                        Arrangement.End
                                    } else {
                                        Arrangement.Start
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GapDotsAnim(progress = { fill }, colorLambda = { mainTextBasicColor })
                            }
                        }
                    }
                }
            }

            key(lines) {
                val translation = remember(index) {
                    lines.last().second.ifBlank { null }
                }

                val blur = derivedStateOf {
                    if (MediaViewModelObject.isUnsyncedLyrics.value ||
                        !showStateAnimation.value ||
                        index == currentLyricIndex.intValue ||
                        index in overlapHeldIndices.value ||
                        !lyricBlurEffect || !blurLambda() || !supportBlur
                    ) {
                        0f
                    } else {
                        (abs(index - currentLyricIndex.intValue) * 2.5f).coerceAtMost(8f)
                    }
                }

                val otherSide = remember(index) {
                    otherSideForLines.getOrElse(index) { false }
                }

                val isBackgroundLine = remember(index) {
                    MediaViewModelObject.backgroundLines.getOrElse(index) { false }
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

                val bubbleOffset = remember(index) { Animatable(0f) }
                var bubbleKicked by remember(index) { mutableStateOf(false) }
                val bubbleDensity = LocalDensity.current.density

                LaunchedEffect(currentLyricIndex.intValue, overlapHeldIndices.value) {
                    if (!enableLyricScroll.value || MediaViewModelObject.isUnsyncedLyrics.value) return@LaunchedEffect
                    if (!bubbleKicked) {
                        bubbleKicked = true
                        return@LaunchedEffect
                    }
                    if (overlapHeldIndices.value.isNotEmpty()) return@LaunchedEffect
                    val current = currentLyricIndex.intValue
                    if (index == current || index == current + 1) return@LaunchedEffect
                    val distance = abs(index - current)
                    if (distance > BUBBLE_RADIUS) return@LaunchedEffect
                    val direction = if (index < current) -1f else 1f
                    val amplitude = bubbleAmplitudeDp(distance) * bubbleDensity
                    bubbleOffset.animateTo(
                        targetValue = direction * amplitude,
                        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                    )
                    bubbleOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.6f,
                            stiffness = 260f,
                            visibilityThreshold = 0.05f
                        )
                    )
                }

                Box(
                    Modifier.graphicsLayer { translationY = bubbleOffset.value }
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
                        isBackgroundLine = isBackgroundLine,
                        onClick = {
                            if (!MediaViewModelObject.isUnsyncedLyrics.value) {
                                Vibrator.doubleClick(context)
                                overlapHeldIndices.value = emptySet()
                                currentLyricIndex.intValue = index
                                mediaEvent.onSeek(lines.first().first.toInt())
                            }
                        }
                    )
                }
            }

            key(index) {
                val show = derivedStateOf { !isLyricEmpty.value || isCurrent.value }
                val thisTargetHeight = remember { mutableStateOf(space) }
                val spacerDensity = LocalDensity.current.density

                LaunchedEffect(currentLyricIndex.intValue) {
                    if (visibleItems.value.isEmpty()) return@LaunchedEffect
                    val cur = currentLyricIndex.intValue
                    val belowCurrent = index > cur
                    if (belowCurrent && showStateAnimation.value && show.value && enableLyricScroll.value) {
                        val distance = index - cur
                        val weight = (distance.toFloat() / PULL_LINE_RANGE.toFloat()).coerceIn(0f, 1f)
                        val pullPx = (scrollDistance.value * PULL_STRENGTH).coerceAtLeast(0f)
                        // The stretch ripples downward from the current line…
                        delay((distance * PULL_STAGGER_MS).toLong())
                        thisTargetHeight.value = ((pullPx * weight) / spacerDensity).dp + space
                        delay(PULL_HOLD_MS + (distance * PULL_RELEASE_MS))
                        thisTargetHeight.value = space
                    } else if (show.value) {
                        thisTargetHeight.value = space
                    } else {
                        thisTargetHeight.value = 0.dp
                    }
                }

                val offset = animateDpAsState(
                    targetValue = thisTargetHeight.value,
                    // never be flung past its set level.
                    animationSpec = if (thisTargetHeight.value == space) {
                        spring(stiffness = 170f, dampingRatio = 1f, visibilityThreshold = 0.01.dp)
                    } else {
                        spring(stiffness = 260f, dampingRatio = 1f, visibilityThreshold = 0.01.dp)
                    }
                )
                Spacer(modifier = Modifier.height(offset.value))
            }
        }

        blankSpacer(uiConfig.blankHeight.dp)
        item("extra_blank") { Spacer(Modifier.height(500.dp)) }
    }

    // ---- Auto‑scroll to current line ----
    LaunchedEffect(currentLyricIndex.intValue, translationLambda(), overlapHeldIndices.value) {
        try {
            if (!enableLyricScroll.value) return@LaunchedEffect
            // Unsynced lyrics never auto-scroll or restore a position.
            if (MediaViewModelObject.isUnsyncedLyrics.value) return@LaunchedEffect

            if (overlapHeldIndices.value.isNotEmpty()) return@LaunchedEffect

            val cur = currentLyricIndex.intValue
            val curBlank = try {
                lrcEntries[cur][1].second.isBlank()
            } catch (_: Exception) { false }
            val prevBlank = try {
                cur - 1 >= 0 && lrcEntries[cur - 1][1].second.isBlank()
            } catch (_: Exception) { false }
            val prevHadGapDots = try {
                cur - 1 >= 0 &&
                    lyricLineEndMs(lrcEntries, cur - 1) + 5000f <= lrcEntries[cur].first().first
            } catch (_: Exception) { false }

            if (curBlank || prevBlank || prevHadGapDots) {
                delay(
                    if (prevHadGapDots) GAP_DOTS_HIDE_MS + GAP_ROW_ANIM_MS + 30
                    else GAP_ROW_ANIM_MS + 30
                )
                if (currentLyricIndex.intValue != cur) return@LaunchedEffect
            }

            if (prevBlank) {
                scrollState.animateCurrentLineToAnchor(
                    currentIndex = cur,
                    viewportHeight = height.intValue,
                    targetOffset = targetOffset.toInt(),
                )
                return@LaunchedEffect
            }

            delay(HIGHLIGHT_LEAD_MS)
            if (currentLyricIndex.intValue != cur) return@LaunchedEffect

            scrollState.animateCurrentLineToAnchor(
                currentIndex = currentLyricIndex.intValue,
                viewportHeight = height.intValue,
                targetOffset = targetOffset.toInt(),
            )
        } catch (_: Exception) { }
    }

    LaunchedEffect(currentLyricIndex.intValue) {
        if (!enableLyricScroll.value) return@LaunchedEffect
        val curAtStart = currentLyricIndex.intValue
        delay(GAP_ROW_ANIM_MS + HIGHLIGHT_LEAD_MS + 500L)
        if (currentLyricIndex.intValue != curAtStart) return@LaunchedEffect
        scrollState.pullCurrentLineToAnchorIfAbove(
            currentIndex = curAtStart,
            viewportHeight = height.intValue,
            targetOffset = targetOffset.toInt(),
        )
    }

    // ---- Live time updater for current index ----
    val lyricLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
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
            val newHeld = mutableSetOf<Int>()
            val overlapWindowStart = (newIdx - 4).coerceAtLeast(0)
            for (i in overlapWindowStart until newIdx) {
                if (lyricLineEndMs(lrcEntries, i) > targetPos) {
                    newHeld.add(i)
                }
            }
            overlapHeldIndices.value = newHeld
            if (newIdx != currentLyricIndex.intValue) {
                currentLyricIndex.intValue = newIdx
            }
            delay(100)
        }
    }
}

private fun LazyListScope.blankSpacer(height: Dp) {
    item { Box(Modifier.height(height)) }
}

private fun lyricLineEndMs(lrcEntries: List<List<Pair<Float, String>>>, index: Int): Float {
    val syncedLines = MediaViewModelObject.wordSyncedLines.value
    if (index < syncedLines.size && syncedLines[index].words.isNotEmpty()) {
        return syncedLines[index].words.maxOf { it.endTimeMs }.toFloat()
    }
    val line = lrcEntries.getOrNull(index) ?: return Float.MAX_VALUE
    val lastWord = line.drop(1).dropLast(1).lastOrNull()
    return lastWord?.first ?: line.first().first
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
    isBackgroundLine: Boolean = false,
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

        val tweenSpecWithDelay = TweenSpec<Float>(durationMillis = 270, easing = yosEasing, delay = 0)
        val tweenSpecWithoutDelay = TweenSpec<Float>(durationMillis = 300, easing = yosEasing, delay = 0)

        val scale = animateFloatAsState(
            targetValue = if (isCurrentLambda()) 1.1f else 1f,
            animationSpec = if (isCurrentLambda()) tweenSpecWithDelay else tweenSpecWithoutDelay
        )

        val cardPadding = if (otherSide) Modifier.padding(start = 28.dp) else Modifier.padding(end = 28.dp)

        val otherSideAnimate = if (otherSide) TransformOrigin(1f, 0.25f) else TransformOrigin(0f, 0.25f)

        if (isLyricEmpty()) {
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
            if (gapMs.value >= 5000f && !MediaViewModelObject.isUnsyncedLyrics.value) {
                val dotsAlpha = animateFloatAsState(
                    targetValue = if (show.value) 1f else 0f,
                    animationSpec = tween(340, easing = yosEasing, delayMillis = if (show.value) 300 else 0)
                )
                val dotsScale = animateFloatAsState(
                    targetValue = if (show.value) 1f else 0.85f,
                    animationSpec = tween(340, easing = yosEasing, delayMillis = if (show.value) 300 else 0)
                )
                Box(
                    Modifier.animateContentSize(
                        animationSpec = tween(durationMillis = GAP_ROW_ANIM_MS.toInt(), easing = yosEasing)
                    )
                ) {
                    if (isCurrentLambda()) {
                        LyricCard(
                            scale = { scale.value },
                            cardPadding = cardPadding,
                            otherSideTransformOrigin = otherSideTransformOrigin,
                            viewAlign = viewAlign
                        ) {
                            Column(
                                Modifier
                                    .graphicsLayer {
                                        alpha = dotsAlpha.value
                                        scaleX = dotsScale.value
                                        scaleY = dotsScale.value
                                        transformOrigin = otherSideAnimate
                                    }
                                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
                                horizontalAlignment = viewAlign
                            ) {
                                GapDotsAnim(progress = { percent.value }, colorLambda = { mainTextBasicColor })
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.height(0.dp))
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

                    val alphaTweenWithDelay = TweenSpec<Float>(durationMillis = 350, easing = yosEasing, delay = 0)
                    val alphaTweenWithoutDelay = TweenSpec<Float>(durationMillis = 350, easing = yosEasing, delay = 0)

                    val bgAlphaScale = if (isBackgroundLine) 0.6f else 1f
                    val thisAlphaAnimated = animateFloatAsState(
                        targetValue = (if (isCurrentLambda()) 1f else 0.14f) * bgAlphaScale,
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

                    val baseLineStyle = if (otherSide) mainTextStyle().copy(textAlign = TextAlign.End) else mainTextStyle()
                    val lineStyle =
                        if (isBackgroundLine) {
                            baseLineStyle.copy(
                                fontSize = (baseLineStyle.fontSize.value * 0.7f).sp,
                                lineHeight = (baseLineStyle.lineHeight.value * 0.7f).sp,
                            )
                        } else {
                            baseLineStyle
                        }
                    val charStyle = if (otherSide) lineStyle.copy(textAlign = TextAlign.End) else lineStyle

                    Line(
                        lines = mainLyric,
                        style = lineStyle,
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

            val lineEndMs = mainLyric.last().first
            val gapStartMs = maxOf(lineEndMs, prevLineEndMs())
            val nextStartMs = nextTime()
            val gapLenMs = (nextStartMs - gapStartMs).coerceAtLeast(0f)
            // Unsynced lyrics have fabricated timestamps — no gap dots.
            if (gapLenMs >= 5000f && !MediaViewModelObject.isUnsyncedLyrics.value) {
                val gapActive =
                    isCurrentLambda() &&
                        liveTime.intValue >= gapStartMs &&
                        liveTime.intValue < nextStartMs

                val gapOffset = remember(mainLyric) { Animatable(0f) }
                val gapAlpha = remember(mainLyric) { Animatable(0f) }
                var gapShown by remember(mainLyric) { mutableStateOf(false) }
                var gapHiding by remember(mainLyric) { mutableStateOf(false) }
                val gapDensity = LocalDensity.current.density

                LaunchedEffect(gapActive) {
                    if (gapActive) {
                        gapShown = true
                        gapHiding = false
                        gapOffset.snapTo(-GAP_DOTS_SLIDE_DP)
                        gapAlpha.snapTo(0f)
                        launch {
                            gapOffset.animateTo(
                                0f,
                                animationSpec = tween(
                                    GAP_DOTS_REVEAL_MS.toInt(),
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                        launch {
                            gapAlpha.animateTo(
                                1f,
                                animationSpec = tween(
                                    (GAP_DOTS_REVEAL_MS * 0.8f).toInt(),
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    } else if (gapShown) {
                        gapShown = false
                        gapHiding = true
                        coroutineScope {
                            launch {
                                gapOffset.animateTo(
                                    GAP_DOTS_SLIDE_DP,
                                    animationSpec = tween(
                                        GAP_DOTS_HIDE_MS.toInt(),
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            }
                            launch {
                                gapAlpha.animateTo(
                                    0f,
                                    animationSpec = tween(
                                        GAP_DOTS_HIDE_MS.toInt(),
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            }
                        }
                        gapHiding = false
                    }
                }
                val gapVisible = gapActive || gapHiding

                Box(
                    Modifier.animateContentSize(
                        animationSpec = tween(durationMillis = GAP_ROW_ANIM_MS.toInt(), easing = yosEasing)
                    )
                ) {
                    if (gapVisible) {
                        val gapFill = ((liveTime.intValue - gapStartMs) / gapLenMs).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = gapAlpha.value
                                    translationY = gapOffset.value * gapDensity
                                }
                                .padding(horizontal = 15.dp, vertical = 12.dp),
                            horizontalArrangement = if (otherSide) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GapDotsAnim(progress = { gapFill }, colorLambda = { mainTextBasicColor })
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
