package com.pryvn.audiophile.ui.widgets.basic

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.pryvn.audiophile.R

@Composable
fun AppleLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    spinnerRes: Int? = null,  // optional override
) {
    // Detect dark mode using Configuration
    val configuration = LocalConfiguration.current
    val isDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    // Choose resource: override or auto
    val resId = spinnerRes ?: if (isDark) {
        R.raw.ios_spinner_white
    } else {
        R.raw.ios_spinner_black
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(resId)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Int.MAX_VALUE,
        isPlaying = true
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(size)
    )
}