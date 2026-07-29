package com.pryvn.audiophile.ui.pages.settings.performance.userinterface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.ui.pages.settings.Divider
import com.pryvn.audiophile.ui.pages.settings.GroupSpacer
import com.pryvn.audiophile.ui.pages.settings.GroupSpacerMedium
import com.pryvn.audiophile.ui.pages.settings.LabelItem
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.pages.settings.SelectItem
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.pages.settings.SwitchItem
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@Composable
fun UserInterfaceSetting(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_performance_ui_title),
            onBack = {
                navController.popBackStack()
            },
        ) {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        // ListHeader(content = stringResource(id = R.string.settings_performance_ui_basic))

                        RoundColumn {
                            SelectItem(
                                title = stringResource(id = R.string.settings_performance_ui_theme),
                                items = listOf(
                                    "Auto",
                                    "Dark",
                                    "Light"
                                ),
                                value = SettingsLibrary.CustomTheme,
                                onValueChange = {
                                    SettingsLibrary.CustomTheme = it
                                }
                            )

                            Divider()

                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_blur_effect_title),
                                // desc = stringResource(id = R.string.settings_performance_ui_blur_effect_desc),
                                onClick = {
                                    SettingsLibrary.BarBlurEffect = !SettingsLibrary.BarBlurEffect
                                },
                                checkedLambda = { SettingsLibrary.BarBlurEffect }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_blur_effect_desc))

                        GroupSpacerMedium()

                        // ---- Typography section ----
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_typography))

                        RoundColumn {
                            SelectItem(
                                title = stringResource(id = R.string.settings_performance_ui_typography_font_weight),
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
                                value = SettingsLibrary.AppFontWeight,
                                onValueChange = { SettingsLibrary.AppFontWeight = it }
                            )
                            Divider()
                            AppFontSizeItem()
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_typography_font_size_desc))

                        GroupSpacerMedium()

                        val showCornerSetDialog =
                        remember("UserInterfaceSetting_showCornerSetDialog") {
                            mutableStateOf(false)
                        }

                        RoundColumn {
                            LabelItem(
                                title = stringResource(id = R.string.settings_performance_ui_screen_corner_title),
                                // desc = stringResource(id = R.string.settings_performance_ui_screen_corner_desc),
                                superLink = true
                            ) {
                                showCornerSetDialog.value = true
                            }
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_screen_corner_desc))

                        if (showCornerSetDialog.value) {
                            ScreenCornerSetDialog {
                                showCornerSetDialog.value = false
                            }
                        }

                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_nowplaying_show_volume_bar),
                                // desc = stringResource(id = R.string.settings_performance_ui_nowplaying_show_volume_bar_desc),
                                onClick = {
                                    SettingsLibrary.NowPlayingShowVolumeBar =
                                        !SettingsLibrary.NowPlayingShowVolumeBar
                                },
                                checkedLambda = { SettingsLibrary.NowPlayingShowVolumeBar }
                            )
                        }

                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_nowplaying_show_volume_bar_desc))
                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_nowplaying_background_effect),
                                // desc = stringResource(id = R.string.settings_performance_ui_nowplaying_background_effect_desc),
                                onClick = {
                                    SettingsLibrary.NowplayingBackgroundEffect =
                                        !SettingsLibrary.NowplayingBackgroundEffect
                                },
                                checkedLambda = { SettingsLibrary.NowplayingBackgroundEffect }
                            )
                        }

                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_nowplaying_background_effect_desc))

                        GroupSpacerMedium()

                        RoundColumn {
                            LabelItem(
                                title = stringResource(id = R.string.settings_library_animated_album_covers),
                                superLink = true
                            ) {
                                navController.toUI(UI.Settings.AnimatedAlbumCovers)
                            }
                        }

                        GroupSpacer()
                    }
                }
            }
        }

@Composable
private fun AppFontSizeItem() {
    var expanded by remember { mutableStateOf(false) }
    var appFontSize by remember { mutableStateOf(SettingsLibrary.AppFontSize) }

    Column(Modifier.fillMaxWidth()) {
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
                text = stringResource(id = R.string.settings_performance_ui_typography_font_size),
                fontSize = 16.5.sp,
                lineHeight = 20.5.sp,
                modifier = Modifier.weight(1f).alpha(0.94f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${appFontSize.toInt()}sp",
                    fontSize = 15.sp,
                    modifier = Modifier.alpha(0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_next),
                    contentDescription = null,
                    modifier = Modifier.height(11.dp).alpha(0.4f),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                val sizeState = remember { mutableStateOf(appFontSize) }
                Slider(
                    value = sizeState.value,
                    onValueChange = { newValue ->
                        sizeState.value = newValue
                        appFontSize = newValue.coerceIn(12f, 32f)
                        SettingsLibrary.AppFontSize = newValue.coerceIn(12f, 32f)
                    },
                    valueRange = 12f..32f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.5.dp),
                )
                Text(
                    text = "${appFontSize.toInt()}sp",
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.5.dp).padding(top = 4.dp, bottom = 8.dp).graphicsLayer { alpha = 0.5f },
                )
            }
        }
    }
}