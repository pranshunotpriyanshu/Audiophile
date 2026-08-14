package com.pryvn.audiophile.ui.widgets

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.code.utils.lrc.YosMediaEvent
import com.pryvn.audiophile.code.utils.lrc.YosUIConfig
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.code.utils.lrc.BACKGROUND_WORD_MARKER
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.MainViewModelObject
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

val yosEasing = CubicBezierEasing(0.75f, 0.0f, 0.25f, 1.0f)

// V2（C:/CArchiveTune LyricsV2.kt）默认值：非活动词的底字透明度（inactiveAlpha = 0.35）
private const val v2InactiveAlpha = 0.35f

// ===== V2 移植（C:/CArchiveTune LyricsV2.kt isRtlText）：行文本 RTL 检测 =====
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

// ===== V2 移植（C:/CArchiveTune LyricsTextWrapping.kt toLyricsWrappingUnits）：按字素切分行文本 =====
private fun String.toLyricsWrappingUnits(): List<String> {
    if (isEmpty()) return emptyList()
    val units = mutableListOf<String>()
    val currentWord = StringBuilder()
    val characterIterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    characterIterator.setText(this)
    fun flushCurrentWord() {
        if (currentWord.isNotEmpty()) {
            units += currentWord.toString()
            currentWord.clear()
        }
    }
    var start = characterIterator.first()
    var end = characterIterator.next()
    while (end != BreakIterator.DONE) {
        val grapheme = substring(start, end)
        val codePoint = grapheme.codePointAt(0)
        when {
            grapheme.all(Char::isWhitespace) -> {
                currentWord.append(grapheme)
                flushCurrentWord()
            }

            codePoint.isCjkCodePoint() -> {
                flushCurrentWord()
                units += grapheme
            }

            else -> {
                currentWord.append(grapheme)
            }
        }
        start = end
        end = characterIterator.next()
    }
    flushCurrentWord()
    return units
}

