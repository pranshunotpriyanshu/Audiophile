@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.pryvn.audiophile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.akane.libphonograph.hasScopedStorageWithMediaTypes
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.api.InnerTubeClient
import moe.rukamori.archivetune.innertube.YouTube
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePause
import com.pryvn.audiophile.code.utils.player.FadeExo.fadePlay
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.data.models.ImageViewModel
import com.pryvn.audiophile.data.models.MainViewModel
import com.pryvn.audiophile.data.models.MediaViewModel
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.data.objects.PlaybackLoadingState
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.UI.Settings.Companion.ExoplayerSetting
import com.pryvn.audiophile.ui.pages.HomeNav

import com.pryvn.audiophile.ui.pages.NowPlaying
import com.pryvn.audiophile.ui.pages.NowPlayingPage.Album
import com.pryvn.audiophile.ui.pages.library.Library
import com.pryvn.audiophile.ui.pages.library.NormalMusic
import com.pryvn.audiophile.ui.pages.library.albums.AlbumInfo
import com.pryvn.audiophile.ui.pages.library.albums.LocalAlbums
import com.pryvn.audiophile.ui.pages.library.artists.LocalArtists
import com.pryvn.audiophile.ui.pages.library.playlists.PlayLists
import com.pryvn.audiophile.ui.pages.settings.Settings
import com.pryvn.audiophile.ui.pages.settings.audio.exoPlayer.ExoPlayerSettings
import com.pryvn.audiophile.ui.pages.settings.audio.exoPlayer.MediaCodec
import com.pryvn.audiophile.ui.pages.settings.library.LibraryOverview
import com.pryvn.audiophile.ui.pages.settings.others.About
import com.pryvn.audiophile.ui.pages.settings.performance.LyricSetting
import com.pryvn.audiophile.ui.pages.settings.performance.NotificationSetting
import com.pryvn.audiophile.ui.pages.ytmusic.YTMusicLoginScreen
import com.pryvn.audiophile.ui.pages.ytmusic.YTMusicExploreScreen
import com.pryvn.audiophile.ui.pages.ytmusic.YTMusicSearchScreen
import com.pryvn.audiophile.ui.pages.ytmusic.YTMusicCategoryScreen
import com.pryvn.audiophile.ui.pages.ytmusic.YTMusicPlaylistsScreen
import com.pryvn.audiophile.ui.pages.ytmusic.OnlinePlaylistScreen
import com.pryvn.audiophile.ui.pages.ytmusic.onlinealbuminfo.OnlineAlbumInfo
import com.pryvn.audiophile.ui.pages.ytmusic.onlineartistinfo.OnlineArtistInfo
import com.pryvn.audiophile.ui.pages.settings.performance.userinterface.ScreenCornerSetDialog
import com.pryvn.audiophile.ui.pages.settings.performance.userinterface.UserInterfaceSetting
import com.pryvn.audiophile.ui.pages.settings.integration.ShazamRecognitionScreen
import com.pryvn.audiophile.ui.theme.YosMusicTheme
import com.pryvn.audiophile.ui.theme.YosRoundedCornerShape
import com.pryvn.audiophile.ui.theme.isAudiophileInDarkMode
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.BottomNavigator
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.NavItem
import com.pryvn.audiophile.ui.widgets.basic.AppleLoadingSpinner
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper
import java.io.File
import kotlin.math.abs

/*//MediaPlayer全局控制器
var mediaController = com.pryvn.audiophile.code.MediaController*/

class MainActivity : BaseActivity() {

    private val mediaViewModel: MediaViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    private val imageViewModel: ImageViewModel by viewModels()

