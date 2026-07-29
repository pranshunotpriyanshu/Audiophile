package com.pryvn.audiophile.ui.pages.settings.integration

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.pryvn.audiophile.ui.widgets.basic.CachedArtworkImage
import com.pryvn.audiophile.R
import com.pryvn.audiophile.code.api.shazam.Shazam
import com.pryvn.audiophile.code.api.shazam.ShazamSignatureGenerator
import com.pryvn.audiophile.code.api.shazam.models.RecognitionResult
import com.pryvn.audiophile.code.api.shazam.models.RecognitionStatus
import com.pryvn.audiophile.ui.pages.settings.SettingBackground
import com.pryvn.audiophile.ui.theme.SfProFontFamily
import com.pryvn.audiophile.ui.widgets.basic.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAMPLE_RATE = 16000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShazamRecognitionScreen(navController: NavController) =
    SettingBackground {
        Title(
            title = stringResource(id = R.string.shazam_title),
            onBack = { navController.popBackStack() },
            content = {
                item("shazam") {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()

                    var status by remember { mutableStateOf<RecognitionStatus>(RecognitionStatus.Ready) }
                    var message by remember { mutableStateOf("") }
                    var isListening by remember { mutableStateOf(false) }
                    var hasMicPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        hasMicPermission = granted
                        if (!granted) {
                            message = "Microphone permission denied"
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))

                        if (!hasMicPermission) {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.shazam_mic_permission),
                                fontSize = 15.sp,
                                fontFamily = SfProFontFamily,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Grant Permission")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (isListening) return@Button
                                    isListening = true
                                    status = RecognitionStatus.Ready
                                    message = ""
                                    scope.launch {
                                        runRecognition(
                                            onStatusUpdate = { status = it },
                                            onMessage = { message = it },
                                        )
                                        isListening = false
                                    }
                                },
                                enabled = !isListening,
                                modifier = Modifier
                                    .size(120.dp),
                                shape = RoundedCornerShape(60.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isListening)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isListening) {
                                        Text(
                                            text = stringResource(R.string.shazam_listening),
                                            fontSize = 14.sp,
                                            fontFamily = SfProFontFamily,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.shazam_listen),
                                            fontSize = 16.sp,
                                            fontFamily = SfProFontFamily,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            when (val s = status) {
                                is RecognitionStatus.Ready -> {}
                                is RecognitionStatus.Listening -> {
                                    Text(
                                        text = stringResource(R.string.shazam_listening),
                                        fontSize = 15.sp,
                                        fontFamily = SfProFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                is RecognitionStatus.Processing -> {
                                    Text(
                                        text = stringResource(R.string.shazam_processing),
                                        fontSize = 15.sp,
                                        fontFamily = SfProFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                is RecognitionStatus.Success -> {
                                    RecognitionResultCard(s.result)
                                }
                                is RecognitionStatus.NoMatch -> {
                                    Text(
                                        text = s.message,
                                        fontSize = 15.sp,
                                        fontFamily = SfProFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                is RecognitionStatus.Error -> {
                                    Text(
                                        text = s.message,
                                        fontSize = 15.sp,
                                        fontFamily = SfProFontFamily,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            if (message.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = message,
                                    fontSize = 13.sp,
                                    fontFamily = SfProFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            },
        )
    }

@Composable
private fun RecognitionResultCard(result: RecognitionResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!result.coverArtUrl.isNullOrBlank()) {
                CachedArtworkImage(
                    url = result.coverArtUrl,
                    contentDescription = null,
                    size = 300,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = result.title,
                fontSize = 18.sp,
                fontFamily = SfProFontFamily,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = result.artist,
                fontSize = 14.sp,
                fontFamily = SfProFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (!result.album.isNullOrBlank()) {
                Text(
                    text = result.album,
                    fontSize = 13.sp,
                    fontFamily = SfProFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private suspend fun runRecognition(
    onStatusUpdate: (RecognitionStatus) -> Unit,
    onMessage: (String) -> Unit,
) = coroutineScope {
    try {
        onStatusUpdate(RecognitionStatus.Listening)

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onStatusUpdate(RecognitionStatus.Error("Failed to get buffer size"))
            return@coroutineScope
        }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 4,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            onStatusUpdate(RecognitionStatus.Error("Failed to initialize AudioRecord"))
            return@coroutineScope
        }

        val generator = ShazamSignatureGenerator()
        val readBuffer = ShortArray(bufferSize)
        var signature: com.pryvn.audiophile.code.api.shazam.ShazamSignature? = null

        audioRecord.startRecording()

        val captureStart = System.currentTimeMillis()
        val maxCaptureMs = 5000L

        while (isActive) {
            val elapsed = System.currentTimeMillis() - captureStart
            if (elapsed > maxCaptureMs) break

            val read = audioRecord.read(readBuffer, 0, readBuffer.size)
            if (read > 0) {
                generator.feedPcm16Mono(if (read < readBuffer.size) readBuffer.copyOf(read) else readBuffer)
                signature = generator.nextSignatureOrNull()
                if (signature != null) break
            }
            delay(50)
        }

        audioRecord.stop()
        audioRecord.release()

        if (signature == null) {
            signature = generator.nextSignatureOrNull()
        }

        if (signature == null) {
            onStatusUpdate(RecognitionStatus.NoMatch("Could not generate audio signature"))
            return@coroutineScope
        }

        onStatusUpdate(RecognitionStatus.Processing)

        val result = withContext(Dispatchers.IO) {
            Shazam.recognize(
                signature = signature!!.uri,
                sampleDurationMs = signature!!.sampleDurationMs,
            )
        }

        result.onSuccess { recognitionResult ->
            onStatusUpdate(RecognitionStatus.Success(recognitionResult))
        }.onFailure { e ->
            if (e.message?.contains("No match found") == true) {
                onStatusUpdate(RecognitionStatus.NoMatch("No match found"))
            } else {
                onStatusUpdate(RecognitionStatus.Error(e.message ?: "Recognition failed"))
            }
        }

        onMessage("")
    } catch (e: Exception) {
        onStatusUpdate(RecognitionStatus.Error(e.message ?: "Recognition error"))
    }
}
