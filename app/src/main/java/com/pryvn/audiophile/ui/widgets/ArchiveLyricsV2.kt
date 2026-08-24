package com.pryvn.audiophile.ui.widgets

import android.content.Context
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.coroutineScope
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

private val HEAD_LYRICS_ENTRY = LyricsEntry.HEAD_LYRICS_ENTRY

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
}    // Word-synced lyrics honor the font-weight setting too: active/past lines render
    // at the configured weight and upcoming lines step one weight class down so the
    // current line keeps its emphasis while still following the setting.
    private fun lyricActiveFontWeight(): FontWeight = lyricFontWeight()


private fun lyricInactiveFontWeight(): FontWeight =
    FontWeight((lyricFontWeight().weight - 200).coerceAtLeast(100))

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
    pollingEnabled: () -> Boolean = { true },
    onBackgroundClick: () -> Unit = {},
) {
    val isInteractive = LyricsInteractionController.isInteractive()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // ── Reactive settings observers ──
    // These wrap the data-saver settings to trigger recomposition on change.
    // The SettingLibrary properties are @Stable, so reading them directly in
    // composition is not tracked — mirroring them into local state and
    // observing via snapshotFlow is what makes the effect apply instantly
    // (instead of only on the next song).
    var lyricFontWeight by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricFontWeight)
    }
    var lyricFontSize by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricFontSize)
    }
    var lyricBounceAmount by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricBounceAmount)
    }
    var lyricGlowAmount by remember {
        androidx.compose.runtime.mutableStateOf(SettingsLibrary.LyricGlowAmount)
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
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsLibrary.LyricBounceAmount }
            .distinctUntilChanged()
            .collect { lyricBounceAmount = it }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsLibrary.LyricGlowAmount }
            .distinctUntilChanged()
            .collect { lyricGlowAmount = it }
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
    // Word bounce and glow amounts come from the Lyric Display settings
    // (bounce 0x..1.0x, glow 0x..1.3x), mirrored reactively above so the
    // sliders take effect on the current song immediately.
    val bounceFactor = lyricBounceAmount
    val glowFactor = lyricGlowAmount
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }
    // When the next line has started but earlier lines' words are still being
    // sung (overlapping timestamps), those lines are "held": every held line
    // renders as active — 2, 3 or 4 lines can run simultaneously — and the
    // auto-scroll waits until the last held line finishes before moving on.
    var heldLineIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val timeSortedLines = remember(entriesWithWords) {
        entriesWithWords.mapIndexedNotNull { index, entry ->
            if (entry !== LyricsEntry.HEAD_LYRICS_ENTRY && entry.time >= 0) {
                index to entry.time
            } else {
                null
            }
        }.sortedBy { it.second }
    }

    LaunchedEffect(entriesWithWords, isSynced, leadMs, lyricsSyncOffset) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        val pollIntervalMs = if (isTtmlFormat) 33L else 50L
        while (isActive) {
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                delay(500)
                continue
            }
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: player.currentPosition

            val newPlaybackPos = (pos + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
            val newCurrentPos =
                (newPlaybackPos + leadMs + LYRIC_VISUAL_TUNING_OFFSET_MS).coerceAtLeast(0L)
            if (newPlaybackPos != playbackPositionMs) playbackPositionMs = newPlaybackPos
            if (newCurrentPos != currentPositionMs) currentPositionMs = newCurrentPos

            if (timeSortedLines.isNotEmpty()) {
                var lo = 0
                var hi = timeSortedLines.lastIndex
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (timeSortedLines[mid].second <= newCurrentPos) {
                        lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                val nextIndex = if (hi < 0) -1 else timeSortedLines[hi].first
                val firstLineIndex =
                    if (entriesWithWords.firstOrNull() === LyricsEntry.HEAD_LYRICS_ENTRY) 1 else 0
                val newLineIndex = if (nextIndex < 0) firstLineIndex else nextIndex
                // Overlap hold: any line before the current one whose last word
                // is still being sung stays active (2, 3 or 4 lines can run
                // simultaneously); the scroll defers until the last one ends.
                val newHeld = mutableSetOf<Int>()
                val overlapWindowStart = (newLineIndex - 4).coerceAtLeast(0)
                for (i in overlapWindowStart until newLineIndex) {
                    if (lineEndTimesMs.getOrElse(i) { Long.MAX_VALUE } > newCurrentPos) {
                        newHeld.add(i)
                    }
                }
                heldLineIndices = newHeld
                if (newLineIndex != currentLineIndex) currentLineIndex = newLineIndex
            }
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

    LaunchedEffect(currentLineIndex, heldLineIndices, isManualScrolling, lyricsScroll) {
        if (!lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect
        // While held (overlapping) lines are still finishing, defer the scroll —
        // this effect re-fires when the last hold clears.
        if (heldLineIndices.isNotEmpty()) return@LaunchedEffect

        // Leaving a gap-dots row (an instrumental row or between-lines dots):
        // that row collapses with a short fixed tween as soon as the line stops
        // being active, pushing the new current line up. Wait for the collapse
        // so the measured offset is final — the anchored line lands exactly on
        // its set level instead of overshooting.
        val prevIndex = currentLineIndex - 1
        val prevIsReal =
            prevIndex >= 0 && entriesWithWords.getOrNull(prevIndex)?.let {
                it !== LyricsEntry.HEAD_LYRICS_ENTRY && it.time >= 0
            } == true
        val prevWasInstrumental =
            prevIsReal && entriesWithWords.getOrNull(prevIndex)?.isInstrumental == true
        val prevHadBetweenDots =
            prevIsReal &&
                (lineEndTimesMs.getOrNull(prevIndex) ?: Long.MAX_VALUE) + 5000L <=
                    (entriesWithWords.getOrNull(currentLineIndex)?.time ?: Long.MAX_VALUE)
        if (prevWasInstrumental || prevHadBetweenDots) {
            // Between-lines dots animate out over GAP_DOTS_HIDE_MS before the
            // row collapses, so the layout is final only after that + the tween.
            delay(
                if (prevHadBetweenDots) GAP_DOTS_HIDE_MS + GAP_ROW_ANIM_MS + 30
                else GAP_ROW_ANIM_MS + 30
            )
            if (currentLineIndex != prevIndex + 1) return@LaunchedEffect
        }

        // The next line lights up first (its scale/alpha animations start
        // immediately) and the list scrolls only after the highlight has
        // begun — no lag when switching lyrics.
        val curAtScroll = currentLineIndex
        delay(HIGHLIGHT_LEAD_MS)
        if (currentLineIndex != curAtScroll) return@LaunchedEffect

        // Shared anchor logic: the current line sits at 8% of the viewport —
        // identical to the line-synced renderer, so both views place the
        // current line at exactly the same height.
        listState.animateCurrentLineToAnchor(
            currentIndex = currentLineIndex,
            viewportHeight = listState.layoutInfo.viewportSize.height,
        )
    }

    // ---- Anchor guard ----
    // The current line has a fixed anchor level and can never sit above it:
    // after the scroll settles, if it is still above the level it is pulled
    // back down exactly onto it with a critically damped spring, so it can
    // never cross upward again.
    LaunchedEffect(currentLineIndex, isManualScrolling) {
        if (!lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        val curAtStart = currentLineIndex
        delay(GAP_ROW_ANIM_MS + HIGHLIGHT_LEAD_MS + 500L)
        if (currentLineIndex != curAtStart || isManualScrolling) return@LaunchedEffect
        listState.pullCurrentLineToAnchorIfAbove(
            currentIndex = curAtStart,
            viewportHeight = listState.layoutInfo.viewportSize.height,
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
                    val isGapActive = playbackPositionMs in startTimeMs until endTimeMs

                    // The instrumental row only occupies its height while the
                    // gap is actually active; when the gap ends it collapses
                    // with a short fixed tween so the lyric list stays tight.
                    // The auto-scroll waits for that collapse before measuring,
                    // so the anchored line still lands exactly on its set level.
                    val instrAlpha = animateFloatAsState(
                        targetValue = if (isGapActive) 1f else 0f,
                        animationSpec = tween(340, easing = FastOutSlowInEasing),
                        label = "v2InstrumentalAlpha",
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec =
                                        tween(GAP_ROW_ANIM_MS.toInt(), easing = FastOutSlowInEasing)
                                ),
                    ) {
                        if (isGapActive) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = instrAlpha.value }
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
                                        ).height(48.dp).then(
                                            if (isInteractive && lyricsClick && item.time > 0) {
                                                Modifier.clickable { player.seekTo(item.time) }
                                            } else {
                                                Modifier
                                            },
                                        ),
                            ) {
                                val instrAlign =
                                    when (item.agent?.lowercase()) {
                                        "v2" -> TextAlign.End
                                        else -> TextAlign.Start
                                    }
                                val instrFill =
                                    when {
                                        item.durationMs <= 0L -> 0f
                                        playbackPositionMs <= startTimeMs -> 0f
                                        playbackPositionMs >= endTimeMs -> 1f
                                        else ->
                                            ((playbackPositionMs - startTimeMs).toFloat() / item.durationMs.toFloat())
                                                .coerceIn(0f, 1f)
                                    }
                                GapDots(
                                    fillFraction = instrFill,
                                    textColor = textColor,
                                    textAlign = instrAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp),
                                )
                            }
                        }
                    }
                    return@itemsIndexed
                }

                // Vocal-agent markers place the lyric: v1/v1000 = first vocalist
                // (left), v2/v2000 = second vocalist (right); anything else stays
                // centered.
                val agentSide =
                    when (item.agent?.lowercase()) {
                        "v1", "v1000", null -> Alignment.Start
                        "v2", "v2000" -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                val textAlign =
                    when (agentSide) {
                        Alignment.End -> TextAlign.End
                        Alignment.CenterHorizontally -> TextAlign.Center
                        else -> TextAlign.Start
                    }
                val horizontalAlignment = agentSide

                // ── Intro gap dots ──
                // The song starts with silence before the first line: the dots
                // fill across that intro gap, centered above the first line with
                // the same reveal/hide motion as the between-lines dots.
                val isFirstRealLine =
                    index == 0 || (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)
                if (isFirstRealLine && isSynced && item.time >= 3000L) {
                    val introActive =
                        currentLineIndex == index && currentPositionMs < item.time
                    val introOffset = remember(index) { Animatable(0f) }
                    val introAlpha = remember(index) { Animatable(0f) }
                    var introShown by remember(index) { mutableStateOf(false) }
                    var introHiding by remember(index) { mutableStateOf(false) }
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
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec =
                                    tween(GAP_ROW_ANIM_MS.toInt(), easing = FastOutSlowInEasing)
                            )
                    ) {
                        if (introVisible) {
                            val fill =
                                (currentPositionMs.toFloat() / item.time.toFloat()).coerceIn(0f, 1f)
                            GapDots(
                                fillFraction = fill,
                                textColor = textColor,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        // Same left inset as a lyric line: the text
                                        // sits 9 (item) + 20 (inner) = 29.dp in, so
                                        // 24.dp + the dots' own 5.dp = 29.dp.
                                        .padding(horizontal = 24.dp, vertical = 24.dp)
                                        .graphicsLayer {
                                            alpha = introAlpha.value
                                            translationY = introOffset.value * introDensity
                                        },
                                textAlign = textAlign,
                            )
                        }
                    }

                    // When the intro dots collapse (the first line starts), pull
                    // the first line back onto its anchor level so the intro
                    // never leaves it drifting off the anchor.
                    LaunchedEffect(introActive, isManualScrolling) {
                        if (introActive || !lyricsScroll || isManualScrolling || !isSynced) {
                            return@LaunchedEffect
                        }
                        delay(GAP_ROW_ANIM_MS + 30)
                        val anchorPx =
                            lyricAnchorOffsetPx(listState.layoutInfo.viewportSize.height)
                        val firstItem =
                            listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                        if (firstItem != null && firstItem.offset < anchorPx) {
                            listState.animateScrollBy(
                                firstItem.offset - anchorPx.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = 1f,
                                    stiffness = 150f,
                                    visibilityThreshold = 0.01f
                                )
                            )
                        }
                    }
                }

                // A line is "active" when it is the current line or the previous
                // line is still finishing (overlap hold): both stay fully lit.
                // The held line is not treated as "past" so its words keep
                // filling progressively until its last word is done.
                val isActive = isSynced && (index == currentLineIndex || index in heldLineIndices)
                val isPast = isSynced && index < currentLineIndex && index !in heldLineIndices
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
                val targetLineScale = if (isActive) 1.1f else 1f
                val animatedLineScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetLineScale,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = 400,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LineScale",
                )
                val isRightSide = agentSide == Alignment.End

                val lineTransformOrigin =
                    remember(item.agent) {
                        when (item.agent?.lowercase()) {
                            "v2", "v2000" -> androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                            "v1", "v1000", null -> androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
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

                // ---- Bubble bounce ----
                // When a line settles at the anchor, the surrounding lines wobble
                // like bubbles — the kick falls off with distance from the current
                // line and each line springs back smoothly.
                val bubbleOffset = remember(index) { Animatable(0f) }
                var bubbleKicked by remember(index) { mutableStateOf(false) }
                val bubbleDensity = LocalDensity.current.density

                LaunchedEffect(currentLineIndex, heldLineIndices, isManualScrolling) {
                    if (isManualScrolling || !lyricsScroll) return@LaunchedEffect
                    if (!bubbleKicked) {
                        bubbleKicked = true
                        return@LaunchedEffect
                    }
                    // While held (overlapping) lines are still finishing the scroll
                    // is deferred — bounce together with the actual scroll.
                    if (heldLineIndices.isNotEmpty()) return@LaunchedEffect
                    val current = currentLineIndex
                    if (index == current) return@LaunchedEffect
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
                                    translationY = bubbleOffset.value
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
                                        // Line-synced lyrics never glow — only
                                        // word-synced karaoke words carry the
                                        // Word Glow setting.
                                        glowFactor = 0f,
                                    )
                                } else {
                                        Text(
                                        text = item.text,
                                        style =
                                            TextStyle(
                                                fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                                fontWeight = if (isActive) lyricActiveFontWeight() else lyricInactiveFontWeight(),
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
                                // While the current line has finished singing and the
                                // next line hasn't started yet, a row of dots fills
                                // left-to-right across the gap. The dots are vertically
                                // centered in the space between the two lines: they
                                // slide out of the line above fast and ease into place
                                // when the gap begins, and when the next line starts
                                // they linger at the anchor then accelerate into the
                                // line below while fading. The row keeps its height
                                // through the hide, so the anchored line never shifts,
                                // then collapses.
                                val lineEnd = lineEndTimesMs.getOrElse(index) { item.time }
                                val nextEntry = entriesWithWords.getOrNull(index + 1)
                                val gapStart = lineEnd
                                val gapEnd = nextEntry?.time ?: -1L
                                val gapActive =
                                    isSynced &&
                                        isActive &&
                                        nextEntry != null &&
                                        gapEnd > gapStart &&
                                        (gapEnd - gapStart) >= 5000L &&
                                        currentPositionMs in gapStart until gapEnd

                                val dotsOffset = remember(index) { Animatable(0f) }
                                val dotsAlpha = remember(index) { Animatable(0f) }
                                var dotsShown by remember(index) { mutableStateOf(false) }
                                var dotsHiding by remember(index) { mutableStateOf(false) }
                                val dotsDensity = LocalDensity.current.density

                                LaunchedEffect(gapActive) {
                                    if (gapActive) {
                                        dotsShown = true
                                        dotsHiding = false
                                        // Reveal: come out of the line above fast,
                                        // then ease into the centered position.
                                        dotsOffset.snapTo(-GAP_DOTS_SLIDE_DP)
                                        dotsAlpha.snapTo(0f)
                                        launch {
                                            dotsOffset.animateTo(
                                                0f,
                                                animationSpec = tween(
                                                    GAP_DOTS_REVEAL_MS.toInt(),
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                        }
                                        launch {
                                            dotsAlpha.animateTo(
                                                1f,
                                                animationSpec = tween(
                                                    (GAP_DOTS_REVEAL_MS * 0.8f).toInt(),
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                        }
                                    } else if (dotsShown) {
                                        dotsShown = false
                                        dotsHiding = true
                                        // Hide: slow near the anchor, then
                                        // accelerate into the next line.
                                        coroutineScope {
                                            launch {
                                                dotsOffset.animateTo(
                                                    GAP_DOTS_SLIDE_DP,
                                                    animationSpec = tween(
                                                        GAP_DOTS_HIDE_MS.toInt(),
                                                        easing = LinearOutSlowInEasing
                                                    )
                                                )
                                            }
                                            launch {
                                                dotsAlpha.animateTo(
                                                    0f,
                                                    animationSpec = tween(
                                                        GAP_DOTS_HIDE_MS.toInt(),
                                                        easing = LinearOutSlowInEasing
                                                    )
                                                )
                                            }
                                        }
                                        dotsHiding = false
                                    }
                                }
                                val dotsVisible = gapActive || dotsHiding

                                // The dots row only occupies its height while it is
                                // visible; once hidden it collapses with a short fixed
                                // tween so the lyric list stays tight. The auto-scroll
                                // waits for that collapse before measuring, so the next
                                // line lands exactly on its set level.
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .animateContentSize(
                                            animationSpec =
                                                tween(GAP_ROW_ANIM_MS.toInt(), easing = FastOutSlowInEasing)
                                        )
                                ) {
                                    if (dotsVisible) {
                                        val fill =
                                            ((currentPositionMs - gapStart).toFloat() / (gapEnd - gapStart).toFloat())
                                                .coerceIn(0f, 1f)
                                        GapDots(
                                            fillFraction = fill,
                                            textColor = textColor,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    // Vertically centered between the two
                                                    // lines: below the row sit 9 (bottom
                                                    // padding) + 4 (gutter) + 9 (next line's
                                                    // top padding) = 22.dp of fixed space,
                                                    // so top padding = bottom + 22.
                                                    .padding(top = 31.dp, bottom = 9.dp)
                                                    .graphicsLayer {
                                                        alpha = dotsAlpha.value
                                                        translationY = dotsOffset.value * dotsDensity
                                                    },
                                            textAlign = textAlign,
                                        )
                                    }
                                }
                            }
                        }

                        // ── Small gutter between lyrics lines ──
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // ---- Pull spacer (the "specanim" inertia effect) ----
                // Mirrors the line-synced renderer: when the current line
                // advances, every spacer below it stretches like a rubber band —
                // the pull grows with distance, so the lower lyrics feel dragged
                // by inertia — then releases in a wave from the top down and the
                // block springs onto the anchor.
                val pullTargetHeight = remember(index) { mutableStateOf(0.dp) }
                val pullDensity = LocalDensity.current.density
                LaunchedEffect(currentLineIndex, isManualScrolling) {
                    if (listState.layoutInfo.visibleItemsInfo.isEmpty()) return@LaunchedEffect
                    val cur = currentLineIndex
                    val belowCurrent = index > cur
                    if (belowCurrent && !isManualScrolling && lyricsScroll && isSynced) {
                        val distance = index - cur
                        val weight = (distance.toFloat() / PULL_LINE_RANGE.toFloat()).coerceIn(0f, 1f)
                        val targetOffsetPx =
                            lyricAnchorOffsetPx(listState.layoutInfo.viewportSize.height)
                        val currentItem = listState.layoutInfo.visibleItemsInfo.find { it.index == cur }
                        val pullPx = if (currentItem != null) {
                            ((currentItem.offset - targetOffsetPx) * PULL_STRENGTH).coerceAtLeast(0f)
                        } else {
                            0f
                        }
                        delay((distance * PULL_STAGGER_MS).toLong())
                        pullTargetHeight.value = ((pullPx * weight) / pullDensity).dp
                        delay(PULL_HOLD_MS + (distance * PULL_RELEASE_MS))
                        pullTargetHeight.value = 0.dp
                    } else {
                        pullTargetHeight.value = 0.dp
                    }
                }
                val pullOffset = animateDpAsState(
                    targetValue = pullTargetHeight.value,
                    // Critical damping on the release so the anchored line can
                    // never be flung past its set level.
                    animationSpec = if (pullTargetHeight.value == 0.dp) {
                        spring(stiffness = 170f, dampingRatio = 1f, visibilityThreshold = 0.01.dp)
                    } else {
                        spring(stiffness = 260f, dampingRatio = 1f, visibilityThreshold = 0.01.dp)
                    }
                )
                Spacer(modifier = Modifier.height(pullOffset.value))
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

    // One entry per ORIGINAL word (CJK words expand to their syllable chars).
    // Each word renders as a single Row — one flow item — so its syllables can
    // never wrap apart and a word is never broken across lines.
    val expandedMain = remember(mainWords, isCjk) { mainWords.map { expandWord(it) } }
    val expandedBg = remember(bgWords, isCjk) { bgWords.map { expandWord(it) } }

    if (expandedMain.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            expandedMain.forEachIndexed { unitIndex, unit ->
                if (unit.isEmpty()) return@forEachIndexed
                val first = unit[0]
                if (first.text == " ") {
                    Text(
                        text = " ",
                        style =
                            TextStyle(
                                fontFamily = SfProFontFamily,
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }
                if (first.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }

                // The whole word is one unbreakable flow item: its syllable
                // chars stay glued together while real words keep their gap
                // (word-boundary whitespace is baked into each word's text).
                Row {
                    unit.forEach { word ->
                        AnimatedWordV2(
                            word = word,
                            wordIndex = unitIndex,
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
                            lyricsFontFamily = lyricsFontFamily,
                        )
                    }
                }
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
            expandedBg.forEachIndexed { unitIndex, unit ->
                if (unit.isEmpty()) return@forEachIndexed
                val first = unit[0]
                if (first.text == " ") {
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

                Row {
                    unit.forEach { word ->
                        AnimatedWordV2(
                            word = word,
                            wordIndex = unitIndex + expandedMain.size,
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
                            lyricsFontFamily = lyricsFontFamily,
                        )
                    }
                }
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
// Word-level composable: karaoke fill + glow + bounce
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
    val fontWeight = if (isLineActive || isLinePast) lyricActiveFontWeight() else lyricInactiveFontWeight()
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
                                val edge = 0.3f
                                val start = (progress - edge).coerceIn(0f, 1f)
                                val end = (progress + edge).coerceIn(0f, 1f)
                                drawRect(
                                    brush =
                                        if (isRtl) {
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                end to Color.Transparent,
                                                start to Color.Black,
                                                1f to Color.Black,
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                0f to Color.Black,
                                                start to Color.Black,
                                                end to Color.Transparent,
                                                1f to Color.Transparent,
                                            )
                                        },
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
    glowFactor: Float,
) {
    val words = remember(text) { text.toLyricsWrappingUnits() }
    val effectiveFontSize = if (isAllBackground) fontSize * 0.82f else fontSize
    val fontWeight = if (isActive) lyricActiveFontWeight() else lyricInactiveFontWeight()
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
                glowFactor = glowFactor,
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
    glowFactor: Float,
    isActive: Boolean,
) {
    // Staggered animation using progress with word-index delay
    val staggeredProgress = (progress - wordIndex * 0.05f).coerceIn(0f, 1f)
    val easedProgress = (1f - (1f - staggeredProgress) * (1f - staggeredProgress)) * staggeredProgress // easeOutCubic
    
    val scale = 1f + 0.045f * bounceFactor * easedProgress
    val transY = -5f * bounceFactor * easedProgress

    // Glow ramps up twice as fast as the bounce, then stays lit while the
    // line is active — same curve the word-synced karaoke words use.
    val glowProgress = (staggeredProgress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isActive) 0.45f * glowFactor * glowProgress else 0f
    val glowRadius = if (isActive) (12f * glowFactor * glowProgress).coerceAtLeast(1f) else 0f

    Text(
        text = text,
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = fontSize.sp,
                fontWeight = if (isActive) lyricActiveFontWeight() else lyricInactiveFontWeight(),
                fontStyle = fontStyle,
                lineHeight = (fontSize * lineSpacing).sp,
                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                shadow =
                    if (glowAlpha > 0f) {
                        Shadow(
                            color = color.copy(alpha = glowAlpha),
                            offset = Offset.Zero,
                            blurRadius = glowRadius,
                        )
                    } else {
                        null
                    },
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
