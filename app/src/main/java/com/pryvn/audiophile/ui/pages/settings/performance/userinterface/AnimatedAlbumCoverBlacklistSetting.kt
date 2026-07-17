package com.pryvn.audiophile.ui.pages.settings.performance.userinterface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@Composable
fun AnimatedAlbumCoverBlacklistSetting(navController: NavController)
{
    SettingBackground {
        Title(
            title = stringResource(id = R.string.settings_library_animated_album_cover_blacklist),
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("AnimatedAlbumCoverBlacklistField") {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RoundColumn {
                        BasicTextField(
                            value = SettingsLibrary.AnimatedAlbumCoverBlacklist,
                            onValueChange = {
                                SettingsLibrary.AnimatedAlbumCoverBlacklist = it
                            },
                            textStyle = TextStyle(
                                color = Color.Black withNight Color.White,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (SettingsLibrary.AnimatedAlbumCoverBlacklist.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.settings_library_animated_album_cover_blacklist_placeholder),
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.alpha(0.45f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    ListHeader(content = stringResource(id = R.string.settings_library_animated_album_cover_blacklist_hint))
                }
            }
        }
    }
}
