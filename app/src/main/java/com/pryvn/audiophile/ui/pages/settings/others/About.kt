package com.pryvn.audiophile.ui.pages.settings.others

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.blankj.utilcode.util.AppUtils
import com.pryvn.audiophile.R
import com.pryvn.audiophile.ui.pages.settings.DefaultItem
import com.pryvn.audiophile.ui.pages.settings.GroupSpacer
import com.pryvn.audiophile.ui.pages.settings.LabelItem
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.pages.settings.startWeb
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@Composable
fun About(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_others_about),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        val context = LocalContext.current

                        ListHeader(content = stringResource(id = R.string.settings_others_about_info))
                        val appVersion = remember("About_appVersion") {
                            mutableStateOf(AppUtils.getAppVersionName())
                        }

                        RoundColumn {
                            DefaultItem(
                                title = stringResource(id = R.string.app_name),
                                onClick = null,
                                desc = appVersion.value
                            )
                        }

                        GroupSpacer()

                        ListHeader(content = stringResource(id = R.string.settings_others_about_developers))
                        RoundColumn {
                            LabelItem(
                                title = "pranshunotpriyanshu",
                                desc = stringResource(id = R.string.settings_others_about_developers_yos_x)
                            ) {
                                startWeb(
                                    url = "https://github.com/pranshunotpriyanshu",
                                    context
                                )
                            }
                        }
                    }
                }
            }
        )
    }