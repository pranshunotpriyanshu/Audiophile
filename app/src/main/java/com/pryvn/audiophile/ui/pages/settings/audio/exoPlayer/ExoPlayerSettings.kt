package com.pryvn.audiophile.ui.pages.settings.audio.exoPlayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.UI
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
fun ExoPlayerSettings(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_audio_exoplayer),
            subTitle = stringResource(id = R.string.settings_audio_exoplayer_sub),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {

                        ListHeader(stringResource(id = R.string.settings_audio_exoplayer_behaviors))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_behaviors_audio_attributes),
                                // desc = stringResource(id = R.string.settings_audio_exoplayer_behaviors_audio_attributes_desc),
                                onClick = {
                                    SettingsLibrary.AudioAttributes =
                                        !SettingsLibrary.AudioAttributes
                                },
                                checkedLambda = { SettingsLibrary.AudioAttributes }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_audio_exoplayer_behaviors_audio_attributes_desc))

                        GroupSpacer()

                        ListHeader(stringResource(id = R.string.settings_audio_exoplayer_decode))
                        RoundColumn {
                            SelectItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_decode_codec),
                                items = listOf(
                                    "Auto",
                                    "FFmpeg",
                                    "System"
                                ),
                                value = SettingsLibrary.Codec,
                                onValueChange = {
                                    SettingsLibrary.Codec = it
                                }
                            )

                            Divider()

                            LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer_support_mediacodec)) {
                                navController.navigate(UI.Settings.MediaCodec)
                            }

                            Divider()

                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_decode_hardware_audio_track_playback_params),
                                onClick = {
                                    SettingsLibrary.HardwareAudioTrackPlayBackParams =
                                        !SettingsLibrary.HardwareAudioTrackPlayBackParams
                                },
                                checkedLambda = { SettingsLibrary.HardwareAudioTrackPlayBackParams }
                            )
                        }

                        GroupSpacerMedium()

                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_decode_audio_float_output),
                                // desc = stringResource(id = R.string.settings_audio_exoplayer_decode_audio_float_output_desc),
                                onClick = {
                                    SettingsLibrary.AudioFloatOutput =
                                        !SettingsLibrary.AudioFloatOutput
                                },
                                checkedLambda = { SettingsLibrary.AudioFloatOutput }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_audio_exoplayer_decode_audio_float_output_desc))

                        GroupSpacerMedium()

                        ListHeader(stringResource(id = R.string.settings_audio_exoplayer_ui))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_exoplayer_full_screen_static_artwork),
                                desc = stringResource(id = R.string.settings_audio_exoplayer_full_screen_static_artwork_desc),
                                onClick = {
                                    SettingsLibrary.NowplayingFullScreenStaticArtwork =
                                        !SettingsLibrary.NowplayingFullScreenStaticArtwork
                                },
                                checkedLambda = { SettingsLibrary.NowplayingFullScreenStaticArtwork }
                            )

                            Divider()

                            SelectItem(
                                enabled = !SettingsLibrary.NowplayingFullScreenStaticArtwork,
                                title = stringResource(id = R.string.settings_audio_exoplayer_background),
                                desc = stringResource(id = R.string.settings_audio_exoplayer_background_desc),
                                items = listOf("Solid", "Blurred"),
                                value = SettingsLibrary.NowPlayingBackground,
                                onValueChange = {
                                    SettingsLibrary.NowPlayingBackground = it
                                }
                            )
                        }

                        GroupSpacer()
                    }

                }
            }
        )
    }
