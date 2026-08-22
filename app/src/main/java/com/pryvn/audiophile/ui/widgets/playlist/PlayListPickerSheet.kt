package com.pryvn.audiophile.ui.widgets.playlist

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.PlayList
import com.pryvn.audiophile.data.libraries.PlayListLibrary
import com.pryvn.audiophile.data.libraries.PlayListLibrary.addMusic
import com.pryvn.audiophile.data.libraries.PlayListLibrary.playList
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.defaultTitle
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.theme.withNight
import com.pryvn.audiophile.ui.widgets.basic.SheetAnimatedContent
import com.pryvn.audiophile.ui.widgets.basic.SheetNavigationBackward
import com.pryvn.audiophile.ui.widgets.basic.SheetNavigationForward
import com.pryvn.audiophile.ui.widgets.basic.YosBottomSheetDialog
import com.pryvn.audiophile.ui.widgets.basic.sheetBackground
import com.pryvn.audiophile.ui.widgets.basic.sheetSeparator
import com.pryvn.audiophile.ui.widgets.basic.sheetTextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListPickerSheet(
    isOpen: MutableState<Boolean>,
    songToAdd: YosMediaItem?,
    songsToAdd: List<YosMediaItem>? = null,
    onCreated: ((PlayList) -> Unit)? = null,
    centered: Boolean = false,
) {
    if (!isOpen.value) return

    if (centered) {
        Dialog(
            onDismissRequest = { isOpen.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = true),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White withNight Color.Black,
                contentColor = Color.Black withNight Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                ) {
                    PlayListPickerContent(
                        songToAdd = songToAdd,
                        songsToAdd = songsToAdd,
                        onDone = { isOpen.value = false },
                        onCreated = onCreated,
                    )
                }
            }
        }
        return
    }

    YosBottomSheetDialog(onDismissRequest = { isOpen.value = false }) {
        PlayListPickerContent(
            songToAdd = songToAdd,
            songsToAdd = songsToAdd,
            onDone = { isOpen.value = false },
            onCreated = onCreated,
        )
    }
}

