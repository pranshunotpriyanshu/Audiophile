package com.pryvn.audiophile.np

import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.np.haptics.HapticPreset
import com.pryvn.audiophile.np.haptics.NowPlayingHaptics
import com.pryvn.audiophile.np.models.DefaultSettingsSnapshot
import com.pryvn.audiophile.np.models.NowPlayingSettingsSnapshot

object NpBackend {
    val isPlaying get() = MediaViewModelObject.isPlaying.value

    fun settingsSnapshot(): NowPlayingSettingsSnapshot = DefaultSettingsSnapshot(
        nowPlayingTranslation = SettingsLibrary.NowPlayingTranslation,
        showVolumeBar = SettingsLibrary.NowPlayingShowVolumeBar,
        staticFullScreenAlbum = SettingsLibrary.NowplayingFullScreenStaticArtwork,
        fengShaderEnabled = false, hapticFeedback = true, barBlurEffect = true, screenScale = 1f
    )

    fun hapticTick(context: android.content.Context) =
        NowPlayingHaptics.perform(context, settingsSnapshot(), HapticPreset.TICK)
    fun hapticClick(context: android.content.Context) =
        NowPlayingHaptics.perform(context, settingsSnapshot(), HapticPreset.CLICK)
}

object FlamingoHapticsBridge {
    @Volatile private var ctx: android.content.Context? = null
    fun init(context: android.content.Context) { ctx = context.applicationContext; NowPlayingHaptics.appContext = ctx }
}

object NpResourceBridges {
    fun install(context: android.content.Context) { /* lazy resolution via fallbacks */ }
}
