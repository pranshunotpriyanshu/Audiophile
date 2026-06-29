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
import com.pryvn.audiophile.data.libraries.SettingsLibrary
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
            content = {
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

                        GroupSpacer()
                    }
                }
            }
        )
    }