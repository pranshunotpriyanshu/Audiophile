package com.pryvn.audiophile.ui.pages.settings.performance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.MediaController.mediaControl
import com.pryvn.audiophile.code.YosPlaybackService
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.pages.settings.GroupSpacer
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.pages.settings.SwitchItem
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@Composable
fun NotificationSetting(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_performance_notification_title),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        // ListHeader(content = stringResource(id = R.string.settings_performance_notification_basic))

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_notification_basic_enable_icon_title),
                                // desc = stringResource(id = R.string.settings_performance_ui_notification_basic_enable_icon_desc),
                                onClick = {
                                    SettingsLibrary.NotificationEnableIcon =
                                        !SettingsLibrary.NotificationEnableIcon
                                    MediaController.mediaControl?.let {
                                        YosPlaybackService().setCustomButtons(
                                            it
                                        )
                                    }
                                },
                                checkedLambda = { SettingsLibrary.NotificationEnableIcon }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_notification_basic_enable_icon_desc))

                        GroupSpacer()

                        ListHeader(content = stringResource(id = R.string.settings_performance_notification_others))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_performance_ui_notification_others_smaller_icon_title),
                                // desc = stringResource(id = R.string.settings_performance_ui_notification_others_smaller_icon_desc),
                                onClick = {
                                    SettingsLibrary.NotificationSmallerIcon =
                                        !SettingsLibrary.NotificationSmallerIcon
                                    mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                                },
                                checkedLambda = { SettingsLibrary.NotificationSmallerIcon }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_performance_ui_notification_others_smaller_icon_desc))
                        GroupSpacer()
                    }
                }
            }
        )
    }