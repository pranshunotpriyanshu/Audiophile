package com.pryvn.audiophile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary

val SfProFontFamily = FontFamily(
    Font(R.font.sf_pro_display_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.sf_pro_display_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_heavy_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.sf_pro_display_black_italic, FontWeight.Black, FontStyle.Italic),
)

fun fontWeightFromString(weight: String): FontWeight = when (weight) {
    "Thin" -> FontWeight.Thin
    "ExtraLight" -> FontWeight.ExtraLight
    "Light" -> FontWeight.Light
    "Regular" -> FontWeight.Normal
    "Medium" -> FontWeight.Medium
    "SemiBold" -> FontWeight.SemiBold
    "Bold" -> FontWeight.Bold
    "ExtraBold" -> FontWeight.ExtraBold
    "Black" -> FontWeight.Black
    else -> FontWeight.Normal
}

@Composable
fun userFontWeight(): FontWeight = fontWeightFromString(SettingsLibrary.AppFontWeight)

@Composable
fun headingFontWeight(): FontWeight {
    val baseWeight = userFontWeight().weight
    return FontWeight((baseWeight + 100).coerceAtMost(900))
}

@Composable
fun screenTitleFontWeight(): FontWeight = FontWeight.Bold

fun buildTypography(
    fontFamily: FontFamily = SfProFontFamily,
    fontWeight: FontWeight = fontWeightFromString(SettingsLibrary.AppFontWeight),
): Typography = Typography(
    displayLarge = TextStyle(fontFamily = fontFamily, fontSize = 57.sp, fontWeight = fontWeight, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = fontFamily, fontSize = 45.sp, fontWeight = fontWeight, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = fontFamily, fontSize = 36.sp, fontWeight = fontWeight, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = fontFamily, fontSize = 32.sp, fontWeight = fontWeight, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = fontFamily, fontSize = 28.sp, fontWeight = fontWeight, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = fontFamily, fontSize = 24.sp, fontWeight = fontWeight, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = fontFamily, fontSize = 22.sp, fontWeight = fontWeight, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, fontWeight = fontWeight, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, fontWeight = fontWeight, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, fontWeight = fontWeight, lineHeight = 20.8.sp, letterSpacing = 0.0618.sp),
    bodyMedium = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, fontWeight = fontWeight, lineHeight = 19.2.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, fontWeight = fontWeight, lineHeight = 17.6.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, fontWeight = fontWeight, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, fontWeight = fontWeight, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = fontFamily, fontSize = 11.sp, fontWeight = fontWeight, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
