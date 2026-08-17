package com.pryvn.audiophile.ui.pages.settings.performance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.pages.settings.GroupSpacer
import com.pryvn.audiophile.ui.pages.settings.GroupSpacerMedium
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.pages.settings.SelectItem
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.pages.settings.SwitchItem
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

@Composable
fun LyricSetting(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_performance_lyric_title),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_style))

                        RoundColumn {
                            SelectItem(
                                title = stringResource(id = R.string.settings_performance_lyric_style_font_weight),
                                items = listOf(
                                    "Thin",
                                    "ExtraLight",
                                    "Light",
                                    "Regular",
                                    "Medium",
                                    "SemiBold",
                                    "Bold",
                                    "ExtraBold",
                                    "Black"
                                ),
                                value = SettingsLibrary.LyricFontWeight,
                                onValueChange = {
                                    SettingsLibrary.LyricFontWeight = it
                                }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_style_font_weight_desc))

                        GroupSpacerMedium()

                        // Font Size slider with preview
                        RoundColumn {
                            FontSizeSliderItem(
                                title = stringResource(id = R.string.settings_performance_lyric_style_font_size),
                                sampleText = stringResource(id = R.string.settings_performance_lyric_style_font_size_sample),
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_style_font_size_desc))

                        GroupSpacerMedium()

                        // Word Effects: one live preview showing BOTH the glow and
                        // the bounce, with a plain smooth slider for each — glow
                        // 0x..1.3x, bounce 0x..1.0x.
                        RoundColumn {
                            WordEffectsSliderItem(
                                title = stringResource(id = R.string.settings_performance_lyric_style_effects),
                            )
                        }

                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_line_balance),
                                onClick = {
                                    SettingsLibrary.LyricLineBalance =
                                        !SettingsLibrary.LyricLineBalance
                                },
                                checkedLambda = { SettingsLibrary.LyricLineBalance }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_line_balance_desc))

                        GroupSpacer()

                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_others))

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_blur_effect),
                                onClick = {
                                    SettingsLibrary.LyricBlurEffect =
                                        !SettingsLibrary.LyricBlurEffect
                                },
                                checkedLambda = { SettingsLibrary.LyricBlurEffect }
                            )
                        }

                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_blur_effect_desc))

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_smart_wbw_lyric),
                                onClick = {
                                    SettingsLibrary.LyricSmartWbw =
                                        !SettingsLibrary.LyricSmartWbw
                                },
                                checkedLambda = { SettingsLibrary.LyricSmartWbw }
                            )
                        }

                        ListHeader(content = stringResource(id = R.string.settings_performance_lyric_smart_wbw_lyric_desc))
                        GroupSpacer()
                    }
                }
            }
        )
    }

@Composable
private fun FontSizeSliderItem(
    title: String,
    sampleText: String,
) {
    var expanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(SettingsLibrary.LyricFontSize) }
    var fontWeight by remember { mutableStateOf(SettingsLibrary.LyricFontWeight) }

    Column(Modifier.fillMaxWidth()) {
        DefaultItem(enabled = true, title = title, desc = null, onClick = {
            expanded = !expanded
        }) {
            Row(
                modifier = Modifier
                    .alpha(0.4f), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${fontSize.toInt()}sp", fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_next),
                    contentDescription = title,
                    modifier = Modifier
                        .height(11.dp)
                        .alpha(0.4f),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                // Sample text preview
                Text(
                    text = sampleText,
                    fontFamily = SfProFontFamily,
                    fontSize = fontSize.sp,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.5.dp, vertical = 8.dp)
                        .alpha(0.6f),
                )

                // Slider
                val fontSizeState = remember { mutableStateOf(fontSize) }
                Slider(
                    value = fontSizeState.value,
                    onValueChange = { newValue ->
                        fontSizeState.value = newValue
                        fontSize = newValue.coerceIn(12f, 48f)
                        SettingsLibrary.LyricFontSize = newValue.coerceIn(12f, 48f)
                    },
                    valueRange = 12f..48f,
                    steps = 28,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.5.dp),
                )

                // Current value text
                Text(
                    text = "${fontSize.toInt()}sp",
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.5.dp)
                        .padding(top = 4.dp, bottom = 8.dp)
                        .graphicsLayer { alpha = 0.5f },
                )
            }
        }
    }
}

@Composable
private fun WordEffectsSliderItem(
    title: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val glow = remember { mutableStateOf(SettingsLibrary.LyricGlowAmount) }
    val bounce = remember { mutableStateOf(SettingsLibrary.LyricBounceAmount) }

    Column(Modifier.fillMaxWidth()) {
        DefaultItem(enabled = true, title = title, desc = null, onClick = {
            expanded = !expanded
        })

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                // One live preview: the sample lyric line with "broken" half-filled
                // like a real paused word-synced line, glowing, and bouncing once.
                WordEffectsPreview(glow = glow.value, bounce = bounce.value)

                // A subheading above each slider names what it controls. The sliders
                // themselves stay plain and smooth: no steps, ticks, or value labels.
                Text(
                    text = stringResource(id = R.string.settings_performance_lyric_style_glow),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.5.dp, end = 18.5.dp, top = 8.dp, bottom = 2.dp)
                        .alpha(0.5f),
                )
                Slider(
                    value = glow.value,
                    onValueChange = { newValue ->
                        glow.value = newValue
                        SettingsLibrary.LyricGlowAmount = newValue
                    },
                    valueRange = 0f..1.3f,
                    steps = 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.5.dp),
                )
                Text(
                    text = stringResource(id = R.string.settings_performance_lyric_style_bounce),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.5.dp, end = 18.5.dp, top = 8.dp, bottom = 2.dp)
                        .alpha(0.5f),
                )
                Slider(
                    value = bounce.value,
                    onValueChange = { newValue ->
                        bounce.value = newValue
                        SettingsLibrary.LyricBounceAmount = newValue
                    },
                    valueRange = 0f..1f,
                    steps = 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.5.dp),
                )
            }
        }
    }
}

