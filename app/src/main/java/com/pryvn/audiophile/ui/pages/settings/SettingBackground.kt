package com.pryvn.audiophile.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingBackground(content: @Composable BoxScope.() -> Unit) =
    Box(modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.secondary), content = content)