package com.pryvn.audiophile.ui.pages.settings.performance.userinterface

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.AnimatedArtworkLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.pages.settings.Divider
import com.pryvn.audiophile.ui.pages.settings.LabelItem
import com.pryvn.audiophile.ui.pages.settings.ListHeader
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.pages.settings.SwitchItem
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.RoundColumn
import com.pryvn.audiophile.ui.widgets.basic.Title

@Composable
fun AnimatedAlbumCoversSetting(navController: NavController) =
    SettingBackground {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val videoPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        val animatedAlbumCoverCacheDeleteArmed = remember("AnimatedAlbumCoversSetting_animatedAlbumCoverCacheDeleteArmed") {
            mutableStateOf(false)
        }
        val animatedAlbumCoverCacheSizeBytes = remember("AnimatedAlbumCoversSetting_animatedAlbumCoverCacheSizeBytes") {
            mutableLongStateOf(0L)
        }

        LaunchedEffect(Unit)
        {
            animatedAlbumCoverCacheSizeBytes.longValue = AnimatedArtworkLibrary.cachedArtworkFilesSizeBytes(context)
        }

        Title(title = stringResource(id = R.string.settings_library_animated_album_covers),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_library_animated_album_covers),
                                onClick = {
                                    SettingsLibrary.AnimatedAlbumCovers =
                                        !SettingsLibrary.AnimatedAlbumCovers
                                    if (
                                        SettingsLibrary.AnimatedAlbumCovers &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_MEDIA_VIDEO
                                        ) != PackageManager.PERMISSION_GRANTED
                                    )
                                    {
                                        videoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                                    }
                                },
                                checkedLambda = { SettingsLibrary.AnimatedAlbumCovers }
                            )

                            Divider()

                            LabelItem(
                                title = stringResource(id = R.string.settings_library_animated_album_cover_blacklist),
                            ) {
                                navController.toUI(UI.Settings.AnimatedAlbumCoverBlacklist)
                            }

                            Divider()

                            SwitchItem(
                                title = stringResource(id = R.string.settings_library_animated_album_covers_use_api),
                                onClick = {
                                    SettingsLibrary.AnimatedAlbumCoversUseApi =
                                        !SettingsLibrary.AnimatedAlbumCoversUseApi
                                },
                                checkedLambda = { SettingsLibrary.AnimatedAlbumCoversUseApi }
                            )

                            Divider()

                            LabelItem(
                                title = stringResource(
                                    id = R.string.settings_library_animated_album_cover_cache_clear,
                                    formatAnimatedAlbumCoverCacheSize(animatedAlbumCoverCacheSizeBytes.longValue)
                                ),
                                superLink = true,
                            ) {
                                if (!animatedAlbumCoverCacheDeleteArmed.value)
                                {
                                    animatedAlbumCoverCacheDeleteArmed.value = true
                                    Toast.makeText(
                                        context,
                                        R.string.settings_library_animated_album_cover_cache_clear_confirm,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@LabelItem
                                }

                                animatedAlbumCoverCacheDeleteArmed.value = false
                                scope.launch {
                                    val deletedCount = AnimatedArtworkLibrary.deleteCachedArtworkFiles(context)
                                    animatedAlbumCoverCacheSizeBytes.longValue = AnimatedArtworkLibrary.cachedArtworkFilesSizeBytes(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.settings_library_animated_album_cover_cache_clear_done,
                                            deletedCount
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        ListHeader(content = stringResource(id = R.string.settings_library_animated_album_covers_desc))
                    }
                }
            }
        )
    }

private fun formatAnimatedAlbumCoverCacheSize(sizeBytes: Long): String
{
    if (sizeBytes <= 0L) {return "0Mb"}

    return "${(sizeBytes + 1024L * 1024L - 1L) / (1024L * 1024L)}Mb"
}
