package com.pryvn.audiophile.ui.widgets

import android.content.Context
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.code.player.PlayerAdapter
import com.pryvn.audiophile.code.utils.lrc.TTMLParser
import com.pryvn.audiophile.code.utils.lyrics.LyricsEntryBridge
import com.pryvn.audiophile.code.utils.lyrics.toLyricsWrappingUnits
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.LyricsEntry
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.WordTimestamp
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.LyricsInteractionController
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ──────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────

private const val LRC_LEAD_MS = 300L
private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

private val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")

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

// ──────────────────────────────────────────────────────────────────────
// Maps the user's lyric font-weight setting to a Compose FontWeight.
private fun lyricFontWeight(): FontWeight {
    return when (SettingsLibrary.LyricFontWeight) {
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
}

// Main Composable
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsV2(
    player: PlayerAdapter,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
    onBackgroundClick: () -> Unit = {},
) {
    val isInteractive = LyricsInteractionController.isInteractive()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // ── Reactive settings observers ──
    // These wrap the data-saver settings to trigger recomposition on change
    var lyricFontWeight by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricFontWeight)
    }
    var lyricFontSize by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricFontSize)
    }

    // Sync with external changes (from settings screen) via snapshotFlow
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsLibrary.LyricFontWeight }
            .distinctUntilChanged()
            .collect { lyricFontWeight = it }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsLibrary.LyricFontSize }
            .distinctUntilChanged()
            .collect { lyricFontSize = it }
    }

    // ── Map setting string to FontWeight ──
    val fontWeight: FontWeight = when (lyricFontWeight) {
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

    // ── Preferences ──
    val lyricsClick = true
    val lyricsScroll = true
    val lyricsTextSize = lyricFontSize
    val lyricsLineSpacing = 1.3f
    val lyricsLineBlurPreference = true
    val bounceFactor = 0.22f
    val glowFactor = 1f
    val fillTransitionWidth = 8f
    val lrcBounceEnabled = true
    val lyricsFontFamily: FontFamily? = SfProFontFamily

    // ── Text colour ──
    val textColor = textColorOverride ?: Color.White
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    val inactiveAlpha = 0.35f

    // ── Lyrics data ──
    val lyrics by MediaViewModelObject.onlineLyrics

    // ── Parse lyrics into entries ──
    val isSynced = remember(lyrics) { lyrics != null && (TTMLParser.isLineSyncedLrc(lyrics!!) || TTMLParser.isTtml(lyrics!!)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && TTMLParser.isTtml(lyrics!!) }

    val lyricsEntries: List<LyricsEntry> =
        remember(lyrics) {
            LyricsEntryBridge.fromRawLyrics(lyrics, player.duration)
        }

    val entriesWithWords: List<LyricsEntry> = lyricsEntries

    // End timestamp (ms) of each line: last sung word for word-synced, else the
    // line's own start time. Used to drive the between-lines gap dots.
    val lineEndTimesMs: List<Long> =
        remember(entriesWithWords) {
            entriesWithWords.map { entry ->
                val w = entry.words
                if (!w.isNullOrEmpty()) {
                    (w.maxOf { it.endTime } * 1000.0).toLong().coerceAtLeast(entry.time)
                } else {
                    entry.time
                }
            }
        }

    // ── Playback position tracking ──
    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(entriesWithWords, isSynced, leadMs, lyricsSyncOffset) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        val pollIntervalMs = if (isTtmlFormat) 16L else 50L
        while (isActive) {
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: player.currentPosition

            playbackPositionMs = (pos + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
            currentPositionMs = (playbackPositionMs + leadMs + LYRIC_VISUAL_TUNING_OFFSET_MS).coerceAtLeast(0L)

            val nextIndex = entriesWithWords.indexOfLast { it.time >= 0 && it.time <= currentPositionMs }
            currentLineIndex = if (nextIndex < 0) 0 else nextIndex
            delay(pollIntervalMs)
        }
    }

    // ── Scroll State ──
    val listState = rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        isManualScrolling = true
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Offset.Zero
                }
            }
        }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    LaunchedEffect(currentLineIndex, isManualScrolling, lyricsScroll) {
        if (!lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * 0.35f).toInt() // Center bias at 35% from top

        val distance = abs(currentLineIndex - (listState.firstVisibleItemIndex))
        if (distance > 15) {
            // Far jump — snap first, then settle
            listState.scrollToItem(
                (currentLineIndex - 2).coerceAtLeast(0),
                0,
            )
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset,
        )
    }

    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Render ──
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
                .then(
                    if (isInteractive) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onBackgroundClick() }
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (lyrics == "LYRICS_NOT_FOUND") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Lyrics not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        if (lyrics == null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                repeat(6) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .padding(vertical = 4.dp)
                                .background(
                                    color = textColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp),
                                ),
                    )
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Lyrics not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isInteractive) Modifier.nestedScroll(nestedScrollConnection) else Modifier,
                    )
                    .drawWithContent {
                        drawContent()
                        val fadeHeight = 120.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadeHeight,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadeHeight,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" },
                contentType = { _, entry ->
                    when {
                        entry == HEAD_LYRICS_ENTRY -> "head"
                        entry.isInstrumental -> "instrumental"
                        entry.words != null && isSynced -> "wordSynced"
                        else -> "lineSynced"
                    }
                },
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(120.dp))
                    return@itemsIndexed
                }

                if (item.isInstrumental && isSynced) {
                    val startTimeMs = item.time
                    val endTimeMs = item.time + item.durationMs
                    val isActive = playbackPositionMs in startTimeMs until endTimeMs
                    val distanceFromActive = abs(index - currentLineIndex)
                    val instrAlpha =
                        when {
                            isActive -> 1f
                            isManualScrolling -> {
                                when {
                                    distanceFromActive == 1 -> 0.72f
                                    distanceFromActive == 2 -> 0.56f
                                    distanceFromActive == 3 -> 0.40f
                                    else -> 0.28f
                                }
                            }
                            distanceFromActive == 1 -> 0.52f
                            distanceFromActive == 2 -> 0.30f
                            distanceFromActive == 3 -> 0.18f
                            else -> inactiveAlpha
                        }
                    val animatedInstrAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = instrAlpha,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = if (isActive) 330 else 500,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalAlpha",
                    )
                    val animatedInstrScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.95f,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = 166,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalScale",
                    )
                    val targetInstrBlur =
                        when {
                            !isSynced || isActive || isManualScrolling -> 0f
                            distanceFromActive == 1 -> 2f
                            distanceFromActive == 2 -> 5f
                            else -> 12f
                        }
                    val animatedInstrBlur by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = targetInstrBlur,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = 300,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalBlur",
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top =
                                        if (index == 0 || (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)) {
                                            0.dp
                                        } else {
                                            (lyricsLineSpacing * 8).dp
                                        },
                                    bottom = (lyricsLineSpacing * 8).dp,
                                ).then(
                                    if (lyricsLineBlur) {
                                        Modifier.blur(
                                            radiusX = animatedInstrBlur.dp,
                                            radiusY = animatedInstrBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedInstrScale
                                    scaleY = animatedInstrScale
                                    alpha = animatedInstrAlpha
                                }.then(
                                    if (isInteractive && lyricsClick && item.time > 0) {
                                        Modifier.clickable { player.seekTo(item.time) }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        InstrumentalBreakItem(
                            durationMs = item.durationMs,
                            currentPositionMs = playbackPositionMs,
                            startTimeMs = startTimeMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                        )
                    }
                    return@itemsIndexed
                }

                val textAlign =
                    when (item.agent?.lowercase()) {
                        "v1", null -> TextAlign.Start
                        "v2" -> TextAlign.End
                        else -> TextAlign.Center
                    }
                val horizontalAlignment =
                    when (item.agent?.lowercase()) {
                        "v1", null -> Alignment.Start
                        "v2" -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex
                val isFuture = isSynced && index > currentLineIndex

                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                val lineAlpha =
                    when {
                        !isSynced -> 0.92f
                        isActive -> 1f
                        isManualScrolling -> {
                            when {
                                distanceFromActive == 1 -> 0.72f
                                distanceFromActive == 2 -> 0.56f
                                distanceFromActive == 3 -> 0.40f
                                else -> 0.28f
                            }
                        }
                        distanceFromActive == 1 -> 0.52f
                        distanceFromActive == 2 -> 0.30f
                        distanceFromActive == 3 -> 0.18f
                        else -> 0.14f
                    }
                val targetBlur =
                    when {
                        !isSynced || isActive || isManualScrolling -> 0f
                        distanceFromActive == 1 -> 2f
                        distanceFromActive == 2 -> 5f
                        else -> 12f
                    }
                val animatedBlur = targetBlur
                val targetLineScale = if (isActive) 1.2f else 1f
                val animatedLineScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetLineScale,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = 400,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LineScale",
                )
                val isRightSide = item.agent?.lowercase() == "v2"

                val lineTransformOrigin =
                    remember(item.agent) {
                        when (item.agent?.lowercase()) {
                            "v2" -> androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                            "v1", null -> androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            else -> androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                        }
                    }

                val hasBackgroundWords = item.words?.any { it.isBackground } == true
                val isAllBackground = item.words?.all { it.isBackground || it.text.isBlank() } == true
                val baseLayoutDirection = LocalLayoutDirection.current
                val lineText =
                    remember(item.text, item.words) {
                        item.words
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf { it.isNotBlank() }
                            ?: item.text
                    }
                val lineIsRtl = remember(lineText) { isRtlText(lineText) }
                val lineLayoutDirection =
                    remember(lineIsRtl, baseLayoutDirection) {
                        if (lineIsRtl) LayoutDirection.Rtl else baseLayoutDirection
                    }

                // Match Audiophile's native line-lyrics padding
                // Outer: 9.dp horizontal (item) + 28.dp card (one side) + 20.dp inner (both sides)
                val cardPadding = if (isRightSide)
                    Modifier.padding(start = 28.dp) else Modifier.padding(end = 28.dp)

                CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 9.dp)
                                .then(cardPadding)
                                .padding(
                                    top =
                                        if (index == 0 ||
                                            (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)
                                        ) {
                                            40.dp
                                        } else {
                                            9.dp
                                        },
                                    bottom = 9.dp,
                                ).then(
                                    if (lyricsLineBlur && animatedBlur > 0f) {
                                        Modifier.blur(
                                            radiusX = animatedBlur.dp,
                                            radiusY = animatedBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedLineScale
                                    scaleY = animatedLineScale
                                    alpha = lineAlpha
                                    transformOrigin = lineTransformOrigin
                                }.combinedClickable(
                                    enabled = isInteractive && lyricsClick && isSynced && item.time > 0,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        player.seekTo(item.time)
                                    },
                                    onLongClick = {
                                        player.seekTo(item.time)
                                    },
                                ),
                        horizontalAlignment = horizontalAlignment,
                    ) {
                        val supplementaryBaseTextStyle = MaterialTheme.typography.bodyMedium
                        val supplementaryTextStyle =
                            remember(supplementaryBaseTextStyle, lyricsTextSize, isAllBackground) {
                                supplementaryBaseTextStyle.copy(
                                    fontSize = (lyricsTextSize * 0.55f).sp,
                                    lineHeight = (lyricsTextSize * 0.75f).sp,
                                    fontWeight = FontWeight.Normal,
                                    fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = SfProFontFamily,
                                )
                            }

                        Box(Modifier.padding(start = 20.dp, end = 20.dp)) {
                            Column {
                                if (item.words != null && isSynced) {
                                    LyricsLineV2(
                                        words = item.words,
                                        isActive = isActive,
                                        isPast = isPast,
                                        currentPositionMs = currentPositionMs,
                                        textColor = textColor,
                                        inactiveAlpha = inactiveAlpha,
                                        baseFontSize = lyricsTextSize,
                                        isLineAllBackground = isAllBackground,
                                        textAlign = textAlign,
                                        isRtl = lineIsRtl,
                                        bounceFactor = bounceFactor,
                                        glowFactor = glowFactor,
                                        fillTransitionWidth = fillTransitionWidth,
                                        lyricsFontFamily = lyricsFontFamily,
                                    )
                                } else if (isSynced) {
                                    LyricsLineLrcBounce(
                                        text = item.text,
                                        isActive = isActive,
                                        textColor = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                        fontSize = lyricsTextSize,
                                        lineSpacing = lyricsLineSpacing,
                                        isAllBackground = isAllBackground,
                                        lyricsFontFamily = lyricsFontFamily,
                                        textAlign = textAlign,
                                        bounceFactor = if (lrcBounceEnabled) bounceFactor else 0f,
                                    )
                                } else {
                                        Text(
                                        text = item.text,
                                        style =
                                            TextStyle(
                                                fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                                            ),
                                        color = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                        textAlign = textAlign,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // ── Between-lines gap dots ──
                                // While the current line has finished singing and the next
                                // line hasn't started yet, show a row of dots that fill
                                // left-to-right across the duration of the gap.
                                val lineEnd = lineEndTimesMs.getOrElse(index) { item.time }
                                val nextEntry = entriesWithWords.getOrNull(index + 1)
                                val gapStart = lineEnd
                                val gapEnd = nextEntry?.time ?: -1L
                                val showGap =
                                    isSynced &&
                                        isActive &&
                                        nextEntry != null &&
                                        gapEnd > gapStart &&
                                        (gapEnd - gapStart) >= 5000L &&
                                        currentPositionMs in gapStart until gapEnd
                                AnimatedVisibility(
                                    visible = showGap,
                                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                                ) {
                                    val fill =
                                        ((currentPositionMs - gapStart).toFloat() / (gapEnd - gapStart).toFloat())
                                            .coerceIn(0f, 1f)
                                    GapDots(
                                        fillFraction = fill,
                                        textColor = textColor,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = 20.dp, bottom = 20.dp),
                                        textAlign = textAlign,
                                    )
                                }
                            }
                        }

                        // ── Small gutter between lyrics lines ──
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

    }
}




// -----------------------------------------------------------------
// Line-level composable: renders words with fluid fill animation
// -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineV2(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
    lyricsFontFamily: FontFamily?,
) {
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        }

    val mainWords = words.filter { !it.isBackground && !it.startTime.isNaN() && !it.endTime.isNaN() }
    val bgWords = words.filter { it.isBackground && !it.startTime.isNaN() && !it.endTime.isNaN() }

    val lineText = mainWords.joinToString(separator = "") { it.text }
    val isCjk = remember(lineText) { isCjkText(lineText) }

    fun expandWord(word: WordTimestamp): List<WordTimestamp> {
        if (!isCjk || word.text.length <= 3) return listOf(word)
        val chars = word.text.toList()
        val wordStartMs = (word.startTime * 1000).toLong()
        val wordEndMs = (word.endTime * 1000).toLong()
        val duration = wordEndMs - wordStartMs
        return chars.mapIndexed { charIdx, char ->
            val cStart = wordStartMs + (duration * charIdx / chars.size)
            val cEnd = wordStartMs + (duration * (charIdx + 1) / chars.size)
            WordTimestamp(
                text = char.toString(),
                startTime = cStart / 1000.0,
                endTime = cEnd / 1000.0,
                isBackground = word.isBackground,
            )
        }
    }

    val expandedMain = remember(mainWords, isCjk) { mainWords.flatMap { expandWord(it) } }
    val expandedBg = remember(bgWords, isCjk) { bgWords.flatMap { expandWord(it) } }

    if (expandedMain.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            var prevWasNewline = false
            expandedMain.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            TextStyle(
                                fontFamily = SfProFontFamily,
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                            ),
                        color = Color.Transparent,
                    )
                    prevWasNewline = false
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    prevWasNewline = true
                    return@forEachIndexed
                }

                if (wordIndex > 0 && !prevWasNewline) {
                    Text(
                        text = " ",
                        style =
                            TextStyle(
                                fontFamily = SfProFontFamily,
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                            ),
                        color = Color.Transparent,
                    )
                }
                prevWasNewline = false

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                    lyricsFontFamily = lyricsFontFamily,
                )
            }
        }
    }

    if (expandedBg.isNotEmpty()) {
        val spacerHeight = if (expandedMain.isNotEmpty()) 4.dp else 0.dp
        if (expandedMain.isNotEmpty()) Spacer(modifier = Modifier.height(spacerHeight))

        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f),
            horizontalArrangement = arrangement,
        ) {
            expandedBg.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            TextStyle(
                                fontFamily = SfProFontFamily,
                                fontSize = (baseFontSize * 0.65f).sp,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex + expandedMain.size,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f,
                    isBackground = true,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                    lyricsFontFamily = lyricsFontFamily,
                )
            }
        }
    }
}

