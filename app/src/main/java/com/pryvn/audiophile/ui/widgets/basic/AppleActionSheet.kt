package com.pryvn.audiophile.ui.widgets.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.ui.theme.SfProFontFamily

// Apple-style action sheet colors, adaptive to the system theme (iOS uses a
// near-black sheet in dark mode and a light grey sheet in light mode).
@Composable
fun sheetSurface(): Color = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

@Composable
fun sheetBackground(): Color = if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)

@Composable
fun sheetSeparator(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.09f)

@Composable
fun sheetTextColor(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleActionSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = sheetSurface(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(sheetTextColor().copy(alpha = 0.3f)),
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        HazeStyleSheetBlur()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            content = content,
        )
    }
}

@Composable
fun AppleSheetHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SfProFontFamily,
            color = sheetTextColor(),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontFamily = SfProFontFamily,
                color = sheetTextColor().copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Rounded, grouped container for a set of [AppleSheetMenuRow]s — the way iOS
 * groups action sheet buttons into one card.
 */
@Composable
fun AppleSheetMenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(sheetBackground()),
        content = content,
    )
}

@Composable
fun AppleSheetMenuRow(
    text: String,
    onClick: () -> Unit,
    tint: Color? = null,
    isDestructive: Boolean = false,
    showTopDivider: Boolean = false,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    val resolvedTint = tint ?: sheetTextColor()
    if (showTopDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = sheetSeparator(),
        )
    }
    val rowAlpha = if (enabled) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 16.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isDestructive) Color(0xFFFF453A) else resolvedTint.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            fontSize = 17.sp,
            fontFamily = SfProFontFamily,
            fontWeight = FontWeight.Normal,
            color = if (isDestructive) Color(0xFFFF453A) else resolvedTint,
            textAlign = if (icon != null) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = if (enabled) 1 else 3,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleConfirmSheet(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppleActionSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(sheetBackground())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SfProFontFamily,
                    color = sheetTextColor(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontFamily = SfProFontFamily,
                    color = sheetTextColor().copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sheetBackground()),
            ) {
                AppleSheetMenuRow(
                    text = confirmText,
                    onClick = onConfirm,
                    isDestructive = isDestructive,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sheetBackground()),
            ) {
                AppleSheetMenuRow(
                    text = cancelText,
                    onClick = onDismiss,
                    tint = Color(0xFF0A84FF),
                )
            }
        }
    }
}
