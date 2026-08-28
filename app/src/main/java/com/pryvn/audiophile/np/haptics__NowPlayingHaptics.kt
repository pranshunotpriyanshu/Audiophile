package com.pryvn.audiophile.np.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

enum class HapticPreset(val effectId: Int) {
    CLICK(2), TICK(1), HEAVY_CLICK(5)
}

object NowPlayingHaptics {
    @Volatile
    var appContext: Context? = null

    fun perform(context: Context, settings: com.pryvn.audiophile.np.models.NowPlayingSettingsSnapshot, preset: HapticPreset) {
        if (!settings.hapticFeedback) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(preset.effectId))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(30L)
        }
    }
}
