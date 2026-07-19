@file:Suppress("DEPRECATION")

package com.pryvn.audiophile.ui.pages

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderPositions
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.blankj.utilcode.util.TimeUtils
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.MediaController.mediaControl
import com.pryvn.audiophile.code.MediaController.musicPlaying
import com.pryvn.audiophile.code.MediaController.playingMusicList
import com.pryvn.audiophile.code.api.ArchiveTuneApis
import com.pryvn.audiophile.code.SystemMediaControlResolver
import com.pryvn.audiophile.code.VolumeChangeReceiver
import com.pryvn.audiophile.code.YosPlaybackService
import com.pryvn.audiophile.code.utils.lrc.LyricsProcessor
import com.pryvn.audiophile.code.utils.lrc.YosLrcFactory
import com.pryvn.audiophile.code.utils.lrc.YosMediaEvent
import com.pryvn.audiophile.code.utils.lrc.YosUIConfig
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePause
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePlay
import com.pryvn.audiophile.code.player.SleepTimer
import com.pryvn.audiophile.code.player.SleepTimerState
import com.pryvn.audiophile.data.libraries.FavPlayListLibrary
import com.pryvn.audiophile.data.libraries.PlayListLibrary
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsList
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultArtistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.data.models.MainViewModel
import com.pryvn.audiophile.ui.markNextNavigationFromNowPlaying
import com.pryvn.audiophile.data.models.MediaViewModel
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.PlaybackLoadingState
import com.pryvn.audiophile.ui.pages.NowPlayingPage.Album
import com.pryvn.audiophile.ui.pages.NowPlayingPage.Lyric
import com.pryvn.audiophile.ui.pages.NowPlayingPage.PlayingList
import com.pryvn.audiophile.ui.theme.YosRoundedCornerShape
import com.pryvn.audiophile.ui.widgets.YosLyricView
import com.pryvn.audiophile.ui.widgets.effects.YosFloatingLight
import com.pryvn.audiophile.ui.widgets.audio.MusicQualityIndicator
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.pages.library.FloatingMenu
import com.pryvn.audiophile.ui.pages.library.FloatingMenuDivider
import com.pryvn.audiophile.ui.pages.library.FloatingMenuItem
import com.pryvn.audiophile.ui.widgets.basic.AppleActionSheet
import com.pryvn.audiophile.ui.widgets.basic.AppleSheetHeader
import com.pryvn.audiophile.ui.widgets.basic.AppleSheetMenuRow
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.basic.AnimatedAlbumCoverState
import com.pryvn.audiophile.ui.widgets.basic.AnimatedAlbumCoverOverlay
import com.pryvn.audiophile.ui.widgets.basic.rememberAnimatedAlbumCoverState
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import com.pryvn.audiophile.ui.widgets.basic.YosBottomSheetDialog
import com.pryvn.audiophile.ui.widgets.basic.ActionSheetBody
import com.pryvn.audiophile.ui.widgets.basic.SheetAnimatedContent
import com.pryvn.audiophile.ui.widgets.basic.SheetNavigationForward
import com.pryvn.audiophile.ui.widgets.basic.SheetNavigationBackward
import com.pryvn.audiophile.ui.widgets.basic.ActionItem
import com.pryvn.audiophile.ui.widgets.playlist.PlayListPickerContent
import com.pryvn.audiophile.ui.widgets.sleeptimer.SleepTimerContent
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.effects.ShadowType
import com.pryvn.audiophile.ui.widgets.effects.overlayEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.IconButton


@Stable
object NowPlayingPage {
    const val Album = "Album"
    const val PlayingList = "PlayingList"
    const val Lyric = "Lyric"
}

private const val ShareAlbumKey = "album"
private const val AnimDurationMillis = 300

/*
private val MaterialFadeInTransitionSpec
    get() = SharedElementsTransitionSpec(
        pathMotionFactory = LinearMotionFactory,
        durationMillis = AnimDurationMillis,
        fadeMode = FadeMode.In,
        easing = EaseOutQuart
    )

private val MaterialFadeOutTransitionSpec
    get() = SharedElementsTransitionSpec(
        pathMotionFactory = LinearMotionFactory,
        durationMillis = AnimDurationMillis,
        fadeMode = FadeMode.Out,
        easing = EaseOutQuart
    )
 */

private data class QueueReorderTarget(
    val nextInQueue: Boolean,
    val index: Int,
)

