package com.pryvn.audiophile.ui.pages.ytmusic

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pryvn.audiophile.ui.widgets.basic.AppleConfirmSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.api.YouTubeApi
import com.pryvn.audiophile.code.api.innertube.YouTube as AppYouTube
import moe.rukamori.archivetune.innertube.YouTube
import com.pryvn.audiophile.data.libraries.PlayListLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.sheetTextColor

private const val DEFAULT_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private val YOUTUBE_COOKIE_URLS = listOf(
    "https://music.youtube.com",
    "https://www.youtube.com",
    "https://youtube.com",
)

/**
 * Global open state for the YT Music login bottom sheet. The sheet is rendered
 * by MainActivity (so it can apply the Apple-style depth effect on the whole
 * background) and opened from Settings.
 */
object YtMusicLoginSheet {
    var isOpen by mutableStateOf(false)

    /** Drag-to-dismiss state, driven by the sheet header's drag gesture. */
    var isDragging by mutableStateOf(false)
    var dragOffsetPx by mutableStateOf(0f)

    /** Set by MainActivity when the backdrop scrim is tapped; YTMusicLoginScreen
     *  consumes it and shows the cancel-confirmation instead of closing directly. */
    var confirmCloseRequest by mutableStateOf(false)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YTMusicLoginScreen(
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView: WebView? = null
    var hasLoggedIn by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val maxDragPx = with(density) {
        (LocalConfiguration.current.screenHeightDp * 0.83f).dp.toPx()
    }
    val dismissThresholdPx = with(density) { 150.dp.toPx() }

    // Backdrop taps (signalled by MainActivity's scrim) also require
    // confirmation before the login sheet actually closes.
    LaunchedEffect(YtMusicLoginSheet.confirmCloseRequest) {
        if (YtMusicLoginSheet.confirmCloseRequest) {
            YtMusicLoginSheet.confirmCloseRequest = false
            showCancelConfirm = true
        }
    }

    fun onLoginSuccess() {
        if (hasLoggedIn) return
        hasLoggedIn = true

        scope.launch(Dispatchers.IO) {
            // Fetch and save account info
            YouTubeApi.fetchAccountInfo().onSuccess { accountInfo ->
                SettingsLibrary.YtMusicAccountName = accountInfo.name
                SettingsLibrary.YtMusicAccountEmail = accountInfo.email ?: ""
                SettingsLibrary.YtMusicAvatarUrl = accountInfo.avatarUrl ?: ""
                SettingsLibrary.YtMusicChannelHandle = accountInfo.channelHandle ?: ""
            }.onFailure {
                // Account info failure is not critical for login
            }

            // Sync playlists from YouTube Music library
            YouTubeApi.library().onSuccess { json ->
                val parsedPlaylists = YouTubeApi.parseLibraryPlaylists(json)
                parsedPlaylists.forEach { pl ->
                    PlayListLibrary.create(pl.title)
                }
            }.onFailure {
                // Playlist sync failure - we still consider login successful
                // but could show a warning if needed
            }

            withContext(Dispatchers.Main) {
                // Auto-close after a successful login: the sheet is closing on
                // its own, never as a user dismissal, so any pending cancel
                // confirmation (scrim tap / header drag / back press) must be
                // cancelled — it can never appear once login has succeeded.
                showCancelConfirm = false
                YtMusicLoginSheet.confirmCloseRequest = false
                val name = SettingsLibrary.YtMusicAccountName
                val msg = if (name.isNotBlank()) {
                    context.getString(R.string.ytmusic_login_success) + " $name"
                } else {
                    context.getString(R.string.ytmusic_login_success)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onClose()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Apple-style sheet header: grabber + title + cancel button.
        // Dragging down on the header dismisses the sheet (WebView scrolls are
        // untouched because the gesture is scoped to the header only).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        YtMusicLoginSheet.isDragging = true
                        YtMusicLoginSheet.dragOffsetPx =
                            (YtMusicLoginSheet.dragOffsetPx + delta).coerceIn(0f, maxDragPx)
                    },
                    onDragStopped = { velocity ->
                        val shouldDismiss =
                            YtMusicLoginSheet.dragOffsetPx > dismissThresholdPx || velocity > 1200f
                        YtMusicLoginSheet.isDragging = false
                        YtMusicLoginSheet.dragOffsetPx = 0f
                        if (shouldDismiss) {
                            showCancelConfirm = true
                        }
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(sheetTextColor().copy(alpha = 0.3f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 16.sp,
                    color = Color(0xFFFF453A),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showCancelConfirm = true },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    text = stringResource(R.string.ytmusic_login),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SfProFontFamily,
                    color = sheetTextColor(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(64.dp))
            }
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    val cookieManager = CookieManager.getInstance()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val isYouTubePage =
                                url?.contains("youtube.com", ignoreCase = true) == true
                            if (isYouTubePage) {
                                view.loadUrl(
                                    "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var v=c.get('VISITOR_DATA');if(v){Android.onRetrieveVisitorData(v);return}}var y=window.yt&&window.yt.config_;if(y&&y.VISITOR_DATA){Android.onRetrieveVisitorData(y.VISITOR_DATA);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\\\"VISITOR_DATA\\\":\\\"([^\\\"]+)\\\"/);if(m){Android.onRetrieveVisitorData(m[1]);return}}}catch(e){}})())\""
                                )
                                view.loadUrl(
                                    "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var d=c.get('DATASYNC_ID');if(d){Android.onRetrieveDataSyncId(d);return}}var y=window.yt&&window.yt.config_;if(y&&y.DATASYNC_ID){Android.onRetrieveDataSyncId(y.DATASYNC_ID);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\\\"DATASYNC_ID\\\":\\\"([^\\\"]+)\\\"/);if(m){Android.onRetrieveDataSyncId(m[1]);return}}}catch(e){}})())\""
                                )
                            }

                            val mergedCookie = mergeYouTubeCookies(cookieManager, url)
                            if (!mergedCookie.isNullOrBlank()) {
                                SettingsLibrary.YtMusicCookie = mergedCookie
                                com.pryvn.audiophile.code.api.InnerTubeClient.cookie = mergedCookie
                                YouTube.cookie = mergedCookie
                                AppYouTube.cookie = mergedCookie
                                com.pryvn.audiophile.archivetune.ArchiveTuneAdapter.updateAuth(
                                    cookie = mergedCookie,
                                    visitorData = com.pryvn.audiophile.code.api.InnerTubeClient.visitorData,
                                    dataSyncId = com.pryvn.audiophile.code.api.InnerTubeClient.dataSyncId,
                                )
                                if (mergedCookie.contains("SAPISID") && !hasLoggedIn) {
                                    onLoginSuccess()
                                }
                            }
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onRetrieveVisitorData(newVisitorData: String?) {
                                if (!newVisitorData.isNullOrBlank()) {
                                    SettingsLibrary.YtMusicVisitorData = newVisitorData
                                    com.pryvn.audiophile.code.api.InnerTubeClient.visitorData = newVisitorData
                                    YouTube.visitorData = newVisitorData
                                    AppYouTube.visitorData = newVisitorData
                                    com.pryvn.audiophile.archivetune.ArchiveTuneAdapter.updateAuth(
                                        cookie = SettingsLibrary.YtMusicCookie,
                                        visitorData = newVisitorData,
                                        dataSyncId = com.pryvn.audiophile.code.api.InnerTubeClient.dataSyncId,
                                    )
                                }
                            }

                            @JavascriptInterface
                            fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                if (!newDataSyncId.isNullOrBlank()) {
                                    SettingsLibrary.YtMusicDataSyncId = newDataSyncId
                                    com.pryvn.audiophile.code.api.InnerTubeClient.dataSyncId = newDataSyncId
                                    YouTube.dataSyncId = newDataSyncId
                                    AppYouTube.dataSyncId = newDataSyncId
                                    com.pryvn.audiophile.archivetune.ArchiveTuneAdapter.updateAuth(
                                        cookie = SettingsLibrary.YtMusicCookie,
                                        visitorData = com.pryvn.audiophile.code.api.InnerTubeClient.visitorData,
                                        dataSyncId = newDataSyncId,
                                    )
                                }
                            }
                        },
                        "Android",
                    )
                    webView = this
                    loadUrl(DEFAULT_LOGIN_URL)
                }
            },
        )
    }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            showCancelConfirm = true
        }
    }

    if (showCancelConfirm) {
        AppleConfirmSheet(
            title = stringResource(R.string.ytmusic_login_cancel_title),
            message = stringResource(R.string.ytmusic_login_cancel_message),
            confirmText = stringResource(R.string.ytmusic_login_cancel_yes),
            cancelText = stringResource(R.string.ytmusic_login_cancel_no),
            isDestructive = true,
            onConfirm = {
                showCancelConfirm = false
                Toast.makeText(context, R.string.ytmusic_login_canceled, Toast.LENGTH_SHORT).show()
                onClose()
            },
            onDismiss = { showCancelConfirm = false },
        )
    }
}

private fun mergeYouTubeCookies(
    cookieManager: CookieManager,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()
    if (currentUrl != null) candidateUrls.add(currentUrl)
    candidateUrls.addAll(YOUTUBE_COOKIE_URLS)
    cookieManager.flush()
    candidateUrls.forEach { url ->
        cookieManager.getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach
                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty()) {
                    cookieParts[key] = value
                }
            }
    }
    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}