@Composable
fun PlayListPickerContent(
    songToAdd: YosMediaItem?,
    onDone: () -> Unit,
    songsToAdd: List<YosMediaItem>? = null,
    onBack: (() -> Unit)? = null,
    onCreated: ((PlayList) -> Unit)? = null,
    bulkAddSource: PlayList? = null,
    onBulkAdd: ((PlayList) -> Unit)? = null,
    appleTheme: Boolean = false,
) {
    val context = LocalContext.current
    val bulkMode = bulkAddSource != null
    var createMode by remember {
        mutableStateOf(songToAdd == null && songsToAdd == null && !bulkMode)
    }
    val navigationDirection = remember {
        mutableIntStateOf(SheetNavigationForward)
    }
    var newPlaylistName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    // centered confirmation asks whether to insert duplicates or skip.
    var duplicateTarget by remember { mutableStateOf<PlayList?>(null) }
    var duplicateSongs by remember { mutableStateOf<List<YosMediaItem>>(emptyList()) }

    val pendingSongs: () -> List<YosMediaItem> = { songsToAdd ?: listOfNotNull(songToAdd) }

    fun playlistContains(playlist: PlayList, music: YosMediaItem): Boolean =
        playlist.songDataList.any { existing ->
            if (existing.mediaId != null && music.mediaId != null) existing.mediaId == music.mediaId
            else existing.uri == music.uri
        }

    val addAllTo: (PlayList) -> Unit = { playlist ->
        pendingSongs().forEach { playlist.addMusic(it) }
    }

    val resetTransient: () -> Unit = {
        createMode = songToAdd == null && songsToAdd == null && !bulkMode
        newPlaylistName = ""
        nameError = null
    }

    val finish: () -> Unit = {
        resetTransient()
        onDone()
    }

    val onPickPlaylist: (PlayList) -> Unit = { playlist ->
        if (bulkMode) {
            onBulkAdd?.invoke(playlist)
        } else {
            val alreadyPresent = pendingSongs().filter { playlistContains(playlist, it) }
            if (alreadyPresent.isNotEmpty()) {
                // Ask before inserting duplicates.
                duplicateTarget = playlist
                duplicateSongs = pendingSongs()
            } else {
                addAllTo(playlist)
                if (songsToAdd != null) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_picker_added_many_toast,
                            songsToAdd.size,
                            playlist.name,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else if (songToAdd != null) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_picker_added_toast,
                            playlist.name,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                finish()
            }
        }
    }

    val confirmCreate: () -> Unit = {
        val trimmed = newPlaylistName.trim()
        when {
            trimmed.isEmpty() -> {
                // Confirm button is normally disabled; safety net only.
            }
            playList.any { it.name == trimmed } -> {
                nameError = context.getString(R.string.playlist_picker_duplicate_name)
            }
            else -> {
                PlayListLibrary.create(trimmed)
                val created = playList.firstOrNull { it.name == trimmed }
                if (created != null) {
                    when {
                        songsToAdd != null -> {
                            songsToAdd.forEach { created.addMusic(it) }
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_added_many_toast,
                                    songsToAdd.size,
                                    trimmed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        songToAdd != null -> {
                            created.addMusic(songToAdd)
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_added_toast,
                                    trimmed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_created_toast,
                                    trimmed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    onCreated?.invoke(created)
                }
                finish()
            }
        }
    }

    SheetAnimatedContent(
        targetState = createMode,
        navigationDirection = navigationDirection.intValue,
        modifier = Modifier.fillMaxWidth(),
        label = "PlayListPickerContent",
    ) { creatingPlayList ->
        val headerBack: (() -> Unit)? = when {
            creatingPlayList && (songToAdd != null || bulkMode) -> {
                {
                    navigationDirection.intValue = SheetNavigationBackward
                    createMode = false
                    newPlaylistName = ""
                    nameError = null
                }
            }

            onBack != null -> {
                {
                    navigationDirection.intValue = SheetNavigationBackward
                    onBack()
                }
            }

            else -> null
        }

        PickerHeader(
            title = if (creatingPlayList) {
                stringResource(R.string.playlist_picker_create_title)
            } else {
                stringResource(R.string.playlist_picker_title)
            },
            onBack = headerBack,
            apple = appleTheme,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (creatingPlayList) {
            CreatePlaylistBody(
                name = newPlaylistName,
                onNameChange = {
                    newPlaylistName = it
                    nameError = null
                },
                errorMessage = nameError,
                onConfirm = confirmCreate,
                onCancel = if (songToAdd == null && songsToAdd == null && !bulkMode) {
                    finish
                } else {
                    {
                        navigationDirection.intValue = SheetNavigationBackward
                        createMode = false
                        newPlaylistName = ""
                        nameError = null
                    }
                },
                apple = appleTheme,
            )
        } else if (appleTheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sheetBackground()),
            ) {
                CreateNewRow(
                    onClick = {
                        Vibrator.click(context)
                        navigationDirection.intValue = SheetNavigationForward
                        createMode = true
                    },
                    apple = true,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = sheetSeparator(),
                )
                ExistingPlayListList(
                    songToAdd = songToAdd,
                    songsToAdd = songsToAdd,
                    excludeId = bulkAddSource?.listID,
                    apple = true,
                    onAdd = { playlist -> onPickPlaylist(playlist) },
                )
            }
            duplicateTarget?.let { target ->
                DuplicateAddConfirmDialog(
                    songTitle = duplicateSongs.firstOrNull()?.title ?: defaultTitle,
                    playlistName = target.name,
                    onAddDuplicate = {
                        addAllTo(target)
                        duplicateTarget = null
                        duplicateSongs = emptyList()
                        if (songsToAdd != null) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_added_many_toast,
                                    songsToAdd.size,
                                    target.name,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (songToAdd != null) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_added_toast,
                                    target.name,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        finish()
                    },
                    onSkip = {
                        duplicateTarget = null
                        duplicateSongs = emptyList()
                    },
                )
            }
        } else {
            CreateNewRow(
                onClick = {
                    Vibrator.click(context)
                    navigationDirection.intValue = SheetNavigationForward
                    createMode = true
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Divider()

            ExistingPlayListList(
                songToAdd = songToAdd,
                songsToAdd = songsToAdd,
                excludeId = bulkAddSource?.listID,
                onAdd = { playlist -> onPickPlaylist(playlist) },
            )
            duplicateTarget?.let { target ->
                DuplicateAddConfirmDialog(
                    songTitle = duplicateSongs.firstOrNull()?.title ?: defaultTitle,
                    playlistName = target.name,
                    onAddDuplicate = {
                        addAllTo(target)
                        duplicateTarget = null
                        duplicateSongs = emptyList()
                        if (songsToAdd != null) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_added_many_toast,
                                    songsToAdd.size,
                                    target.name,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (songToAdd != null) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.playlist_picker_added_toast,
                                    target.name,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        finish()
                    },
                    onSkip = {
                        duplicateTarget = null
                        duplicateSongs = emptyList()
                    },
                )
            }
        }
    }
}

@Composable
private fun PickerHeader(
    title: String,
    onBack: (() -> Unit)?,
    apple: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Vibrator.click(context)
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(0.6f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (apple) SfProFontFamily else null,
            color = if (apple) sheetTextColor() else Color.Unspecified,
        )
    }
}

@Composable
private fun CreatePlaylistBody(
    name: String,
    onNameChange: (String) -> Unit,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    apple: Boolean = false,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canConfirm = name.trim().isNotEmpty()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp),
            )
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (name.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_picker_name_placeholder),
                    fontSize = 16.sp,
                    modifier = Modifier.alpha(0.5f),
                )
            }
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.Black withNight Color.White,
                    fontSize = 16.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canConfirm) onConfirm() },
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
            )
        }
    }

    if (errorMessage != null) {
        Text(
            text = errorMessage,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Action buttons. Borrows the style of OptionDialog's positive/negative.
    val buttonHeight = 50.dp
    val buttonShape = RoundedCornerShape(buttonHeight.div(2))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
                .clip(buttonShape)
                .background(
                    color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                )
                .clickable {
                    Vibrator.click(context)
                    onCancel()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.playlist_picker_cancel),
                fontSize = 16.5.sp,
                color = if (apple) sheetTextColor() else Color.Unspecified,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
                .background(
                    color = if (canConfirm) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = buttonShape,
                )
                .clip(buttonShape)
                .clickable(enabled = canConfirm) {
                    Vibrator.click(context)
                    onConfirm()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.playlist_picker_create_confirm),
                color = Color.White,
                fontSize = 16.5.sp,
            )
        }
    }
}

