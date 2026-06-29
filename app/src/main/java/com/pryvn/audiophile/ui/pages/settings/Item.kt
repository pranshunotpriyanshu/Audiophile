package com.pryvn.audiophile.ui.pages.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper


@Composable
fun SelectItem(
    enabled: Boolean = true,
    title: String,
    desc: String? = null,
    items: List<String>,
    onValueChange: (String) -> Unit,
    value: String
) {
    val expanded = rememberSaveable {
        mutableStateOf(false)
    }

    Column(Modifier.fillMaxWidth()) {
        DefaultItem(enabled = enabled, title = title, desc = desc, onClick = {
            expanded.value = !expanded.value
        }) {
            Row(
                modifier = Modifier
                    .alpha(0.4f), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value, fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                /*Icon(
                    painter = painterResource(id = R.drawable.arrow_up_down),
                    contentDescription = "choose",
                    modifier = Modifier
                        .height(16.dp)
                        .alpha(0.8f),
                    tint = MaterialTheme.colorScheme.onBackground
                )*/
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_next), contentDescription = title,
                    modifier = Modifier
                        .height(11.dp)
                        .alpha(0.4f), tint = MaterialTheme.colorScheme.onBackground
                )
            }

        }

        AnimatedVisibility(visible = expanded.value) {
            Column(
                Modifier
                    .fillMaxWidth()
                    //.clip(RoundedCornerShape(16.dp))
                    //.background(Color(0x0D333333) withNight Color(0x0DF1F1F1))
                    .padding(vertical = 10.dp)
            ) {
                items.forEach {
                    key(it) {
                        SelectObjectItem(content = it, onValueChange) { newValue ->
                            expanded.value = newValue
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectObjectItem(
    content: String,
    onValueChange: (String) -> Unit,
    expandedOnChanged: (Boolean) -> Unit
) =
    Box(
        Modifier
            .fillMaxWidth()
            .clickable {
                onValueChange(content)
                expandedOnChanged(false)
            }) {
        Text(
            text = content, modifier = Modifier
                .padding(horizontal = 18.5.dp, vertical = 8.dp)
                .alpha(0.6f)
        )
    }

@Composable
fun LabelItem(
    enabled: Boolean = true,
    title: String,
    desc: String? = null,
    superLink: Boolean = false,
    onClick: (() -> Unit)?
) =
    DefaultItem(enabled = enabled, title = title, titleHighLight = superLink, desc = desc, onClick = onClick) {
        if (!superLink) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next), contentDescription = title,
                modifier = Modifier
                    .height(11.dp)
                    .alpha(0.3f), tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }

@Composable
fun SwitchItem(
    enabled: Boolean = true,
    title: String,
    desc: String? = null,
    onClick: (() -> Unit)?,
    checkedLambda: () -> Boolean
) {
    val context = LocalContext.current
    DefaultItem(enabled = enabled, title = title, desc = desc, onClick = {
        if (onClick != null) {
            Vibrator.click(context)
        }
        onClick?.invoke()
    }) {
        /*Switch(checkedLambda = checkedLambda, onValueChange = {
            if (onClick != null) {
                Vibrator.click(context)
            }
            onClick?.invoke()
        })*/

        CupertinoSwitch(checked = checkedLambda(), onCheckedChange = {
            if (onClick != null){
                Vibrator.click(context)
            }
            onClick?.invoke()
        }, modifier = Modifier.height(25.dp))
    }
}

@Composable
fun DefaultItem(
    enabled: Boolean = true,
    title: String,
    titleHighLight: Boolean = false,
    desc: String? = null,
    onClick: (() -> Unit)?,
    backIcon: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier else Modifier.clickable(enabled) {
                    onClick()
                }
            )
            .padding(horizontal = 15.dp, vertical = 11.dp)
            .graphicsLayer {
                if (!enabled) {
                    // compositingStrategy = CompositingStrategy.Offscreen
                    alpha = 0.6f
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .align(Alignment.CenterVertically)
                .alpha(0.94f)
        ) {
            if (titleHighLight) {
                Text(
                    text = title,
                    fontSize = 16.5.sp,
                    lineHeight = 20.5.sp,
                    // fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = title,
                    fontSize = 16.5.sp,
                    lineHeight = 20.5.sp,
                    // fontWeight = FontWeight.Medium,
                )
            }

            if (desc != null) {
                Text(
                    text = desc,
                    fontSize = 13.2.sp,
                    lineHeight = 16.2.sp,
                    modifier = Modifier.alpha(0.5f),
                )
            }
        }
        Column(
            Modifier.padding(start = 15.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            YosWrapper {
                backIcon?.invoke()
            }
        }
    }
}

/*
@Composable
fun Switch(
    checkedLambda: () -> Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchHeight: Dp = 28.dp,
    switchWidth: Dp = 50.dp,
    trackColor: Color = Color(0x1A333333) withNight Color(0x1AF1F1F1),
    trackColorChecked: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = Color(0xFFFFFFFF),
    thumbColorChecked: Color = Color(0xFFFFFFFF),
    animationDuration: Int = 150
) {
    val switchPadding = (switchHeight - 14.5.dp) / 2
    val thumbSize = switchHeight - switchPadding * 2
    val thumbPosition = animateFloatAsState(
        if (checkedLambda()) 1f else 0f, spring(0.65f)
    )
    val animatedTrackColor = animateColorAsState(
        if (checkedLambda()) trackColorChecked else trackColor,
        tween(durationMillis = animationDuration)
    )
    val animatedThumbColor = animateColorAsState(
        if (checkedLambda()) thumbColorChecked else thumbColor,
        tween(durationMillis = animationDuration)
    )
    Box(
        modifier
            .size(switchWidth, switchHeight)
            .clip(RoundedCornerShape(switchHeight / 2))
            .background(animatedTrackColor.value)
            .clickable(indication = null,
                interactionSource = remember {
                    MutableInteractionSource()
                }) { onValueChange(!checkedLambda()) }
            .padding(switchPadding)
    ) {
        val density = LocalDensity.current
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(x = with(density) {
                        ((switchWidth - switchHeight) * thumbPosition.value)
                            .toPx()
                            .toInt()
                    }, y = 0)
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(animatedThumbColor.value)
        )
    }
}*/
