package com.pryvn.audiophile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    background = backgroundDark,
    onBackground = background,
    surface = backgroundDark,
    onSurface = background,
    surfaceVariant = settingBackDark,
    onSurfaceVariant = background,
    secondary = settingContainerBackDark,
    onSecondary = settingBackDark,
    outline = appleMusicTextSecondaryDark,
    outlineVariant = appleMusicSeparatorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = primary,
    background = background,
    onBackground = backgroundDark,
    surface = background,
    onSurface = backgroundDark,
    surfaceVariant = settingBack,
    onSurfaceVariant = backgroundDark,
    secondary = settingContainerBack,
    onSecondary = settingBack,
    outline = appleMusicTextSecondary,
    outlineVariant = appleMusicSeparator,
)

@Composable
fun YosMusicTheme(
    darkTheme: Boolean = isAudiophileInDarkMode(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        YosWrapper {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    !darkTheme
            }
        }
    }

    val appTypography = buildTypography()

    // Apply the app-wide font-size setting as a global density font scale so every
    // .sp text — including components with hardcoded sizes — follows the setting
    // instead of only the Material body styles.
    val baseDensity = LocalDensity.current
    val appFontScale = (SettingsLibrary.AppFontSize / 16f).coerceIn(0.75f, 2f)
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * appFontScale
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography,
            content = content
        )
    }
}