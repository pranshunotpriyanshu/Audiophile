package com.pryvn.audiophile.ui.widgets.basic

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.utils.others.Vibrator
import com.pryvn.audiophile.data.libraries.SettingsLibrary
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PersonCropCircle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileButton(
    size: Dp = 24.dp,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val uriString = SettingsLibrary.ProfilePictureUri

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) { }
        SettingsLibrary.ProfilePictureUri = uri.toString()
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    Vibrator.longClick(context)
                    imagePicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (uriString.isNotBlank()) {
            CachedArtworkImage(
                url = uriString,
                contentDescription = stringResource(R.string.profile_picture),
                size = size.value.toInt(),
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = CupertinoIcons.Default.PersonCropCircle,
                contentDescription = stringResource(R.string.profile_picture),
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
