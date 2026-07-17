package com.pryvn.audiophile.ui.widgets.sleeptimer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.player.SleepTimer
import com.pryvn.audiophile.code.player.SleepTimerOption
import com.pryvn.audiophile.code.player.SleepTimerState
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.pages.library.FloatingMenuScreenTransition
import com.pryvn.audiophile.ui.pages.library.SheetNavigationBackward
import com.pryvn.audiophile.ui.pages.library.SheetNavigationForward
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.YosBottomSheetDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(isOpen: MutableState<Boolean>) {
    if (!isOpen.value) return

    YosBottomSheetDialog(
        properties = androidx.compose.material3.ModalBottomSheetDefaults.properties(),
        onDismissRequest = { isOpen.value = false },
        cornerRadius = { SettingsLibrary.ScreenCorner.toInt().dp },
    ) {
        SleepTimerContent(onDone = { isOpen.value = false })
    }
}

@Composable
fun SleepTimerContent(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var screen by remember { mutableStateOf(Screen.Presets) }
    val navigationDirection = remember {
        mutableIntStateOf(SheetNavigationForward)
    }

    FloatingMenuScreenTransition(
        targetState = screen,
        navigationDirection = navigationDirection.intValue,
        modifier = Modifier.fillMaxWidth(),
        label = "SleepTimerContent",
    ) { currentScreen ->
        when (currentScreen) {
            Screen.Presets -> PresetScreen(
                onPicked = {
                    screen = Screen.Presets
                    onDone()
                },
                onOpenCustom = {
                    navigationDirection.intValue = SheetNavigationForward
                    screen = Screen.Custom
                },
                onOpenFade = {
                    navigationDirection.intValue = SheetNavigationForward
                    screen = Screen.Fade
                },
                onBack = onBack,
            )

            Screen.Custom -> CustomScreen(
                onCancel = {
                    navigationDirection.intValue = SheetNavigationBackward
                    screen = Screen.Presets
                },
                onConfirm = {
                    navigationDirection.intValue = SheetNavigationBackward
                    screen = Screen.Presets
                    onDone()
                },
            )

            Screen.Fade -> FadeScreen(
                onBack = {
                    navigationDirection.intValue = SheetNavigationBackward
                    screen = Screen.Presets
                },
            )
        }
    }
}

private enum class Screen { Presets, Custom, Fade }

@Composable
private fun PresetScreen(
    onPicked: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenFade: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val timerState by SleepTimer.state
    val activeOption = (timerState as? SleepTimerState.Active)?.option
    val activeDurationOption = activeOption as? SleepTimerOption.Duration
    val durationPresetMinutes = remember { listOf(5, 15, 30, 60) }
    val highlightedDurationMs = rememberSaveable(key = "PresetScreen_highlightedDurationMs") {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(activeDurationOption?.durationMs) {
        if (activeDurationOption == null) {
            highlightedDurationMs.longValue = 0L
        } else if (
            highlightedDurationMs.longValue == 0L &&
            durationPresetMinutes.any { it * 60_000L == activeDurationOption.durationMs }
        ) {
            highlightedDurationMs.longValue = activeDurationOption.durationMs
        }
    }

    SheetTitle(text = stringResource(R.string.sleep_timer_title), onBack = onBack)

    if (timerState is SleepTimerState.Active) {
        ActiveStatusRow(onCancel = {
            SleepTimer.cancel()
        })
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))
    }

    PresetRow(
        label = stringResource(R.string.sleep_timer_end_of_track),
        selected = activeOption is SleepTimerOption.EndOfTrack,
        onClick = {
            SleepTimer.start(SleepTimerOption.EndOfTrack)
            onPicked()
        },
    )
    PresetRow(
        label = stringResource(R.string.sleep_timer_end_of_queue),
        selected = activeOption is SleepTimerOption.EndOfQueue,
        onClick = {
            SleepTimer.start(SleepTimerOption.EndOfQueue)
            onPicked()
        },
    )
    Spacer(modifier = Modifier.height(4.dp))
    Divider()
    Spacer(modifier = Modifier.height(4.dp))

    durationPresetMinutes.forEach { minutes ->
        val durationMs = minutes * 60_000L
        PresetRow(
            label = stringResource(
                if (activeDurationOption != null) {
                    R.string.sleep_timer_add_minutes
                } else {
                    R.string.sleep_timer_minutes
                },
                minutes,
            ),
            selected = highlightedDurationMs.longValue == durationMs ||
                activeDurationOption?.durationMs == durationMs,
            onClick = {
                highlightedDurationMs.longValue = durationMs
                SleepTimer.addDuration(durationMs)
                onPicked()
            },
        )
    }

    PresetRow(
        label = stringResource(R.string.sleep_timer_custom),
        selected = false,
        showChevron = true,
        onClick = onOpenCustom,
    )

    Spacer(modifier = Modifier.height(8.dp))
    Divider()
    Spacer(modifier = Modifier.height(8.dp))

    FadeSelectorRow(onClick = onOpenFade)
}

