package com.pryvn.audiophile.code.utils.lrc

import androidx.compose.runtime.Stable

/**
 * YosLyricView UI control class
 *
 * This class only manages options that are not frequently adjusted in daily use. For toggling translation or blur effects, modify them directly when calling YosLyricView()
 *
 * @param edgeFade Whether to enable YosLyricView edge fade effect
 * @param formatText Whether to enable YosLyricView lyrics formatting. When enabled, lyrics will be formatted, e.g., multiple consecutive spaces replaced with single space, leading/trailing spaces removed, etc.
 * @param blankHeight Height of blank padding at the top and bottom of YosLyricView list, in dp
 * @param mainTextSize Main text size, in sp
 * @param subTextSize Secondary text size, in sp. If not set, it will be calculated automatically based on main text size
 * @param mainTextBasicColor Base color of main text
 * @param subTextBasicColor Base color of secondary text
 * @param normalMainTextAlpha Text alpha when the main text of this line is not highlighted
 * @param normalSubTextAlpha Text alpha when the secondary text of this line is not highlighted
 * @param currentMainTextAlpha Text alpha when the main text of this line is highlighted
 * @param currentSubTextAlpha Text alpha when the secondary text of this line is highlighted
 */
@Stable
data class YosUIConfig(
    val edgeFade: Boolean = true,
    val formatText: Boolean = true,
    val noLrcText: String = "No lyrics",
    val blankHeight: Int = 70,
    val mainTextSize: Int = 34,
    val subTextSize: Int = mainTextSize - 18,
    val mainTextBasicColor: Long = 0xFFF2F2F2,
    val subTextBasicColor: Long = 0xFF919191,
    val normalMainTextAlpha: Float = 0.4f,
    val normalSubTextAlpha: Float = 0.3f,
    val currentMainTextAlpha: Float = 0.9f,
    val currentSubTextAlpha: Float = 0.6f
)
