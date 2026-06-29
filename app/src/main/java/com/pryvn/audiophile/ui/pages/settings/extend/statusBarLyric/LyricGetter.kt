package com.pryvn.audiophile.ui.pages.settings.extend.statusBarLyric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cn.lyric.getter.api.API
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.pages.settings.DefaultItem
import com.pryvn.audiophile.ui.pages.settings.Divider
import com.pryvn.audiophile.ui.pages.settings.GroupSpacer
import com.pryvn.audiophile.ui.pages.settings.GroupSpacerMedium
import com.pryvn.audiophile.ui.pages.settings.LabelItem
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.pages.settings.SwitchItem
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

@Composable
fun LyricGetter(navController: NavController) =
    YosWrapper {
        val lyricAPI by lazy { API() }
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondary)
            /*.statusBarsPadding()*/
        ) {
            val apiVersion = remember("apiVersion") {
                mutableIntStateOf(API.API_VERSION)
            }
            Title(title = stringResource(id = R.string.settings_extend_statusbarlyric),
                subTitle = "Lyric Getter API ${apiVersion.intValue}",
                onBack = {
                    navController.popBackStack()
                },
                content = {
                    item("settings") {
                        Column(Modifier.fillMaxSize()) {
                            // ListHeader(stringResource(id = R.string.settings_others_statusbarlyric_basic))

                            RoundColumn {
                                SwitchItem(
                                    title = stringResource(id = R.string.settings_others_statusbarlyric_basic_switch),
                                    onClick = {
                                        SettingsLibrary.StatusBarLyricEnabled =
                                            !SettingsLibrary.StatusBarLyricEnabled
                                        lyricAPI.clearLyric()
                                    },
                                    checkedLambda = { SettingsLibrary.StatusBarLyricEnabled }
                                )

                                Divider()

                                DefaultItem(
                                    title = stringResource(id = R.string.settings_others_statusbarlyric_basic_status),
                                    onClick = null,
                                    desc = stringResource(id = if (SettingsLibrary.StatusBarLyricHooked) R.string.settings_others_statusbarlyric_basic_status_enabled else R.string.settings_others_statusbarlyric_basic_status_disabled)
                                )
                            }

                            GroupSpacer()

                            ListHeader(stringResource(id = R.string.settings_others_statusbarlyric_debug))

                            RoundColumn {
                                LabelItem(
                                    title = stringResource(id = R.string.settings_others_statusbarlyric_debug_send_lyric),
                                    // desc = stringResource(id = R.string.settings_others_statusbarlyric_debug_send_lyric_desc),
                                    superLink = true
                                ) {
                                    lyricAPI.sendLyric("[${(99999..9999999).random()}] Audiophile Lyric Debug")
                                }
                            }
                            ListHeader(content = stringResource(id = R.string.settings_others_statusbarlyric_debug_send_lyric_desc))

                            GroupSpacerMedium()
                            RoundColumn {
                                LabelItem(
                                    title = stringResource(id = R.string.settings_others_statusbarlyric_debug_clear_lyric),
                                    // desc = stringResource(id = R.string.settings_others_statusbarlyric_debug_clear_lyric_desc),
                                    superLink = true
                                ) {
                                    lyricAPI.clearLyric()
                                }
                            }
                            ListHeader(content = stringResource(id = R.string.settings_others_statusbarlyric_debug_clear_lyric_desc))
                        }

                    }
                }
            )
        }
    }