private fun isCjkText(text: String): Boolean = text.any { isCjkChar(it) }

private fun isCjkChar(c: Char): Boolean {
    val code = c.code
    return code in 0x4E00..0x9FFF ||
        code in 0x3400..0x4DBF ||
        code in 0xF900..0xFAFF ||
        code in 0x3040..0x309F ||
        code in 0x30A0..0x30FF ||
        code in 0xAC00..0xD7AF ||
        code in 0x1100..0x11FF
}

// -----------------------------------------------------------------
// Between-lines gap dots: a row of dots that fill left-to-right across
// the duration of the pause between two lyric lines.
// -----------------------------------------------------------------

@Composable
fun GapDots(
    fillFraction: Float,
    textColor: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        }

    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 5.dp),
        ) {
            for (i in 0 until 3) {
                val segmentStart = i / 3f
                val segmentEnd = (i + 1) / 3f
                val raw = (fillFraction - segmentStart) / (segmentEnd - segmentStart)
                val dotAlpha = (0.2f + 0.8f * raw.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .size(11.dp)
                        .background(textColor.copy(alpha = dotAlpha), shape = CircleShape)
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// ──────────────────────────────────────────────────────────────────────
// Word-level composable: liquid fill sweep + glow + bounce
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedWordV2(
    word: WordTimestamp,
    wordIndex: Int,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
) {
    val density = LocalDensity.current.density
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    val progress =
        when {
            isWordComplete -> 1f
            currentPositionMs <= wordStartMs -> 0f
            else -> ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        }

    val sinProgress = kotlin.math.sin(progress * kotlin.math.PI).toFloat()
    val wordScale = 1f + (0.015f * bounceFactor * sinProgress)
    val targetFloat = if (isWordActive) -4f * bounceFactor * sinProgress else 0f
    val floatOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFloat,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = if (isWordActive) 50 else 350,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "v2FloatOffset",
    )

    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val wordGlowAlpha = if (isWordActive) glowProgress * 0.45f * glowFactor else 0f
    val wordGlowRadius = if (isWordActive) glowProgress * 12f * glowFactor else 0f

    val actualFontSize = if (isBackground) fontSize * 0.85f else fontSize
    val fontWeight = if (isLineActive || isLinePast) FontWeight.ExtraBold else FontWeight.SemiBold
    val baseColor = textColor.copy(alpha = if (isBackground) inactiveAlpha * 0.7f else inactiveAlpha)
    val fillColor = textColor.copy(alpha = if (isBackground) 0.75f else 1f)
    val glowPadding = 10.dp

    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val glowPaddingPx = glowPadding.roundToPx()
                    val looseConstraints = Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    )
                    val placeable = measurable.measure(looseConstraints)
                    val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                    val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)
                    layout(coreWidth, coreHeight) {
                        placeable.place(-glowPaddingPx, -glowPaddingPx)
                    }
                }.graphicsLayer {
                    clip = false
                    translationY = floatOffset * density
                    scaleX = wordScale
                    scaleY = wordScale
                },
    ) {
        // Layer 1: Base text (always dimmed)
        Text(
            text = word.text,
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontSize = actualFontSize.sp,
                    fontWeight = fontWeight,
                    fontStyle = FontStyle.Normal,
                    lineHeight = (actualFontSize * 1.35f).sp,
                    fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                ),
            color = baseColor,
            modifier = Modifier.padding(glowPadding),
        )

        // Layer 2: Filled overlay with liquid sweep mask + glow
        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = actualFontSize.sp,
                        fontWeight = fontWeight,
                        fontStyle = FontStyle.Normal,
                        lineHeight = (actualFontSize * 1.35f).sp,
                        fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                        shadow =
                            if (wordGlowAlpha > 0f) {
                                Shadow(
                                    color = textColor.copy(alpha = wordGlowAlpha),
                                    offset = Offset.Zero,
                                    blurRadius = wordGlowRadius.coerceAtLeast(1f),
                                )
                            } else {
                                null
                            },
                    ),
                color = fillColor,
                modifier =
                    if (isWordActive && !isWordComplete) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val edgeWidth = fillTransitionWidth.dp.toPx()
                                val center =
                                    if (isRtl) {
                                        size.width - ((size.width + edgeWidth * 2) * progress - edgeWidth)
                                    } else {
                                        (size.width + edgeWidth * 2) * progress - edgeWidth
                                    }
                                drawRect(
                                    brush =
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors =
                                                if (isRtl) {
                                                    listOf(Color.Transparent, Color.Black)
                                                } else {
                                                    listOf(Color.Black, Color.Transparent)
                                                },
                                            startX = center - edgeWidth,
                                            endX = center + edgeWidth,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }.padding(glowPadding)
                    } else {
                        Modifier.padding(glowPadding)
                    },
            )
        }
    }
}