@Composable
private fun CreateNewRow(
    onClick: () -> Unit,
    apple: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = if (apple) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_action_add),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.playlist_picker_create_new),
            fontSize = 16.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ExistingPlayListList(
    songToAdd: YosMediaItem?,
    excludeId: String?,
    onAdd: (PlayList) -> Unit,
    songsToAdd: List<YosMediaItem>? = null,
    apple: Boolean = false,
) {
    val playlists = remember(playList, excludeId) {
        playList.filter { it.listID != excludeId }.sortedBy { it.name }
    }

    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.playlist_picker_empty),
                fontSize = 14.sp,
                modifier = Modifier.alpha(0.5f),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, p -> p.listID },
        ) { _, playlist ->
            ExistingPlayListRow(
                playlist = playlist,
                isAlreadyIn = (songsToAdd ?: listOfNotNull(songToAdd)).any { song ->
                    playlist.songDataList.any { it.uri == song.uri }
                },
                onClick = { onAdd(playlist) },
                apple = apple,
            )
        }
    }
}

@Composable
private fun ExistingPlayListRow(
    playlist: PlayList,
    isAlreadyIn: Boolean,
    onClick: () -> Unit,
    apple: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = if (apple) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.placeholder_playlist_default),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (apple) sheetTextColor() else Color.Unspecified,
            )
            Text(
                text = pluralSongCount(playlist.songDataList.size),
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (apple) sheetTextColor().copy(alpha = 0.55f) else Color.Unspecified,
            )
        }
        if (isAlreadyIn) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_check),
                contentDescription = stringResource(R.string.playlist_picker_already_added_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
}

@Composable
private fun DuplicateAddConfirmDialog(
    songTitle: String,
    playlistName: String,
    onAddDuplicate: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White withNight Color.Black,
            contentColor = Color.Black withNight Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.playlist_picker_duplicate_message, songTitle, playlistName),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                Vibrator.click(context)
                                onSkip()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_picker_duplicate_skip),
                            fontSize = 16.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(23.dp))
                            .clip(RoundedCornerShape(23.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                Vibrator.click(context)
                                onAddDuplicate()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_picker_duplicate_add),
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun pluralSongCount(count: Int): String {
    return pluralStringResource(R.plurals.playlist_picker_song_count, count, count)
}
