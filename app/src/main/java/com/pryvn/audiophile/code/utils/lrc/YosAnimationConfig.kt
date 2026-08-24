package com.pryvn.audiophile.code.utils.lrc

import androidx.compose.runtime.Stable

/**
 * YosLyricView animation control class
 *
 * Currently limited options, awaiting future development
 *
 * @param ignoreSystemAnimationScale Whether to ignore system Animator duration scale. Note: this has significant compatibility issues, keeping default is recommended
 */
@Stable
data class YosAnimationConfig(
    val ignoreSystemAnimationScale: Boolean = false
)