// -----------------------------------------------------------------
// LRC bounce: word-by-word spring bounce for line-synced lyrics
// -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineLrcBounce(
    text: String,
    isActive: Boolean,
    textColor: Color,
    fontSize: Float,
    lineSpacing: Float,
    isAllBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    textAlign: TextAlign,
    bounceFactor: Float,
) {
    val words = remember(text) { text.toLyricsWrappingUnits() }
    val effectiveFontSize = if (isAllBackground) fontSize * 0.82f else fontSize
    val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
    val fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal

    // Single spring animation for the entire line instead of per-word Animatables
    val bounceProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "lineBounceProgress"
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            when (textAlign) {
                TextAlign.Center -> Arrangement.Center
                TextAlign.End -> Arrangement.End
                else -> Arrangement.Start
            },
    ) {
        words.forEachIndexed { i, word ->
            LrcBouncingWord(
                text = word,
                progress = bounceProgress,
                wordIndex = i,
                color = textColor,
                fontSize = effectiveFontSize,
                lineSpacing = lineSpacing,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                lyricsFontFamily = lyricsFontFamily,
                bounceFactor = bounceFactor,
                isActive = isActive,
            )
        }
    }
}

@Composable
private fun LrcBouncingWord(
    text: String,
    progress: Float,
    wordIndex: Int,
    color: Color,
    fontSize: Float,
    lineSpacing: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    lyricsFontFamily: FontFamily?,
    bounceFactor: Float,
    isActive: Boolean,
) {
    // Staggered animation using progress with word-index delay
    val staggeredProgress = (progress - wordIndex * 0.05f).coerceIn(0f, 1f)
    val easedProgress = (1f - (1f - staggeredProgress) * (1f - staggeredProgress)) * staggeredProgress // easeOutCubic
    
    val scale = 1f + 0.045f * bounceFactor * easedProgress
    val transY = -5f * bounceFactor * easedProgress

    Text(
        text = text,
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = fontSize.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontStyle = fontStyle,
                lineHeight = (fontSize * lineSpacing).sp,
                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
            ),
        color = color,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = transY
            },
    )
}

