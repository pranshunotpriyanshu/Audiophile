package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.runtime.Composable

@Composable
inline fun YosWrapper(crossinline content: @Composable () -> Unit) = content()
