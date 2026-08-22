package com.pryvn.audiophile.ui.pages.library.artists

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.MediaController
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.PlayList
import com.pryvn.audiophile.data.libraries.PlayListLibrary
import com.pryvn.audiophile.data.libraries.PlayListLibrary.addMusic
import com.pryvn.audiophile.data.libraries.PlayListLibrary.playList
import com.pryvn.audiophile.data.libraries.ArtistRelease
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.ui.pages.library.FloatingMenuAnchored
import com.pryvn.audiophile.ui.pages.library.FloatingMenuDivider
import com.pryvn.audiophile.ui.pages.library.FloatingMenuItem
import com.pryvn.audiophile.ui.pages.library.FloatingMenuItemDivider
import com.pryvn.audiophile.ui.pages.library.FloatingMenuPlayListPickerContent
import com.pryvn.audiophile.ui.theme.headingFontWeight
import com.pryvn.audiophile.ui.theme.isAudiophileInDarkMode
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ArtistHeroNameText(artistName: String) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontWeight = headingFontWeight()
    val fontFamily = MaterialTheme.typography.headlineLarge.fontFamily
    val displayName = artistName.uppercase()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val fittedFontSize = remember(displayName, maxWidth, textMeasurer, density, fontWeight) {
            val baseStyle = TextStyle(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = 44.sp,
                lineHeight = 46.sp,
            )
            var fontSize = 44.sp
            var layout = textMeasurer.measure(
                text = AnnotatedString(displayName),
                style = baseStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            while (layout.lineCount > 2 && fontSize > 24.sp) {
                fontSize = (fontSize.value * 0.94f).sp
                layout = textMeasurer.measure(
                    text = AnnotatedString(displayName),
                    style = baseStyle.copy(
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.05f).sp,
                    ),
                    constraints = Constraints(maxWidth = maxWidthPx),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            fontSize
        }

        Text(
            text = displayName,
            color = if (isAudiophileInDarkMode()) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            fontSize = fittedFontSize,
            lineHeight = (fittedFontSize.value * 1.05f).sp,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Large solid-white circular hero action button used for Play and Shuffle. */
@Composable
internal fun ArtistHeroActionButton(
    painter: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.42f)
            .size(62.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(Color.White)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    Vibrator.click(context)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Section header with an optional right-facing chevron (instead of a "See all" label). */
@Composable
internal fun ArtistSectionHeader(
    title: String,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 21.sp,
            fontWeight = headingFontWeight(),
            modifier = Modifier.weight(1f),
        )

        if (onMore != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMore,
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Translucent top bar over the hero: back, follow toggle and the overflow menu button. */
@Composable
internal fun ArtistTopBar(
    isFollowed: Boolean,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onMore: () -> Unit,
    onMorePositioned: (Offset) -> Unit,
    followEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ArtistTopBarCircleButton(
            painter = painterResource(id = R.drawable.ic_back),
            contentDescription = null,
            iconSize = 18.dp,
            onClick = onBack,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtistTopBarCircleButton(
                painter = painterResource(
                    id = if (isFollowed) {
                        R.drawable.ic_action_favorited
                    } else {
                        R.drawable.ic_action_favorite
                    },
                ),
                contentDescription = stringResource(
                    id = if (isFollowed) {
                        R.string.artist_action_unfollow
                    } else {
                        R.string.artist_action_follow
                    },
                ),
                iconSize = 22.dp,
                tint = if (isFollowed) MaterialTheme.colorScheme.primary else Color.White,
                enabled = followEnabled,
                onClick = onFollow,
            )

            ArtistTopBarCircleButton(
                painter = painterResource(id = R.drawable.ic_action_more),
                contentDescription = stringResource(id = R.string.playlist_overflow_more_cd),
                iconSize = 22.dp,
                modifier = Modifier.onGloballyPositioned {
                    onMorePositioned(it.localToRoot(Offset.Zero))
                },
                onClick = onMore,
            )
        }
    }
}

@Composable
private fun ArtistTopBarCircleButton(
    painter: Painter,
    contentDescription: String?,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.42f)
            .size(38.dp)
            .then(modifier)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    Vibrator.click(context)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Overflow menu (Add to Playlist / Play next) anchored to the top-bar "more" button. */
@Composable
internal fun ArtistOverflowMenu(
    expandedLambda: () -> Boolean,
    expandedOnChanged: (Boolean) -> Unit,
    buttonPosition: Offset,
    artistName: String,
    songs: List<YosMediaItem>,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addToPlaylistExpanded = remember(artistName) { mutableStateOf(false) }

    // Collapse the sub-menu again whenever the overflow is reopened.
    LaunchedEffect(expandedLambda()) {
        if (expandedLambda()) {
            addToPlaylistExpanded.value = false
        }
    }

    FloatingMenuAnchored(expandedLambda, expandedOnChanged, buttonPosition) {
        FloatingMenuItem(
            label = stringResource(id = R.string.now_playing_overflow_add_to_playlist),
            icon = painterResource(id = R.drawable.ic_action_add),
            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            trailingIconRotated = addToPlaylistExpanded.value,
        ) {
            addToPlaylistExpanded.value = !addToPlaylistExpanded.value
        }
        AnimatedVisibility(visible = addToPlaylistExpanded.value) {
            Column {
                FloatingMenuItemDivider()
                ArtistAddToPlaylistContent(
                    artistName = artistName,
                    songs = songs,
                    showHeader = false,
                    onDone = { expandedOnChanged(false) },
                    onBack = { addToPlaylistExpanded.value = false },
                )
            }
        }
        FloatingMenuDivider()
        FloatingMenuItem(
            label = stringResource(id = R.string.playlist_overflow_play_next),
            icon = painterResource(id = R.drawable.ic_action_play_next),
        ) {
            expandedOnChanged(false)
            if (songs.isEmpty()) { return@FloatingMenuItem }

            scope.launch(Dispatchers.IO) {
                val queued = MediaController.playNext(songs)
                if (!queued) { return@launch }

                withContext(Dispatchers.Main) {
                    val message = if (songs.size == 1) {
                        context.getString(R.string.playlist_play_next_toast_one)
                    } else {
                        context.getString(
                            R.string.playlist_play_next_toast_other,
                            songs.size,
                        )
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun ArtistAddToPlaylistContent(
    artistName: String,
    songs: List<YosMediaItem>,
    showHeader: Boolean = true,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceStub = remember(artistName) {
        PlayList(
            listID = "artist-bulk:$artistName",
            name = artistName,
            songDataList = emptyList(),
        )
    }

    val performBulkAdd: (PlayList) -> Unit = { target ->
        scope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                val live = playList.firstOrNull { it.listID == target.listID } ?: return@forEach
                PlayListLibrary.run {
                    live.addMusic(song)
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.playlist_picker_added_toast, target.name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    FloatingMenuPlayListPickerContent(
        excludeListId = sourceStub.listID,
        showHeader = showHeader,
        onBack = onBack ?: onDone,
        onDone = onDone,
        onPlaylistSelected = performBulkAdd,
    )
}

/** Square release card used in the full Albums / Singles & EPs grid pages. */
@Composable
internal fun ArtistReleaseGridCard(
    release: ArtistRelease,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        ShadowImage(
            dataLambda = { release.songs.firstOrNull()?.thumb },
            contentDescription = release.albumName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            shadowAlpha = 0f,
            cornerRadius = 10.dp,
            imageQuality = ImageQuality.HIGH,
        )
        Text(
            text = release.albumName,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(0.92f),
        )
        release.releaseYear?.let {
            Text(
                text = it.toString(),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .alpha(0.55f),
            )
        }
    }
}
