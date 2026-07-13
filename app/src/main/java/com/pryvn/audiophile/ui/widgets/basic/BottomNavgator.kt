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

@Stable
data class NavItem(val label: String, val iconResId: Int)

@Composable
fun BottomNavigator(
    nowLabel: () -> String,
    onLabelChange: (String) -> Unit,
    items: List<NavItem>,
    modifier: Modifier
) {
    val navBarHeight = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() + 64.dp }
    Box(
        modifier
            .fillMaxWidth()
            .height(navBarHeight)
        //.background(Color.White withNight Color.Black)
    ) {
        /*Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.08f)
                .height(1.6.dp)
                .background(Color.Black withNight Color.White)
        )*/
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
) =
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = /*ripple(bounded = true, radius = 68.dp)*/ null
            ) {
                onLabelChange(item.label)
            }
    ) {
        val isSelected = remember(item) {
            derivedStateOf {
                nowLabel() == item.label
            }
        }
        val color = animateColorAsState(
            targetValue = if (isSelected.value) MaterialTheme.colorScheme.primary else Color.Gray,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.82f),
            label = "navTint",
        )

        Icon(
            painterResource(item.iconResId),
            contentDescription = null,
            tint = color.value,
            modifier = Modifier
                .size(30.dp)
                .padding(bottom = 0.dp)
        )
        Text(
            item.label,
            color = color.value,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
