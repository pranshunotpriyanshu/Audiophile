package com.pryvn.audiophile.ui.pages.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.ui.theme.headline
import com.pryvn.audiophile.ui.theme.headlineDark
import com.pryvn.audiophile.ui.theme.withNight

@Composable
fun ListHeader(content: String) = Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp, vertical = 5.dp)
) {
    Text(text = content, fontSize = 13.sp, lineHeight = 15.sp, color = headline withNight headlineDark)
}