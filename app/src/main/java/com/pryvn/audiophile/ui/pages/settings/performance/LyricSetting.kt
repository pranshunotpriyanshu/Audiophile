package com.pryvn.audiophile.ui.pages.settings.performance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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