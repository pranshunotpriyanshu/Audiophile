package com.pryvn.audiophile.ui.widgets

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.* 
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.code.utils.lrc.YosMediaEvent
import com.pryvn.audiophile.code.utils.lrc.YosUIConfig
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.MainViewModelObject
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import kotlin.math.abs
import kotlin.math.roundToInt

val yosEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

private const val LRC_LEAD_MS = 300L
private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

/**
 * YosLyricView 主控件
 * @param lrcEntriesLambda 处理完毕的 Lrc 文本
 * @param liveTimeLambda 当前歌曲进度
 * @param mediaEvent YosLyricView 媒体事件
 * @param translationLambda 是否开启翻译
 * @param blurLambda 是否启用模糊效果
 * @param uiConfig YosLyricView UI 控制，仅管理在日常使用中不经常调节的选项
 */
@Composable
fun YosLyricView(
    //mediaViewModel: MediaViewModel,
    lrcEntriesLambda: () -> List<List<Pair<Float, String>>>,
    liveTimeLambda: () -> Int,
    mediaEvent: YosMediaEvent,
    translationLambda: () -> Boolean = { true },
    blurLambda: () -> Boolean = { false },
    //animationConfig: YosAnimationConfig = YosAnimationConfig(),
    uiConfig: YosUIConfig = YosUIConfig(),
    weightLambda: () -> Boolean,
    wordSyncedLambda: () -> Boolean = { false },
    modifier: Modifier,
    onBackClick: () -> Unit
) {
    println("重组：YosLyricView")
    val context = LocalContext.current
    val mainTextBasicColor = Color(uiConfig.mainTextBasicColor)
    val subTextBasicColor = Color(uiConfig.subTextBasicColor)
    //Color(0xFF919191)
    val otherSideForLines = MediaViewModelObject.otherSideForLines

    val lrcEntries = lrcEntriesLambda()

    //val thisLyricLines = MediaViewModelObject.mainLyricLines
    if (lrcEntries.isEmpty() || otherSideForLines.isEmpty() /*|| thisLyricLines.isEmpty()*/) {
        println(
            lrcEntries.isEmpty()
                .toString() + otherSideForLines.isEmpty()/* + thisLyricLines.isEmpty()*/
        )
        val isLoading = MediaViewModelObject.isLoadingLyrics.value
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    onBackClick()
                }
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppleLoadingSpinner(
                        modifier = Modifier.size(56.dp),
                        color = Color(uiConfig.mainTextBasicColor).copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Loading lyrics...",
                        fontSize = 14.sp,
                        fontFamily = SfProFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = Color(uiConfig.mainTextBasicColor).copy(alpha = 0.5f),
                    )
                }
            } else {
                Text(
                    text = "Lyrics couldn't be loaded",
                    fontSize = 16.sp,
                    color = Color(uiConfig.mainTextBasicColor).copy(alpha = 0.5f),
                )
            }
        }
    } else {
        val scrollState = rememberLazyListState()
        val currentLyricIndex =
            remember("YosLyricView_currentLyricIndex") { MainViewModelObject.syncLyricIndex }
        /*val noAnimateItems by remember {
            derivedStateOf { scrollState.layoutInfo.totalItemsCount - scrollState.layoutInfo.visibleItemsInfo.size - 1 }
        }
        val showAnimate by remember {
            derivedStateOf {
                currentLyricIndex in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 } && currentLyricIndex > 0 && currentLyricIndex < noAnimateItems
            }
        }*/
        val blankSpacer: (LazyListScope.() -> Unit) = {
            item {
                Box(
                    modifier = Modifier
                        .height(uiConfig.blankHeight.dp)
                ) {
                }
            }
        }
        //val coroutineScope = rememberCoroutineScope()
        val enableLyricScroll = remember("YosLyricView_enableLyricScroll") {
            mutableStateOf(true)
        }
        /*val lastClickTime = rememberSaveable(key = "YosLyricView_lastClickTime") {
            mutableLongStateOf(0L)
        }*/

        /*YosWrapper {
            LaunchedEffect(enableLyricScroll.value, lastClickTime.longValue) {
                if (!enableLyricScroll.value) {
                    val time = 1500L
                    delay(time)
                    withContext(Dispatchers.Main) {
                        if (TimeUtils.getNowMills() - lastClickTime.longValue >= time) {
                            enableLyricScroll.value = true
                        }
                    }
                }
            }
        }*/

        val height = rememberSaveable(key = "YosLyricView_height") { mutableIntStateOf(0) }

        val targetWeight = 0.0618f
        val targetOffset = rememberSaveable(height.intValue, key = "YosLyricView_targetOffset") {
            //println("计算边距使用：${height.intValue}")
            //println("计算边距为：${height.intValue * targetWeight}")
            height.intValue * targetWeight
        }
        // 顶部边距

        val space = 0.dp
        // 行距

        val measurer = rememberTextMeasurer(
            cacheSize = 32
        )

        val visibleItems = remember("YosLyricView_visibleItems") {
            derivedStateOf {
                scrollState.layoutInfo.visibleItemsInfo
            }
        }
        val targetItem = remember("YosLyricView_targetItem") {
            derivedStateOf {
                visibleItems.value.find {
                    it.index == currentLyricIndex.intValue + 1
                }
            }
        }
        val currentOffset = remember("YosLyricView_currentOffset", targetOffset) {
            derivedStateOf {
                targetItem.value?.offset ?: targetOffset.toInt()
            }
        }
        val scrollDistance = remember("YosLyricView_scrollDistance", targetOffset) {
            derivedStateOf {
                currentOffset.value - targetOffset
            }
        }
        val nowFirst = remember("YosLyricView_nowFirst") {
            derivedStateOf {
                scrollState.firstVisibleItemIndex
            }
        }
        val supportBlur = rememberSaveable(key = "supportBlur") {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }

        val isUserScrolling = remember { mutableStateOf(false) }
        val nestedScrollConnection = remember {
            @Stable
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    isUserScrolling.value = true
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    isUserScrolling.value = false
                    return super.onPostFling(consumed, available)
                }
            }
        }

        YosWrapper {
            LaunchedEffect(isUserScrolling.value) {
                if (isUserScrolling.value) {
                    enableLyricScroll.value = false
                } else {
                    delay(3000)
                    enableLyricScroll.value = true
                }
            }
        }

        YosWrapper {
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(vertical = 16.dp),/*
            verticalArrangement = Arrangement.spacedBy(5.dp),*/
                modifier =
                modifier
                    .fillMaxSize()
                    /*.drawWithCache {
                        onDrawWithContent {
                            val colors = if (weightLambda()) {
                                listOf(
                                    Color.Transparent,
                                    Color(0x59000000),
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color(0x59000000),
                                    Color(0x21000000),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent
                                )
                            } else {
                                listOf(
                                    Color.Transparent,
                                    Color(0x59000000),
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color.Black,
                                    Color(0x59000000),
                                    Color(0x3F000000),
                                    Color(0x21000000),
                                )
                            }

                            drawContent()

                            drawRect(
                                brush = Brush.verticalGradient(colors),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }*/
                    /*.scrollable(state = rememberScrollableState {
                        enableLyricScroll.value = false
                        lastClickTime.longValue =
                            TimeUtils.getNowMills()
                        it
                    }, orientation = Orientation.Vertical)*/
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        onBackClick()
                    }
                    .nestedScroll(nestedScrollConnection)
                    .onSizeChanged {
                        if (height.intValue == 0 && it.height != 0) {
                            height.intValue = it.height
                            //println("计算歌词视图高度：${height.intValue}")
                        }
                    }
            ) {
                //println("重组：歌词列表")
                blankSpacer()
                itemsIndexed(
                    items = lrcEntries,
                    key = { _, lines -> lines }/*,
                contentType = { _, _ -> "YosLyricView_item" }*/
                ) { index, lines ->
                    val isCurrent = remember(lines) {
                        derivedStateOf {
                            index == currentLyricIndex.intValue
                        }
                    }

                    val isTop = remember(lines) {
                        derivedStateOf {
                            index == (currentLyricIndex.intValue - 1)
                        }
                    }

                    val showStateAnimation = remember(index) {
                        derivedStateOf {
                            (currentLyricIndex.intValue in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 } && currentLyricIndex.intValue >= 0) && enableLyricScroll.value
                        }
                    }

                    val isLyricEmpty = rememberSaveable(lines) {
                        mutableStateOf(
                            lines.all { it.second.isBlank() }
                        )
                    }

                    key(lines) {
                        val translation = remember(index) {
                            val str = lines.last().second
                            str.ifBlank { null }
                        }

                        val blur = remember(index) {
                            derivedStateOf {
                                if (!showStateAnimation.value || index == currentLyricIndex.intValue || !blurLambda() || !supportBlur) {
                                    0f
                                } else {
                                    (abs(index - currentLyricIndex.intValue) * 2.5f).coerceAtMost(
                                        8f
                                    )
                                }
                            }
                        }

                        val otherSide = remember(index) {
                            otherSideForLines.getOrElse(index) { false }
                        }

                        val thisWordSyncedWords = remember(index) {
                            derivedStateOf {
                                if (MediaViewModelObject.hasWordSyncedLyrics.value) {
                                    val syncedLines = MediaViewModelObject.wordSyncedLines.value
                                    if (index < syncedLines.size) {
                                        syncedLines[index].words.map { word ->
                                            Triple(word.startTimeMs.toFloat(), word.endTimeMs.toFloat(), word.isBackground)
                                        }
                                    } else emptyList()
                                } else emptyList()
                            }
                        }

                        YosWrapper {
                            LyricItem(
                                isCurrentLambda = {
                                    isCurrent.value
                                },
                                isTopLambda = {
                                    isTop.value
                                },
                                mainLyric = lines.dropLast(1),
                                translation,
                                translationLambda(),
                                //mainTextSize = uiConfig.mainTextSize,
                                subTextSize = uiConfig.subTextSize,
                                blur = { blur.value },
                                mainTextBasicColor,
                                subTextBasicColor,
                                otherSide = otherSide,
                                liveTimeLambda = liveTimeLambda,
                                measurer = measurer,
                                isLyricEmpty = { isLyricEmpty.value },
                                nextTime = {
                                    if (index + 1 > lrcEntries.size - 1) {
                                        0f
                                    } else {
                                        lrcEntries[(index + 1)].first().first
                                    }
                                },
                                wordSyncedWords = thisWordSyncedWords.value
                            ) {
                                Vibrator.doubleClick(context)
                                currentLyricIndex.intValue = index
                                mediaEvent.onSeek(lines.first().first.toInt())
                            }
                        }
                    }

                    key(index) {
                        YosWrapper {
                            /*//println(mainLyricSide.value+":"+mainLyricSide.value.isNotBlank())
                        if ((*//*(mainLyricSide.isBlank() && isCurrent.value && countdownPercent.value != 0f) || *//*mainLyricSide.value.isNotBlank())) {
                                val offset = animateDpAsState(
                                    targetValue = if (index <= currentLyricIndex.value || !showStateAnimation.value) 0.dp else 6.18.dp * (index - (nowFirst.value / 2)),
                                    animationSpec = spring(
                                        stiffness = 70f,
                                        dampingRatio = 0.8f,
                                        visibilityThreshold = 0.001.dp
                                    )
                                )
                                Spacer(modifier = Modifier.height(offset.value))
                            }*/

                            //val nowFirst = remember(index) { derivedStateOf { scrollState.firstVisibleItemIndex } }

                            /*val space = 16.dp*/ /*remember(index) {
                                    derivedStateOf {
                                        if (lines.isNotEmpty() && isCurrent.value) 5.dp else
                                    }
                                }*/

                            //val visibleItems = remember(index) { derivedStateOf { scrollState.layoutInfo.visibleItemsInfo } }

                            /*val nowVisible = remember(visibleItems) {
                        visibleItems.value.size
                    }*/

                            //val targetItem = visibleItems.value.find { it.index == currentLyricIndex.intValue /** 2*/ + 1 }


                            val show = remember(index) {
                                derivedStateOf { !isLyricEmpty.value || isCurrent.value }
                            }

                            val thisScrollDistance = if (targetItem.value != null) {
                                (scrollDistance.value / (visibleItems.value.size)).toDp()
                            } else {
                                0.dp
                            }

                            val thisTargetHeight = remember(index) {
                                mutableStateOf(space)
                            }

                            YosWrapper {
                                LaunchedEffect(currentLyricIndex.intValue) {
                                    if (visibleItems.value.isEmpty()) {
                                        //println(mainLyric.value.text+" 未设置")
                                        return@LaunchedEffect
                                    }
                                    //println(mainLyric.value.text+" "+(index >= currentLyricIndex.intValue && showStateAnimation.value && show.value))
                                    if (index >= currentLyricIndex.intValue - 1 && showStateAnimation.value && show.value) {
                                        val weight =
                                            (1f - ((index - (nowFirst.value)) / visibleItems.value.size))
                                        delay((550 * (1f - weight)).toLong())
                                        thisTargetHeight.value =
                                            (thisScrollDistance * weight).plus(space)
                                        delay(
                                            ((550 / 1.95f) * weight).toLong()
                                        )
                                        thisTargetHeight.value = space
                                    } else if (show.value) {
                                        thisTargetHeight.value = space
                                    } else {
                                        thisTargetHeight.value = 0.dp
                                    }
                                }
                            }

                            val offset = animateDpAsState(
                                targetValue = thisTargetHeight.value,
                                animationSpec = if (thisTargetHeight.value == 0.dp || thisTargetHeight.value == space) {
                                    spring(
                                        stiffness = 180f,
                                        dampingRatio = 0.8f,
                                        visibilityThreshold = 0.01.dp
                                    )
                                } else {
                                    spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 150f,
                                        visibilityThreshold = 0.01.dp
                                    )
                                }
                            )

                            YosWrapper {
                                Spacer(modifier = Modifier.height(offset.value))
                            }
                        }
                    }


                }
                blankSpacer()
                item("extra_blank") {
                    Spacer(modifier = Modifier.height(500.dp))
                }
            }
        }

        YosWrapper {
            //val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            LaunchedEffect(currentLyricIndex.intValue, translationLambda()) {
                try {
                    if (enableLyricScroll.value) {
                        /*visibleItems = scrollState.layoutInfo.visibleItemsInfo
                        targetItem =
                            visibleItems.find { it.index == currentLyricIndex.intValue */
                        /** 2*/
                        /** 2*//* + 1 }*/
                        if (
                            try {
                                if (currentLyricIndex.intValue - 1 < 0) false
                                else (
                                        (lrcEntries[(currentLyricIndex.intValue - 1)][1].second.isBlank())
                                        /*&&
                                        (lrcEntries[(currentLyricIndex.intValue).coerceAtLeast(
                                            0
                                        )].first().first - lrcEntries[(currentLyricIndex.intValue - 1)].first().first > 900f)*/)
                                // 这里有一个特殊的更改，因为AppleMusic歌词转过来会有两个连续一样的时间轴，在LrcFactory有更改，下面的那个900不用管
                                // 已经作了规范处理

                            } catch (_: Exception) {
                                false
                            }
                        ) {
                            return@LaunchedEffect
                        }

                        if (targetItem.value != null /*|| lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)*/) {
                            /*currentOffset.value = targetItem.value?.offset?:targetOffset.toInt()
                            scrollDistance.value = currentOffset - targetOffset*/
                            scrollState.animateScrollBy(
                                scrollDistance.value,
                                /*animationSpec = tween(
                                    durationMillis = abs(0.5 * currentOffset).toInt().coerceAtLeast(540)
                                        .coerceAtMost(1200),
                                    delayMillis = 0,
                                    easing = yosEasing
                                )*/
                                animationSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = 150f,
                                    visibilityThreshold = 0.01f,
                                )
                            )
                        } else {
                            scrollState.animateScrollToItem(
                                index = (currentLyricIndex.intValue
                                        /** 2*/
                                        /** 2*/
                                        + 1).coerceAtLeast(0),
                                scrollOffset = -targetOffset.toInt()
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        /*YosWrapper {
            LaunchedEffect(Unit) {
                while (true) {
                    val liveTime = liveTimeLambda()
                    val nextIndex = lrcEntries.indexOfFirst { line ->
                        line.first().first > liveTime
                    }

                    if (nextIndex != -1 && nextIndex - 1 != currentLyricIndex.intValue) {
                        currentLyricIndex.intValue = nextIndex - 1
                    } else if (nextIndex == -1 && currentLyricIndex.intValue != lrcEntries.size - 1) {
                        currentLyricIndex.intValue = lrcEntries.size - 1
                    }

                    delay(100)
                }
            }
        }*/

        YosWrapper {
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

    }
}

/*@Composable
fun Dp.toPx(): Float {
    val density = LocalDensity.current
    return this.value * density.density
}*/

@Composable
fun Float.toDp(): Dp {
    val density = LocalDensity.current
    return (this / density.density).dp
}

@Composable
private fun LazyItemScope.Line(
    lines: List<Pair<Float, String>>,
    style: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
    viewAlign: Alignment.Horizontal,
    draw: CacheDrawScope.(Constraints, TextLayoutResult) -> DrawResult
) =
    YosWrapper {
        /*val styledString = remember(style, lines) {
            buildAnnotatedString {
                lines.forEachIndexed { _, char ->
                    if (char.second.isNotEmpty()) {
                        withStyle(style.toSpanStyle()) {
                            append(char.second)
                        }
                    }
                }
            }
        }*/

        val styledString = remember(style, lines) {
            buildString {
                lines.forEach { char ->
                    if (char.second.isNotEmpty()) {
                        append(char.second)
                    }
                }
            }
        }


        Column(
            horizontalAlignment = viewAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            SubcomposeLayout(modifier = modifier) { constraints ->

                val measureResult = measurer.measure(
                    text = styledString,
                    style = style,
                    constraints = Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth,
                    ),
                    layoutDirection = LayoutDirection.Ltr
                )

                val height = (style.lineHeight * measureResult.lineCount)

                val width = runCatching {
                    (0 until measureResult.lineCount).maxOf {
                        measureResult.getBoundingBox(
                            measureResult.getLineEnd(it, visibleEnd = true) - 1
                        ).right
                    }
                }.getOrDefault(constraints.maxWidth.toFloat())

                val content = subcompose(lines) {
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .drawWithCache { draw(constraints, measureResult) }
                    )
                }.first()


                val placeable = content.measure(
                    Constraints.fixed(width.roundToInt(), height.roundToPx())
                )

                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }

                /*layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }*/
            }
        }
    }