// Renders the sample lyric line exactly like a paused word-synced line: every
// word sits dimmed, "broken" is half-filled by the karaoke sweep with the glow
// on the filled part, and the word bounces ONCE (the same single pop a real
// word makes when it is sung) whenever the preview opens or a slider moves —
// never a looping pulse.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordEffectsPreview(
    glow: Float,
    bounce: Float,
) {
    val sampleLine = stringResource(id = R.string.settings_performance_lyric_style_effects_sample)
    val words = remember(sampleLine) { sampleLine.split(" ") }

    // One-shot bounce: snap to 0 then glide to 1. sin(0..PI) gives a single
    // rise-and-settle, the same per-word curve AnimatedWordV2 uses. While a
    // bounce is already running (fast slider drag) it is not restarted, so the
    // word pops once per change instead of stuttering.
    val bounceProgress = remember { Animatable(1f) }
    var bounceRunning by remember { mutableStateOf(false) }
    LaunchedEffect(glow, bounce) {
        if (bounceRunning) return@LaunchedEffect
        bounceRunning = true
        bounceProgress.snapTo(0f)
        bounceProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
        bounceRunning = false
    }
    val sinProgress = kotlin.math.sin(bounceProgress.value * kotlin.math.PI.toFloat()).toFloat()
    val wordScale = 1f + 0.015f * bounce * sinProgress
    val floatOffset = -4f * bounce * sinProgress

    // "broken" is caught mid-word: 50% filled, the glow ramped exactly like the
    // real renderer (twice as fast as the fill); the rest of the line is empty.
    val fillProgress = 0.5f
    val glowProgress = (fillProgress * 2f).coerceAtMost(1f)
    val glowAlpha = glowProgress * 0.45f * glow
    val glowRadius = (glowProgress * 12f * glow).coerceAtLeast(1f)
    val glowPadding = 6.dp

    val baseStyle =
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProFontFamily,
            lineHeight = (30f * 1.35f).sp,
        )

    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.5.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEachIndexed { _, word ->
            if (word == "broken") {
                Box(
                    modifier =
                        Modifier
                            .padding(glowPadding)
                            .graphicsLayer {
                                scaleX = wordScale
                                scaleY = wordScale
                                translationY = floatOffset * density
                            },
                ) {
                    // Base layer: dim, always visible.
                    Text(
                        text = word,
                        style = baseStyle,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                    // Filled overlay: the karaoke sweep masks the left half, with
                    // the glow riding the filled part.
                    Text(
                        text = word,
                        style =
                            baseStyle.copy(
                                shadow =
                                    if (glowAlpha > 0f) {
                                        Shadow(
                                            color = Color.White.copy(alpha = glowAlpha),
                                            offset = Offset.Zero,
                                            blurRadius = glowRadius,
                                        )
                                    } else {
                                        null
                                    },
                            ),
                        color = Color.White,
                        modifier =
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val edge = 0.25f
                                    val start = (fillProgress - edge).coerceIn(0f, 1f)
                                    val end = (fillProgress + edge).coerceIn(0f, 1f)
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                0f to Color.Black,
                                                start to Color.Black,
                                                end to Color.Transparent,
                                                1f to Color.Transparent,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                    )
                }
            } else {
                Text(
                    text = word,
                    style = baseStyle,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DefaultItem(
    enabled: Boolean = true,
    title: String,
    titleHighLight: Boolean = false,
    desc: String? = null,
    onClick: (() -> Unit)?,
    backIcon: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier else Modifier.clickable(enabled) {
                    onClick()
                }
            )
            .padding(horizontal = 15.dp, vertical = 11.dp)
            .graphicsLayer {
                if (!enabled) {
                    alpha = 0.6f
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .align(Alignment.CenterVertically)
                .alpha(0.94f)
        ) {
            if (titleHighLight) {
                Text(
                    text = title,
                    fontSize = 16.5.sp,
                    lineHeight = 20.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = title,
                    fontSize = 16.5.sp,
                    lineHeight = 20.5.sp,
                )
            }

            if (desc != null) {
                Text(
                    text = desc,
                    fontSize = 13.2.sp,
                    lineHeight = 16.2.sp,
                    modifier = Modifier.alpha(0.5f),
                )
            }
        }
        Column(
            Modifier.padding(start = 15.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            YosWrapper {
                backIcon?.invoke()
            }
        }
    }
}