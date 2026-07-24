package com.pryvn.audiophile.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.code.utils.others.BitmapResolver
import com.pryvn.audiophile.ui.pages.NowPlayingPage
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

@Composable
fun YosFloatingLight(
    modifier: Modifier,
    album: () -> Uri?,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    showMiniPlayer: () -> Boolean
) {
    val drawable = remember(album) {
        mutableStateOf<Drawable?>(null)
    }

    val context = LocalContext.current
    val imageLoader = ImageLoader(context)
    YosWrapper {
        LaunchedEffect(album()) {
            if (album() == null) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(album())
                    .build()
                val thisBitmap = imageLoader.execute(request).drawable?.toBitmap()?.run {
                    BitmapResolver.bitmapCompress(this)
                }
                if (thisBitmap != null) {
                    drawable.value = imageResolve(
                        thisBitmap
                    ).toDrawable(context.resources)
                    thisBitmap.recycle()
                }
                imageLoader.shutdown()
            }
        }
    }

    YosWrapper {
        val useBackground = remember("YosFloatingLight_useBackground") {
            derivedStateOf {
                album() == null
            }
        }

        // Static background image (no Ken Burns animation)
        YosWrapper {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(data = drawable.value)
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        onDrawBehind {
                            if (useBackground.value) {
                                drawRect(Color.Black)
                            }
                        }
                    }
            )
        }

        // Dimming overlay: visible when on Lyrics page
        YosWrapper {
            val dimmed = remember("YosFloatingLight_dimmed") {
                derivedStateOf {
                    nowPage() == NowPlayingPage.Lyric
                }
            }

            val alpha = animateFloatAsState(
                targetValue = if (dimmed.value) 0.618f else 0f, animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
            AsyncImage(
                model = ImageRequest.Builder(context).data(data = drawable.value)
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        this.alpha = alpha.value
                    },
                colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay)
            )
        }
    }
}

fun imageResolve(image: Bitmap, moreLight: Boolean = false): Bitmap {
    var resizedBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
    resizedBitmap.applyCanvas {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(3f)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)
        drawBitmap(resizedBitmap, 0f, 0f, paint)

        if (moreLight) {
            drawColor((0x1AFFFFFF).toInt())
            drawColor((0xFFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x52FFFFFF).toInt())
            drawColor((0xBFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
        } else {
            drawColor((0x33000000).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x40000000).toInt())
        }
    }
        resizedBitmap = Toolkit.blur(resizedBitmap, 12)
    return resizedBitmap
}
