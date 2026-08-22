package com.pryvn.audiophile.ui.widgets

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pryvn.audiophile.R
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.artistsName
import com.pryvn.audiophile.data.objects.MediaViewModelObject
import com.pryvn.audiophile.ui.theme.SfProFontFamily

@Composable
fun LyricShareContent(
    song: YosMediaItem?,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lrcEntries = MediaViewModelObject.lrcEntries.value
    val lineTexts = remember(lrcEntries) {
        lrcEntries.map { line ->
            line.dropLast(1).joinToString("") { it.second }.trim()
        }
    }

    var selectedRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showCard by remember { mutableStateOf(false) }

    val maxLines = 14

    fun onLineClick(index: Int) {
        if (lineTexts.isEmpty()) return
        val current = selectedRange
        val newRange = when {
            current == null -> Pair(index, index)
            index in current.first..current.second -> Pair(index, index)
            index < current.first -> {
                val len = current.second - index + 1
                if (len > maxLines) {
                    Toast.makeText(context, context.getString(R.string.tip_lyric_share_save_fail), Toast.LENGTH_SHORT).show()
                    return
                }
                Pair(index, current.second)
            }
            else -> {
                val len = index - current.first + 1
                if (len > maxLines) {
                    Toast.makeText(context, context.getString(R.string.tip_lyric_share_save_fail), Toast.LENGTH_SHORT).show()
                    return
                }
                Pair(current.first, index)
            }
        }
        selectedRange = newRange
        showCard = true
    }

    val selectedLines = remember(selectedRange, lineTexts) {
        selectedRange?.let { (start, end) ->
            lineTexts.subList(start, (end + 1).coerceAtMost(lineTexts.size))
        } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable {
                        if (!selectedLines.isEmpty()) {
                            selectedRange = null
                            showCard = false
                        } else {
                            onBack()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.lyric_share_title, selectedLines.size),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontFamily = SfProFontFamily,
                )
                Text(
                    text = stringResource(R.string.lyric_share_subtitle, selectedLines.size),
                    fontSize = 13.2.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = SfProFontFamily,
                )
            }
        }

        if (lineTexts.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    itemsIndexed(lineTexts) { index, text ->
                        val isSelected = selectedRange?.let { (s, e) -> index in s..e } == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { onLineClick(index) }
                                .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent),
                        ) {
                            Text(
                                text = text,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                fontFamily = SfProFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_action_check),
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.tip_no_lyrics),
                fontSize = 16.5.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = SfProFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
            )
        }

        AnimatedVisibility(visible = showCard && selectedLines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                val cardModifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                    .padding(20.dp)

                Box(cardModifier) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (song != null) {
                            Text(
                                text = song.title ?: "Unknown",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = SfProFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artistsName ?: "Unknown Artist",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = SfProFontFamily,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            selectedLines.forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontFamily = SfProFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showCard && selectedLines.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            saveLyricsText(context, selectedLines, song)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lyric_share_save),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontFamily = SfProFontFamily,
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            shareLyricsText(context, selectedLines, song)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lyric_share_to),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontFamily = SfProFontFamily,
                    )
                }
            }
        }
    }
}

private fun buildLyricsText(selectedLines: List<String>, song: YosMediaItem?): String {
    val builder = StringBuilder()
    if (song != null) {
        builder.append(song.title ?: "Unknown")
        builder.append(" — ")
        builder.append(song.artistsName ?: "Unknown Artist")
        builder.append("\n\n")
    }
    selectedLines.forEach { line ->
        builder.append(line)
        builder.append("\n")
    }
    return builder.toString()
}

private suspend fun saveLyricsText(context: Context, selectedLines: List<String>, song: YosMediaItem?) = withContext(Dispatchers.IO) {
    val text = buildLyricsText(selectedLines, song)
    val name = "Lyrics_${System.currentTimeMillis()}.txt"
    val values = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Lyrics")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return@withContext
    try {
        resolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Toast.makeText(context, context.getString(R.string.tip_lyric_share_save_success), Toast.LENGTH_SHORT).show()
        uri
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        Toast.makeText(context, context.getString(R.string.tip_lyric_share_save_fail), Toast.LENGTH_SHORT).show()
        null
    }
}

private suspend fun shareLyricsText(context: Context, selectedLines: List<String>, song: YosMediaItem?) = withContext(Dispatchers.IO) {
    val text = buildLyricsText(selectedLines, song)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.lyric_share_to))
    context.startActivity(chooser)
}
