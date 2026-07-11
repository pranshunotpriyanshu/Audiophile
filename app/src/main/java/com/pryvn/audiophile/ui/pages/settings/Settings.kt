package com.pryvn.audiophile.ui.pages.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.code.api.InnerTubeClient
import moe.rukamori.archivetune.innertube.YouTube
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.AppleConfirmSheet
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navController: NavController) =
    SettingBackground {
        val context = LocalContext.current
        Title(title = stringResource(id = R.string.page_settings_title),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        // ---- Account section at TOP ----
                        ListHeader(stringResource(id = R.string.settings_account))
                        RoundColumn {
                            val isLoggedIn = SettingsLibrary.isYtMusicLoggedIn
                            if (isLoggedIn) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val avatarUrl = SettingsLibrary.YtMusicAvatarUrl
                                    if (avatarUrl.isNotBlank()) {
                                        CachedArtworkImage(
                                            url = avatarUrl,
                                            contentDescription = null,
                                            size = 128,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(MaterialTheme.shapes.extraLarge),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.songcredits_monogram_person),
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = SettingsLibrary.YtMusicAccountName,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        val email = SettingsLibrary.YtMusicAccountEmail
                                        if (email.isNotBlank()) {
                                            Text(
                                                text = email,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                }
                                Divider()
                                SwitchItem(
                                    title = stringResource(R.string.ytmusic_sync),
                                    desc = stringResource(R.string.ytmusic_sync_desc),
                                    onClick = {
                                        SettingsLibrary.YtMusicSyncEnabled =
                                            !SettingsLibrary.YtMusicSyncEnabled
                                    },
                                    checkedLambda = { SettingsLibrary.YtMusicSyncEnabled }
                                )
                                Divider()
                                var showLogoutDialog by remember { mutableStateOf(false) }
                                LabelItem(
                                    title = stringResource(R.string.ytmusic_logout),
                                    superLink = true
                                ) {
                                    showLogoutDialog = true
                                }
                                if (showLogoutDialog) {
                                    AppleConfirmSheet(
                                        title = stringResource(R.string.ytmusic_logout),
                                        message = stringResource(R.string.ytmusic_logout_confirm),
                                        confirmText = "Log Out",
                                        cancelText = "Cancel",
                                        onConfirm = {
                                            SettingsLibrary.YtMusicCookie = ""
                                            SettingsLibrary.YtMusicVisitorData = ""
                                            SettingsLibrary.YtMusicDataSyncId = ""
                                            SettingsLibrary.YtMusicAccountName = ""
                                            SettingsLibrary.YtMusicAccountEmail = ""
                                            SettingsLibrary.YtMusicAvatarUrl = ""
                                            SettingsLibrary.YtMusicSyncEnabled = true
                                            InnerTubeClient.cookie = null
                                            InnerTubeClient.visitorData = null
                                            InnerTubeClient.dataSyncId = null
                                            YouTube.cookie = null
                                            YouTube.visitorData = null
                                            YouTube.dataSyncId = null
                                            com.pryvn.audiophile.code.playback.SimpMusicStreamResolver.updateAuth(
                                                cookie = null,
                                                visitorData = null,
                                                dataSyncId = null,
                                            )
                                            showLogoutDialog = false
                                        },
                                        onDismiss = { showLogoutDialog = false },
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.ytmusic_login_desc),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                                Button(
                                    onClick = {
                                        navController.toUI(UI.YTMusicLogin)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ytmusic_login),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        GroupSpacer()
                        // ---- Local Music section ----
                        ListHeader(stringResource(id = R.string.settings_local_music_title))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(R.string.settings_local_music),
                                desc = stringResource(R.string.settings_local_music_desc),
                                onClick = {
                                    SettingsLibrary.LocalMusicEnabled =
                                        !SettingsLibrary.LocalMusicEnabled
                                },
                                checkedLambda = { SettingsLibrary.LocalMusicEnabled }
                            )
                            if (SettingsLibrary.LocalMusicEnabled) {
                                Divider()
                                SwitchItem(
                                    title = stringResource(id = R.string.settings_library_refresh_everytime),
                                    onClick = {
                                        SettingsLibrary.RefreshEveryTime =
                                            !SettingsLibrary.RefreshEveryTime
                                    },
                                    checkedLambda = { SettingsLibrary.RefreshEveryTime }
                                )

                                Divider()
                                LabelItem(title = stringResource(id = R.string.settings_library_overview)) {
                                    navController.toUI(UI.Settings.LibraryOverview)
                                }
                                Divider()
                                val scope = rememberCoroutineScope()
                                LabelItem(
                                    title = stringResource(id = R.string.settings_library_refresh_now),
                                    superLink = true
                                ) {
                                    scope.launch(Dispatchers.Main) {
                                        var toast = Toast.makeText(
                                            context,
                                            R.string.tip_scanning,
                                            Toast.LENGTH_SHORT
                                        )
                                        toast.show()
                                        withContext(Dispatchers.IO) {
                                            MusicLibrary.scanMedia(context)
                                        }
                                        toast.cancel()
                                        val size = MediaController.mainMusicList.size
                                        if (size == 0) {
                                            toast = Toast.makeText(
                                                context,
                                                R.string.tip_no_song,
                                                Toast.LENGTH_SHORT
                                            )
                                        } else {
                                            val msg =
                                                context.getString(R.string.tip_scan_finished, size)
                                            toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                        }
                                        toast.show()
                                    }
                                }
                            }
                        }

                        GroupSpacer()
                        // ---- Performance section ----
                        ListHeader(stringResource(id = R.string.settings_performance))
                        RoundColumn {
                            LabelItem(title = stringResource(id = R.string.settings_performance_lyric_title)) {
                                navController.toUI(UI.Settings.LyricSetting)
                            }
                            Divider()
                            LabelItem(title = stringResource(id = R.string.settings_performance_ui_title)) {
                                navController.toUI(UI.Settings.UserInterfaceSetting)
                            }
                            Divider()
                            LabelItem(title = stringResource(id = R.string.settings_performance_notification_title)) {
                                navController.toUI(UI.Settings.NotificationSetting)
                            }
                        }

                        GroupSpacer()
                        // ---- Audio section ----
                        ListHeader(stringResource(id = R.string.settings_audio))
                        RoundColumn {
                            LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer)) {
                                navController.toUI(UI.Settings.ExoplayerSetting)
                            }
                            Divider()
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_fade_in_out),
                                onClick = { },
                                checkedLambda = { SettingsLibrary.FadePlay }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_audio_fade_in_out_desc))

                        GroupSpacer()
                        // ---- Play section ----
                        ListHeader(stringResource(id = R.string.settings_play))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_play_history),
                                onClick = { },
                                checkedLambda = { SettingsLibrary.ListenHistory }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_play_history_desc))

                        GroupSpacer()
                        // ---- Others section ----
                        ListHeader(stringResource(id = R.string.settings_others))
                        RoundColumn {
                            LabelItem(
                                title = stringResource(id = R.string.settings_others_about),
                            ) {
                                navController.toUI(UI.Settings.About)
                            }
                        }
                    }
                }
            })
    }

fun safeStartActivity(context: Context, intent: Intent, options: Bundle?) {
    if (intent.resolveActivity(context.packageManager) != null) {
        ContextCompat.startActivity(context, intent, options)
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.tip_intent_resolve_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun startWeb(url: String, context: Context) {
    try {
        val uri: Uri =
            Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        safeStartActivity(context, intent, null)
    } catch (_: Exception) {
    }
}