// -----------------------------------------------------------------
// Instrumental break icon: music-note filled bottom-to-top over the gap
// -----------------------------------------------------------------

@Composable
private fun InstrumentalBreakItem(
    durationMs: Long,
    currentPositionMs: Long,
    startTimeMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
) {
    val musicNotePath =
        remember {
            androidx.compose.ui.graphics.vector
                .PathParser()
                .parsePathString(
                    "M10 21q-1.65 0-2.825-1.175T6 17t1.175-2.825T10 13q.575 0 1.063.138t.937.412V4" +
                        "q0-.425.288-.712T13 3h4q.425 0 .713.288T18 4v2q0 .425-.288.713T17 7h-3v10" +
                        "q0 1.65-1.175 2.825T10 21",
                ).toPath()
        }

    val targetFillFraction =
        when {
            durationMs <= 0L -> 0f
            currentPositionMs <= startTimeMs -> 0f
            currentPositionMs >= startTimeMs + durationMs -> 1f
            else -> ((currentPositionMs - startTimeMs).toDouble() / durationMs.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }
    val fillFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFillFraction,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessHigh,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        label = "instrumentalFill",
    )

    Canvas(modifier = Modifier.size(48.dp)) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val pivot = Offset.Zero

        withTransform(
            transformBlock = { scale(scaleX, scaleY, pivot) },
        ) {
            drawPath(path = musicNotePath, color = textColor.copy(alpha = inactiveAlpha))
        }

        if (fillFraction > 0f) {
            val clipTop = size.height * (1f - fillFraction)
            clipRect(
                left = 0f,
                top = clipTop,
                right = size.width,
                bottom = size.height,
            ) {
                withTransform(
                    transformBlock = { scale(scaleX, scaleY, pivot) },
                ) {
                    drawPath(path = musicNotePath, color = textColor)
                }
            }
        }
    }
}
