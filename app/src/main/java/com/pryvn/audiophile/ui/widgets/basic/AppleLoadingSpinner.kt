package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.model.KeyPath
import com.pryvn.audiophile.R

@Composable
fun AppleLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 40.dp,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.ios_spinner)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Int.MAX_VALUE,
        isPlaying = true
    )

    val dynamicProperties = rememberLottieDynamicProperties(
        LottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = color.toArgb(),
            keyPath = KeyPath("**")
        )
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(size),
        dynamicProperties = dynamicProperties
    )
}