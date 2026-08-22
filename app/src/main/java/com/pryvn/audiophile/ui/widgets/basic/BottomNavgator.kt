package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.ui.animation.pressableFeedback
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.withNight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials


@Stable
data class NavItem(val label: String, val iconResId: Int)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier,
    hazeState: HazeState = remember { HazeState() },
) {
    val navBarHeight = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() + 64.dp }
    Box(
        modifier
            .fillMaxWidth()
            .height(navBarHeight)
    ) {
        // Apple-style translucent material background with blur
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .align(Alignment.BottomCenter)
                .then(
                    if (SettingsLibrary.BarBlurEffect)
                        Modifier.hazeChild(
                            hazeState,
                            HazeMaterials.thick(Color.White withNight Color.Black)
                                .copy(
                                    blurRadius = 40.dp
                                )
                        )
                    else
                        Modifier.background(Color.White withNight Color.Black)
                )
        )
        // Thin top separator — Apple's hairline divider
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .align(Alignment.TopCenter)
                .background((Color.Black withNight Color.White).copy(alpha = 0.12f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { item ->
                NavigatorItem(item, nowLabel, onLabelChange)
            }
        }
    }
}

@Composable
fun RowScope.NavigatorItem(
    item: NavItem,
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit
) {
    val isSelected = remember(item) {
        derivedStateOf { nowLabel() == item.label }
    }
    val navInteraction = remember { MutableInteractionSource() }
    val color by animateColorAsState(
        targetValue = if (isSelected.value) MaterialTheme.colorScheme.primary else Color(0xFF8E8E93),
        animationSpec = spring(stiffness = 350f, dampingRatio = 0.85f),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected.value) 1f else 0.92f,
        animationSpec = spring(stiffness = 350f, dampingRatio = 0.85f),
        label = "navScale",
    )

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .pressableFeedback(navInteraction, pressedScale = 0.85f, pressedAlpha = 0.65f)
            .clickable(
                interactionSource = navInteraction,
                indication = null,
            ) {
                onLabelChange(item.label)
            }
    ) {
        Icon(
            painterResource(item.iconResId),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        Text(
            item.label,
            color = color,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (isSelected.value) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = SfProFontFamily,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}