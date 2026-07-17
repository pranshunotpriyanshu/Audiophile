package com.pryvn.audiophile.ui.pages.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.MusicLibrary.songs
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.objects.LibraryObject
import com.pryvn.audiophile.ui.UI
import com.pryvn.audiophile.ui.toUI
import com.pryvn.audiophile.ui.widgets.basic.ProfileButton
import com.pryvn.audiophile.ui.widgets.basic.Title
import com.pryvn.audiophile.ui.widgets.basic.YosWrapper

@Composable
fun Library(navController: NavController) =
    Column(
        Modifier
            .fillMaxSize()
    ) {
        Title(
            title = stringResource(id = R.string.page_library_title),
            rightBarIcon = {
                ProfileButton(
                    size = 24.dp,
                    onClick = { navController.toUI(UI.Settings.Main) },
                )
            }
        ) {
            if (!SettingsLibrary.LocalMusicEnabled) {
                if (SettingsLibrary.isYtMusicLoggedIn) {
                    item("yt_library") {
                        Column(Modifier.fillMaxSize()) {
                            SmallLabelItem(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                                label = stringResource(id = R.string.ytmusic_playlists)
                            ) {
                                navController.toUI(UI.YTMusicPlaylists)
                            }
                            LibraryDivider()
                            SmallLabelItem(
                                icon = painterResource(id = R.drawable.ic_uitabbar_search),
                                label = stringResource(id = R.string.ytmusic_explore)
                            ) {
                                navController.toUI(UI.YTMusicExplore)
                            }
                            LibraryDivider()
                            SmallLabelItem(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_songs),
                                label = stringResource(id = R.string.page_library_playlists)
                            ) {
                                navController.toUI(UI.PlayLists)
                            }
                        }
                    }
                } else {
                    item("guest_library") {
                        Column(Modifier.fillMaxSize()) {
                            SmallLabelItem(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                                label = stringResource(id = R.string.page_library_playlists)
                            ) {
                                navController.toUI(UI.PlayLists)
                            }
                        }
                    }
                }
            } else {
                item("Library") {
                    Column(
                        Modifier
                            .fillMaxSize(),
                    ) {
                        SmallLabelItem(
                            icon = painterResource(id = R.drawable.ic_library_link_icon_playlists),
                            label = stringResource(
                                id = R.string.page_library_playlists
                            )
                        ) {
                            navController.toUI(UI.PlayLists)
                        }
                        LibraryDivider()
                        SmallLabelItem(
                            icon = painterResource(id = R.drawable.ic_library_link_icon_artists),
                            label = stringResource(
                                id = R.string.page_library_artists
                            )
                        ) {
                            navController.toUI(UI.LocalArtists)
                        }
                        LibraryDivider()
                        SmallLabelItem(
                            icon = painterResource(id = R.drawable.ic_library_link_icon_album),
                            label = stringResource(
                                id = R.string.page_library_albums
                            )
                        ) {
                            navController.toUI(UI.LocalAlbums)
                        }
                        LibraryDivider()

                        YosWrapper {
                            val targetTitle = stringResource(
                                id = R.string.page_library_songs
                            )
                            val targetList = songs
                            SmallLabelItem(
                                icon = painterResource(id = R.drawable.ic_library_link_icon_songs),
                                label = targetTitle
                            ) {
                                LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                                navController.toUI(UI.NormalMusic)
                            }
                        }

                        LibraryDivider()
                    }
                }
            }
        }
    }