@Composable
private fun ActiveStatusRow(onCancel: () -> Unit) {
    val context = LocalContext.current
    val timerState by SleepTimer.state
    val remaining by SleepTimer.remainingMs
    val active = timerState as? SleepTimerState.Active ?: return

    val label = when (active.option) {
        SleepTimerOption.EndOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
        SleepTimerOption.EndOfQueue -> stringResource(R.string.sleep_timer_end_of_queue)
        is SleepTimerOption.Duration -> stringResource(R.string.sleep_timer_active_short)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_setting_moon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (active.option is SleepTimerOption.Duration) {
                Text(
                    text = formatRemaining(remaining),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alpha(0.85f),
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onCancel()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.sleep_timer_cancel),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    selected: Boolean,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else (Color.Black withNight Color.White),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (showChevron) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.4f),
            )
        }
    }
}

@Composable
private fun FadeSelectorRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val fadeMs = SettingsLibrary.SleepTimerFadeDurationMs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sleep_timer_fade_label),
            fontSize = 14.5.sp,
            modifier = Modifier
                .weight(1f)
                .alpha(0.75f),
        )
        Text(
            text = fadeLabel(fadeMs),
            fontSize = 14.sp,
            modifier = Modifier
                .padding(end = 8.dp)
                .alpha(0.65f),
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_action_next),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .alpha(0.4f),
        )
    }
}

@Composable
private fun CustomScreen(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(30) }

    SheetTitle(text = stringResource(R.string.sleep_timer_custom))

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(
            label = stringResource(R.string.sleep_timer_custom_hours),
            range = 0..12,
            initial = hours,
            onValueChange = { hours = it },
        )
        Text(
            text = ":",
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 24.dp),
        )
        WheelColumn(
            label = stringResource(R.string.sleep_timer_custom_minutes),
            range = 0..59,
            initial = minutes,
            onValueChange = { minutes = it },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val canStart = (hours > 0 || minutes > 0)
    val buttonHeight = 50.dp
    val buttonShape = RoundedCornerShape(buttonHeight.div(2))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .background(
                color = if (canStart) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = buttonShape,
            )
            .clip(buttonShape)
            .clickable(enabled = canStart) {
                Vibrator.click(context)
                val total = hours * 3_600_000L + minutes * 60_000L
                SleepTimer.addDuration(total)
                onConfirm()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sleep_timer_confirm),
            color = Color.White,
            fontSize = 16.5.sp,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .clip(buttonShape)
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
            )
            .clickable {
                Vibrator.click(context)
                onCancel()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.playlist_picker_cancel),
            fontSize = 16.5.sp,
        )
    }
}

@Composable
private fun WheelColumn(
    label: String,
    range: IntRange,
    initial: Int,
    onValueChange: (Int) -> Unit,
) {
    val items = remember(range) { range.toList() }
    val itemHeight = 40.dp
    val visibleCount = 3
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (initial - range.first).coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(state)

    val centered by remember {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            val itemPx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            val centerIndex = if (offset > itemPx / 2) first + 1 else first
            items.getOrNull(centerIndex) ?: range.first
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centered }.collectLatest { onValueChange(it) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .alpha(0.5f),
        )
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(itemHeight * visibleCount),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .align(Alignment.Center)
                    .background(
                        color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            )

            LazyColumn(
                state = state,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    vertical = itemHeight,
                ),
            ) {
                items(items) { value ->
                    val isCentered = value == centered
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString().padStart(2, '0'),
                            fontSize = if (isCentered) 22.sp else 18.sp,
                            fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(if (isCentered) 1f else 0.35f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FadeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var fadeMs by remember { mutableLongStateOf(SettingsLibrary.SleepTimerFadeDurationMs) }

    SheetTitle(text = stringResource(R.string.sleep_timer_fade_label))

    val options = remember {
        listOf(
            0L to R.string.sleep_timer_fade_off,
            5_000L to R.string.sleep_timer_fade_5s,
            10_000L to R.string.sleep_timer_fade_10s,
            30_000L to R.string.sleep_timer_fade_30s,
        )
    }
    options.forEach { (value, labelRes) ->
        PresetRow(
            label = stringResource(labelRes),
            selected = fadeMs == value,
            onClick = {
                fadeMs = value
                SettingsLibrary.SleepTimerFadeDurationMs = value
            },
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f))
            .clickable {
                Vibrator.click(context)
                onBack()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.playlist_picker_cancel),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun SheetTitle(text: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Vibrator.click(context)
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(0.6f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
}

private fun fadeLabel(ms: Long): String = when (ms) {
    0L -> "Off"
    5_000L -> "5s"
    10_000L -> "10s"
    30_000L -> "30s"
    else -> "${ms / 1000}s"
}

private fun formatRemaining(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}
