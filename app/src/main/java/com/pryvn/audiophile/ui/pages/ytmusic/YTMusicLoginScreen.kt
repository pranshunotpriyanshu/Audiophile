package com.pryvn.audiophile.ui.pages.ytmusic

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.api.YouTubeApi
import moe.rukamori.archivetune.innertube.YouTube
import com.pryvn.audiophile.data.libraries.PlayListLibrary
import com.pryvn.audiophile.data.libraries.SettingsLibrary

private const val DEFAULT_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private val YOUTUBE_COOKIE_URLS = listOf(
    "https://music.youtube.com",
    "https://www.youtube.com",
    "https://youtube.com",
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTMusicLoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView: WebView? = null
    var hasNavigated by remember { mutableStateOf(false) }

    fun onLoginSuccess() {
        if (hasNavigated) return
        hasNavigated = true

        scope.launch(Dispatchers.IO) {
            // Fetch and save account info
            YouTubeApi.fetchAccountInfo().onSuccess { accountInfo ->
                SettingsLibrary.YtMusicAccountName = accountInfo.name
                SettingsLibrary.YtMusicAccountEmail = accountInfo.email ?: ""
                SettingsLibrary.YtMusicAvatarUrl = accountInfo.avatarUrl ?: ""
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
                val name = SettingsLibrary.YtMusicAccountName
                val msg = if (name.isNotBlank()) {
                    context.getString(R.string.ytmusic_login_success) + " $name"
                } else {
                    context.getString(R.string.ytmusic_login_success)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                navController.navigate(com.pryvn.audiophile.ui.UI.HomePage) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                val cookieManager = CookieManager.getInstance()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val isYouTubePage =
                            url?.contains("youtube.com", ignoreCase = true) == true
                        if (isYouTubePage) {
                            view.loadUrl(
                                "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var v=c.get('VISITOR_DATA');if(v){Android.onRetrieveVisitorData(v);return}}var y=window.yt&&window.yt.config_;if(y&&y.VISITOR_DATA){Android.onRetrieveVisitorData(y.VISITOR_DATA);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"VISITOR_DATA\":\"([^\"]+)\"/);if(m){Android.onRetrieveVisitorData(m[1]);return}}}catch(e){}})())"
                            )
                            view.loadUrl(
                                "javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var d=c.get('DATASYNC_ID');if(d){Android.onRetrieveDataSyncId(d);return}}var y=window.yt&&window.yt.config_;if(y&&y.DATASYNC_ID){Android.onRetrieveDataSyncId(y.DATASYNC_ID);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"DATASYNC_ID\":\"([^\"]+)\"/);if(m){Android.onRetrieveDataSyncId(m[1]);return}}}catch(e){}})())"
                            )
                        }

                        val mergedCookie = mergeYouTubeCookies(cookieManager, url)
                        if (!mergedCookie.isNullOrBlank()) {
                            SettingsLibrary.YtMusicCookie = mergedCookie
                            com.pryvn.audiophile.code.api.InnerTubeClient.cookie = mergedCookie
                            YouTube.cookie = mergedCookie
                            com.pryvn.audiophile.code.playback.SimpMusicStreamResolver.updateAuth(
                                cookie = mergedCookie,
                                visitorData = com.pryvn.audiophile.code.api.InnerTubeClient.visitorData,
                                dataSyncId = com.pryvn.audiophile.code.api.InnerTubeClient.dataSyncId,
                            )
                            if (mergedCookie.contains("SAPISID") && !hasNavigated) {
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
                                com.pryvn.audiophile.code.playback.SimpMusicStreamResolver.updateAuth(
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
                                com.pryvn.audiophile.code.playback.SimpMusicStreamResolver.updateAuth(
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

    TopAppBar(
        title = { Text(stringResource(R.string.ytmusic_login)) },
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = null,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
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