private fun Int.isCjkCodePoint(): Boolean =
    when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true

        else -> false
    }

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
    lineEndTimesLambda: () -> List<Float> = { emptyList() },
    lineTransliterationsLambda: () -> List<String?> = { emptyList() },
    lineSubtitlesLambda: () -> List<String?> = { emptyList() },
    isTtmlLyricsLambda: () -> Boolean = { false },
    liveTimeLambda: () -> Int,
    mediaEvent: YosMediaEvent,
    translationLambda: () -> Boolean = { true },
    blurLambda: () -> Boolean = { false },
    //animationConfig: YosAnimationConfig = YosAnimationConfig(),
    uiConfig: YosUIConfig = YosUIConfig(),
    weightLambda: () -> Boolean,
    modifier: Modifier,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val mainTextBasicColor = Color(uiConfig.mainTextBasicColor)
    val subTextBasicColor = Color(uiConfig.subTextBasicColor)
    //Color(0xFF919191)
    val otherSideForLines = MediaViewModelObject.otherSideForLines

    val lrcEntries = lrcEntriesLambda()
    val lineEndTimes = lineEndTimesLambda()
    val lineTransliterations = lineTransliterationsLambda()
    val lineSubtitles = lineSubtitlesLambda()
    val isTtmlLyrics = isTtmlLyricsLambda()

    //val thisLyricLines = MediaViewModelObject.mainLyricLines
    if (lrcEntries.isEmpty() || otherSideForLines.isEmpty() /*|| thisLyricLines.isEmpty()*/) {
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
            Text(
                text = uiConfig.noLrcText,
                fontSize = 18.sp,
                color = Color(uiConfig.mainTextBasicColor)
            )
        }
    } else {
        val scrollState = rememberLazyListState()
        val currentLyricIndex =
            remember("YosLyricView_currentLyricIndex") { MainViewModelObject.syncLyricIndex }
        val ttmlLiveTime = remember("YosLyricView_ttmlLiveTime") {
            mutableIntStateOf(liveTimeLambda())
        }

        YosWrapper {
            LaunchedEffect(isTtmlLyrics, lrcEntries) {
                while (isTtmlLyrics) {
                    ttmlLiveTime.intValue = liveTimeLambda()
                    delay(10L)
                }
            }
        }

        val focusedLyricIndices = remember(
            "YosLyricView_focusedLyricIndices",
            isTtmlLyrics,
            lrcEntries,
            lineEndTimes
        ) {
            derivedStateOf {
                if (!isTtmlLyrics) {
                    return@derivedStateOf listOf(currentLyricIndex.intValue)
                }

                val liveTime = ttmlLiveTime.intValue
                val activeIndices = lrcEntries.mapIndexedNotNull { index, line ->
                    val lineStart = line.firstOrNull()?.first ?: return@mapIndexedNotNull null
                    val lineEnd = lineEndTimes.getOrNull(index)
                        ?: lrcEntries.getOrNull(index + 1)?.firstOrNull()?.first
                        ?: lineStart

                    if (liveTime >= lineStart && liveTime < lineEnd.coerceAtLeast(lineStart + 1f)) {
                        index
                    } else {
                        null
                    }
                }

                activeIndices.ifEmpty {
                    if (currentLyricIndex.intValue >= 0) listOf(currentLyricIndex.intValue) else emptyList()
                }
            }
        }

        val focusedLyricAnchorIndex = remember(
            "YosLyricView_focusedLyricAnchorIndex",
            focusedLyricIndices
        ) {
            derivedStateOf {
                focusedLyricIndices.value.firstOrNull() ?: currentLyricIndex.intValue
            }
        }
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
                        .height((uiConfig.blankHeight * SettingsLibrary.LyricFontSize / 30.5f).roundToInt().dp)
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
                    it.index == focusedLyricAnchorIndex.value + 1
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
                    if (source != NestedScrollSource.SideEffect && source != NestedScrollSource.Relocate) {
                        isUserScrolling.value = true
                    }
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
                    delay(1600)
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
                            focusedLyricIndices.value.contains(index)
                        }
                    }

                    val isTop = remember(lines) {
                        derivedStateOf {
                            index == (focusedLyricAnchorIndex.value - 1)
                        }
                    }

                    val showStateAnimation = remember(index) {
                        derivedStateOf {
                            (focusedLyricAnchorIndex.value in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 } && focusedLyricAnchorIndex.value >= 0) && enableLyricScroll.value
                        }
                    }

                    val isLyricEmpty = rememberSaveable(lines) {
                        mutableStateOf(
                            lines.all { it.second.isBlank() }
                        )
                    }

                    key(lines) {
                        val translation = remember(
                            index,
                            lines,
                            isTtmlLyrics,
                            translationLambda(),
                            lineTransliterations,
                            lineSubtitles
                        ) {
                            if (isTtmlLyrics) {
                                val secondaryText = if (translationLambda()) {
                                    lineSubtitles.getOrNull(index)
                                } else {
                                    lineTransliterations.getOrNull(index)
                                }
                                secondaryText?.ifBlank { null }
                            } else {
                                val str = lines.last().second
                                str.ifBlank { null }
                            }
                        }

                        val blur = remember(index) {
                            derivedStateOf {
                                if (!showStateAnimation.value || focusedLyricIndices.value.contains(index) || !blurLambda() || !supportBlur) {
                                    0f
                                } else {
                                    (abs(index - focusedLyricAnchorIndex.value) * 2.5f).coerceAtMost(
                                        8f
                                    )
                                }
                            }
                        }

                        val otherSide = remember(index) {
                            otherSideForLines.getOrElse(index) { false }
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
                                if (isTtmlLyrics) translation != null else translationLambda(),
                                //mainTextSize = uiConfig.mainTextSize,
                                subTextSize = (uiConfig.subTextSize * SettingsLibrary.LyricFontSize / 30.5f).roundToInt().coerceAtLeast(12),
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
                                }
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
                                LaunchedEffect(focusedLyricAnchorIndex.value) {
                                    if (visibleItems.value.isEmpty()) {
                                        //println(mainLyric.value.text+" 未设置")
                                        return@LaunchedEffect
                                    }
                                    //println(mainLyric.value.text+" "+(index >= focusedLyricAnchorIndex.value && showStateAnimation.value && show.value))
                                    if (index >= focusedLyricAnchorIndex.value - 1 && showStateAnimation.value && show.value) {
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
                                animationSpec = if (thisTargetHeight.value == 0.dp || thisTargetHeight.value == space/*16.dp || thisTargetHeight.value == 5.dp*/) {
                                    spring(
                                        stiffness = 105F,
                                        dampingRatio = /*0.85f*/ 1f,
                                        visibilityThreshold = 0.0001.dp
                                    )
                                    //tween(durationMillis = 510, easing = yosEasing)
                                } else {
                                    tween(
                                        durationMillis = 550,
                                        easing = yosEasing
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
            LaunchedEffect(focusedLyricAnchorIndex.value, translationLambda()) {
                try {
                    if (enableLyricScroll.value) {
                        /*visibleItems = scrollState.layoutInfo.visibleItemsInfo
                        targetItem =
                            visibleItems.find { it.index == currentLyricIndex.intValue */
                        /** 2*/
                        /** 2*//* + 1 }*/
                        if (
                            try {
                                if (focusedLyricAnchorIndex.value - 1 < 0) false
                                else (
                                        (lrcEntries[(focusedLyricAnchorIndex.value - 1)][1].second.isBlank())
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
                                animationSpec = tween(
                                    durationMillis = 550,
                                    //delayMillis = 15,
                                    easing = yosEasing
                                )
                                /*spring(
                                    stiffness = 105F,
                                    dampingRatio = 1f*//* 1f*//*
                                )*/
                            )
                        } else {
                            scrollState.animateScrollToItem(
                                index = (focusedLyricAnchorIndex.value
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
            //val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            LaunchedEffect(Unit) {
                /*if (!lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)) {
                    return@LaunchedEffect
                }*/
                try {
                    if (currentLyricIndex.intValue != -1) {
                        return@LaunchedEffect
                    }
                    val liveTime = liveTimeLambda()
                    val nextIndex = lrcEntries.indexOfFirst { line ->
                        line.first().first > liveTime
                    }

                    if (nextIndex != -1 && nextIndex - 1 != currentLyricIndex.intValue) {
                        scrollState.scrollToItem(
                            index = (nextIndex).coerceAtLeast(0),
                            scrollOffset = -targetOffset.toInt()
                        )
                        currentLyricIndex.intValue = nextIndex - 1
                    } else if (nextIndex == -1 && currentLyricIndex.intValue != lrcEntries.size - 1) {
                        scrollState.scrollToItem(
                            index = (lrcEntries.size).coerceAtLeast(0),
                            scrollOffset = -targetOffset.toInt()
                        )
                        currentLyricIndex.intValue = lrcEntries.size - 1
                    }
                } catch (_: Exception) {
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
    isRtl: Boolean = false,
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
                    layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
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
    isRtl: Boolean = false,
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
    onClick: () -> Unit
) {
    val mainStyle = mainTextStyle()
    val viewAlign = if (otherSide) Alignment.End else Alignment.Start

    val focusedColor = Color(0xFFFFFFFF)
    val unfocusedColor = Color(0x2EFFFFFF)
    //Color(0x33FFFFFF)

    //val focusedSolidBrush = SolidColor(focusedColor)

    val isNotOneByOne = rememberSaveable(mainLyric) {
        mutableStateOf(
            mainLyric.all { it.first == mainLyric.firstOrNull()?.first }
        )

    }

    val liveTime = remember(mainLyric) { mutableIntStateOf(liveTimeLambda()) }

    YosWrapper {
        val launch = remember(mainLyric) {
            derivedStateOf {
                isLyricEmpty() || !isNotOneByOne.value || (SettingsLibrary.LyricSmartWbw && isCurrentLambda())
            }
        }
        if (launch.value) {
            LaunchedEffect(Unit) {
                while (true) {
                    withContext(Dispatchers.Main) {
                        liveTime.intValue = liveTimeLambda()
                    }
                    delay(10L)
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

            val tweenSpecWithDelay: AnimationSpec<Float> = remember(mainLyric) {
                TweenSpec(
                    durationMillis = 270,
                    easing = yosEasing,
                    delay = /*45*/ /*115*/ 110
                )
            }

            val tweenSpecWithoutDelay: AnimationSpec<Float> = remember(mainLyric) {
                TweenSpec(durationMillis = /*270*/ 300, easing = yosEasing,delay = 45)
            }

            val scale = animateFloatAsState(
                targetValue = if (isCurrentLambda()) 1.005f else 1f,
                animationSpec = if (isCurrentLambda()) tweenSpecWithDelay else tweenSpecWithoutDelay
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
                        enter = fadeIn(animationSpec = TweenSpec(
                            durationMillis = 550,
                            easing = yosEasing,
                            delay = 300
                        )) + scaleIn(
                            initialScale = 0.85f,
                            transformOrigin = otherSideAnimate,
                            animationSpec = TweenSpec(
                                durationMillis = 550,
                                easing = yosEasing,
                                delay = 300
                            )
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.85f,
                            transformOrigin = otherSideAnimate,
                            animationSpec = TweenSpec(
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
                            blur().dp, SnapSpec(delay = if (isTopLambda()) 260 else 0)
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

                                val alphaTweenSpecWithDelay: AnimationSpec<Float> =
                                    remember(mainLyric) {
                                        TweenSpec(
                                            durationMillis = 350,
                                            easing = yosEasing,
                                            delay = 145
                                        )
                                    }

                                val alphaTweenSpecWithoutDelay: AnimationSpec<Float> =
                                    remember(mainLyric) {
                                        TweenSpec(
                                            durationMillis = 350,
                                            easing = yosEasing,
                                            delay = 80
                                        )
                                    }

                                YosWrapper {
                                    val thisAlphaAnimated = animateFloatAsState(
                                        targetValue = if (isCurrentLambda()) /*0.78f*/ 1f else 0.14f,
                                        animationSpec = if (isCurrentLambda()) alphaTweenSpecWithDelay else alphaTweenSpecWithoutDelay
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

                                    val showHighLight = remember(mainLyric, translation) {
                                        derivedStateOf {
                                            if (isNotOneByOne.value) {
                                                true
                                            } else {
                                                val highlightIndex = (mainLyric.size - if (translation != null && mainLyric.size >= 3) 3 else 1)
                                                    .coerceIn(0, mainLyric.lastIndex)
                                                liveTime.intValue >= mainLyric[highlightIndex].first
                                            }
                                        }
                                    }

                                    // ===== V2 AnimatedWordV2 上浮动画（真实 TweenSpec：进入 50ms / 衰减 350ms，FastOutSlowInEasing）=====
                                    // 每个条目携带词的“结束时间”；词开始时间 = 前一条目结束时间（连续边界），与 V2 的 word.startTime/endTime 语义一致
                                    val wordStates = remember(mainLyric) {
                                        buildList {
                                            var lastTime = mainLyric.firstOrNull()?.first ?: 0f
                                            mainLyric.forEach { w ->
                                                if (w.second.isEmpty()) {
                                                    return@forEach
                                                }
                                                val end = w.first
                                                if (w.second.trimEnd().isEmpty()) {
                                                    lastTime = end
                                                    return@forEach
                                                }
                                                add(lastTime to end)
                                                lastTime = end
                                            }
                                        }
                                    }
                                    val wordFloats = wordStates.map { (start, end) ->
                                        val now = liveTime.intValue.toFloat()
                                        val isActive = now >= start && now < end
                                        val progress = when {
                                            now >= end -> 1f
                                            now <= start -> 0f
                                            else -> ((now - start) / (end - start).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        }
                                        val sinProgress = sin(progress * PI).toFloat()
                                        animateFloatAsState(
                                            targetValue = if (isActive) -4f * sinProgress else 0f,
                                            animationSpec = tween(
                                                durationMillis = if (isActive) 50 else 350,
                                                easing = FastOutSlowInEasing
                                            ),
                                            label = "v2FloatOffset"
                                        ).value
                                    }

                                    // ===== V2 LyricsLineLrcBounce 移植（行同步歌词：激活时逐词级联弹跳）=====
                                    // 每个词两个 Animatable（scale/float），由真实 spring 驱动（与源实现相同的 spec）
                                    val bounceUnits = remember(mainLyric) {
                                        mainLyric.joinToString("") { it.second }.toLyricsWrappingUnits()
                                    }
                                    val bounceScales = remember(mainLyric) { List(bounceUnits.size) { Animatable(1f) } }
                                    val bounceFloats = remember(mainLyric) { List(bounceUnits.size) { Animatable(0f) } }
                                    LaunchedEffect(isCurrentLambda()) {
                                        if (!isCurrentLambda() || !isNotOneByOne.value || bounceUnits.isEmpty()) return@LaunchedEffect
                                        bounceUnits.indices.forEach { i ->
                                            launch {
                                                delay(i * 40L)
                                                try {
                                                    bounceScales[i].animateTo(
                                                        targetValue = 1f + 0.045f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        )
                                                    )
                                                    bounceScales[i].animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                } finally {
                                                    withContext(NonCancellable) { bounceScales[i].snapTo(1f) }
                                                }
                                            }
                                            launch {
                                                delay(i * 40L)
                                                try {
                                                    bounceFloats[i].animateTo(
                                                        targetValue = -5f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        )
                                                    )
                                                    bounceFloats[i].animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                } finally {
                                                    withContext(NonCancellable) { bounceFloats[i].snapTo(0f) }
                                                }
                                            }
                                        }
                                    }

                                    // 行级 RTL 检测（V2 isRtlText），用于逐字扫描方向翻转
                                    val lineText = remember(mainLyric) { mainLyric.joinToString("") { it.second } }
                                    val lineIsRtl = remember(lineText) { isRtlText(lineText) }

                                    Line(
                                        lines = mainLyric,
                                        style = if (otherSide) mainStyle.copy(textAlign = TextAlign.End) else mainStyle,
                                        measurer = measurer,
                                        isRtl = lineIsRtl,
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
                                            if (SettingsLibrary.LyricSmartWbw && isCurrentLambda()) {
                                                val lineStart = mainLyric.firstOrNull()?.first ?: 0f
                                                val lineDuration = (nextTime() - lineStart).coerceAtLeast(1f)
                                                val rawProgress = ((liveTime.intValue - lineStart - 1000f) / 2000f).coerceIn(0f, 1f)
                                                // Cubic ease: t²(3-2t)
                                                val progress = rawProgress * rawProgress * (3f - 2f * rawProgress)
                                                val edge = 0.06f
                                                val lead = 0.75f
                                                val trail = 1.06f
                                                return@Line onDrawWithContent {
                                                    drawText(
                                                        textLayoutResult = measureResult,
                                                        brush = Brush.horizontalGradient(
                                                            0f to focusedColor,
                                                            (progress * lead - edge).coerceIn(0f, 1f) to focusedColor,
                                                            (progress * lead + edge).coerceIn(0f, 1f) to unfocusedColor,
                                                            (progress * trail).coerceIn(0f, 1f) to unfocusedColor,
                                                            1f to unfocusedColor
                                                        )
                                                    )
                                                }
                                            }
                                            // LRC 弹跳（V2 LyricsLineLrcBounce 移植）：当前行激活时逐词级联 spring（真实 TweenSpec/Spring spec，非近似）
                                            // 词按整行 TextLayoutResult 的首尾包围盒绘制，行测量/换行/锚定不变
                                            val lrcBouncing = isCurrentLambda() &&
                                                (bounceScales.any { it.value != 1f } || bounceFloats.any { it.value != 0f })
                                            if (lrcBouncing) {
                                                val bounceStyle = if (otherSide) mainStyle.copy(textAlign = TextAlign.End) else mainStyle
                                                return@Line onDrawBehind {
                                                    var charOffset = 0
                                                    bounceUnits.forEachIndexed { unitIndex, unit ->
                                                        val scale = bounceScales[unitIndex].value
                                                        val float = bounceFloats[unitIndex].value
                                                        val box = runCatching {
                                                            val maxIdx = (mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0)
                                                            val firstBox = measureResult.getBoundingBox(charOffset.coerceAtMost(maxIdx))
                                                            val lastBox = measureResult.getBoundingBox(
                                                                (charOffset + unit.length - 1).coerceAtMost(maxIdx)
                                                            )
                                                            Rect(
                                                                left = minOf(firstBox.left, lastBox.left),
                                                                top = minOf(firstBox.top, lastBox.top),
                                                                right = maxOf(firstBox.right, lastBox.right),
                                                                bottom = maxOf(firstBox.bottom, lastBox.bottom)
                                                            )
                                                        }.getOrNull()
                                                        charOffset += unit.length
                                                        if (box == null) return@forEachIndexed
                                                        val layout = measurer.measure(
                                                            text = unit,
                                                            style = bounceStyle,
                                                            constraints = measureResult.layoutInput.constraints
                                                        )
                                                        withTransform({
                                                            translate(left = box.center.x, top = box.center.y + float)
                                                            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                                                            translate(left = -box.center.x, top = -box.center.y)
                                                        }) {
                                                            drawText(
                                                                textLayoutResult = layout,
                                                                topLeft = box.topLeft,
                                                                color = focusedColor
                                                            )
                                                        }
                                                    }
                                                }
                                            }
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

                                        // 以下为逐字处理（V2 AnimatedWordV2 移植：C:/CArchiveTune LyricsV2.kt）
                                        // 词起止 = 连续边界（前一条目结束时间 → 本条结束时间），与 V2 的 word.startTime/endTime 语义一致
                                        // 缩放/发光/液体扫描 与源实现逐项一致；上浮偏移由真实 TweenSpec（50ms/350ms，FastOutSlowInEasing）驱动
                                        // RTL 检测（V2 isRtlText）：扫描方向按行文本翻转
                                        val isRtl = lineIsRtl

                                        var sum = 0
                                        var lastTime = mainLyric.firstOrNull()?.first ?: 0f

                                        val wordsToDraw = arrayListOf<DrawWord>()

                                        mainLyric.fastForEachIndexed { _, word ->
                                            val thisWord = word.second
                                            if (thisWord.isEmpty()) {
                                                return@fastForEachIndexed
                                            }

                                            // 词起止 = 连续边界（前一条目结束时间 → 本条结束时间）
                                            val wordStartMs = lastTime
                                            val wordEndMs = word.first
                                            lastTime = wordEndMs

                                            // 背景和声词（TTML isBackground）：条目文本带不可见标记前缀，渲染更小更暗（V2 第二行效果）
                                            val isBackground = thisWord.startsWith(BACKGROUND_WORD_MARKER)
                                            val visibleText = (if (isBackground) thisWord.removePrefix(BACKGROUND_WORD_MARKER) else thisWord).trimEnd()
                                            val visibleLen = visibleText.length

                                            if (visibleLen == 0) {
                                                sum += thisWord.length
                                                return@fastForEachIndexed
                                            }

                                            // 标记字符不占宽度；词框从可见首字符开始
                                            val startIdx = sum + if (isBackground) 1 else 0
                                            val endIdx = startIdx + visibleLen
                                            sum += thisWord.length

                                            // 词框 = 整行 TextLayoutResult 中该词首尾字符包围盒（行测量/换行不变）
                                            val box = runCatching {
                                                val maxIdx = (mainLyric.sumOf { it.second.length } - 1).coerceAtLeast(0)
                                                val firstBox = measureResult.getBoundingBox(startIdx.coerceAtMost(maxIdx))
                                                val lastBox = measureResult.getBoundingBox((endIdx - 1).coerceAtMost(maxIdx))
                                                Rect(
                                                    left = minOf(firstBox.left, lastBox.left),
                                                    top = minOf(firstBox.top, lastBox.top),
                                                    right = maxOf(firstBox.right, lastBox.right),
                                                    bottom = maxOf(firstBox.bottom, lastBox.bottom)
                                                )
                                            }.getOrNull() ?: return@fastForEachIndexed

                                            val layout = measurer.measure(
                                                text = visibleText,
                                                style = if (otherSide) mainStyle.copy(
                                                    textAlign = TextAlign.End
                                                ) else mainStyle,
                                                constraints = measureResult.layoutInput.constraints
                                            )

                                            wordsToDraw += DrawWord(
                                                layout = layout,
                                                topLeft = box.topLeft,
                                                box = box,
                                                startMs = wordStartMs,
                                                endMs = wordEndMs,
                                                floatOffset = wordFloats.getOrNull(wordsToDraw.size) ?: 0f,
                                                isBackground = isBackground
                                            )
                                        }

                                        onDrawBehind {
                                            wordsToDraw.fastForEach { l ->
                                                val now = liveTime.intValue.toFloat()

                                                val isWordComplete = now >= l.endMs
                                                val isWordActive = now >= l.startMs && now < l.endMs

                                                // 完美线性进度 [0..1]，与每个词自身的起止时间对应（V2 原式）
                                                val progress = when {
                                                    isWordComplete -> 1f
                                                    now <= l.startMs -> 0f
                                                    else -> ((now - l.startMs) / (l.endMs - l.startMs).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                }

                                                // ── 缩放（源实现：sin 峰值在进度 50% 处）──
                                                val sinProgress = sin(progress * PI).toFloat()
                                                val wordScale = 1f + 0.015f * sinProgress

                                                // ── 发光（仅当前活动词；已完成/未开始 一律无发光）──
                                                val glowProgress = (progress * 2f).coerceAtMost(1f)
                                                val glowAlpha = if (isWordActive) glowProgress * 0.45f else 0f
                                                val glowRadius = if (isWordActive) glowProgress * 12f else 0f

                                                // ── 双层渲染：常显暗底 + 亮色填充叠加（修复“活动词不可见”）──
                                                val glowPad = 10.dp.toPx()
                                                val region = Rect(
                                                    l.box.left - glowPad,
                                                    l.box.top - glowPad,
                                                    l.box.right + glowPad,
                                                    l.box.bottom + glowPad
                                                )

                                                val densityValue = density
                                                // 背景和声词缩小（0.65×，V2 第二行字号）
                                                val bgScale = if (l.isBackground) 0.65f else 1f
                                                withTransform({
                                                    translate(
                                                        left = region.center.x,
                                                        top = region.center.y + l.floatOffset * densityValue
                                                    )
                                                    scale(
                                                        scaleX = wordScale * bgScale,
                                                        scaleY = wordScale * bgScale,
                                                        pivot = Offset.Zero
                                                    )
                                                    translate(left = -region.center.x, top = -region.center.y)
                                                }) {
                                                    // Layer 1：底字（始终可见、暗色；背景和声词再暗一档：inactiveAlpha*0.7 再乘整行 0.85）
                                                    drawText(
                                                        textLayoutResult = l.layout,
                                                        topLeft = l.topLeft,
                                                        color = focusedColor.copy(
                                                            alpha = if (l.isBackground) {
                                                                v2InactiveAlpha * 0.7f * 0.85f
                                                            } else {
                                                                v2InactiveAlpha
                                                            }
                                                        )
                                                    )

                                                    // Layer 2：填充叠加（已完成 / 活动中；本分支只处理当前行，过去行已在上方处理）
                                                    // saveLayer 独立图层 = 等价于 V2 的 Offscreen 合成策略，使 DstIn 遮罩只作用于本词内容
                                                    if (isWordComplete || isWordActive) {
                                                        drawIntoCanvas { canvas ->
                                                            canvas.saveLayer(region, Paint())
                                                        }
                                                        drawText(
                                                            textLayoutResult = l.layout,
                                                            topLeft = l.topLeft,
                                                            color = focusedColor.copy(
                                                                alpha = if (l.isBackground) 0.75f * 0.85f else 1f
                                                            ),
                                                            shadow = if (glowAlpha > 0f) {
                                                                Shadow(
                                                                    color = focusedColor.copy(alpha = glowAlpha),
                                                                    offset = Offset.Zero,
                                                                    blurRadius = glowRadius.coerceAtLeast(1f)
                                                                )
                                                            } else {
                                                                null
                                                            }
                                                        )

                                                        // 液体扫描遮罩（DstIn，V2 原式：8dp 过渡宽度，渐变覆盖整词区域）
                                                        if (isWordActive && !isWordComplete) {
                                                            val edgeWidth = 8.dp.toPx()
                                                            val fullWidth = region.width + edgeWidth * 2f
                                                            val center = fullWidth * progress - edgeWidth
                                                            drawRect(
                                                                brush = Brush.horizontalGradient(
                                                                    colors = if (isRtl) {
                                                                        listOf(Color.Transparent, Color.Black)
                                                                    } else {
                                                                        listOf(Color.Black, Color.Transparent)
                                                                    },
                                                                    startX = center - edgeWidth,
                                                                    endX = center + edgeWidth
                                                                ),
                                                                topLeft = region.topLeft,
                                                                size = region.size,
                                                                blendMode = BlendMode.DstIn
                                                            )
                                                        }
                                                        drawIntoCanvas { canvas ->
                                                            canvas.restore()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                YosWrapper {
                                    AnimatedVisibility(showTranslation && translation != null) {
                                        translation?.let {
                                            val translationAlpha = animateFloatAsState(
                                                targetValue = if (isCurrentLambda()) 0.5f else 0.14f,
                                                animationSpec = if (isCurrentLambda()) alphaTweenSpecWithDelay else alphaTweenSpecWithoutDelay
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
    val fontSize = SettingsLibrary.LyricFontSize
    return TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 10).sp,
        fontWeight =
        when (SettingsLibrary.LyricFontWeight) {
            "Thin" -> FontWeight.Thin
            "ExtraLight" -> FontWeight.ExtraLight
            "Light" -> FontWeight.Light
            "Regular" -> FontWeight.Normal
            "Medium" -> FontWeight.Medium
            "SemiBold" -> FontWeight.SemiBold
            "Bold" -> FontWeight.Bold
            "ExtraBold" -> FontWeight.ExtraBold
            "Black" -> FontWeight.Black
            else -> FontWeight.ExtraBold
        },
        letterSpacing = 0.05.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        ),
        lineBreak = LineBreak(
            strategy = if (SettingsLibrary.LyricLineBalance) LineBreak.Strategy.Balanced else LineBreak.Strategy.Simple,
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
    val layout: TextLayoutResult,
    val topLeft: Offset,
    val box: Rect,
    val startMs: Float,
    val endMs: Float,
    val floatOffset: Float,
    val isBackground: Boolean
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