private fun resolveQueueReorderTarget(
    lazyListIndex: Int,
    nextInQueueSize: Int,
    upNextSize: Int,
): QueueReorderTarget? {
    var cursor = 1

    if (nextInQueueSize > 0) {
        cursor += 1

        if (lazyListIndex in cursor until cursor + nextInQueueSize) {
            return QueueReorderTarget(true, lazyListIndex - cursor)
        }

        cursor += nextInQueueSize
    }

    if (upNextSize > 0) {
        cursor += 1

        if (lazyListIndex in cursor until cursor + upNextSize) {
            return QueueReorderTarget(false, lazyListIndex - cursor)
        }
    }

    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalSharedTransitionApi
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NowPlaying(
    mainViewModel: MainViewModel,
    mediaViewModel: MediaViewModel,
    navController: NavController,
    isPlayingStatusLambda: () -> Boolean,
    isPlayingOnChanged: (Boolean) -> Unit,
    nowPageLambda: () -> String,
    showMiniPlayer: () -> Boolean,
    nowPageOnChanged: (String) -> Unit
) =
    Surface(
        modifier = Modifier.fillMaxSize(),
        contentColor = Color.White,
        color = Color.Transparent
    ) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = LocalDensity.current
        val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

        val overflowSheetOpen = remember { mutableStateOf(false) }
        val snapshotSong = remember { mutableStateOf<YosMediaItem?>(null) }

        val lrcEntries: MutableState<List<List<Pair<Float, String>>>> =
            MediaViewModelObject.lrcEntries
        val bitmap: MutableState<Uri?> = MediaViewModelObject.bitmap

        val thisMusicPlaying = remember("NowPlaying_thisMusicPlaying") {
            musicPlaying
        }

        val lastClickTime = rememberSaveable(key = "NowPlaying_lastClickTime") {
            mutableLongStateOf(0L)
        }

        val showControl = rememberSaveable(key = "NowPlaying_showControl") {
            mutableStateOf(true)
        }

        val translation = rememberSaveable(key = "NowPlaying_translation") {
            mutableStateOf(SettingsLibrary.NowPlayingTranslation)
        }

        val translatedLrcEntries = remember {
            mutableStateOf<List<List<Pair<Float, String>>>>(emptyList())
        }

        val displayLrcEntries = remember {
            derivedStateOf {
                if (translation.value && translatedLrcEntries.value.isNotEmpty())
                    translatedLrcEntries.value
                else
                    lrcEntries.value
            }
        }

        val shuffleModeEnabled = rememberSaveable(key = "NowPlaying_shuffleModeEnabled") {
            mutableStateOf(mediaControl?.shuffleModeEnabled ?: false)
        }
        val repeatMode = rememberSaveable(key = "NowPlaying_repeatMode") {
            mutableIntStateOf(mediaControl?.repeatMode ?: REPEAT_MODE_OFF)
        }

        /*val nowPage = rememberSaveable(key = "NowPlaying_nowPage") {
            MainViewModelObject.nowPage
        }*/

        println("重组：NowPlaying")

        // 触摸超时
        YosWrapper {
            LaunchedEffect(showControl.value, nowPageLambda(), lastClickTime.longValue) {
                if (nowPageLambda() != Lyric && !showControl.value) {
                    showControl.value = true
                }
                if (showControl.value) {
                    val time = 2500L
                    delay(time)
                    withContext(Dispatchers.Main) {
                        if (TimeUtils.getNowMills() - lastClickTime.longValue >= time && nowPageLambda() == Lyric) {
                            showControl.value = false
                        }
                    }
                }
            }
        }


        // 背景流光
        YosWrapper {
            println("重组：背景")

            YosFloatingLight(
                album = { bitmap.value },
                isPlaying = isPlayingStatusLambda,
                modifier = Modifier.fillMaxSize(),
                nowPage = { nowPageLambda() },
                showMiniPlayer = showMiniPlayer
            )
        }

        // 动态专辑艺术颜色渐变
        YosWrapper {
            val vibrant = MediaViewModelObject.paletteVibrantColor.value
            val darkVibrant = MediaViewModelObject.paletteDarkVibrantColor.value
            val darkMuted = MediaViewModelObject.paletteDarkMutedColor.value

            val animatedVibrant = animateColorAsState(
                targetValue = vibrant,
                animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f)
            ).value
            val animatedDarkVibrant = animateColorAsState(
                targetValue = darkVibrant,
                animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f)
            ).value
            val animatedDarkMuted = animateColorAsState(
                targetValue = darkMuted,
                animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f)
            ).value

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedVibrant.copy(alpha = 0.3f),
                                animatedDarkVibrant.copy(alpha = 0.2f),
                                animatedDarkMuted.copy(alpha = 0.15f)
                            )
                        )
                    )
            )
        }

        // 实际显示区
        YosWrapper {
            /*
        val controlAlpha = animateFloatAsState(
            targetValue = if (showControl.value) 1f else 0f,
            tween(200)
        )

        val buttonEnabled = remember("NowPlaying_buttonEnabled") {
            derivedStateOf { controlAlpha.value != 0f }
        }

        val translationButtonEnabled = remember("NowPlaying_translationButtonEnabled") {
            derivedStateOf { buttonEnabled.value && alpha.value != 0f }
        }*/

            // 沉浸式封面背景层：播放时淡入并铺满全屏模糊，统辖把手/歌词/队列/控件
            ImmersiveArtwork(
                isPlaying = isPlayingStatusLambda,
                albumUrl = { thisMusicPlaying.value?.thumb }
            )

            val scope = rememberCoroutineScope()

            val alphaAnim = remember { Animatable(0f) }

            YosWrapper {
                LaunchedEffect(nowPageLambda()) {
                    val targetAlpha = if (nowPageLambda() == Lyric) 1f else 0f
                    scope.launch {
                        alphaAnim.animateTo(targetAlpha)
                    }
                }
            }

            val translationButtonEnabled = remember("NowPlaying_translationButtonEnabled") {
                derivedStateOf { showControl.value && alphaAnim.value != 0f }
            }

            println("重组：主功能区")

            if (!isLandscape) {

            // 歌词
            YosWrapper {

                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy =
                                CompositingStrategy.ModulateAlpha
                            this.alpha = alphaAnim.value
                        }
                ) {
                    println("重组：YosLyricView 外层 3")

                    Lyric(
                        lrcEntries = { displayLrcEntries.value },
                        weightLambda = { showControl.value },
                        translationLambda = { translation.value },
                        onBackClick = {
                            showControl.value = true
                            lastClickTime.longValue =
                                TimeUtils.getNowMills()
                        },
                        mainViewModel = mainViewModel,
                        mediaViewModel = mediaViewModel,
                        wordSyncedLambda = { MediaViewModelObject.hasWordSyncedLyrics.value }
                    )
                }
            }

            // 这是小把手
            YosWrapper {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 20.dp), contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .overlayEffect()
                                .size(
                                    width = 32.dp,
                                    height = 4.5.dp
                                )
                                .background(Color(0x4DFFFFFF), RoundedCornerShape(2.25.dp))
                                .clip(RoundedCornerShape(2.25.dp))
                        )
                    }
                }
            }

            // 主 View
            YosWrapper {
                SharedTransitionLayout {
                    Crossfade(
                        targetState = nowPageLambda(),
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 22.dp)
                    ) {
                        //println("nowPage: ${nowPageLambda()}")
                        //println("nowPageIt: $it")
                        when (it) {
                            Album ->
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                    ) {
                                        YosWrapper {
                                            Column(Modifier.fillMaxHeight(0.595f)) {
                                                val isVisible = nowPageLambda() == Album
                                                val animatedAlbumLifecycleState =
                                                    LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

                                                Album(
                                                    modifier = Modifier.sharedElementWithCallerManagedVisibility(
                                                        sharedContentState = rememberSharedContentState(
                                                            key = ShareAlbumKey
                                                        ),
                                                        visible = isVisible
                                                    ),
                                                    albumUrl = { thisMusicPlaying.value?.thumb },
                                                    isPlaying = isPlayingStatusLambda,
                                                    music = { thisMusicPlaying.value },
                                                    active = nowPageLambda() == Album &&
                                                        animatedAlbumLifecycleState.value.isAtLeast(Lifecycle.State.STARTED)
                                                )
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 32.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        Modifier
                                                            .weight(1f)
                                                            .padding(end = 15.dp)
                                                    ) {
                                                        val song = thisMusicPlaying.value
                                                        Text(
                                                            text = song?.title
                                                                ?: defaultTitle,
                                                            fontSize = 19.5.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color.White,
                                                        )
                                                        Text(
                                                            text = song?.artistsName
                                                                ?: defaultArtistsName,
                                                            fontSize = 18.5.sp,
                                                            modifier = Modifier.overlayEffect(),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = Color.White.copy(alpha = 0.35f)
                                                        )
                                                    }

                                                    YosWrapper {
                                                        ActionButtonsRow(
                                                            musicPlayingLambda = { thisMusicPlaying.value },
                                                            navController = navController,
                                                            albumUrlLambda = { thisMusicPlaying.value?.thumb },
                                                            onShowMenu = {
                            snapshotSong.value = thisMusicPlaying.value
                            overflowSheetOpen.value = true
                        },
                                                        )
                                                    }
                                                }
                                            }
                                         }
                             }
                              Lyric ->
                                Column(Modifier.fillMaxSize()) {
                                    YosWrapper {
                                        val isVisible = nowPageLambda() == Lyric
                                        PlayingBar(
                                            modifier = Modifier.sharedElementWithCallerManagedVisibility(
                                                sharedContentState = rememberSharedContentState(
                                                    key = ShareAlbumKey
                                                ),
                                                visible = isVisible
                                            ),
                                            albumUrlLambda = {
                                                thisMusicPlaying.value?.thumb
                                            },
                                            musicPlayingLambda = { thisMusicPlaying.value },
                                            navController = navController,
                                            isLyricsView = true,
                                            onRefetchLyrics = {
                                                scope.launch(Dispatchers.IO) {
                                                    val track = thisMusicPlaying.value ?: return@launch
                                                    MediaViewModelObject.isLoadingLyrics.value = true
                                                    LyricsProcessor.resetLyricsState()
                                                    MediaViewModelObject.lrcEntries.value = emptyList()
                                                    MediaViewModelObject.otherSideForLines.clear()
                                                    val lyrics = ArchiveTuneApis.fetchLyrics(
                                                        title = track.title,
                                                        artist = track.artists,
                                                        album = track.album,
                                                        durationMs = track.duration,
                                                        videoId = track.mediaId,
                                                    )
                                                    if (lyrics != null && lyrics.text.isNotBlank()) {
                                                        LyricsProcessor.applyLyrics(lyrics, lrcEntriesSetter = { MediaViewModelObject.lrcEntries.value = it })
                                                    }
                                                    MediaViewModelObject.isLoadingLyrics.value = false
                                                }
                                            },
                                            onAlbumClick = { nowPageOnChanged(Album) },
                                            onShowMenu = {
                            snapshotSong.value = thisMusicPlaying.value
                            overflowSheetOpen.value = true
                        })
                                    }
                                }

                            PlayingList ->
                                YosWrapper {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                    ) {
                                        val isVisible = nowPageLambda() == PlayingList
                                        PlayingBar(
                                            modifier = Modifier.sharedElementWithCallerManagedVisibility(
                                                sharedContentState = rememberSharedContentState(
                                                    key = ShareAlbumKey
                                                ),
                                                visible = isVisible
                                            ),
                                            albumUrlLambda = {
                                                thisMusicPlaying.value?.thumb
                                            },
                                            musicPlayingLambda = { thisMusicPlaying.value },
                                            navController = navController,
                                            onAlbumClick = { nowPageOnChanged(Album) },
                                            onShowMenu = {
                            snapshotSong.value = thisMusicPlaying.value
                            overflowSheetOpen.value = true
                        })
                                        YosWrapper {
                                            PlayingList(
                                                shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                                                shuffleModeOnChanged = { shuffleModeSet ->
                                                    shuffleModeEnabled.value = shuffleModeSet
                                                },
                                                repeatModeLambda = { repeatMode.intValue },
                                                repeatModeOnChanged = { repeatModeSet ->
                                                    repeatMode.intValue = repeatModeSet
                                                },
                                                thisMusicPlayingLambda = { thisMusicPlaying.value }
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            // 音乐控制
            YosWrapper {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding(), verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        Modifier
                            /*.fillMaxHeight(0.385f)*/
                            .fillMaxHeight(0.437f)
                            .fillMaxWidth()
                    ) {
                        println("重组：控制区域外部")

                        YosWrapper {
                            if (showControl.value) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 40.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                //showControl.value = true
                                                /*lastClickTime.longValue =
                                                TimeUtils.getNowMills()*/
                                            })
                                )
                            }
                        }

                        YosWrapper {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                 AnimatedVisibility(
                                     visible = showControl.value,
                                     enter = fadeIn() + expandVertically(
                                         expandFrom = Alignment.Top,
                                         initialHeight = { (it / 1.4).toInt() },
                                         animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                                     ),
                                     exit = fadeOut() + shrinkVertically(
                                         shrinkTowards = Alignment.Top,
                                         targetHeight = { (it / 1.4).toInt() },
                                         animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                                     )
                                 ) {
                                        YosWrapper {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 32.dp)
                                                    .graphicsLayer {
                                                        compositingStrategy =
                                                            CompositingStrategy.ModulateAlpha
                                                        this.alpha = alphaAnim.value
                                                    },
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                YosWrapper {
                                                    Box(
                                                        modifier = Modifier
                                                            .overlayEffect()
                                                            .alpha(0.4f)
                                                            .clickable(
                                                                enabled = translationButtonEnabled.value,
                                                                onClick = {
                                                                    Vibrator.click(context)
                                                                    translation.value =
                                                                        !translation.value
                                                                    showControl.value = true
                                                                    lastClickTime.longValue =
                                                                        TimeUtils.getNowMills()
                                                                    SettingsLibrary.NowPlayingTranslation =
                                                                        translation.value
                                                                    if (translation.value) {
                                                                        scope.launch(Dispatchers.IO) {
                                                                            val lyricsText = lrcEntries.value.joinToString("\n") { group ->
                                                                                group.firstOrNull()?.second?.trim() ?: ""
                                                                            }.trim()
                                                                            if (lyricsText.isNotBlank()) {
                                                                                val result = ArchiveTuneApis.fetchTranslation(lyricsText)
                                                                                result.onSuccess { translatedText ->
                                                                                    val translatedLines = translatedText.lines().map { it.trim() }
                                                                                    if (translatedLines.isNotEmpty()) {
                                                                                        val newEntries = lrcEntries.value.mapIndexedNotNull { index, group ->
                                                                                            val tl = translatedLines.getOrNull(index)?.takeIf { it.isNotBlank() }
                                                                                            if (tl != null) {
                                                                                                val time = group.first().first
                                                                                                group + (time to tl)
                                                                                            } else {
                                                                                                null
                                                                                            }
                                                                                        }
                                                                                        if (newEntries.isNotEmpty()) {
                                                                                            translatedLrcEntries.value = newEntries
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                indication = null,
                                                                interactionSource = remember { MutableInteractionSource() }),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        AnimatedContent(
                                                            targetState = translation.value,
                                                            transitionSpec = {
                                                                fadeIn() togetherWith fadeOut()
                                                            }) {
                                                            if (it) {
                                                                Icon(
                                                                    painterResource(id = R.drawable.ic_nowplaying_translateon),
                                                                    contentDescription = null,
                                                                    modifier = Modifier
                                                                        .size(30.dp)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    painterResource(id = R.drawable.ic_nowplaying_translate),
                                                                    contentDescription = null,
                                                                    modifier = Modifier
                                                                        .size(30.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                    PlayerControl(
                                        isPlayingLambda = isPlayingStatusLambda,
                                        isPlayingOnChanged = isPlayingOnChanged,
                                        onPrevious = {
                                            MediaController.manualPrevious()
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        onStatus = { status ->
                                            if (status) {
                                                mediaControl?.fadePlay()
                                            } else {
                                                mediaControl?.fadePause()
                                            }
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        onNext = {
                                            MediaController.manualNext()
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        onSeek = { position ->
                                            mediaControl?.seekTo(position.toLong())
                                        },
                                        onLyrics = {
                                            if (nowPageLambda() == Lyric) {
                                                nowPageOnChanged(Album)
                                            } else {
                                                nowPageOnChanged(Lyric)
                                            }
                                        },
                                        onPlaylist = {
                                            if (nowPageLambda() == PlayingList) {
                                                nowPageOnChanged(Album)
                                            } else {
                                                nowPageOnChanged(PlayingList)
                                            }
                                        },
                                        nowPage = {
                                            nowPageLambda()
                                        },
                                        onSlider = {
                                            showControl.value = true
                                            lastClickTime.longValue = TimeUtils.getNowMills()
                                        },
                                        modifier = Modifier
                                            /*.graphicsLayer {
                                                compositingStrategy =
                                                    CompositingStrategy.Offscreen
                                                //this.alpha = controlAlpha.value
                                            }*/
                                            .padding(top = 52.dp),
                                        onWhile = {
                                            shuffleModeEnabled.value =
                                                mediaControl?.shuffleModeEnabled ?: false
                                            repeatMode.intValue =
                                                mediaControl?.repeatMode ?: REPEAT_MODE_OFF
                                        })
                                }
                            }
                        }
                    }
                }
            }
            } else {
                val lyricsOn = nowPageLambda() == Lyric
                Row(Modifier.fillMaxSize()) {
                    // Left panel
                    Column(
                        Modifier
                            .weight(if (lyricsOn) 0.4f else 0.5f)
                            .fillMaxHeight()
                            .fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 44.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            SharedTransitionLayout {
                                Crossfade(
                                    targetState = nowPageLambda(),
                                    modifier = Modifier.fillMaxWidth(0.55f),
                                ) { page ->
                                    when (page) {
                                        Album, Lyric -> {
                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                            val animatedAlbumLifecycleState =
                                                LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
                                            Album(
                                                modifier = Modifier.fillMaxWidth(),
                                                albumUrl = { thisMusicPlaying.value?.thumb },
                                                isPlaying = isPlayingStatusLambda,
                                                music = { thisMusicPlaying.value },
                                                active = nowPageLambda() == Album &&
                                                    animatedAlbumLifecycleState.value.isAtLeast(Lifecycle.State.STARTED)
                                            )
                                        }
                                        }
                                        PlayingList -> {
                                            PlayingList(
                                                shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                                                shuffleModeOnChanged = { shuffleModeSet ->
                                                    shuffleModeEnabled.value = shuffleModeSet
                                                },
                                                repeatModeLambda = { repeatMode.intValue },
                                                repeatModeOnChanged = { repeatModeSet ->
                                                    repeatMode.intValue = repeatModeSet
                                                },
                                                thisMusicPlayingLambda = { thisMusicPlaying.value }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = thisMusicPlaying.value?.title ?: defaultTitle,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = SfProFontFamily,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Text(
                                text = thisMusicPlaying.value?.artistsName ?: defaultArtistsName,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White.copy(alpha = 0.5f),
                                fontFamily = SfProFontFamily,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                        PlayerControl(
                            isPlayingLambda = isPlayingStatusLambda,
                            isPlayingOnChanged = isPlayingOnChanged,
                            onPrevious = {
                                MediaController.manualPrevious()
                            },
                            onStatus = { status ->
                                if (status) {
                                    mediaControl?.fadePlay()
                                } else {
                                    mediaControl?.fadePause()
                                }
                            },
                            onNext = {
                                MediaController.manualNext()
                            },
                            onSeek = { position ->
                                mediaControl?.seekTo(position.toLong())
                            },
                            onLyrics = {
                                if (nowPageLambda() == Lyric) {
                                    nowPageOnChanged(Album)
                                } else {
                                    nowPageOnChanged(Lyric)
                                }
                            },
                            onPlaylist = {
                                if (nowPageLambda() == PlayingList) {
                                    nowPageOnChanged(Album)
                                } else {
                                    nowPageOnChanged(PlayingList)
                                }
                            },
                            nowPage = { nowPageLambda() },
                            onSlider = {
                                showControl.value = true
                                lastClickTime.longValue = TimeUtils.getNowMills()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            onWhile = {
                                shuffleModeEnabled.value = mediaControl?.shuffleModeEnabled ?: false
                                repeatMode.intValue = mediaControl?.repeatMode ?: REPEAT_MODE_OFF
                            })
                        if (!lyricsOn) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_volume),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White.copy(alpha = 0.5f),
                                )
                                Slider(
                                    value = curVol.toFloat() / maxVol.toFloat(),
                                    onValueChange = { vol ->
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * maxVol).toInt(), 0)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable(
                                            onClick = {
                                                if (nowPageLambda() == PlayingList) {
                                                    nowPageOnChanged(Album)
                                                } else {
                                                    nowPageOnChanged(PlayingList)
                                                }
                                            },
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_nowplaying_queue),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }

                    // Right panel
                    if (lyricsOn) {
                        Column(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable(
                                            onClick = {
                                                if (nowPageLambda() == PlayingList) {
                                                    nowPageOnChanged(Lyric)
                                                } else {
                                                    nowPageOnChanged(PlayingList)
                                                }
                                            },
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        painterResource(id = if (nowPageLambda() == PlayingList) R.drawable.ic_nowplaying_lyricson else R.drawable.ic_nowplaying_queue),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = Color.White,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (nowPageLambda() == PlayingList) "Lyrics" else "Queue",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontFamily = SfProFontFamily,
                                    )
                                }
                            }
                            if (nowPageLambda() == Lyric) {
                                Lyric(
                                    lrcEntries = { displayLrcEntries.value },
                                    weightLambda = { false },
                                    translationLambda = { translation.value },
                                    onBackClick = {
                                        showControl.value = true
                                        lastClickTime.longValue = TimeUtils.getNowMills()
                                    },
                                    mainViewModel = mainViewModel,
                                    mediaViewModel = mediaViewModel,
                                    wordSyncedLambda = { MediaViewModelObject.hasWordSyncedLyrics.value }
                                )
                            } else {
                                PlayingList(
                                    shuffleModeEnabledLambda = { shuffleModeEnabled.value },
                                    shuffleModeOnChanged = { shuffleModeSet ->
                                        shuffleModeEnabled.value = shuffleModeSet
                                    },
                                    repeatModeLambda = { repeatMode.intValue },
                                    repeatModeOnChanged = { repeatModeSet ->
                                        repeatMode.intValue = repeatModeSet
                                    },
                                    thisMusicPlayingLambda = { thisMusicPlaying.value }
                                )
                            }
                        }
                    }
            }
        }

        NowPlayingOverflowSheet(
            isOpen = overflowSheetOpen,
            song = snapshotSong.value,
            navController = navController,
            onOpenLibraryTarget = {
                navController.markNextNavigationFromNowPlaying()
                navController.toUI(it.route)
            },
        )
        }
    }

@Composable
private fun ColumnScope.Album(
    modifier: Modifier,
    albumUrl: () -> Uri?,
    isPlaying: () -> Boolean,
    music: () -> YosMediaItem?,
    active: Boolean
) = Box(
    Modifier
        .weight(1f)
        .padding(top = 20.dp)
        .padding(horizontal = 15.dp)
        .padding(bottom = 33.dp),
    contentAlignment = Alignment.BottomCenter
) {
    val springSpec: AnimationSpec<Float> = remember("Album_springSpec") {
        SpringSpec(stiffness = 300f, dampingRatio = 0.8f, visibilityThreshold = 0.001f)
    }

    val tweenSpec: AnimationSpec<Float> = remember("Album_tweenSpec") {
        SpringSpec(stiffness = 400f, dampingRatio = 0.7f, visibilityThreshold = 0.001f)
    }

    val isBuffering = MediaViewModelObject.isBuffering.value
    val scale = animateFloatAsState(
        targetValue = if (isPlaying() && !isBuffering) 0f else 1f,
        animationSpec = if (isPlaying()) springSpec else tweenSpec,
        visibilityThreshold = 0.001f
    )

    val animatedAlbumCoverState = rememberAnimatedAlbumCoverState(
        music = music(),
        isPlaying = isPlaying(),
        active = active
    )

    YosWrapper {
        val dp = (7 + (27 * scale.value)).dp
        ShadowImageWithCache(
            dataLambda = albumUrl, contentDescription = null, modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .padding(start = dp, end = dp, bottom = dp)
                .then(modifier),
            imageQuality = ImageQuality.RAW,
            shadowOverlay = true,
            overlayContent = {
                AnimatedAlbumCoverOverlay(animatedAlbumCoverState)
            }
        )
    }
}

/**
 * 沉浸式封面背景层：播放时将同一封面重度模糊并铺满全屏，作为前景封面的延续；
 * 底部以长渐变缓慢加深（承载控件），顶部轻量压暗（把手/状态栏可读）。
 * 暂停时整体淡出，恢复为原本居中的方形封面。
 * 复用相同 URL 的 Coil 缓存，不重新解码 bitmap。
 */
@Composable
private fun ImmersiveArtwork(
    isPlaying: () -> Boolean,
    albumUrl: () -> Uri?
) {
    val appear = animateFloatAsState(
        targetValue = if (isPlaying()) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "immersiveAppear"
    )
    val blur = animateDpAsState(
        targetValue = if (isPlaying()) 60.dp else 0.dp,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "immersiveBlur"
    )
    val scale = animateFloatAsState(
        targetValue = if (isPlaying()) 1.08f else 1f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "immersiveScale"
    )
    if (appear.value > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = appear.value }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(albumUrl())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .blur(blur.value, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f * appear.value),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f * appear.value),
                                Color.Black.copy(alpha = 0.92f * appear.value)
                            )
                        )
                    )
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayingList(
    shuffleModeEnabledLambda: () -> Boolean,
    shuffleModeOnChanged: (Boolean) -> Unit,
    repeatModeLambda: () -> Int,
    repeatModeOnChanged: (Int) -> Unit,
    thisMusicPlayingLambda: () -> YosMediaItem?
) {
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(12.dp))

    val nextInQueue = MediaController.nextInQueueMusicList.value
    val upNext = MediaController.playingMusicList.value ?: emptyList()
    val scope = rememberCoroutineScope()

    YosWrapper {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.545f),
        ) {
            val hide = remember("PlayingList_hide") {
                derivedStateOf {
                    nextInQueue.isEmpty() && upNext.isEmpty()
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .padding(top = 10.dp)
                    .height(65.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.page_library_playlists),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(
                            id = R.string.page_library_playlists_music_total,
                            upNext.size
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .overlayEffect()
                            .alpha(0.35f)
                    )
                }

                Row(
                    modifier = Modifier
                        .overlayEffect()
                        .alpha(0.6f)
                ) {
                    val dp = 36.dp
                    YosWrapper {
                        val shuffleBackgroundAlpha =
                            animateFloatAsState(targetValue = if (shuffleModeEnabledLambda()) 0.9f else 0f)
                        Box(
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        Vibrator.click(context)
                                        scope.launch(Dispatchers.IO) {
                                            val newState = MediaController.toggleShuffleMode()
                                            shuffleModeOnChanged(newState)
                                        }
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() })
                                .size(36.dp)
                                .background(
                                    Color.White.copy(alpha = shuffleBackgroundAlpha.value),
                                    shape = YosRoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            YosWrapper {
                                val shuffleIconTint =
                                    animateColorAsState(targetValue = if (shuffleModeEnabledLambda()) Color.Black else Color.White)
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_shuffle),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp),
                                    tint = shuffleIconTint.value
                                )
                            }
                        }
                    }
                    YosWrapper {
                        val repeatHighlight =
                            repeatModeLambda() == REPEAT_MODE_ALL || repeatModeLambda() == REPEAT_MODE_ONE
                        val repeatBackgroundAlpha =
                            animateFloatAsState(targetValue = if (repeatHighlight) 0.9f else 0f)
                        Box(
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        Vibrator.click(context)
                                        val targetMode = when (repeatModeLambda()) {
                                            REPEAT_MODE_OFF -> {
                                                REPEAT_MODE_ALL
                                            }

                                            REPEAT_MODE_ALL -> {
                                                REPEAT_MODE_ONE
                                            }

                                            else -> {
                                                REPEAT_MODE_OFF
                                            }
                                        }
                                        mediaControl?.repeatMode = targetMode
                                        mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                                        repeatModeOnChanged(targetMode)
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() })
                                .padding(start = 10.dp)
                                .size(36.dp)
                                .background(
                                    Color.White.copy(alpha = repeatBackgroundAlpha.value),
                                    shape = YosRoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            YosWrapper {
                                AnimatedContent(targetState = repeatModeLambda(), transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                }) {
                                    when (it) {
                                        REPEAT_MODE_ONE -> Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_repeatone),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(dp),
                                            tint = animateColorAsState(targetValue = if (repeatHighlight) Color.Black else Color.White).value
                                        )

                                        else -> Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_repeat),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(dp),
                                            tint = animateColorAsState(targetValue = if (repeatHighlight) Color.Black else Color.White).value
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            if (hide.value) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_uitabbar_library),
                        contentDescription = null,
                        modifier = Modifier
                            .overlayEffect()
                            .size(70.dp)
                            .alpha(0.6f)
                    )
                    Text(
                        text = stringResource(id = R.string.playlist_unavailable_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.playlist_unavailable_desc),
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier
                            .overlayEffect()
                            .alpha(0.4f)
                    )
                }
            } else {
                val currentPlaying = thisMusicPlayingLambda()

                val lazyListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = 1,
                    initialFirstVisibleItemScrollOffset = -15
                )
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val source = resolveQueueReorderTarget(
                        from.index,
                        nextInQueue.size,
                        upNext.size,
                    ) ?: return@rememberReorderableLazyListState
                    val destination = resolveQueueReorderTarget(
                        to.index,
                        nextInQueue.size,
                        upNext.size,
                    ) ?: return@rememberReorderableLazyListState

                    if (source.nextInQueue != destination.nextInQueue || source.index == destination.index) {
                        return@rememberReorderableLazyListState
                    }

                    Vibrator.click(context)
                    if (source.nextInQueue) {
                        MediaController.moveNextInQueueItemDuringDrag(source.index, destination.index)
                    } else {
                        MediaController.moveUpNextItemDuringDrag(source.index, destination.index)
                    }
                }

                YosWrapper {
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {

                        LazyColumn(state = lazyListState, modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                onDrawWithContent {
                                    val colors = listOf(
                                        Color.Transparent,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Black,
                                        Color.Transparent
                                    )

                                    drawContent()

                                    drawRect(
                                        brush = Brush.verticalGradient(colors),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                        ) {
                            item("blank_before") {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (nextInQueue.isNotEmpty()) {
                                item("niq_header") {
                                    QueueSectionHeader(
                                        title = stringResource(R.string.queue_next_in_queue),
                                        itemCount = nextInQueue.size,
                                        onClear = {
                                            scope.launch(Dispatchers.IO) {
                                                MediaController.clearNextInQueue()
                                            }
                                        }
                                    )
                                }
                                itemsIndexed(
                                    items = nextInQueue,
                                    key = { _, item -> "niq_${item.mediaId ?: item.title}" }
                                ) { index, song ->
                                    ReorderableItem(
                                        reorderableState,
                                        key = "niq_${song.mediaId ?: song.title}"
                                    ) { isDragging ->
                                        val swipeState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = {
                                                if (it == SwipeToDismissBoxValue.StartToEnd) {
                                                    scope.launch(Dispatchers.IO) {
                                                        if (index != 0) {
                                                            MediaController.moveNextInQueueItem(index, 0)
                                                        }
                                                    }
                                                    true
                                                } else if (it == SwipeToDismissBoxValue.EndToStart) {
                                                    scope.launch(Dispatchers.IO) {
                                                        MediaController.removeNextInQueueItem(index)
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = swipeState,
                                            enableDismissFromStartToEnd = true,
                                            enableDismissFromEndToStart = true,
                                            backgroundContent = {
                                                val direction = swipeState.dismissDirection
                                                val color by animateColorAsState(
                                                    targetValue = when (direction) {
                                                        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF34C759)
                                                        SwipeToDismissBoxValue.EndToStart -> Color(0xFFE8453C)
                                                        else -> Color.Transparent
                                                    },
                                                    label = "swipeBg"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(color)
                                                )
                                            }
                                        ) {
                                            QueueMusicListItem(
                                                music = song,
                                                isCurrentItem = false,
                                                itemClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        MediaController.skipToNextInQueueItem(index)
                                                    }
                                                },
                                                trailing = {
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch(Dispatchers.IO) {
                                                                MediaController.removeNextInQueueItem(index)
                                                            }
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Remove from queue",
                                                            modifier = Modifier.alpha(0.5f)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (upNext.isNotEmpty()) {
                                item("upnext_header") {
                                    QueueSectionHeader(
                                        title = stringResource(R.string.queue_up_next),
                                        itemCount = upNext.size,
                                        onClear = null
                                    )
                                }
                                itemsIndexed(
                                    items = upNext,
                                    key = { _, item -> "upnext_${item.mediaId ?: item.title}" }
                                ) { index, song ->
                                    ReorderableItem(
                                        reorderableState,
                                        key = "upnext_${song.mediaId ?: song.title}"
                                    ) { isDragging ->
                                        val swipeState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = {
                                                if (it == SwipeToDismissBoxValue.StartToEnd) {
                                                    scope.launch(Dispatchers.IO) {
                                                        MediaController.moveUpNextToNextQueue(index)
                                                    }
                                                    true
                                                } else if (it == SwipeToDismissBoxValue.EndToStart) {
                                                    scope.launch(Dispatchers.IO) {
                                                        MediaController.removeUpNextItem(index)
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = swipeState,
                                            enableDismissFromStartToEnd = true,
                                            enableDismissFromEndToStart = true,
                                            backgroundContent = {
                                                val direction = swipeState.dismissDirection
                                                val color by animateColorAsState(
                                                    targetValue = when (direction) {
                                                        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF34C759)
                                                        SwipeToDismissBoxValue.EndToStart -> Color(0xFFE8453C)
                                                        else -> Color.Transparent
                                                    },
                                                    label = "swipeBg"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(color)
                                                )
                                            }
                                        ) {
                                            val dragModifier = Modifier
                                                .draggableHandle()
                                                .alpha(if (isDragging) 0.85f else 0.4f)
                                                .size(36.dp)
                                            QueueMusicListItem(
                                                music = song,
                                                isCurrentItem = song.mediaId != null && song.mediaId == currentPlaying?.mediaId,
                                                itemClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        MediaController.prepare(
                                                            song,
                                                            upNext
                                                        )
                                                    }
                                                },
                                                trailing = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DragHandle,
                                                            contentDescription = "Drag to reorder",
                                                            modifier = dragModifier
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                scope.launch(Dispatchers.IO) {
                                                                    MediaController.removeUpNextItem(index)
                                                                }
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Clear,
                                                                contentDescription = "Remove from queue",
                                                                modifier = Modifier.alpha(0.5f)
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            item("blank_after") {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun LazyItemScope.QueueSectionHeader(
    title: String,
    itemCount: Int,
    onClear: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.overlayEffect().alpha(0.5f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "· $itemCount",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.overlayEffect().alpha(0.35f)
        )
        if (onClear != null) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.queue_clear),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun LazyItemScope.QueueMusicListItem(
    music: YosMediaItem,
    isCurrentItem: Boolean = false,
    itemClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isCurrentItem)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else
            Color.Transparent,
        animationSpec = tween(250),
        label = "nowPlayingItemBg"
    )
    Row(
        modifier = Modifier
            .height(64.dp)
            .fillMaxWidth()
            .clickable { itemClick() }
            .then(
                if (isCurrentItem) {
                    Modifier
                        .padding(horizontal = 20.dp)
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp)
                } else {
                    Modifier.padding(start = 30.dp, end = 12.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShadowImageWithCache(
            dataLambda = { music.thumb },
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            cornerRadius = 4.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.LOW
        )

        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = music.title ?: defaultTitle,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )

            Text(
                text = music.artistsName ?: defaultArtistsName,
                modifier = Modifier.alpha(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.5.sp,
                lineHeight = 11.5.sp,
            )
        }

        trailing()
    }
}

@Composable
private fun Lyric(
    lrcEntries: () -> List<List<Pair<Float, String>>>,
    weightLambda: () -> Boolean,
    translationLambda: () -> Boolean,
    mainViewModel: MainViewModel,
    mediaViewModel: MediaViewModel,
    onBackClick: () -> Unit,
    wordSyncedLambda: () -> Boolean = { false }
) = YosWrapper {

    val context = LocalContext.current
    val lyDensity = LocalDensity.current
    val statusBarHeight = with(lyDensity) { WindowInsets.statusBars.getTop(this).toDp() }

    println("重组：YosLyricView 外层 2")

    Column(
        Modifier
            .fillMaxSize()
    ) {
        YosWrapper {
            println("重组：YosLyricView 外层 1")

            Spacer(modifier = Modifier.height(statusBarHeight + 110.dp))

            val dominantBackground = MediaViewModelObject.paletteDarkVibrantColor.value
            val lyricTextColor =
                if (dominantBackground.luminance() < 0.4f)
                    Color.White
                else
                    Color.Black
            YosLyricView(
                //mediaViewModel = mediaViewModel,
                lrcEntriesLambda = lrcEntries,
                liveTimeLambda = {
                    (mediaControl?.currentPosition ?: 0).toInt()
                },
                mediaEvent = object : YosMediaEvent {
                    override fun onSeek(position: Int) {
                        mediaControl?.seekTo(position.toLong())
                    }
                },
                translationLambda = translationLambda,
                blurLambda = {
                    SettingsLibrary.LyricBlurEffect
                },
                uiConfig = YosUIConfig(
                    noLrcText = stringResource(id = R.string.tip_no_lyrics),
                    mainTextBasicColor = lyricTextColor.toArgb().toLong(),
                    subTextBasicColor = lyricTextColor.copy(alpha = 0.55f).toArgb().toLong()
                ),
                weightLambda = weightLambda,
                wordSyncedLambda = wordSyncedLambda,
                modifier = Modifier.drawWithCache {
                    onDrawWithContent {
                        val overlayPaint = Paint().apply {
                            blendMode = BlendMode.Plus
                        }
                        val rect = Rect(0f, 0f, size.width, size.height)
                        val canvas = this.drawContext.canvas

                        canvas.saveLayer(rect, overlayPaint)

                        val colors = if (weightLambda()) {
                            listOf(
                                Color.Transparent,
                                Color(0x59000000),
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color(0x59000000),
                                Color(0x21000000),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                Color.Transparent,
                                Color(0x59000000),
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                /*Color(0xD9000000),
                                Color(0xA6000000),
                                Color(0x73000000),
                                Color(0x59000000),
                                Color(0x3F000000),
                                Color(0x21000000),
                                Color(0x0C000000),*/
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black,
                                Color.Black
                            )
                        }

                        drawContent()

                        drawRect(
                            brush = Brush.verticalGradient(colors),
                            blendMode = BlendMode.DstIn
                        )

                        canvas.restore()
                    }
                },
                onBackClick = onBackClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionButtonsRow(
    musicPlayingLambda: () -> YosMediaItem?,
    navController: NavController? = null,
    isLyricsView: Boolean = false,
    onRefetchLyrics: (() -> Unit)? = null,
    albumUrlLambda: () -> Uri? = { null },
    onShowMenu: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .overlayEffect(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dp = 28.dp

        val context = LocalContext.current

        Box(
            modifier = Modifier
                .clickable(
                    onClick = {
                        val musicPlaying = musicPlayingLambda()
                        if (musicPlaying != null) {
                            Vibrator.click(context)
                            if (musicPlaying.let { FavPlayListLibrary.isFavorite(it) }) {
                                FavPlayListLibrary.removeMusic(musicPlaying)
                            } else {
                                FavPlayListLibrary.addMusic(musicPlaying)
                            }
                        }
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .size(dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = musicPlayingLambda()?.let { FavPlayListLibrary.isFavorite(it) }
                    ?: false,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }) {
                if (it) {
                    Icon(
                        painterResource(id = R.drawable.ic_nowplaying_favorited),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dp)
                    )
                } else {
                    Icon(
                        painterResource(id = R.drawable.ic_nowplaying_favorite),
                        contentDescription = null,
                        modifier = Modifier
                            .overlayEffect()
                            .size(dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 90f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .clickable(
                    onClick = onShowMenu,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() })
                .size(dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(id = R.drawable.ic_nowplaying_more),
                contentDescription = null,
                modifier = Modifier.size(dp).overlayEffect(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingOverflowSheet(
    isOpen: MutableState<Boolean>,
    song: YosMediaItem?,
    navController: NavController,
    onOpenLibraryTarget: (OverflowLibraryTarget) -> Unit,
) {
    if (!isOpen.value) return

    var screen by remember { mutableStateOf(OverflowScreen.Menu) }
    val navigationDirection = remember {
        mutableIntStateOf(SheetNavigationForward)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onDismiss: () -> Unit = {
        isOpen.value = false
        screen = OverflowScreen.Menu
        navigationDirection.intValue = SheetNavigationForward
    }

    YosBottomSheetDialog(
        bottomSheetState = sheetState,
        blurred = true,
        onDismissRequest = onDismiss,
    ) {
        SheetAnimatedContent(
            targetState = screen,
            navigationDirection = navigationDirection.intValue,
            modifier = Modifier.fillMaxWidth(),
            label = "NowPlayingOverflowSheet",
        ) { currentScreen ->
            when (currentScreen) {
                OverflowScreen.Menu -> OverflowMenuBody(
                    song = song,
                    navController = navController,
                    onOpenLibraryTarget = onOpenLibraryTarget,
                    onDismiss = onDismiss,
                    onPickPlaylist = {
                        navigationDirection.intValue = SheetNavigationForward
                        screen = OverflowScreen.Playlist
                    },
                    onPickSleepTimer = {
                        navigationDirection.intValue = SheetNavigationForward
                        screen = OverflowScreen.SleepTimer
                    },
                )

                OverflowScreen.Playlist -> PlayListPickerContent(
                    songToAdd = song,
                    onDone = onDismiss,
                    onBack = {
                        navigationDirection.intValue = SheetNavigationBackward
                        screen = OverflowScreen.Menu
                    },
                )

                OverflowScreen.SleepTimer -> SleepTimerContent(
                    onDone = {},
                    onBack = {
                        navigationDirection.intValue = SheetNavigationBackward
                        screen = OverflowScreen.Menu
                    },
                )
            }
        }
    }
}

private enum class OverflowScreen { Menu, Playlist, SleepTimer }

private enum class OverflowLibraryTarget(val route: String)
{
    Artist(UI.ArtistInfo),
    Album(UI.AlbumInfo),
}

@Composable
private fun OverflowMenuBody(
    song: YosMediaItem?,
    navController: NavController,
    onOpenLibraryTarget: (OverflowLibraryTarget) -> Unit,
    onDismiss: () -> Unit,
    onPickPlaylist: () -> Unit,
    onPickSleepTimer: () -> Unit,
) {
    val addToPlaylistLabel = stringResource(R.string.now_playing_overflow_add_to_playlist)
    val sleepTimerLabel = stringResource(R.string.now_playing_overflow_sleep_timer)
    val refetchLyricsLabel = stringResource(R.string.now_playing_overflow_refetch_lyrics)

    val sleepTimerActive = SleepTimer.state.value is SleepTimerState.Active
    val accent = MaterialTheme.colorScheme.primary

    val scope = rememberCoroutineScope()

    val onRefetchLyrics: () -> Unit = {
        onDismiss()
        scope.launch(Dispatchers.IO) {
            val track = song ?: return@launch
            MediaViewModelObject.isLoadingLyrics.value = true
            LyricsProcessor.resetLyricsState()
            MediaViewModelObject.lrcEntries.value = emptyList()
            MediaViewModelObject.otherSideForLines.clear()
            val lyrics = ArchiveTuneApis.fetchLyrics(
                title = track.title,
                artist = track.artists,
                album = track.album,
                durationMs = track.duration,
                videoId = track.mediaId,
            )
            if (lyrics != null && lyrics.text.isNotBlank()) {
                LyricsProcessor.applyLyrics(lyrics, lrcEntriesSetter = { MediaViewModelObject.lrcEntries.value = it })
            }
            MediaViewModelObject.isLoadingLyrics.value = false
        }
    }

    val items = remember(
        addToPlaylistLabel, sleepTimerLabel, refetchLyricsLabel, sleepTimerActive, accent,
        onPickPlaylist, onPickSleepTimer, onRefetchLyrics,
    ) {
        listOf(
            ActionItem(
                iconRes = R.drawable.ic_action_add,
                label = addToPlaylistLabel,
                onClick = onPickPlaylist,
            ),
            ActionItem(
                iconRes = R.drawable.ic_setting_moon,
                label = sleepTimerLabel,
                tint = if (sleepTimerActive) accent else null,
                onClick = onPickSleepTimer,
            ),
            ActionItem(
                iconRes = R.drawable.ic_refresh,
                label = refetchLyricsLabel,
                onClick = onRefetchLyrics,
            ),
        )
    }

    ActionSheetBody(
        header = if (song != null) {
            {
                NowPlayingOverflowHeader(
                    song = song,
                    navController = navController,
                    onOpenLibraryTarget = onOpenLibraryTarget,
                    onDismiss = onDismiss,
                )
            }
        } else null,
        items = items,
    )
}

@Composable
private fun NowPlayingOverflowHeader(
    song: YosMediaItem,
    navController: NavController,
    onOpenLibraryTarget: (OverflowLibraryTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(8.dp)
    val targetArtistNames = remember(song) {
        song.artistsList.orEmpty().filter { it.isNotBlank() }
    }
    val targetAlbumName = remember(song) {
        song.album?.takeIf { it.isNotBlank() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.thumb)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = shape
                },
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.orEmpty(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (targetArtistNames.isNotEmpty()) {
                val artistAnnotatedText = remember(targetArtistNames) {
                    buildAnnotatedString {
                        targetArtistNames.forEachIndexed { index, artistName ->
                            pushStringAnnotation(tag = "artist", annotation = artistName)
                            append(artistName)
                            pop()
                            if (index < targetArtistNames.lastIndex) {
                                append("、")
                            }
                        }
                    }
                }

                ClickableText(
                    text = artistAnnotatedText,
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        val artistName = artistAnnotatedText
                            .getStringAnnotations(tag = "artist", start = offset, end = offset)
                            .firstOrNull()
                            ?.item
                            ?: return@ClickableText

                        LibraryObject.setTargetArtistName(artistName)
                        LibraryObject.setArtistSongsSearchOnOpen(false)
                        navController.markNextNavigationFromNowPlaying()
                        onDismiss()
                        onOpenLibraryTarget(OverflowLibraryTarget.Artist)
                    },
                )
            } else if (!song.artists.isNullOrBlank()) {
                Text(
                    text = song.artists,
                    fontSize = 13.5.sp,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .alpha(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!song.album.isNullOrBlank()) {
                Text(
                    text = song.album,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(0.5f)
                        .clickable(
                            enabled = targetAlbumName != null,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            val albumName = targetAlbumName ?: return@clickable
                            LibraryObject.setTargetAlbumName(albumName)
                            navController.markNextNavigationFromNowPlaying()
                            onDismiss()
                            onOpenLibraryTarget(OverflowLibraryTarget.Album)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


@Composable
private fun PlayingBar(
    modifier: Modifier,
    albumUrlLambda: () -> Uri?,
    musicPlayingLambda: () -> YosMediaItem?,
    onAlbumClick: () -> Unit,
    navController: NavController? = null,
    isLyricsView: Boolean = false,
    onRefetchLyrics: (() -> Unit)? = null,
    onShowMenu: () -> Unit = {},
) = YosWrapper {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.5.dp)
            .padding(top = 22.dp)
            .height(70.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        ShadowImageWithCache(
            dataLambda = albumUrlLambda, contentDescription = null, modifier = modifier
                .size(69.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        onAlbumClick()
                    }), cornerRadius = 5.dp,
            imageQuality = ImageQuality.LOW,
            shadowType = ShadowType.Small,
            shadowOverlay = true
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 12.dp, end = 15.dp)
        ) {
            var showTitleMenu by remember { mutableStateOf(false) }
            val song = musicPlayingLambda()
            Box {
                Text(
                    text = song?.title ?: defaultTitle,
                    fontSize = 16.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.5.sp,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { showTitleMenu = true }
                    )
                )
                DropdownMenu(
                    expanded = showTitleMenu,
                    onDismissRequest = { showTitleMenu = false }
                ) {
                    if (song?.album?.isBlank() == false) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Album", fontSize = 13.sp, color = Color.Gray)
                                    Text(song.album ?: "", fontSize = 15.sp)
                                }
                            },
                            onClick = { showTitleMenu = false; onAlbumClick() }
                        )
                    }
                }
            }
            var showArtistMenu by remember { mutableStateOf(false) }
            Box {
                Text(
                    text = song?.artistsName ?: defaultArtistsName,
                    fontSize = 15.sp,
                    modifier = Modifier.overlayEffect().clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { showArtistMenu = true }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.35f)
                )
                DropdownMenu(
                    expanded = showArtistMenu,
                    onDismissRequest = { showArtistMenu = false }
                ) {
                    if (song?.album?.isBlank() == false) {
                        DropdownMenuItem(
                            text = { Text("View Album") },
                            onClick = { showArtistMenu = false; onAlbumClick() }
                        )
                    }
                    if (song?.artists?.isNotEmpty() == true) {
                        DropdownMenuItem(
                            text = { Text("View Artist") },
                            onClick = { showArtistMenu = false }
                        )
                    }
                }
            }
        }

        YosWrapper {
            ActionButtonsRow(
                musicPlayingLambda = musicPlayingLambda,
                navController = navController,
                isLyricsView = isLyricsView,
                onRefetchLyrics = onRefetchLyrics,
                albumUrlLambda = albumUrlLambda,
                onShowMenu = onShowMenu,
            )
        }
    }

}

@Composable
fun RowScope.AirPlay() {
    val contextCompose = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val connectedDevices =
        remember("AirPlay_connectedDevices") { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val audioDeviceName = remember("AirPlay_audioDeviceName") { mutableStateOf("") }
    val showName = remember("AirPlay_showName") { mutableStateOf(false) }

    YosWrapper {
        DisposableEffect(Unit) {
            val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction("com.pryvn.audiophile.BLUETOOTH_STATUS_REFRESH")
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (action == BluetoothDevice.ACTION_ACL_CONNECTED || action == BluetoothDevice.ACTION_ACL_DISCONNECTED || action == "com.pryvn.audiophile.BLUETOOTH_STATUS_REFRESH") {
                        if (ActivityCompat.checkSelfPermission(
                                contextCompose,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        connectedDevices.value =
                            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

                        val thisName =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.alias
                            } else {
                                connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.name
                            }
                        showName.value = thisName != null
                        if (thisName != null) {
                            audioDeviceName.value = thisName.trim()
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                contextCompose.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                contextCompose.registerReceiver(receiver, filter)
            }

            if (ActivityCompat.checkSelfPermission(
                    contextCompose,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                connectedDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                val thisName =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.alias
                    } else {
                        connectedDevices.value.firstOrNull { it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && it.isConnected() }?.name
                    }
                showName.value = thisName != null
                if (thisName != null) {
                    audioDeviceName.value = thisName.trim()
                }
            }

            onDispose {
                runCatching {
                    contextCompose.unregisterReceiver(receiver)
                }
            }
        }
    }

    val pbDensity = LocalDensity.current
    val navBarHeight = with(pbDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }

    YosWrapper {
        val context = LocalContext.current

        val systemMediaControlResolver = SystemMediaControlResolver(context)

        Column(
            modifier = Modifier
                .heightIn(min = 53.dp)
                .height(navBarHeight + 48.dp)
                .weight(1f)
                .clickable(
                    onClick = {
                        systemMediaControlResolver.intentSystemMediaDialog()
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = showName.value, transitionSpec = {
                    (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                        scaleOut(
                            targetScale = 0.3f
                        ) + fadeOut()
                    )
                }, contentAlignment = Alignment.Center) {
                    if (it) {
                        Icon(
                            painterResource(id = R.drawable.ic_earphone),
                            contentDescription = null,
                            modifier = Modifier
                                .size(27.dp)
                        )
                    } else {
                        Icon(
                            painterResource(id = R.drawable.ic_nowplaying_airplay),
                            contentDescription = null,
                            modifier = Modifier
                                .size(21.5.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(showName.value, enter = scaleIn(initialScale = 0.3f) + fadeIn(), exit = scaleOut(
                targetScale = 0.3f
            ) + fadeOut()) {
                Text(
                    text = audioDeviceName.value,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun BluetoothDevice.isConnected(): Boolean {
    return runCatching {
        val isConnectedMethod =
            BluetoothDevice::class.java.getMethod("isConnected")
        isConnectedMethod.isAccessible = true
        isConnectedMethod.invoke(this) as Boolean
    }.getOrDefault(false)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControl(
    isPlayingLambda: () -> Boolean,
    isPlayingOnChanged: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onStatus: (Boolean) -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onLyrics: () -> Unit,
    onPlaylist: () -> Unit,
    nowPage: () -> String,
    onSlider: () -> Unit,
    onWhile: suspend () -> Unit,
    modifier: Modifier
) {
    val playingDuration = rememberSaveable(key = "PlayerControl_playingDuration") {
        mutableLongStateOf(0L)
    }
    val playingPosition = rememberSaveable(key = "PlayerControl_playingPosition") {
        mutableLongStateOf(0L)
    }
    val context = LocalContext.current
    val playedTime = rememberSaveable(key = "PlayerControl_playedTime") { mutableStateOf("0:00") }
    val remainingTime =
        rememberSaveable(key = "PlayerControl_remainingTime") { mutableStateOf("-0:00") }
    val sliderPosition = remember("PlayerControl_sliderPosition") { mutableFloatStateOf(0f) }
    val isSliding = remember("PlayerControl_isSliding") {
        mutableStateOf(false)
    }
    val isPressed = remember("PlayerControl_isPressed") { mutableStateOf(false) }
    val isDragging = remember("PlayerControl_isDragging") { mutableStateOf(false) }
    val isLoading = remember("PlayerControl_isLoading") {
        derivedStateOf {
            val state = MediaViewModelObject.playbackLoadingState.value
            state == PlaybackLoadingState.ResolvingStream ||
            state == PlaybackLoadingState.PreparingPlayer ||
            (state == PlaybackLoadingState.Buffering && playingDuration.longValue == 0L)
        }
    }
    val timestampFontSize by animateFloatAsState(
        targetValue = if (isPressed.value || isDragging.value) 16f else 12f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
    )
    val seekbarAlpha by animateFloatAsState(
        targetValue = if (isPressed.value || isDragging.value) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
    )

    YosWrapper {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 25.dp)
                .padding(bottom = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            println("重组：控制区域内部")

            YosWrapper {
                // 启动作用
                YosWrapper {
                    val lifecycleState =
                        LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

                    LaunchedEffect(Unit) {
                        var lastPosition = 0L
                        while (true) {
                            //isPlaying.value = /*mediaControl?.isPlaying ?: false*/ FadeExo.targetStatus != 0
                            if (lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)) {
                                playingDuration.longValue = mediaControl?.duration ?: 0
                                playingPosition.longValue = mediaControl?.currentPosition ?: 0

                                if (!isSliding.value && playingDuration.longValue > 0L) {
                                    val totalSeconds =
                                        playingPosition.longValue.coerceAtLeast(0) / 1000
                                    if (totalSeconds != lastPosition) {
                                        playedTime.value = formatTime(totalSeconds)

                                        sliderPosition.floatValue =
                                            playingPosition.longValue.coerceAtLeast(0).toFloat()

                                        val remainingSeconds =
                                            playingDuration.longValue.coerceAtLeast(0) / 1000 - totalSeconds
                                        remainingTime.value = "-${formatTime(remainingSeconds)}"
                                        lastPosition = totalSeconds
                                    }
                                }

                                onWhile()
                            }

                            MediaViewModelObject.isBuffering.value =
                                mediaControl?.playbackState == Player.STATE_BUFFERING

                            delay(700)
                        }
                    }
                }

                // 进度条
                YosWrapper {
                    val seekBarHeight by animateDpAsState(
                        targetValue = if (isPressed.value || isDragging.value) 12.dp else 7.dp,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .overlayEffect()
                            .graphicsLayer(alpha = seekbarAlpha)
                            .pointerInput(Unit) {
                                if (isLoading.value) return@pointerInput
                                detectTapGestures(
                                    onPress = {
                                        isPressed.value = true
                                        tryAwaitRelease()
                                        if (!isDragging.value) {
                                            isPressed.value = false
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                if (isLoading.value) return@pointerInput
                                detectDragGestures(
                                    onDragStart = {
                                        isPressed.value = true
                                        isDragging.value = true
                                        isSliding.value = true
                                    },
                                    onDrag = { change, dragAmount ->
                                        val range = playingDuration.longValue.toFloat().coerceAtLeast(0f)
                                        val delta = dragAmount.x / size.width
                                        sliderPosition.floatValue = (sliderPosition.floatValue + delta * range).coerceIn(0f, range)
                                        val totalSeconds = (sliderPosition.floatValue / 1000).toLong()
                                        playedTime.value = formatTime(totalSeconds)
                                        val remainingSeconds = playingDuration.longValue / 1000 - totalSeconds
                                        remainingTime.value = "-${formatTime(remainingSeconds)}"
                                        onSlider()
                                    },
                                    onDragEnd = {
                                        Vibrator.longClick(context)
                                        MediaViewModelObject.isBuffering.value = true
                                        onSeek(sliderPosition.floatValue)
                                        isDragging.value = false
                                        isPressed.value = false
                                        isSliding.value = false
                                    },
                                    onDragCancel = {
                                        isDragging.value = false
                                        isPressed.value = false
                                        isSliding.value = false
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val trackHeight = seekBarHeight.toPx()
                            val trackY = size.height / 2f

                            drawRoundRect(
                                color = Color(0x0DFFFFFF),
                                topLeft = Offset(0f, trackY - trackHeight / 2f),
                                size = Size(width, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f)
                            )

                            val fraction = if (playingDuration.longValue > 0L)
                                (sliderPosition.floatValue / playingDuration.longValue).coerceIn(0f, 1f) else 0f
                            if (fraction > 0f) {
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(0f, trackY - trackHeight / 2f),
                                    size = Size(width * fraction, trackHeight),
                                    cornerRadius = CornerRadius(trackHeight / 2f)
                                )
                            }
                        }
                    }
                }

                // 控制按钮&进度文本
                YosWrapper {
                    //println("重组：控制区域内部 - 控制按钮&进度文本")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 7.dp)
                            .heightIn(min = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading.value) {
                            Text(
                                text = "Loading...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.3f),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = playedTime.value,
                                    fontSize = timestampFontSize.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.3.sp,
                                    color = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .overlayEffect()
                                        .graphicsLayer(alpha = seekbarAlpha)
                                )
                                Text(
                                    text = remainingTime.value,
                                    fontSize = timestampFontSize.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.3.sp,
                                    color = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .overlayEffect()
                                        .graphicsLayer(alpha = seekbarAlpha)
                                )
                            }
                        }

                        MusicQualityIndicator()
                    }


                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(61.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false),
                                        onClick = {
                                            Vibrator.click(context)
                                            onPrevious()
                                        }),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_rewind),
                                    contentDescription = "Previous",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(43.dp))

                            Box(
                                modifier = Modifier
                                    .size(58.5.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false),
                                        onClick = {
                                            Vibrator.click(context)
                                            isPlayingOnChanged(!isPlayingLambda())
                                            onStatus(isPlayingLambda())
                                        }),
                                contentAlignment = Alignment.Center
                            ) {
                                val buttonState = when {
                                    MediaViewModelObject.isBuffering.value ||
                                    MediaViewModelObject.playbackLoadingState.value == PlaybackLoadingState.ResolvingStream ||
                                    MediaViewModelObject.playbackLoadingState.value == PlaybackLoadingState.PreparingPlayer -> "spinner"
                                    isPlayingLambda() -> "pause"
                                    else -> "play"
                                }
                                AnimatedContent(targetState = buttonState, transitionSpec = {
                                    (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                                        scaleOut(
                                            targetScale = 0.3f
                                        ) + fadeOut()
                                    )
                                }) {
                                    when (it) {
                                        "spinner" -> AppleLoadingSpinner(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            size = 35.dp
                                        )
                                        "pause" -> Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_pause),
                                            contentDescription = "Pause",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp)
                                        )
                                        else -> Icon(
                                            painterResource(id = R.drawable.ic_nowplaying_play),
                                            contentDescription = "Play",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(9.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(43.dp))
                            Box(
                                modifier = Modifier
                                    .size(61.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false),
                                        onClick = {
                                            Vibrator.click(context)
                                            onNext()
                                        }),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_fforward),
                                    contentDescription = "Next",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 音量调节
            YosWrapper {
                if (SettingsLibrary.NowPlayingShowVolumeBar) {
                    VolumeSlider(context = context, onSlider)
                }
            }

            // 底部 歌词&播放列表
            YosWrapper {
                //println("重组：控制区域内部 - 底部栏")
                Row(
                    modifier = Modifier
                        .overlayEffect()
                        .fillMaxWidth()
                        .alpha(0.4f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val dp = 32.dp
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .weight(1f)
                            .clickable(
                                onClick = { onLyrics() },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = nowPage() == Lyric,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            }) {
                            if (it) {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_lyricson),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp)
                                )
                            } else {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_lyrics),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.1f))

                    AirPlay()

                    Spacer(modifier = Modifier.weight(0.1f))

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .weight(1f)
                            .clickable(
                                onClick = { onPlaylist() },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = nowPage() == PlayingList,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            }) {
                            if (it) {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_queueon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp)
                                )
                            } else {
                                Icon(
                                    painterResource(id = R.drawable.ic_nowplaying_queue),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp)
                                )
                            }
                        }
                    }
                }
            }

            // 边距填充
            /*YosWrapper {
                Spacer(modifier = Modifier.navigationBarsHeight(5.dp))
            }*/
            // 为显示设备名称，迁移到 AirPlay 底部处理
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSlider(context: Context, onSlider: () -> Unit) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val sliderPosition =
        remember("VolumeSlider_sliderPosition") { mutableFloatStateOf(currentVolume / maxVolume.toFloat()) }
    val sliding = remember("VolumeSlider_sliding") {
        mutableStateOf(false)
    }

    val volumeChangeReceiver = remember("VolumeSlider_volumeChangeReceiver") {
        VolumeChangeReceiver { newVolume ->
            sliderPosition.floatValue = newVolume / maxVolume.toFloat()
        }
    }
    val intentFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                volumeChangeReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(volumeChangeReceiver, intentFilter)
        }

        onDispose {
            context.unregisterReceiver(volumeChangeReceiver)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 1.5.dp)
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 2.5.dp)
            .overlayEffect()
            .alpha(0.45f)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_nowplaying_volume),
            contentDescription = "Mute",
            modifier = Modifier.size(20.dp)
        )

        YosWrapper {
            val animatedProgress = if (sliding.value) {
                sliderPosition
            } else {
                animateFloatAsState(
                    targetValue = sliderPosition.floatValue,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    visibilityThreshold = 0.0001f
                )
            }

            Slider(
                value = (animatedProgress.value * maxVolume),
                onValueChange = { newValue ->
                    sliding.value = true
                    sliderPosition.floatValue = newValue / maxVolume
                    val volume = newValue.toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                    onSlider()
                },
                valueRange = 0f..maxVolume.toFloat(),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x0DFFFFFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 1.5.dp, end = 5.dp),
                thumb = {
                },
                track = {
                    Track(
                        sliderPositions = SliderPositions(
                            initialActiveRange = 0f..animatedProgress.value
                        ), height = 7.dp
                    )
                },
                onValueChangeFinished = {
                    Vibrator.longClick(context)
                    sliding.value = false
                }
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_nowplaying_volume_full),
            contentDescription = "Max Volume",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Track(
    sliderPositions: SliderPositions,
    modifier: Modifier = Modifier,
    height: Dp
) = YosWrapper {
    val inactiveTrackColor = Color.White.copy(alpha = 0.5f)
    val activeTrackColor = Color.White
    val inactiveTickColor = Color.White.copy(alpha = 0.5f)
    val activeTickColor = Color.White
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val sliderLeft = Offset(0f, center.y)
        val sliderRight = Offset(size.width, center.y)
        val sliderStart = if (isRtl) sliderRight else sliderLeft
        val sliderEnd = if (isRtl) sliderLeft else sliderRight
        val tickSize = 2.0.dp.toPx()
        val trackStrokeWidth = height.toPx()
        drawLine(
            inactiveTrackColor,
            sliderStart,
            sliderEnd,
            trackStrokeWidth,
            StrokeCap.Round
        )
        val sliderValueEnd = Offset(
            sliderStart.x +
                    (sliderEnd.x - sliderStart.x) * sliderPositions.activeRange.endInclusive,
            center.y
        )

        val sliderValueStart = Offset(
            sliderStart.x +
                    (sliderEnd.x - sliderStart.x) * sliderPositions.activeRange.start,
            center.y
        )

        drawLine(
            activeTrackColor,
            sliderValueStart,
            sliderValueEnd,
            trackStrokeWidth,
            StrokeCap.Round
        )
        sliderPositions.tickFractions.groupBy {
            it > sliderPositions.activeRange.endInclusive ||
                    it < sliderPositions.activeRange.start
        }.forEach { (outsideFraction, list) ->
            drawPoints(
                list.fastMap {
                    Offset(lerp(sliderStart, sliderEnd, it).x, center.y)
                },
                PointMode.Points,
                (if (outsideFraction) inactiveTickColor else activeTickColor),
                tickSize,
                StrokeCap.Round
            )
        }
    }
}

fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${if (secs < 10) "0$secs" else "$secs"}"
}
