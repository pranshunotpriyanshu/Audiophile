package com.pryvn.audiophile.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.flaviofaria.kenburnsview.KenBurnsView
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.code.utils.others.BitmapResolver
import com.pryvn.audiophile.data.libraries.SettingsLibrary.NowplayingBackgroundEffect
import com.pryvn.audiophile.ui.animation.MotionTokens
import com.pryvn.audiophile.ui.pages.NowPlayingPage
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

@Stable
private enum class Option {
    Set,
    Pause,
    Resume,
    Init
}

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

    // Outgoing artwork kept on top and slowly faded out when the song changes,
    // so the blurred background mixes into the next song instead of swapping
    // instantly.
    val scope = rememberCoroutineScope()
    val transitionOverlay = remember { mutableStateOf<Drawable?>(null) }
    val transitionAlpha = remember { Animatable(1f) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }

    val context = LocalContext.current
    YosWrapper {
        LaunchedEffect(album()) {
            if (album() == null) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                // Use the shared Coil loader so artwork hits the app-wide memory + disk
                // caches instead of being re-downloaded/re-decoded on every visit.
                val imageLoader = context.imageLoader
                try {
                    val request = ImageRequest.Builder(context)
                        .data(album())
                        .size(CoilSize(256, 256))
                        .build()
                    val thisBitmap = imageLoader.execute(request).drawable?.toBitmap()?.run {
                        BitmapResolver.bitmapCompress(this)
                    }
                    if (thisBitmap != null) {
                        val loaded = imageResolve(
                            thisBitmap
                        ).toDrawable(context.resources)
                        thisBitmap.recycle()
                        // If artwork is already on screen, keep the outgoing one
                        // on top and fade it out over the incoming one — a slow
                        // mix into the next song's colors.
                        val outgoing = drawable.value
                        drawable.value = loaded
                        if (outgoing != null) {
                            transitionJob?.cancel()
                            transitionOverlay.value = outgoing
                            transitionAlpha.snapTo(1f)
                            transitionJob = scope.launch {
                                transitionAlpha.animateTo(
                                    0f,
                                    animationSpec = tween(
                                        durationMillis = MotionTokens.BackgroundMixDurationMs.toInt(),
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                transitionOverlay.value = null
                            }
                        }
                    }
                } catch (_: Exception) {
                    // keep previous drawable on failure
                }
            }
        }
    }

    YosWrapper {
        val lossEffect = remember("YosFloatingLight_lossEffect") {
            derivedStateOf {
                nowPage() != NowPlayingPage.Lyric
            }
        }

        val useBackground = remember("YosFloatingLight_useBackground") {
            derivedStateOf {
                album() == null
            }
        }

        if (NowplayingBackgroundEffect) {
            val lastOption = remember("YosFloatingLight_lastOption") {
                mutableStateOf(Option.Init.name)
            }
            YosWrapper {
                val lifecycleState =
                    LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
                val active = lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)&&!showMiniPlayer()
                AndroidView(factory = {
                    KenBurnsView(it).apply {
                        setTransitionGenerator(
                            RandomTransitionGenerator(
                                12000,
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    }
                }, modifier = modifier.drawWithCache {
                    onDrawBehind {
                        if (useBackground.value) {
                        drawRect(Color.Black)
                            }
                    }
                }) {
                    if (drawable.value != null) {
                        if (it.drawable != drawable.value) {
                            // Update whenever the view is not already showing the
                            // current artwork — the instance check alone guards
                            // against redundant re-sets on recomposition, so the
                            // blurred background actually advances with the song.
                            it.setImageDrawable(drawable.value!!)
                            lastOption.value = Option.Set.name
                        } else if (!isPlaying() || !active) {
                            val thisOptionType = Option.Pause.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            it.pause()
                            lastOption.value = thisOptionType
                        } else {
                            val thisOptionType = Option.Resume.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            it.resume()
                            lastOption.value = thisOptionType
                        }
                    }
                }
            }
        } else {
            YosWrapper {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(data = drawable.value)
                        .crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .drawWithCache {
                            onDrawBehind {
                                if (useBackground.value) {
                                    drawRect(Color.Black)
                                }
                            }
                        }
                )
            }
        }

        YosWrapper {
            val alpha = animateFloatAsState(
                targetValue = if (lossEffect.value) 0.618f else 0f, animationSpec = tween(
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
                        this.alpha = alpha.value
                    },
                colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay)
            )
        }

        // Topmost layer: the outgoing artwork fades out over everything else,
        // so the new song's blurred background is revealed gradually — a slow
        // mix instead of an instant color/artwork change. Rendered with a plain
        // ImageView so the outgoing drawable shows synchronously — an async
        // image load would leave a one-frame gap that flashes the new artwork
        // through before the fade starts.
        YosWrapper {
            val overlay = transitionOverlay.value
            if (overlay != null) {
                AndroidView(
                    factory = { viewContext ->
                        android.widget.ImageView(viewContext).apply {
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = transitionAlpha.value
                        },
                    update = { it.setImageDrawable(overlay) }
                )
            }
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
