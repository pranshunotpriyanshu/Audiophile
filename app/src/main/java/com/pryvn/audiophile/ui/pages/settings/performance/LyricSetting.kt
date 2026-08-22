package com.pryvn.audiophile.ui.pages.settings.performance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.pages.settings.GroupSpacer
import com.pryvn.audiophile.ui.pages.settings.GroupSpacerMedium
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.pages.settings.SwitchItem
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

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
                        FontWeightSizeMenu()
                        GroupSpacerMedium()
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_blur_effect),
                                onClick = { SettingsLibrary.LyricBlurEffect = !SettingsLibrary.LyricBlurEffect },
                                checkedLambda = { SettingsLibrary.LyricBlurEffect }
                            )
                        }
                        GroupSpacerMedium()
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_lyric_smart_wbw_lyric),
                                onClick = { SettingsLibrary.LyricSmartWbw = !SettingsLibrary.LyricSmartWbw },
                                checkedLambda = { SettingsLibrary.LyricSmartWbw }
                            )
                        }
                        GroupSpacer()
                    }
                }
            }
        )
    }

@Composable
private fun FontWeightSizeMenu() {
    var expanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(SettingsLibrary.LyricFontSize) }
    val fontWeightOptions = listOf(
        "Thin" to FontWeight.Thin,
        "ExtraLight" to FontWeight.ExtraLight,
        "Light" to FontWeight.Light,
        "Regular" to FontWeight.Normal,
        "Medium" to FontWeight.Medium,
        "SemiBold" to FontWeight.SemiBold,
        "Bold" to FontWeight.Bold,
        "ExtraBold" to FontWeight.ExtraBold,
        "Black" to FontWeight.Black,
    )
    val fontWeightIndex = remember {
        mutableStateOf(
            fontWeightOptions.indexOfFirst { it.first == SettingsLibrary.LyricFontWeight }.coerceAtLeast(6)
        )
    }

    RoundColumn {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded }
                )
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.settings_performance_lyric_style),
                fontSize = 16.5.sp,
                lineHeight = 20.5.sp,
                modifier = Modifier.weight(1f).alpha(0.94f)
            )
            Text(
                text = fontSize.toInt().toString() + "sp \u00b7 " + fontWeightOptions[fontWeightIndex.value].first,
                fontSize = 13.sp,
                modifier = Modifier.alpha(0.4f)
            )
        }
    }

    AnimatedVisibility(visible = expanded) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Aa Bb Cc lyrics preview",
                fontFamily = SfProFontFamily,
                fontSize = fontSize.sp,
                fontWeight = fontWeightOptions[fontWeightIndex.value].second,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).alpha(0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Font Weight", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp).alpha(0.5f))
            Slider(
                value = fontWeightIndex.value.toFloat(),
                onValueChange = { idx -> fontWeightIndex.value = idx.toInt(); SettingsLibrary.LyricFontWeight = fontWeightOptions[idx.toInt()].first },
                valueRange = 0f..8f, steps = 7,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "Font Size", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp).alpha(0.5f))
            Slider(
                value = fontSize,
                onValueChange = { newValue -> fontSize = newValue; SettingsLibrary.LyricFontSize = newValue },
                valueRange = 12f..48f, steps = 28,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            )
            Text(
                text = fontSize.toInt().toString() + "sp",
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp).alpha(0.4f),
            )
        }
    }
}
