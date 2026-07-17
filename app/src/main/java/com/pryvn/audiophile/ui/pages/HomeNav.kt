package com.pryvn.audiophile.ui.pages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.models.ImageViewModel
import com.pryvn.audiophile.ui.pages.library.Library
import com.pryvn.audiophile.ui.pages.ytmusic.YTMusicSearchScreen
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

@Composable
fun HomeNav(
    navController: NavController,
    pagerState: PagerState,
    imageViewModel: ImageViewModel,
    nowPageOnChanged: (String) -> Unit
) =
    YosWrapper {
        val context = LocalContext.current
        val home = context.getString(R.string.page_home_title)
        val search = context.getString(R.string.page_search_title)
        val library = context.getString(R.string.page_library_title)

        YosWrapper {
            LaunchedEffect(pagerState) {
                nowPageOnChanged(
                    when (pagerState.currentPage) {
                        0 -> home
                        1 -> search
                        2 -> library
                        else -> home
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 4,
            key = { page -> page },
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> Home(navController, imageViewModel)
                1 -> YTMusicSearchScreen(
                    showBackButton = false,
                    initialQuery = null,
                    isMoodGenreBrowse = false,
                    navController = navController
                )
                2 -> Library(navController)
            }
        }
    }
