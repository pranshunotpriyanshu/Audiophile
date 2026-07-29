package com.pryvn.audiophile.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.pryvn.audiophile.ui.theme.headline
import com.pryvn.audiophile.ui.theme.headlineDark
import com.pryvn.audiophile.ui.theme.withNight

/**
 * Settings item horizontal divider
 */
@Composable
fun Divider(modifier: Modifier = Modifier) =
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(start = 15.dp)
            .height(0.3.dp)
            .alpha(0.2f)
            .background(color = headline withNight headlineDark)
    )

/**
 * Settings section padding, usually above ListHeader
 */
@Composable
fun GroupSpacer(modifier: Modifier = Modifier) =
    Spacer(
        modifier
            .height(18.dp)
    )

/**
 * Settings section padding, usually above ListHeader
 */
@Composable
fun GroupSpacerMedium(modifier: Modifier = Modifier) =
    Spacer(
        modifier
            .height(14.dp)
    )