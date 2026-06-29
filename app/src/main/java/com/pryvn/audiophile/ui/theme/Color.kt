package com.pryvn.audiophile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pryvn.audiophile.data.libraries.SettingsLibrary

// Apple Music inspired color palette
val appleMusicRed = Color(0xFFFA0B34)
val appleMusicRedDark = Color(0xFFFF3B5C)
val appleMusicBackground = Color.White
val appleMusicBackgroundDark = Color(0xFF000000)
val appleMusicSurface = Color(0xFFF2F2F7)
val appleMusicSurfaceDark = Color(0xFF1C1C1E)
val appleMusicSecondarySurface = Color(0xFFE5E5EA)
val appleMusicSecondarySurfaceDark = Color(0xFF2C2C2E)
val appleMusicSeparator = Color(0xFFC6C6C8)
val appleMusicSeparatorDark = Color(0xFF3A3A3C)
val appleMusicTextPrimary = Color(0xFF000000)
val appleMusicTextPrimaryDark = Color(0xFFFFFFFF)
val appleMusicTextSecondary = Color(0xFF8E8E93)
val appleMusicTextSecondaryDark = Color(0xFF8E8E93)
val appleMusicTextTertiary = Color(0xFF8E8E93).copy(alpha = 0.6f)
val appleMusicTextTertiaryDark = Color(0xFF8E8E93).copy(alpha = 0.6f)

val primary = appleMusicRed
val primaryDark = appleMusicRedDark
val headline = appleMusicTextSecondary
val headlineDark = appleMusicTextSecondaryDark
val background = appleMusicBackground
val backgroundDark = appleMusicBackgroundDark

val settingBack = appleMusicSurface
val settingBackDark = appleMusicSurfaceDark
val settingContainerBack = appleMusicBackground
val settingContainerBackDark = appleMusicSecondarySurface

@Composable
infix fun Color.withNight(nightColor: Color): Color {
    return if (isAudiophileInDarkMode()) nightColor else this
}

@Composable
fun isAudiophileInDarkMode(): Boolean {
    return if (SettingsLibrary.CustomTheme == "Auto") isSystemInDarkTheme() else SettingsLibrary.CustomTheme == "Dark"
}

// Apple Music style gradients
val appleMusicGradient = listOf(
    Color(0xFFFA0B34),
    Color(0xFFFF3B5C),
    Color(0xFFFF6B8A)
)

val appleMusicGradientDark = listOf(
    Color(0xFFFF3B5C),
    Color(0xFFFA0B34),
    Color(0xFFFF6B8A)
)

val appleMusicCardGradient = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFF2F2F7)
)

val appleMusicCardGradientDark = listOf(
    Color(0xFF2C2C2E),
    Color(0xFF1C1C1E)
)