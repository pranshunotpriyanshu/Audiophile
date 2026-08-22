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

@Composable
fun Divider(modifier: Modifier = Modifier) =
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .alpha(0.15f)
            .background(color = headline withNight headlineDark)
    )

@Composable
fun GroupSpacer(modifier: Modifier = Modifier) =
    Spacer(
        modifier
            .height(20.dp)
    )

@Composable
fun GroupSpacerMedium(modifier: Modifier = Modifier) =
    Spacer(
        modifier
            .height(14.dp)
    )
