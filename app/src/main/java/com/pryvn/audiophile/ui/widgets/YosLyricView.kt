package com.pryvn.audiophile.ui.widgets

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pryvn.audiophile.code.utils.lrc.YosMediaEvent
import com.pryvn.audiophile.code.utils.lrc.YosUIConfig
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.MainViewModelObject
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


val yosEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

private const val LRC_LEAD_MS = 300L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

/**
 * YosLyricView 主控件
 * @param lrcEntriesLambda 处理完毕的 Lrc 文本 (每个条目是 List<Pair<Float, String>>)
 * @param liveTimeLambda 当前歌曲进度 (毫秒)
 * @param mediaEvent YosLyricView 媒体事件
 * @param translationLambda 是否开启翻译
 * @param blurLambda 是否启用模糊效果
 * @param uiConfig YosLyricView UI 控制
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
    modifier: Modifier,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val mainTextBasicColor = Color(uiConfig.mainTextBasicColor)
    val subTextBasicColor = Color(uiConfig.subTextBasicColor)
    val otherSideForLines = MediaViewModelObject.otherSideForLines
    val lrcEntries = lrcEntriesLambda()

    // ---- Empty / Loading state ----
    if (lrcEntries.isEmpty() || otherSideForLines.isEmpty()) {
        val isLoading = MediaViewModelObject.isLoadingLyrics.value
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onBackClick() }
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppleLoadingSpinner(
                        modifier = Modifier.size(56.dp),
                        color = mainTextBasicColor.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Loading lyrics...",
                        fontSize = 14.sp,
                        fontFamily = SfProFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = mainTextBasicColor.copy(alpha = 0.5f)
                    )
                }
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
    val enableLyricScroll = remember { mutableStateOf(true) }

    val height = rememberSaveable { mutableIntStateOf(0) }
    val targetWeight = 0.0618f
    val targetOffset = rememberSaveable(height.intValue) {
        height.intValue * targetWeight
    }
    val space = 0.dp

    val measurer = rememberTextMeasurer(cacheSize = 32)

    val visibleItems = derivedStateOf { scrollState.layoutInfo.visibleItemsInfo }
    val targetItem = derivedStateOf {
        visibleItems.value.find { it.index == currentLyricIndex.intValue + 1 }
    }
    val currentOffset = derivedStateOf {
        targetItem.value?.offset ?: targetOffset.toInt()
    }
    val scrollDistance = derivedStateOf {
        currentOffset.value - targetOffset
    }
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onBackClick() }
            .nestedScroll(nestedScrollConnection)
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
                    liveTimeLambda = liveTimeLambda,
                    measurer = measurer,
                    isLyricEmpty = { isLyricEmpty.value },
                    nextTime = {
                        if (index + 1 > lrcEntries.size - 1) 0f else lrcEntries[index + 1].first().first
                    },
                    wordSyncedWords = thisWordSyncedWords.value,
                    onClick = {
                        Vibrator.doubleClick(context)
                        currentLyricIndex.intValue = index
                        mediaEvent.onSeek(lines.first().first.toInt())
                    }
                )
            }

            // ---- Spacer animation for each item ----
            key(index) {
                val show = derivedStateOf { !isLyricEmpty.value || isCurrent.value }
                val thisScrollDistance = if (targetItem.value != null) {
                    (scrollDistance.value / visibleItems.value.size.coerceAtLeast(1)).toDp()
                } else 0.dp

                val thisTargetHeight = remember { mutableStateOf(space) }

                LaunchedEffect(currentLyricIndex.intValue) {
                    if (visibleItems.value.isEmpty()) return@LaunchedEffect
                    if (index >= currentLyricIndex.intValue - 1 && showStateAnimation.value && show.value) {
                        val weight = 1f - ((index - nowFirst.value).toFloat() / visibleItems.value.size.toFloat())
                        delay((550 * (1f - weight)).toLong())
                        thisTargetHeight.value = (thisScrollDistance * weight) + space
                        delay((550 / 1.95f * weight).toLong())
                        thisTargetHeight.value = space
                    } else if (show.value) {
                        thisTargetHeight.value = space
                    } else {
                        thisTargetHeight.value = 0.dp
                    }
                }

                val offset = animateDpAsState(
                    targetValue = thisTargetHeight.value,
                    animationSpec = if (thisTargetHeight.value == 0.dp || thisTargetHeight.value == space) {
                        spring(stiffness = 180f, dampingRatio = 0.8f, visibilityThreshold = 0.01.dp)
                    } else {
                        spring(stiffness = 150f, dampingRatio = 0.8f, visibilityThreshold = 0.01.dp)
                    }
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
            if (enableLyricScroll.value) {
                // Skip if previous line is blank (special case)
                val skip = try {
                    currentLyricIndex.intValue - 1 >= 0 &&
                            lrcEntries[currentLyricIndex.intValue - 1][1].second.isBlank()
                } catch (_: Exception) { false }
                if (skip) return@LaunchedEffect

                if (targetItem.value != null) {
                    scrollState.animateScrollBy(
                        scrollDistance.value,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = 150f,
                            visibilityThreshold = 0.01f
                        )
                    )
                } else {
                    scrollState.animateScrollToItem(
                        index = (currentLyricIndex.intValue + 1).coerceAtLeast(0),
                        scrollOffset = -targetOffset.toInt()
                    )
                }
            }
        } catch (_: Exception) { }
    }

    // ---- Live time updater for current index ----
    LaunchedEffect(Unit) {
        while (isActive) {
            val liveTime = liveTimeLambda()
            val targetPos = liveTime + LRC_LEAD_MS + LYRIC_VISUAL_TUNING_OFFSET_MS
            val nextIdx = lrcEntries.indexOfFirst { line -> line.first().first > targetPos }
            val newIdx = when {
                nextIdx == -1 -> lrcEntries.size - 1
                nextIdx == 0 -> 0
                else -> nextIdx - 1
            }
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
    otherSide: Boolean,
    liveTimeLambda: () -> Int,
    wordSyncedWords: List<Triple<Float, Float, Boolean>> = emptyList(),
    onClick: () -> Unit
) {
    val viewAlign = if (otherSide) Alignment.End else Alignment.Start

    val focusedColor = Color.White
    val unfocusedColor = Color(0x2EFFFFFF)
    val unfocusedSolidBrush = SolidColor(unfocusedColor)

    val isNotOneByOne = rememberSaveable(mainLyric) {
        mutableStateOf(mainLyric.all { it.first == mainLyric.firstOrNull()?.first })
    }

    val liveTime = remember(mainLyric) { mutableIntStateOf(liveTimeLambda()) }

    // Update liveTime if needed
    LaunchedEffect(isLyricEmpty, isNotOneByOne.value) {
        if (isLyricEmpty() || !isNotOneByOne.value) {
            while (true) {
                withContext(Dispatchers.Main) { liveTime.intValue = liveTimeLambda() }
                delay(50L)
            }
        }
    }

    Column(
        Modifier.padding(horizontal = 9.dp),
        horizontalAlignment = viewAlign
    ) {
        val otherSideAnimate = if (otherSide) TransformOrigin(1f, 0.25f) else TransformOrigin(0f, 0.25f)
        val otherSideTransformOrigin = if (otherSide) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)

        val springSpecWithDelay = spring(dampingRatio = 0.8f, stiffness = 180f, visibilityThreshold = 0.001f)
        val springSpecWithoutDelay = spring(dampingRatio = 0.8f, stiffness = 200f, visibilityThreshold = 0.001f)

        val scale = animateFloatAsState(
            targetValue = if (isCurrentLambda()) 1.005f else 1f,
            animationSpec = if (isCurrentLambda()) springSpecWithDelay else springSpecWithoutDelay
        )

        val cardPadding = if (otherSide) Modifier.padding(start = 28.dp) else Modifier.padding(end = 28.dp)

        if (isLyricEmpty()) {
            // ---- Countdown animation ----
            Column(Modifier.animateContentSize()) {
                val percent = remember(mainLyric) {
                    derivedStateOf {
                        val m = mainLyric.first().first
                        ((liveTime.intValue - m).coerceAtLeast(0f) / (nextTime() - m)).coerceAtMost(1f)
                    }
                }
                val show = remember {
                    derivedStateOf { isLyricEmpty() && isCurrentLambda() && percent.value != 0f }
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
                            CountdownAnimation(progress = { percent.value }, colorLambda = { mainTextBasicColor })
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

                val blurModifier = remember(mainLyric) {
                    derivedStateOf {
                        if (blur() == 0f) Modifier
                        else Modifier.blur(blurValue.value, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    }
                }

                Column(
                    Modifier.then(blurModifier.value).fillMaxWidth(),
                    horizontalAlignment = viewAlign
                ) {
                    val textAlign = if (otherSide) TextAlign.End else TextAlign.Start

                    val alphaSpringWithDelay = spring(dampingRatio = 0.8f, stiffness = 150f, visibilityThreshold = 0.001f)
                    val alphaSpringWithoutDelay = spring(dampingRatio = 0.8f, stiffness = 180f, visibilityThreshold = 0.001f)

                    val thisAlphaAnimated = animateFloatAsState(
                        targetValue = if (isCurrentLambda()) 1f else 0.14f,
                        animationSpec = if (isCurrentLambda()) alphaSpringWithDelay else alphaSpringWithoutDelay
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
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onClick() },
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
                            val wordIsBackground = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                wordSyncedWords[wordIndex].third
                            } else false

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
                                        if (wordIsBackground && (percent < 0f || percent > 1f)) {
                                            return@DrawWord SolidColor(Color.Transparent)
                                        }
                                        val isActive = percent in 0f..1f
                                        val beforeColor = if (percent <= -0.5f) {
                                            if (wordIsBackground) Color.Transparent else unfocusedColor
                                        } else {
                                            if (isActive) Color.White else focusedColor
                                        }
                                        val afterColor = if (percent >= 1f) {
                                            if (wordIsBackground) Color.Transparent else unfocusedColor
                                        } else {
                                            if (isActive) Color.White.copy(alpha = 0.9f) else unfocusedColor
                                        }
                                        val glowSize = if (isActive) (px * 2f).coerceIn(0f, 0.3f) else px
                                        Brush.horizontalGradient(
                                            0f to beforeColor,
                                            (percent - glowSize).coerceIn(0f, 1f) to beforeColor,
                                            (percent + glowSize).coerceIn(0f, 1f) to afterColor,
                                            1f to afterColor
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
                                animationSpec = if (isCurrentLambda()) alphaSpringWithDelay else alphaSpringWithoutDelay
                            )
                            Text(
                                text = it,
                                fontSize = subTextSize.sp,
                                color = subTextBasicColor,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = translationAlpha.value
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                    .padding(start = 20.dp, end = 20.dp, top = 5.dp),
                                lineHeight = (subTextSize + 5).sp,
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

// ---- Countdown dots ----
@Composable
fun CountdownAnimation(progress: () -> Float, colorLambda: () -> Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = yosEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = 0.8f
        },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 5.dp)
        ) {
            for (i in 1..3) {
                val average = 1f / 3f
                val beforePadding = (i - 1) * average
                val thisPercent = (progress() - beforePadding) / ((i * average) - beforePadding)
                val alpha = 0.2f + (0.8f * thisPercent).coerceIn(0f, 0.8f)
                Box(
                    Modifier
                        .size(11.dp)
                        .background(colorLambda().copy(alpha = alpha), shape = CircleShape)
                )
            }
        }
    }
}

// ---- Main text style ----
@Composable
fun mainTextStyle(): TextStyle {
    val fontWeight = SettingsLibrary.LyricFontWeight
    val lineBalance = SettingsLibrary.LyricLineBalance
    return TextStyle(
        fontFamily = SfProFontFamily,
        fontSize = 30.5.sp,
        lineHeight = 40.5.sp,
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