/*@Composable
private fun LazyItemScope.Line(
    lines: List<Pair<Float, String>>,
    style: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
    viewAlign: Alignment.Horizontal,
    draw: CacheDrawScope.(Constraints, TextLayoutResult) -> DrawResult
) =
    YosWrapper {
        val styledString = remember(style, lines) {
            buildString {
                lines.forEach { char ->
                    if (char.second.isNotEmpty()) {
                        append(char.second)
                    }
                }
            }
        }

        Column(
            modifier = modifier,
            horizontalAlignment = viewAlign
        ) {
            Layout(
                content = {
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val constraints = Constraints(
                                    minWidth = 0,
                                    maxWidth = size.width.toInt()
                                )
                                val measureResult = measurer.measure(
                                    text = styledString,
                                    style = style,
                                    constraints = constraints
                                )
                                draw(constraints, measureResult)
                            }
                    )
                }
            ) { measurables, constraints ->

                val measureResult = measurer.measure(
                    text = styledString,
                    style = style,
                    constraints = Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth
                    )
                )

                // 确保高度计算正确，包含所有文本行
                val height = measureResult.size.height

                val width = runCatching {
                    (0 until measureResult.lineCount).maxOf {
                        measureResult.getBoundingBox(
                            measureResult.getLineEnd(it, visibleEnd = true) - 1
                        ).right
                    }
                }.getOrDefault(constraints.maxWidth.toFloat()).roundToInt()

                val placeable = measurables.first().measure(
                    Constraints.fixed(width, height)
                )

                layout(width, height) {
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }*/

