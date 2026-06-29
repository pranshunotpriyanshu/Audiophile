package com.pryvn.audiophile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.R

val SfProFontFamily = FontFamily(
    Font(R.font.sf_pro_text_thin, FontWeight.Thin),
    Font(R.font.sf_pro_text_light, FontWeight.Light),
    Font(R.font.sf_pro_text_regular, FontWeight.Normal),
    Font(R.font.sf_pro_text_medium, FontWeight.Medium),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_text_bold, FontWeight.Bold),
    Font(R.font.sf_pro_text_heavy, FontWeight.ExtraBold),
    Font(R.font.sf_pro_text_black, FontWeight.Black),
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = SfProFontFamily,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.0618.sp,
    )
)