    @Suppress("DEPRECATION")
    @OptIn(
        ExperimentalAnimationApi::class,
        ExperimentalHazeMaterialsApi::class, ExperimentalSharedTransitionApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            YosMusicTheme {
                    val context = LocalContext.current
                    val density = LocalDensity.current

                    val offsetY = remember("MainActivity_offsetY") { Animatable(0f) }
                    val parentHeight =
                        remember("MainActivity_parentHeight") { mutableIntStateOf(0) }
                    val screenCorner = remember("MainActivity_screenCorner") {
                        val corner = SettingsLibrary.ScreenCorner.toInt()
                        if (corner == 0) 1 else corner
                    }

                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        scope.launch {
                            val cookie = SettingsLibrary.YtMusicCookie
                            if (!cookie.isNullOrBlank()) {
                                InnerTubeClient.cookie = cookie
                                InnerTubeClient.visitorData = SettingsLibrary.YtMusicVisitorData
                                InnerTubeClient.dataSyncId = SettingsLibrary.YtMusicDataSyncId
                                YouTube.cookie = cookie
                                YouTube.visitorData = SettingsLibrary.YtMusicVisitorData
                                YouTube.dataSyncId = SettingsLibrary.YtMusicDataSyncId
                                com.pryvn.audiophile.archivetune.ArchiveTuneAdapter.updateAuth(
                                    cookie = cookie,
                                    visitorData = SettingsLibrary.YtMusicVisitorData,
                                    dataSyncId = SettingsLibrary.YtMusicDataSyncId,
                                )
                            }
                            InnerTubeClient.ensureVisitorData()
                            val visitorData = InnerTubeClient.visitorData
                            if (!visitorData.isNullOrBlank() && SettingsLibrary.YtMusicVisitorData.isNullOrBlank()) {
                                SettingsLibrary.YtMusicVisitorData = visitorData
                            }
                            if (YouTube.visitorData.isNullOrBlank()) {
                                YouTube.visitorData = visitorData
                            }
                            com.pryvn.audiophile.archivetune.ArchiveTuneAdapter.updateAuth(
                                cookie = SettingsLibrary.YtMusicCookie,
                                visitorData = visitorData,
                                dataSyncId = SettingsLibrary.YtMusicDataSyncId,
                            )
                        }
                    }

                    val hasMusic = remember { derivedStateOf { MediaController.musicPlaying.value != null } }

                    /*val parentHeightDp = remember(parentHeight.intValue) {
                        with(density) {
                            parentHeight.intValue.toDp()
                        }
                    }*/

                    println("重组：底层载体")

                    /*Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = Color.Black withNight Color.White
                    ) {*/
                        println("重组：主载体")
                        val miniPlayerHeight = 62.dp
                        val height = remember("MainActivity_height") { mutableIntStateOf(0) }

                        val navHeight = remember("MainActivity_navHeight") {
                            with(density) {
                                height.intValue.toDp().plus(miniPlayerHeight)
                            }
                        }
                        // 逻辑初始化区域
                        val navController = rememberNavController()
                        val navSpec = spring(
                            stiffness = 380f,
                            dampingRatio = 0.86f,
                            visibilityThreshold = 0.001f
                        )
                        // val scaffoldState = rememberBottomSheetScaffoldState()
                        val route = rememberSaveable(key = "MainActivity_route") {
                            mutableStateOf(UI.HomePage)
                        }
                        // 记录当前路线

                        YosWrapper {
                            val backstackEntry =
                                navController.currentBackStackEntryAsState()
                            route.value =
                                backstackEntry.value?.destination?.route ?: UI.HomePage
                        }

                        // 显示控制区域
                        val yosBottomSheetConfig = object {
                            val progress
                                get() = if (parentHeight.intValue == 0) 0f else abs(offsetY.value / parentHeight.intValue).coerceIn(
                                    0f,
                                    1f
                                )
                            val menuAlpha
                                get() = 1f - progress
                            val mainContainerCardScale
                                get() = 0.9f + (0.1f * menuAlpha)
                            val thisShowCorner
                                get() = progress > 0f

                            val barShowCorner
                                get() = progress < 1f
                            val showMenu
                                get() = progress < 1f

                            val barShapeValue
                                get() = lerp(12, screenCorner, progress)

                            val RTCorner
                                get() = screenCorner == 0
                        }

                        val showNowPlaying = remember("MainActivity_showNowPlaying") {
                            derivedStateOf {
                                yosBottomSheetConfig.menuAlpha < 0.3f
                            }
                        }

                        val nowPageNowPlaying =
                            rememberSaveable(key = "MainActivity_nowPageNowPlaying") {
                                mutableStateOf(Album)
                            }

                        val hazeState = remember { HazeState() }

                        YosWrapper {
                            val isNight = isAudiophileInDarkMode()
                            if (showNowPlaying.value) {
                                rememberSystemUiController().run {
                                    setNavigationBarColor(
                                        Color.Transparent,
                                        darkIcons = false,
                                        navigationBarContrastEnforced = false
                                    )
                                    setStatusBarColor(Color.Transparent, darkIcons = false)
                                    var activity = LocalContext.current as? Activity
                                    DisposableEffect(Unit) {
                                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        onDispose {
                                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                            activity = null
                                        }
                                    }
                                }
                            } else {
                                rememberSystemUiController().run {
                                    setNavigationBarColor(
                                        color = Color.Transparent,
                                        darkIcons = !isNight,
                                        navigationBarContrastEnforced = false
                                    )

                                    setStatusBarColor(Color.Transparent, darkIcons = !isNight)
                                }
                            }
                        }

                        // 导航区域
                        val defaultHome = stringResource(id = R.string.page_home_title)

                        val nowLabel = rememberSaveable(key = "MainActivity_nowLabel") {
                            mutableStateOf(defaultHome)
                        }

                        val pagerState = rememberPagerState(pageCount = { 4 })

                        // 以下为实际显示

                        // 主界面
                        YosWrapper {
                            println("重组：主界面")

                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val thisMainContainerCardScale =
                                            yosBottomSheetConfig.mainContainerCardScale
                                        scaleX = thisMainContainerCardScale
                                        scaleY = thisMainContainerCardScale
                                    }
                                    .graphicsLayer {
                                        //compositingStrategy = CompositingStrategy.Offscreen
                                        //transformOrigin = TransformOrigin(0.5f, 1f)

                                        if (yosBottomSheetConfig.thisShowCorner && !yosBottomSheetConfig.RTCorner) {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                            clip = true
                                            shape = YosRoundedCornerShape(screenCorner.dp)
                                        } else {
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                            clip = false
                                        }

                                        if (!yosBottomSheetConfig.barShowCorner) {
                                            alpha = 0f
                                        }
                                    }

                                ,
                                color = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ) {
                                // 主界面本体
                                SharedTransitionLayout {
                                    YosWrapper {
                                        BackHandler(showNowPlaying.value) {
                                            // println("isVisible: ${showNowPlaying.value}, nowPageNowPlaying.value: ${nowPageNowPlaying.value}")
                                            if (nowPageNowPlaying.value != Album) {
                                                nowPageNowPlaying.value =
                                                    Album
                                            } else {
                                                scope.launch {
                                                    // scaffoldState.bottomSheetState.partialExpand()
                                                    offsetY.animateTo(0f, animationSpec = navSpec)
                                                }
                                            }
                                        }
                                    }

                                    YosWrapper {
                                        val animateSpeed = 280
                                        val animationSpec: FiniteAnimationSpec<IntSize> =
                                            spring(stiffness = 380f, dampingRatio = 0.86f)
                                        val fadeAnimationSpec: FiniteAnimationSpec<Float> =
                                            tween(
                                                durationMillis = animateSpeed,
                                        easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f),
                                        )

                                        val backPressedTime = remember { mutableStateOf(0L) }

                                        BackHandler(
                                            enabled = route.value != UI.HomePage && !showNowPlaying.value
                                        ) {
                                            navController.popBackStack(UI.HomePage, false)
                                        }

                                        BackHandler(
                                            enabled = route.value == UI.HomePage && !showNowPlaying.value
                                        ) {
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - backPressedTime.value > 2000L) {
                                                backPressedTime.value = currentTime
                                                Toast.makeText(this@MainActivity, getString(R.string.page_home_press_back_again), Toast.LENGTH_SHORT).show()
                                            } else {
                                                finish()
                                            }
                                        }

                                        AnimatedNavHost(
                                            modifier = Modifier.then(
                                                if (SettingsLibrary.BarBlurEffect && !showNowPlaying.value) {
                                                    Modifier.haze(state = hazeState)
                                                } else {
                                                    //println("haze 父控件效果关闭")
                                                    Modifier
                                                }
                                            ),
                                            navController = navController,
                                            startDestination = UI.HomePage,
                                            enterTransition = {
                                                fadeIn(animationSpec = fadeAnimationSpec) + expandHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    expandFrom = Alignment.Start
                                                ) {
                                                    -it / 2
                                                }
                                            },
                                            exitTransition = {
                                                fadeOut(animationSpec = fadeAnimationSpec) + shrinkHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    shrinkTowards = Alignment.End
                                                ) {
                                                    it / 2
                                                }
                                            },
                                            popEnterTransition = {
                                                fadeIn(animationSpec = fadeAnimationSpec) + expandHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    expandFrom = Alignment.End
                                                ) {
                                                    it / 2
                                                }
                                            },
                                            popExitTransition = {
                                                fadeOut(animationSpec = fadeAnimationSpec) + shrinkHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    shrinkTowards = Alignment.Start
                                                ) {
                                                    -it / 2
                                                }
                                            }) {

                                            composable(UI.HomePage) {
                                                HomeNav(
                                                    navController,
                                                    pagerState,
                                                    imageViewModel
                                                ) {
                                                    nowLabel.value = it
                                                }
                                            }
                                            composable(UI.Library) {
                                                Library(
                                                    navController
                                                )
                                            }

                                            composable(UI.PlayLists) {
                                                PlayLists(navController)
                                            }
                                            composable(UI.NormalMusic) {
                                                NormalMusic(navController)
                                            }
                                            composable(UI.LocalAlbums) {
                                                LocalAlbums(
                                                    navController,
                                                    this@SharedTransitionLayout,
                                                    this@composable
                                                )
                                            }
                                            composable(UI.LocalArtists) {
                                                LocalArtists(navController)
                                            }

                                            composable(UI.AlbumInfo) {
                                                AlbumInfo(
                                                    navController,
                                                    this@SharedTransitionLayout,
                                                    this@composable
                                                )
                                            }

                                            composable(UI.Settings.Main) {
                                                Settings(
                                                    navController
                                                )
                                            }
                                            composable(UI.Settings.LibraryOverview) {
                                                LibraryOverview(navController)
                                            }
                                            composable(ExoplayerSetting) {
                                                ExoPlayerSettings(navController)
                                            }
                                            composable(UI.Settings.About) {
                                                About(
                                                    navController
                                                )
                                            }
                                            composable(UI.Settings.MediaCodec) {
                                                MediaCodec(navController)
                                            }

                                            composable(UI.Settings.LyricSetting) {
                                                LyricSetting(navController)
                                            }
                                            composable(UI.Settings.UserInterfaceSetting) {
                                                UserInterfaceSetting(navController)
                                            }
                                            composable(UI.Settings.NotificationSetting) {
                                                NotificationSetting(navController)
                                            }

                                            composable(UI.Settings.ShazamRecognition) {
                                                ShazamRecognitionScreen(navController)
                                            }

                                            composable(UI.YTMusicLogin) {
                                                YTMusicLoginScreen(navController)
                                            }
                                            composable(UI.YTMusicExplore) {
                                                YTMusicExploreScreen(navController)
                                            }
                                            composable(UI.YTMusicSearch) {
                                                YTMusicSearchScreen(
                                                    showBackButton = true,
                                                    onBackClick = { navController.popBackStack() },
                                                    navController = navController
                                                )
                                            }
                                            composable("${UI.YTMusicCategory}/{category}") { backStackEntry ->
                                                YTMusicCategoryScreen(
                                                    category = backStackEntry.arguments?.getString("category") ?: "",
                                                    navController = navController
                                                )
                                            }
                                            composable(UI.YTMusicPlaylists) {
                                                YTMusicPlaylistsScreen(navController)
                                            }
                                            composable(UI.OnlinePlaylist) {
                                                OnlinePlaylistScreen(navController)
                                            }
                                            composable(UI.OnlineAlbumInfo) {
                                                OnlineAlbumInfo(navController)
                                            }
                                            composable(UI.OnlineArtistInfo) {
                                                @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
                                                OnlineArtistInfo(navController)
                                            }
                                        }


                                    }
                                }

                                // 底部导航栏
                                YosWrapper {
                                    val showNavBar = route.value in listOf(
                                        UI.HomePage,
                                    )
                                    if (showNavBar) {
                                        val color =
                                            Color(0xFFF5F5F5) withNight /*Color(0xFF111111)*/ Color.Black
                                        Box(
                                            Modifier
                                                .fillMaxSize(),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            val navBarHeight128 = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() + 128.dp }
                                            // 背景
                                            if (!showNowPlaying.value && SettingsLibrary.BarBlurEffect) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(navBarHeight128)
                                                        .graphicsLayer {
                                                            compositingStrategy =
                                                                CompositingStrategy.Offscreen
                                                        }
                                                        .hazeChild(
                                                            hazeState,
                                                            HazeMaterials
                                                                .regular(
                                                                    color
                                                                )
                                                                .copy(
                                                                    blurRadius = 48.dp
                                                                )
                                                        )
                                                        .drawWithCache {
                                                            onDrawWithContent {
                                                                val colors = listOf(
                                                                    Color.Transparent,
                                                                    color.copy(alpha = 0.3f),
                                                                    color.copy(alpha = 0.6f),
                                                                    color,
                                                                    color,
                                                                    color,
                                                                    color,
                                                                    color
                                                                )

                                                                drawContent()

                                                                drawRect(
                                                                    brush = Brush.verticalGradient(
                                                                        colors
                                                                    ),
                                                                    blendMode = BlendMode.DstIn
                                                                )
                                                            }
                                                        }
                                                )
                                            } else {
                                                //println("haze 底栏效果关闭")
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(navBarHeight128)
                                                        .background(
                                                            brush = Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    color.copy(alpha = 0.3f),
                                                                    color.copy(alpha = 0.6f),
                                                                    color,
                                                                    color,
                                                                    color,
                                                                    color,
                                                                    color
                                                ),
                                                                startY = 0f,
                                                                endY = Float.POSITIVE_INFINITY
                                                            )
                                                        )
                                                )
                                            }
                                            BottomNavigator(
                                                nowLabel = { nowLabel.value },
                                            onLabelChange = {
                                                nowLabel.value = it

                                                val home =
                                                    context.getString(R.string.page_home_title)
                                                val browse =
                                                    context.getString(R.string.page_browse_title)
                                                val library =
                                                    context.getString(R.string.page_library_title)
                                                val search =
                                                    context.getString(R.string.page_search_title)
                                                val target = when (it) {
                                                    home -> 0
                                                    browse -> 1
                                                    search -> 2
                                                    library -> 3
                                                    else -> 0
                                                }
                                                if (route.value == UI.HomePage) {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(target)
                                                    }
                                                } else {
                                                    scope.launch {
                                                        pagerState.scrollToPage(target)
                                                    }
                                                    navController.popBackStack(
                                                        UI.HomePage,
                                                        false
                                                    )
                                                }
                                            },
                                            items = buildList {
                                                add(
                                                    NavItem(
                                                        stringResource(id = R.string.page_home_title),
                                                        R.drawable.ic_app_logo
                                                    )
                                                )
                                                add(
                                                    NavItem(
                                                        stringResource(id = R.string.page_browse_title),
                                                        R.drawable.ic_uitabbar_browse
                                                    )
                                                )
                                                add(
                                                    NavItem(
                                                        stringResource(id = R.string.page_search_title),
                                                        R.drawable.ic_uitabbar_search
                                                    )
                                                )
                                                add(
                                                    NavItem(
                                                        stringResource(id = R.string.page_library_title),
                                                        R.drawable.ic_uitabbar_library
                                                    )
                                                )
                                            },
                                            modifier = Modifier.onSizeChanged { height.intValue = it.height }
                                        )
                                    }
                                }
                            }

                            // 弹窗
                            YosWrapper {
                                val showCornerSetDialog =
                                    remember("MainActivity_showCornerSetDialog") {
                                        mutableStateOf(!SettingsLibrary.ScreenCornerSet)
                                    }

                                if (showCornerSetDialog.value) {
                                    ScreenCornerSetDialog {
                                        showCornerSetDialog.value = false
                                    }
                                } else {
                                    CheckAndRequestPermission()
                                                }
                                        }

                            }
                            }


                            // 播放条&播放界面
                        YosWrapper {
                            if (height.intValue == 0) return@YosWrapper
                            if (!hasMusic.value) return@YosWrapper

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onSizeChanged {
                                        parentHeight.intValue = it.height
                                    }
                                    .graphicsLayer {
                                        //compositingStrategy = CompositingStrategy.Offscreen
                                        val plus =
                                            0.07f * yosBottomSheetConfig.progress
                                        this.scaleX = 0.93f + plus
                                        this.scaleY = 0.93f + plus
                                        this.translationY =
                                            -(height.intValue + 10) * (yosBottomSheetConfig.menuAlpha)
                                        this.transformOrigin = TransformOrigin(0.5f, 1f)
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                YosWrapper {
                                    val miniPlayerHeightPx = remember("MainActivity_miniPlayerHeightPx") {
                                        with(density) {
                                            miniPlayerHeight.toPx()
                                        }
                                    }

                                    val miniPlayerShadow = remember("MainActivity_miniPlayerShadow") {
                                        with(density) {
                                            7.5.dp.toPx()
                                        }
                                    }

                                    val color = Color.White withNight Color(0xFF1C1C1E)

                                    println("重组：播放条&播放界面 外层")

                                    val showNavBar = route.value in listOf(
                                        UI.HomePage,
                                    )
                                    val seekbarRect = remember { mutableStateOf<Rect>(Rect.Zero) }
                                    val surfaceInRoot = remember { mutableStateOf<Offset>(Offset.Zero) }
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = yosBottomSheetConfig.progress * 0.5f }
                                            .background(Color.Black)
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .graphicsLayer {
                                            translationY =
                                                ((parentHeight.intValue - miniPlayerHeightPx) * (1 - yosBottomSheetConfig.progress))

                                            if (yosBottomSheetConfig.barShowCorner) {
                                                shape = YosRoundedCornerShape(
                                                    roundSize = yosBottomSheetConfig.barShapeValue.dp,
                                                    mSize = Size(
                                                        width = size.width,
                                                        height = (miniPlayerHeightPx + (parentHeight.intValue - miniPlayerHeightPx) * yosBottomSheetConfig.progress)
                                                    )
                                                )
                                                shadowElevation = miniPlayerShadow
                                                spotShadowColor = Color.Black.copy(alpha = 0.2f)
                                            }
                                        }
                                            .graphicsLayer {
                                                if (yosBottomSheetConfig.barShowCorner) {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                    clip = true
                                                    shape = YosRoundedCornerShape(
                                                        roundSize = yosBottomSheetConfig.barShapeValue.dp,
                                                        mSize = Size(
                                                            width = size.width,
                                                            height = (miniPlayerHeightPx + (parentHeight.intValue - miniPlayerHeightPx) * yosBottomSheetConfig.progress)
                                                        )
                                                    )
                                                } else {
                                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                                    clip = false
                                                }
                                            }
                                            .onGloballyPositioned { surfaceInRoot.value = it.localToRoot(Offset.Zero) }
                                            .pointerInput(Unit) {
                                                val velocityTracker = VelocityTracker()
                                                var ignoreDrag = false
                                                var totalDisplacement = 0f
                                                detectVerticalDragGestures(
                                                    onDragStart = { start ->
                                                        totalDisplacement = 0f
                                                        velocityTracker.resetTracking()
                                                        val touchInRoot = surfaceInRoot.value + start
                                                        ignoreDrag = touchInRoot.x in seekbarRect.value.left..seekbarRect.value.right &&
                                                            touchInRoot.y in seekbarRect.value.top..seekbarRect.value.bottom
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        if (ignoreDrag) return@detectVerticalDragGestures
                                                        totalDisplacement += abs(dragAmount)
                                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                        scope.launch {
                                                            offsetY.snapTo(
                                                                (offsetY.value - dragAmount).coerceIn(
                                                                    0f,
                                                                    parentHeight.intValue.toFloat()
                                                                )
                                                            )
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        if (ignoreDrag) {
                                                            velocityTracker.resetTracking()
                                                            return@detectVerticalDragGestures
                                                        }
                                                        val velocity = -velocityTracker.calculateVelocity().y
                                                        velocityTracker.resetTracking()
                                                        val max = parentHeight.intValue.toFloat()
                                                        val midPoint = max * 0.5f
                                                        offsetY.updateBounds(0f, max)
                                                        scope.launch {
                                                            if (totalDisplacement < 20f && offsetY.value < midPoint) {
                                                                offsetY.animateTo(max)
                                                            } else if (totalDisplacement >= 20f) {
                                                                val shouldExpand = if (abs(velocity) > 0.5f) velocity > 0f else offsetY.value > midPoint
                                                                if (shouldExpand) {
                                                                    offsetY.animateTo(max, initialVelocity = velocity.coerceAtLeast(0f))
                                                                } else {
                                                                    offsetY.animateTo(0f, initialVelocity = velocity.coerceAtMost(0f))
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        velocityTracker.resetTracking()
                                                    }
                                                )
                                            }
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(
                                                    constraints.copy(
                                                        minHeight = parentHeight.intValue,
                                                        maxHeight = parentHeight.intValue
                                                    )
                                                )
                                                layout(
                                                    placeable.width,
                                                    placeable.height
                                                ) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            },
                                        color = Color.Transparent
                                    ) {
                                        println("重组：播放条&播放界面")

                                        val isPlaying =
                                            rememberSaveable(key = "MainActivity_isPlaying") {
                                                MediaViewModelObject.isPlaying
                                            }

                                        // NowPlaying
                                        YosWrapper {
                                            NowPlaying(
                                                    mainViewModel = mainViewModel,
                                                    mediaViewModel = mediaViewModel,
                                                    navController = navController,
                                                    isPlayingStatusLambda = { isPlaying.value },
                                                    isPlayingOnChanged = {
                                                        isPlaying.value = it
                                                    },
                                                    nowPageLambda = { nowPageNowPlaying.value },
                                                    showMiniPlayer = { yosBottomSheetConfig.showMenu }
                                                ) {
                                                    nowPageNowPlaying.value = it
                                                }
                                        }

                                        // 迷你播放状态
                                        YosWrapper {
                                            //println("变换：菜单透明度 $menuAlpha")
                                            if (yosBottomSheetConfig.showMenu) {
                                                YosWrapper {
                                                    Column(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer {
                                                                compositingStrategy =
                                                                    CompositingStrategy.ModulateAlpha
                                                                this.alpha =
                                                                    yosBottomSheetConfig.menuAlpha
                                                            }
                                                            .background(color)
                                                    ) {
                                                        val miniPlayerBottomPad = if (showNavBar) 0.dp else 32.dp
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(miniPlayerHeight + miniPlayerBottomPad)
                                                                .padding(bottom = miniPlayerBottomPad)
                                                                .clickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null,
                                                                    onClick = {
                                                                        scope.launch {
                                                                            offsetY.animateTo(
                                                                                parentHeight.intValue.toFloat(),
                                                                                animationSpec = navSpec
                                                                            )
                                                                        }
                                                                    })
                                                        ) {

                                                            YosWrapper {
                                                                val showMiniBarHaze = remember(
                                                                    "MainActivity_showMiniBarHaze",
                                                                    yosBottomSheetConfig.menuAlpha
                                                                ) {
                                                                    derivedStateOf {
                                                                        yosBottomSheetConfig.menuAlpha == 1f && SettingsLibrary.BarBlurEffect
                                                                    }
                                                                }
                                                                this@Column.AnimatedVisibility(
                                                                    visible = showMiniBarHaze.value,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(
                                                                            navHeight
                                                                        ),
                                                                    enter = fadeIn(tween(100)),
                                                                    exit = fadeOut(tween(100))
                                                                ) {
                                                                    Spacer(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .hazeChild(
                                                                                hazeState,
                                                                                HazeMaterials
                                                                                    .thick(
                                                                                       color
                                                                                    )
                                                                                    .copy(
                                                                                        blurRadius = 48.dp
                                                                                    )
                                                                            )
                                                                    )
                                                                }
                                                            }

                                                            Row(
                                                                Modifier
                                                                    .height(miniPlayerHeight)
                                                                    .fillMaxWidth()
                                                                    .padding(
                                                                        horizontal = 8.dp
                                                                    )
                                                            ) {
                                                                Row(
                                                                    Modifier
                                                                        .height(
                                                                            miniPlayerHeight
                                                                        )
                                                                        .weight(1f),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    YosWrapper {
                                                                        ShadowImageWithCache(
                                                                            dataLambda = { MediaViewModelObject.bitmap.value },
                                                                            contentDescription = null,
                                                                            modifier = Modifier
                                                                                .size(47.dp),
                                                                            cornerRadius = 6.dp,
                                                                            shadowAlpha = 0f,
                                                                            imageQuality = ImageQuality.LOW
                                                                        )
                                                                    }
                                                                    Column(
                                                                        Modifier
                                                                            .padding(
                                                                                start = 10.dp,
                                                                                end = 5.dp
                                                                            )
                                                                    ) {
                                                                        Text(
                                                                            text = MediaController.musicPlaying.value?.title
                                                                                ?: defaultTitle,
                                                                            fontWeight = FontWeight.Medium,
                                                                            fontSize = 16.sp,
                                                                            lineHeight = 16.sp,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            color = Color.Black withNight Color.White
                                                                        )
                                                                        /*Text(
                                                                text = musicPlaying.value?.Artist
                                                                    ?: "未知艺术家",
                                                                fontSize = 13.5.sp,
                                                                lineHeight = 13.5.sp,
                                                                modifier = Modifier.alpha(
                                                                    0.6f
                                                                ),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = Color.Black withNight Color.White
                                                            )*/
                                                                    }
                                                                }
                                                                Row(
                                                                    Modifier
                                                                        .fillMaxHeight()
                                                                        .padding(end = 10.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(34.dp)
                                                                            .clickable(
                                                                                interactionSource = remember { MutableInteractionSource() },
                                                                                indication = ripple(
                                                                                    bounded = false
                                                                                ),
                                                                                onClick = {
                                                                                    val loadingState = MediaViewModelObject.playbackLoadingState.value
                                                                                    if (loadingState == PlaybackLoadingState.ResolvingStream || loadingState == PlaybackLoadingState.PreparingPlayer) return@clickable
                                                                                    Vibrator.click(
                                                                                        context
                                                                                    )
                                                                                    isPlaying.value =
                                                                                        !isPlaying.value
                                                                                    if (isPlaying.value) {
                                                                                        MediaController.mediaControl?.fadePlay()
                                                                                    } else {
                                                                                        MediaController.mediaControl?.fadePause()
                                                                                    }
                                                                                }),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        val miniLoadingState = MediaViewModelObject.playbackLoadingState.value
                                                                        val miniButtonState = when {
                                                                            miniLoadingState == PlaybackLoadingState.ResolvingStream ||
                                                                            miniLoadingState == PlaybackLoadingState.PreparingPlayer -> "loading"
                                                                            isPlaying.value -> "pause"
                                                                            else -> "play"
                                                                        }
                                                                        AnimatedContent(
                                                                            targetState = miniButtonState,
                                                                            transitionSpec = {
                                                                                (scaleIn(
                                                                                    initialScale = 0.3f
                                                                                ) + fadeIn()).togetherWith(
                                                                                    scaleOut(
                                                                                        targetScale = 0.3f
                                                                                    ) + fadeOut()
                                                                                )
                                                                            }) {
                                                                            when (it) {
                                                                                "loading" -> AppleLoadingSpinner(
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize()
                                                                                        .padding(6.dp),
                                                                                    size = 22.dp
                                                                                )
                                                                                "pause" -> Icon(
                                                                                    painterResource(
                                                                                        id = R.drawable.ic_nowplaying_mp_pause
                                                                                    ),
                                                                                    contentDescription = "Pause",
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize(),
                                                                                    tint = Color.Black withNight Color.White
                                                                                )
                                                                                else -> Icon(
                                                                                    painterResource(
                                                                                        id = R.drawable.ic_nowplaying_mp_play
                                                                                    ),
                                                                                    contentDescription = "Play",
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize(),
                                                                                    tint = Color.Black withNight Color.White
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                        Spacer(
                                                                        modifier = Modifier.width(
                                                                            18.dp
                                                                        )
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(36.dp)
                                                                            .clickable(
                                                                                interactionSource = remember { MutableInteractionSource() },
                                                                                indication = ripple(
                                                                                    bounded = false
                                                                                ),
                                                                                onClick = {
                                                                                    Vibrator.click(
                                                                                        context
                                                                                    )
                                                                                    MediaController.mediaControl?.seekToNextMediaItem()
                                                                                }),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Icon(
                                                                            painterResource(
                                                                                id = R.drawable.ic_nowplaying_mp_fforward
                                                                            ),
                                                                            contentDescription = "Next",
                                                                            modifier = Modifier
                                                                                .fillMaxSize(),
                                                                            tint = Color.Black withNight Color.White
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    /*}*/
            }
        }
    }

        @Composable
    fun CheckAndRequestPermission() {
        val context = LocalContext.current
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isGranted = permissions.entries.all { it.value }
            if (isGranted) {
                // Load music list here
                loadMusic(context, enforce = true)
                sendBroadcast(Intent("com.pryvn.audiophile.BLUETOOTH_STATUS_REFRESH"))
            } else {
                // Set music list to empty if permission is denied
                // mainMusicList.value = mutableListOf()
            }
        }

        YosWrapper {
            LaunchedEffect(Unit) {

                var permissions = emptyArray<String>()

                if ((hasScopedStorageWithMediaTypes()
                            && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED)
                    /*|| (!hasScopedStorageV2()
                            && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED)*/
                    || (!hasScopedStorageWithMediaTypes()
                            && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED)
                ) {
                    permissions += arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

                    if (hasScopedStorageWithMediaTypes()) {
                        permissions += arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                    }
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions += Manifest.permission.POST_NOTIFICATIONS
                }

                if (permissions.isNotEmpty()) {
                    requestPermissionLauncher.launch(permissions)
                }
                else {
                    loadMusic(context)
                }
            }
        }
    }


    /*LaunchedEffect(permissionState.value) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            if (hasPermission) {
                permissionState.value = PermissionState.DENIED
                loadMusic(context)
            }
        }

        val showDialog = remember("CheckPermission_showDialog") {
            derivedStateOf { permissionState.value != PermissionState.DENIED  }
        }

        if (showDialog.value) {
            val dialogProperties = ModalBottomSheetProperties(
                securePolicy = SecureFlagPolicy.Inherit,
                isFocusable = true,
                shouldDismissOnBackPress = false
            )

            val bottomSheetState = rememberModalBottomSheetState()
            val scope = rememberCoroutineScope()

            YosWrapper {
                OptionDialog(
                    title = stringResource(id = R.string.permission_grant_title),
                    subTitle = stringResource(id = R.string.permission_grant_subtitle),
                    content = {
                        Text(text = stringResource(id = R.string.permission_grant_desc))
                    },
                    positiveContent = stringResource(id = R.string.permission_grant_button_positive),
                    properties = dialogProperties,
                    bottomSheetState = bottomSheetState,
                    onPositive = {
                        scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                            if (!bottomSheetState.isVisible) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionState.value = PermissionState.DENIED
                            }
                        }
                    },
                    negativeContent = stringResource(id = R.string.permission_grant_button_negative),
                    onNegative = {
                        scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                            if (!bottomSheetState.isVisible) {
                                mainMusicList.value = mutableListOf()
                                permissionState.value = PermissionState.DENIED
                            }
                        }
                    }
                ) {
                    mainMusicList.value = mutableListOf()
                    permissionState.value = PermissionState.DENIED
                }
            }
        }*/

    /*enum class PermissionState {
        UNKNOWN,
        GRANTED,
        DENIED
    }*/

    private fun loadMusic(context: Context, enforce: Boolean = false) {
        val needRefresh = SettingsLibrary.RefreshEveryTime
        if (needRefresh || enforce) {
            mediaViewModel.viewModelScope.launch(Dispatchers.IO) {
                // Application中已还原，这里算是后台扫描
                // mainMusicList.value = MusicScanner(context).getMusicList()
                MusicLibrary.scanMedia(context)
                println("刷新媒体库")
            }
        }
    }

}


fun readFile(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        //e.printStackTrace()
        null
    }
}