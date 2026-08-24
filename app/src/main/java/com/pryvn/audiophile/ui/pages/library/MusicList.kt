package com.pryvn.audiophile.ui.pages.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.libraries.defaultArtistsName
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.ui.widgets.basic.ImageQuality
import com.pryvn.audiophile.ui.widgets.basic.ShadowImageWithCache
import com.pryvn.audiophile.ui.widgets.song.SongOverflowSheet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun /*LazyItemScope.*/MusicList(
    music: YosMediaItem,
    onQueueSwipe: (() -> Unit)? = null,
    navController: NavController? = null,
    /** When true the row is in multi-select mode: taps toggle [selected]
     * (via [onToggleSelected]) instead of playing, and long-press also
     * toggles instead of opening the song overflow sheet. */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** "Select" item shown in the song overflow sheet: switches this list
     * into multi-select mode. When null the item is hidden. */
    onSelect: (() -> Unit)? = null,
    onToggleSelected: (() -> Unit)? = null,
    itemClick: () -> Unit,
) {
    val context = LocalContext.current
    val songMenuOpen = remember(music.uri, music.mediaId) { mutableStateOf(false) }
    /*rememberSaveable(stateSaver = object : Saver<String?, Any> {
    override fun restore(value: Any): String? {
        return value as String?
    }

    override fun SaverScope.save(value: String?): Any {
        return value ?: ""
    }
}) {
    mutableStateOf(null)
}

LaunchedEffect(Unit) {
    val path = context.filesDir.absolutePath + "/${music().ID}.png"
    thumb.value = path
}*/

    /*val background = Color(Color.Transparent.toArgb())
    val originalColor = rememberSaveable(music()) {
        mutableStateOf(0)
    }
    val duration = rememberSaveable(music()) {
        mutableStateOf("00:00")
    }
    if (duration.value == "00:00") {
        YosWrapper {
            LaunchedEffect(Unit) {
                val time = MusicScanner(context).timeConversion(music().duration)
                duration.value = time.min + ":" + time.sec
            }
        }
    }
    if (originalColor.value == 0) {
        if (thumb != null && music() == musicPlaying.value) {
            val thumb = context.filesDir.absolutePath + "/${music().ID}.jpg"
            val bitmap = if (File(thumb).exists()) BitmapFactory.decodeFile(thumb) else null
            if (bitmap != null) {
                val builder = Palette.from(bitmap)
                val palette = builder.generate()
                originalColor.value =
                    Color(palette.getLightVibrantColor(background.toArgb())).toArgb()
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getVibrantColor(background.toArgb())).toArgb()
                }
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getLightMutedColor(background.toArgb())).toArgb()
                }
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getMutedColor(background.toArgb())).toArgb()
                }
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getDominantColor(background.toArgb())).toArgb()
                }
            }
        }
    }
    val color by animateColorAsState(
        targetValue = if ((music() == musicPlaying.value)) Color(originalColor.value) else Color(
            MaterialTheme.colorScheme.background.toArgb()
        )
    )
    val alpha by animateFloatAsState(if (music() == musicPlaying.value) 0.3F else 1F)*/
    Row(
        modifier = Modifier
            /*.animateItem(fadeInSpec = null, fadeOutSpec = null)*/
            .height(64.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelected?.invoke() else itemClick()
                },
                onLongClick = {
                    Vibrator.longClick(context)
                    songMenuOpen.value = true
                },
            )
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            ShadowImageWithCache(
                dataLambda = { music.thumb },
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                cornerRadius = 3.5.dp,
                shadowAlpha = 0f,
                imageQuality = ImageQuality.LOW
            )
            if (selectionMode && selected) {
                // Dim the cover and overlay a check so the user can see the
                // selection state at a glance.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_check),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        Column(Modifier.padding(start = 16.dp)) {
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
                fontSize = 13.sp,
                lineHeight = 13.sp,
            )
        }
    }

    if (!selectionMode) {
        SongOverflowSheet(
            isOpen = songMenuOpen,
            song = music,
            navController = navController,
            onSelect = onSelect,
        )
    }
}
