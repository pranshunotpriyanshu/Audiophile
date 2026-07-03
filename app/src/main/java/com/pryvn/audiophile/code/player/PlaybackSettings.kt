package com.pryvn.audiophile.code.player

import androidx.compose.runtime.Stable
import com.pryvn.audiophile.data.libraries.SettingsLibrary

@Stable
object PlaybackSettings {
    var speed: Float
        get() = SettingsLibrary.PlaybackSpeed
        set(value) { SettingsLibrary.PlaybackSpeed = value }

    var pitch: Float
        get() = SettingsLibrary.PlaybackPitch
        set(value) { SettingsLibrary.PlaybackPitch = value }

    var skipSilence: Boolean
        get() = SettingsLibrary.SkipSilence
        set(value) { SettingsLibrary.SkipSilence = value }

    var normalizeVolume: Boolean
        get() = SettingsLibrary.NormalizeVolume
        set(value) { SettingsLibrary.NormalizeVolume = value }
}