val easing: Easing = EaseInOutQuad

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LazyItemScope.LyricItem(
    isCurrentLambda: () -> Boolean,
    isTopLambda: () -> Boolean,
    mainLyric: List<Pair<Float, String>>,
    translation: String?,
    showTranslation: Boolean,
    //mainTextSize: Int,
    subTextSize: Int,
    blur: () -> Float,
    /*showBlur: Boolean,*/
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
    println("重组：歌词 $mainLyric")

    val viewAlign = if (otherSide) Alignment.End else Alignment.Start

    val focusedColor = Color(0xFFFFFFFF)
    val unfocusedColor = Color(0x2EFFFFFF)
    //Color(0x33FFFFFF)

    //val focusedSolidBrush = SolidColor(focusedColor)

    val unfocusedSolidBrush = SolidColor(unfocusedColor)

    val isNotOneByOne = rememberSaveable(mainLyric) {
        mutableStateOf(
            mainLyric.all { it.first == mainLyric.firstOrNull()?.first }
        )

    }

    val liveTime = remember(mainLyric) { mutableIntStateOf(liveTimeLambda()) }

    YosWrapper {
        val launch = remember(mainLyric) {
            derivedStateOf {
                isLyricEmpty() || !isNotOneByOne.value
            }
        }
        if (launch.value) {
            LaunchedEffect(Unit) {
                while (true) {
                    withContext(Dispatchers.Main) {
                        liveTime.intValue = liveTimeLambda()
                    }
                    delay(50L)
                }
            }
        }
    }

    YosWrapper {
        Column(
            Modifier
                .padding(horizontal = 9.dp),
            horizontalAlignment = viewAlign
        ) {
            val otherSideAnimate = if (otherSide) {
                TransformOrigin(1f, 0.25f)
            } else {
                TransformOrigin(0f, 0.25f)
            }
            //println("重组：倒计时 "+ mainLyric.isBlank()+ " "+ isCurrentLambda() + " " + (progress() != 0f))

            val otherSideTransformOrigin =
                if (otherSide) TransformOrigin(
                    1f,
                    0.5f
                ) else TransformOrigin(
                    0f,
                    0.5f
                )

            /*val otherSideThisLine = remember(mainLyric) {
                mainLyric.last().second.endsWith(":") || mainLyric.last().second.endsWith(
                    "："
                )
            }*/

            val springSpecWithDelay: AnimationSpec<Float> = remember(mainLyric) {
                spring(
                    dampingRatio = 0.8f,
                    stiffness = 180f,
                    visibilityThreshold = 0.001f
                )
            }

            val springSpecWithoutDelay: AnimationSpec<Float> = remember(mainLyric) {
                spring(
                    dampingRatio = 0.8f,
                    stiffness = 200f,
                    visibilityThreshold = 0.001f
                )
            }

            val scale = animateFloatAsState(
                targetValue = if (isCurrentLambda()) 1.005f else 1f,
                animationSpec = if (isCurrentLambda()) springSpecWithDelay else springSpecWithoutDelay
            )

            /*val blurValue = remember(mainLyric) {
                derivedStateOf {
                    if (blur() == 0f || !showBlur) 0f else blur()
                }
            }*/

            val cardPadding = if (otherSide) {
                Modifier.padding(start = 28.dp)
            } else {
                Modifier.padding(end = 28.dp)
            }

            if (isLyricEmpty()) {
                Column(Modifier.animateContentSize()) {
                    val percent = remember(mainLyric) {
                        derivedStateOf {
                            val m = mainLyric.first().first
                            /*(if ((nextTime() - m) < 900f) {
                                0f
                            } else {
                                */((liveTime.intValue - m).coerceAtLeast(0f) / (nextTime() - m))
                            /*})*/.coerceAtMost(1f)
                        }
                    }
                    val show = remember(mainLyric) {
                        derivedStateOf { (isLyricEmpty() && isCurrentLambda() && percent.value != 0f) }
                    }
                    AnimatedVisibility(
                        show.value,
                        enter = fadeIn(animationSpec = tween(
                            durationMillis = 550,
                            easing = yosEasing,
                            delayMillis = 300
                        )) + scaleIn(
                            initialScale = 0.85f,
                            transformOrigin = otherSideAnimate,
                            animationSpec = tween(
                                durationMillis = 550,
                                easing = yosEasing,
                                delayMillis = 300
                            )
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.85f,
                            transformOrigin = otherSideAnimate,
                            animationSpec = tween(
                                durationMillis = 340,
                                easing = yosEasing
                            )
                        )
                    ) {
                        YosWrapper {
                            LyricCard(
                                { scale.value },
                                cardPadding,
                                otherSideTransformOrigin,
                                viewAlign,
                                //{ otherSideThisLine },
                                //onClick
                            ) {

                                Column(
                                    Modifier
                                        .padding(start = 20.dp, end = 20.dp)
                                        .padding(top = 8.dp, bottom = 10.dp),
                                    horizontalAlignment = viewAlign
                                ) {
                                    CountdownAnimation(
                                        { percent.value },
                                        colorLambda = { mainTextBasicColor })
                                }

                            }
                        }
                    }
                }
            } else {
                YosWrapper {
                    LyricCard(
                        { scale.value },
                        cardPadding,
                        otherSideTransformOrigin,
                        viewAlign,
                        //{ otherSideThisLine },
                        //onClick
                    ) {

                        val blurValue = animateDpAsState(
                            targetValue = blur().dp,
                            animationSpec = tween(
                                durationMillis = 0,
                                delayMillis = if (isTopLambda()) 260 else 0
                            )
                        )

                        val blurModifier = remember(mainLyric) {
                            derivedStateOf {
                                val thisBlur = blur()
                                if (thisBlur == 0f) {
                                    Modifier
                                } else {
                                    Modifier.blur(
                                        blurValue.value,
                                        /*thisBlur.dp*/
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                    )
                                }
                            }
                        }

                        YosWrapper {
                            Column(
                                Modifier
                                    .then(blurModifier.value)
                                    .fillMaxWidth(),
                                horizontalAlignment = viewAlign
                            ) {
                                val textAlign = if (otherSide) TextAlign.End else TextAlign.Start

                                val alphaSpringSpecWithDelay: AnimationSpec<Float> =
                                    remember(mainLyric) {
                                        spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 150f,
                                            visibilityThreshold = 0.001f
                                        )
                                    }

                                val alphaSpringSpecWithoutDelay: AnimationSpec<Float> =
                                    remember(mainLyric) {
                                        spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 180f,
                                            visibilityThreshold = 0.001f
                                        )
                                    }

                                YosWrapper {
                                    val thisAlphaAnimated = animateFloatAsState(
                                        targetValue = if (isCurrentLambda()) /*0.78f*/ 1f else 0.14f,
                                        animationSpec = if (isCurrentLambda()) alphaSpringSpecWithDelay else alphaSpringSpecWithoutDelay
                                    )

                                    val thisAlpha = remember(mainLyric) {
                                        derivedStateOf {
                                            if (isNotOneByOne.value) {
                                                thisAlphaAnimated.value
                                            } else {
                                                1f
                                            }
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
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp
                                                )
                                            }
                                        }
                                    }

                                    val showHighLight = remember(mainLyric) {
                                        derivedStateOf {
                                            if (isNotOneByOne.value) {
                                                true
                                            } else {
                                                liveTime.intValue >= mainLyric[mainLyric.size - (if (translation != null) 3 else 1)].first
                                            }
                                        }
                                    }

                                    val charStyle = if (otherSide) mainTextStyle().copy(
                                        textAlign = TextAlign.End
                                    ) else mainTextStyle()

                                    Line(
                                        lines = mainLyric,
                                        style = if (otherSide) mainTextStyle().copy(textAlign = TextAlign.End) else mainTextStyle(),
                                        measurer = measurer,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                this.alpha = thisAlpha.value
                                                compositingStrategy =
                                                    CompositingStrategy.ModulateAlpha
                                            }
                                            .padding(vertical = 4.dp)
                                            .then(otherSidePadding.value)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                onClick()
                                            },
                                        viewAlign = viewAlign
                                    ) { parentConstraints, measureResult ->


                                        if (isNotOneByOne.value) {
                                            // 当不是逐字时
                                            // 不论情况全高亮
                                            return@Line onDrawWithContent {
                                                drawText(
                                                    textLayoutResult = measureResult,
                                                    color = focusedColor
                                                )
                                            }
                                        }

                                        if (!isCurrentLambda()) {
                                            // 是逐字 但不是当前行
                                            // 是否已播放完？
                                            if (showHighLight.value) {
                                                // 高亮
                                                println("高亮：$mainLyric")
                                                return@Line onDrawWithContent {
                                                    drawText(
                                                        textLayoutResult = measureResult,
                                                        color = focusedColor,
                                                        topLeft = Offset(0F, -4F)
                                                    )
                                                }
                                            } else {
                                                // 不高亮
                                                return@Line onDrawWithContent {
                                                    drawText(
                                                        textLayoutResult = measureResult,
                                                        color = unfocusedColor
                                                    )
                                                }
                                            }
                                        }

                                        // 以下为逐字处理

                                        var sum = 0
                                        var lastTime = 0f

                                        val wordsToDraw = arrayListOf<DrawWord>()

                                        var averageTime = 0f

                                        lastTime = mainLyric.first().first

                                        mainLyric.fastForEachIndexed { wordIndex, word ->

                                            // 旧的逐字处理逻辑
                                            /*val process = processWords(word.second)

                                    process.fastForEach { word ->

                                        // 非逐字转移到上面处理
                                        *//*if (isNotOneByOne.value) {
                                                word.split("").fastForEach { charWord ->
                                                    wordsToDraw += DrawWord(
                                                        time = word.first,
                                                        word = charWord,
                                                        layout = measurer.measure(
                                                            text = charWord,
                                                            style = mainTextStyle(),
                                                            constraints = measureResult.layoutInput.constraints,
                                                            layoutDirection = if (viewAlign.value == Alignment.End) LayoutDirection.Rtl else LayoutDirection.Ltr
                                                        ),
                                                        topLeft = measureResult.getBoundingBox(sum.coerceAtMost(
                                                            mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0)).topLeft,
                                                        brush = { _, _ ->
                                                               focusedSolidBrush
                                                        }
                                                    ).also {
                                                        sum += charWord.length
                                                    }
                                                }

                                                return@fastForEach
                                            }*//*
                                        }*/

                                            //println(word.second + "：" + sum.coerceAtMost(mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0) + "，共 "+ mainLyric.sumOf { it.second.length })

                                            // 新逻辑

                                            val thisWord = word.second

                                            if (thisWord.isEmpty()) {
                                                return@fastForEachIndexed
                                            }

                                            val wordStartTime = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                                wordSyncedWords[wordIndex].first
                                            } else {
                                                lastTime
                                            }
                                            val wordEndTime = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                                wordSyncedWords[wordIndex].second
                                            } else {
                                                word.first
                                            }
                                            val wordIsBackground = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                                wordSyncedWords[wordIndex].third
                                            } else {
                                                false
                                            }
                                            averageTime = (wordEndTime - wordStartTime) / thisWord.length.coerceAtLeast(1)

                                            val thisWordGroupLastTime = if (wordIndex - 1 < 0) {
                                                mainLyric.first().first
                                            } else {
                                                mainLyric[(wordIndex - 1)].first
                                            }
                                            val groupPercent =
                                                if ((wordEndTime - thisWordGroupLastTime) == 0f) {
                                                    0f
                                                } else {
                                                    ((liveTime.intValue - thisWordGroupLastTime).coerceAtLeast(
                                                        0f
                                                    ) / (wordEndTime - thisWordGroupLastTime)).coerceIn(
                                                        0f,
                                                        1f
                                                    )
                                                }
                                            val easedPercent = easing.transform(groupPercent.coerceIn(
                                                0f,
                                                1f
                                            ))
                                            val topLeftWeight = 4 * easedPercent

                                            thisWord.forEach { char ->

                                                //println("$char：$lastTime to ${lastTime + averageTime}")

                                                val charWord = char.toString()

                                                val layout = measurer.measure(
                                                    text = charWord,
                                                    style = charStyle,
                                                    constraints = measureResult.layoutInput.constraints
                                                )

                                                val thisWordLastTime = lastTime
                                                val thisWordAverageTime = averageTime
                                                val thisWordStartTime = wordStartTime
                                                val thisWordEndTime = wordEndTime
                                                val thisWordIsBackground = wordIsBackground

                                                val currentPercent = if (wordSyncedWords.isNotEmpty() && wordIndex < wordSyncedWords.size) {
                                                    val wordDur = (thisWordEndTime - thisWordStartTime).coerceAtLeast(1f)
                                                    ((liveTime.intValue - thisWordStartTime).coerceIn(0f, wordDur) / wordDur)
                                                } else {
                                                    ((liveTime.intValue - thisWordLastTime) / thisWordAverageTime)
                                                }

                                                wordsToDraw += DrawWord(
                                                    time = lastTime + averageTime,
                                                    word = charWord,
                                                    layout = layout,
                                                    topLeft = measureResult.getBoundingBox(sum.coerceAtMost(
                                                        mainLyric.sumOf { it.second.length } - 1)
                                                        .coerceAtLeast(0)).topLeft.minus(
                                                            Offset(
                                                                0F,
                                                                topLeftWeight
                                                            )
                                                            ),
                                                    brush = { px, percent ->
                                                        if (thisWord == " ") {
                                                            return@DrawWord unfocusedSolidBrush
                                                        }

                                                        if (thisWordIsBackground && (percent < 0f || percent > 1f)) {
                                                            return@DrawWord SolidColor(Color.Transparent)
                                                        }

                                                        val isActiveWord = percent in 0f..1f

                                                        val beforeColor = if (percent <= -0.5f) {
                                                            if (thisWordIsBackground) Color.Transparent else unfocusedColor
                                                        } else {
                                                            if (isActiveWord) Color.White else focusedColor
                                                        }

                                                        val afterColor = if (percent >= 1f) {
                                                            if (thisWordIsBackground) Color.Transparent else unfocusedColor
                                                        } else {
                                                            if (isActiveWord) Color.White.copy(alpha = 0.9f) else unfocusedColor
                                                        }

                                                        val glowSize = if (isActiveWord) (px * 2f).coerceIn(0f, 0.3f) else px

                                                        Brush.horizontalGradient(
                                                            0f to beforeColor,
                                                            (percent - glowSize).coerceIn(
                                                                0f,
                                                                1f
                                                            ) to beforeColor,
                                                            (percent + glowSize).coerceIn(
                                                                0f,
                                                                1f
                                                            ) to afterColor,
                                                            1f to afterColor
                                                        )
                                                    },
                                                    percent = {
                                                        if (thisWord == " ") {
                                                            return@DrawWord 0f
                                                        }
                                                        currentPercent
                                                    }
                                                ).also {
                                                    sum += charWord.length
                                                    lastTime += averageTime
                                                }
                                            }
                                        }

                                        onDrawBehind {
                                            wordsToDraw.fastForEach { l ->
                                                drawText(
                                                    textLayoutResult = l.layout,
                                                    topLeft = l.topLeft,
                                                    brush = l.brush(
                                                        0.3f,
                                                        l.percent()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                YosWrapper {
                                    AnimatedVisibility(showTranslation && translation != null) {
                                        translation?.let {
                                            val translationAlpha = animateFloatAsState(
                                                targetValue = if (isCurrentLambda()) 0.5f else 0.14f,
                                                animationSpec = if (isCurrentLambda()) alphaSpringSpecWithDelay else alphaSpringSpecWithoutDelay
                                            )

                                            val translationOtherSidePadding = if (otherSide) {
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp
                                                )
                                            } else {
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp
                                                )
                                            }

                                            Text(
                                                text = it,
                                                fontSize = subTextSize.sp,
                                                color = subTextBasicColor,
                                                fontWeight = FontWeight.Normal,
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        this.alpha =
                                                            translationAlpha.value
                                                        compositingStrategy =
                                                            CompositingStrategy.ModulateAlpha
                                                    }
                                                    .then(translationOtherSidePadding)
                                                    .padding(top = 5.dp),
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
            }
        }
    }
}

@Composable
private fun LyricCard(
    scale: () -> Float,
    cardPadding: Modifier,
    otherSideTransformOrigin: TransformOrigin,
    viewAlign: Alignment.Horizontal,
    //otherSideThisLine: () -> Boolean,
    //onClick: () -> Unit,
    content: @Composable () -> Unit,
) =
    YosWrapper {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    //compositingStrategy = CompositingStrategy.ModulateAlpha
                    val scaleValue = scale()
                    scaleX = scaleValue
                    scaleY = scaleValue
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
            //compositingStrategy = CompositingStrategy.Offscreen
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
                /*val alpha = animateFloatAsState(
                    targetValue = if (progress() >= i / 4f) min(
                        1f,
                        (progress() - (i - 1) / 4f) * 4
                    ) else 0f,
                    animationSpec = tween(
                        if (progress() > 0) (progress() * 1200).toInt() else 1200,
                        easing = LinearEasing
                    )
                )*/

                val average = 1f / 3f
                val beforePadding = (i-1) * average
                val thisPercent = (progress() - beforePadding)  / ((i * average) - beforePadding)
                val alpha = 0.2f + (0.8f * thisPercent).coerceIn(0f, 0.8f)

                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            colorLambda().copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}


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
            LineBreak.Strictness.Default,
            LineBreak.WordBreak.Default
        )
    )
}

/*val BackgroundTextStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.Bold
).copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)*/

@Stable
private data class DrawWord(
    val time: Float,
    val word: String,
    val layout: TextLayoutResult,
    val topLeft: Offset,
    val brush: (px: Float, percent: Float) -> Brush,
    val percent: () -> Float
)

/*
fun processWords(input: String): List<String> {
    val result = mutableListOf<String>()
    var word = ""
    for (char in input) {
        if (char == ' ') {
            if (word.isNotEmpty()) {
                result.add(word)
                word = ""
            }
            result.add(" ")
        } else {
            word += char
        }
    }
    if (word.isNotEmpty()) {
        result.add(word)
    }
    return result
}*/
