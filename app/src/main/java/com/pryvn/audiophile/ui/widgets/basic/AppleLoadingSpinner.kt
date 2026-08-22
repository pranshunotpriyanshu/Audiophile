package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val bg = MaterialTheme.colorScheme.background
    val isDarkBg = (0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue) < 0.5f

    val resId = spinnerRes ?: if (isDarkBg) {
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
