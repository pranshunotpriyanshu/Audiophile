package com.pryvn.audiophile.ui.pages.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.pryvn.audiophile.code.cache.AudioCacheStore
import com.pryvn.audiophile.code.lyrics.LyricsCacheStore
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.theme.userFontWeight
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.code.api.InnerTubeClient
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.innertube.YouTube as AppYouTube
import moe.rukamori.archivetune.innertube.YouTube
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.pages.ytmusic.YtMusicLoginSheet
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.AppleConfirmSheet
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navController: NavController) =
    SettingBackground {
        val context = LocalContext.current
        var showClearAudioConfirm by remember { mutableStateOf(false) }
        var showClearLyricsConfirm by remember { mutableStateOf(false) }
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

                            // Mirror the YT Music account fields into local state:
                            // SettingsLibrary properties are @Stable, so reads here
                            // are not recomposition-tracked. The effect below refreshes
                            // the profile from the API on open and when login state
                            // changes; the mirrors make that update visible immediately.
                            var accountName by remember {
                                mutableStateOf(SettingsLibrary.YtMusicAccountName)
                            }
                            var accountHandle by remember {
                                mutableStateOf(SettingsLibrary.YtMusicChannelHandle)
                            }
                            var accountAvatar by remember {
                                mutableStateOf(SettingsLibrary.YtMusicAvatarUrl)
                            }

                            // Refresh the YT Music account profile whenever this screen
                            // opens (and when login state changes) so the name and
                            // @handle always display, even if they were never saved.
                            LaunchedEffect(isLoggedIn) {
                                if (isLoggedIn) {
                                    YouTubeApi.fetchAccountInfo().onSuccess { info ->
                                        SettingsLibrary.YtMusicAccountName = info.name
                                        SettingsLibrary.YtMusicChannelHandle =
                                            info.channelHandle ?: ""
                                        SettingsLibrary.YtMusicAvatarUrl =
                                            info.avatarUrl ?: ""
                                        accountName = info.name
                                        accountHandle = info.channelHandle ?: ""
                                        accountAvatar = info.avatarUrl ?: ""
                                    }
                                }
                            }

                            if (isLoggedIn) {
                                // The profile picture option sits at the top and
                                // replaces the online profile display when logged in:
                                // its title becomes the YT Music account name and its
                                // subtext becomes the channel handle (@handle). The
                                // online YT avatar is shown until the user picks a
                                // local picture — then the locally chosen one wins.
                                ProfilePictureRow(
                                    title = accountName
                                        .ifBlank { stringResource(R.string.profile_picture) },
                                    subtitle = accountHandle
                                        .ifBlank { stringResource(R.string.profile_picture_change) },
                                    avatarUrl = SettingsLibrary.ProfilePictureUri
                                        .ifBlank { accountAvatar },
                                )
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
                                        SettingsLibrary.YtMusicChannelHandle = ""
                                        SettingsLibrary.YtMusicSyncEnabled = true
                                        InnerTubeClient.cookie = null
                                        InnerTubeClient.visitorData = null
                                        InnerTubeClient.dataSyncId = null
                                        YouTube.cookie = null
                                        YouTube.visitorData = null
                                        YouTube.dataSyncId = null
                                        AppYouTube.cookie = null
                                        AppYouTube.visitorData = null
                                        AppYouTube.dataSyncId = null
                                        com.pryvn.audiophile.archivetune.ArchiveTuneAdapter.updateAuth(
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
                                    YtMusicLoginSheet.isOpen = true
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
                                    fontWeight = userFontWeight(),
                                )
                            }
                        }

                        Divider()
                        LabelItem(
                            title = stringResource(id = R.string.settings_import_spotify),
                        ) {
                            Toast.makeText(
                                context,
                                R.string.settings_import_spotify_soon,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (!isLoggedIn) {
                            Divider()
                            ProfilePictureRow(
                                title = stringResource(R.string.profile_picture),
                                subtitle = if (SettingsLibrary.ProfilePictureUri.isNotBlank()) {
                                    stringResource(R.string.profile_picture_change)
                                } else {
                                    stringResource(R.string.profile_picture_set)
                                },
                                avatarUrl = SettingsLibrary.ProfilePictureUri,
                            )
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
                        // ---- Cache section ----
                        ListHeader(stringResource(id = R.string.settings_cache_title))
                        RoundColumn {
                            // Compose-observable counts: refresh automatically when
                            // a background download completes or a cache is cleared.
                            val audioCount = AudioCacheStore.cachedCount
                            val audioBytes = AudioCacheStore.cachedBytes
                            val lyricCount = LyricsCacheStore.cachedCount
                            CacheStatItem(
                                title = stringResource(id = R.string.settings_cache_cached_songs),
                                value = audioCount.toString(),
                            )
                            Divider()
                            CacheStatItem(
                                title = stringResource(id = R.string.settings_cache_lyrics),
                                value = lyricCount.toString(),
                            )
                            Divider()
                            CacheStatItem(
                                title = stringResource(id = R.string.settings_cache_audio_size),
                                value = AudioCacheStore.formatBytes(audioBytes),
                            )
                            Divider()
                            LabelItem(
                                title = stringResource(id = R.string.settings_cache_clear_audio),
                                superLink = true,
                            ) {
                                showClearAudioConfirm = true
                            }
                            Divider()
                            LabelItem(
                                title = stringResource(id = R.string.settings_cache_clear_lyrics),
                                superLink = true,
                            ) {
                                showClearLyricsConfirm = true
                            }
                        }

                        if (showClearAudioConfirm) {
                            AppleConfirmSheet(
                                title = stringResource(R.string.settings_cache_clear_audio_confirm_title),
                                message = stringResource(R.string.settings_cache_clear_audio_confirm_message),
                                confirmText = stringResource(R.string.settings_cache_clear_confirm),
                                cancelText = stringResource(R.string.playlist_picker_cancel),
                                onConfirm = {
                                    showClearAudioConfirm = false
                                    AudioCacheStore.clear()
                                    Toast.makeText(
                                        context,
                                        R.string.settings_cache_audio_cleared,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onDismiss = { showClearAudioConfirm = false },
                            )
                        }
                        if (showClearLyricsConfirm) {
                            AppleConfirmSheet(
                                title = stringResource(R.string.settings_cache_clear_lyrics_confirm_title),
                                message = stringResource(R.string.settings_cache_clear_lyrics_confirm_message),
                                confirmText = stringResource(R.string.settings_cache_clear_confirm),
                                cancelText = stringResource(R.string.playlist_picker_cancel),
                                onConfirm = {
                                    showClearLyricsConfirm = false
                                    LyricsCacheStore.clear()
                                    MediaViewModelObject.lyricsCache.clear()
                                    Toast.makeText(
                                        context,
                                        R.string.settings_cache_lyrics_cleared,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onDismiss = { showClearLyricsConfirm = false },
                            )
                        }

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

@Composable
private fun CacheStatItem(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 16.5.sp,
            lineHeight = 20.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            modifier = Modifier.alpha(0.5f),
        )
    }
}

@Composable
private fun ProfilePictureRow(
    title: String,
    subtitle: String,
    avatarUrl: String,
) {
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) { }
        SettingsLibrary.ProfilePictureUri = uri.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                imagePicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl.isNotBlank()) {
            CachedArtworkImage(
                url = avatarUrl,
                contentDescription = "Profile picture",
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
                text = title,
                fontSize = 17.sp,
                fontWeight = headingFontWeight(),
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
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
