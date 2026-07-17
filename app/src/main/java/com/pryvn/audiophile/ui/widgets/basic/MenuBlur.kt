package com.pryvn.audiophile.ui.widgets.basic

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pryvn.audiophile.code.utils.others.BitmapResolver

@Composable
internal fun MenuBlurBackground(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = context.findActivity()?.window

    AndroidView(
        modifier = modifier,
        factory = {
            MenuBlurView(
                context = it,
                backgroundWindow = window,
                fallbackColor = backgroundColor.toArgb(),
            )
        },
    )
}

@Composable
internal fun BlurredMenuContainer(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        MenuBlurBackground(backgroundColor, Modifier.matchParentSize())
        content()
    }
}

private class MenuBlurView(
    context: Context,
    private val backgroundWindow: Window?,
    private val fallbackColor: Int,
) : View(context) {
    private val backgroundLocation = IntArray(2)
    private val viewLocation = IntArray(2)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var snapshot: Bitmap? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(::captureBackground)
    }

    override fun onDetachedFromWindow() {
        snapshot?.recycle()
        snapshot = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = snapshot
        if (bitmap == null || width == 0 || height == 0) {
            canvas.drawColor(fallbackColor)
            return
        }

        canvas.drawBitmap(
            bitmap,
            null,
            Rect(0, 0, width, height),
            bitmapPaint,
        )
        canvas.drawColor(
            AndroidColor.argb(
                0x70,
                AndroidColor.red(fallbackColor),
                AndroidColor.green(fallbackColor),
                AndroidColor.blue(fallbackColor),
            ),
            BlendMode.SRC_OVER,
        )
    }

    private fun captureBackground() {
        val window = backgroundWindow ?: return
        val source = window.decorView
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || width == 0 || height == 0) return

        source.getLocationOnScreen(backgroundLocation)
        getLocationOnScreen(viewLocation)
        val sourceRect = Rect(
            viewLocation[0] - backgroundLocation[0],
            viewLocation[1] - backgroundLocation[1],
            viewLocation[0] - backgroundLocation[0] + width,
            viewLocation[1] - backgroundLocation[1] + height,
        )
        val bitmap = Bitmap.createBitmap(
            (width / 4).coerceAtLeast(1),
            (height / 4).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        PixelCopy.request(window, sourceRect, bitmap, { result ->
            if (result != PixelCopy.SUCCESS) {
                bitmap.recycle()
                return@request
            }
            Thread {
                val blurred = BitmapResolver.blurBitmap(bitmap, 25)
                bitmap.recycle()
                post {
                    if (isAttachedToWindow) {
                        snapshot?.recycle()
                        snapshot = blurred
                        invalidate()
                    } else {
                        blurred.recycle()
                    }
                }
            }.start()
        }, Handler(Looper.getMainLooper()))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
