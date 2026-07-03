package com.pryvn.audiophile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.DeviceUtils
import com.pryvn.audiophile.code.utils.others.subStringX
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashActivity : ComponentActivity() {
    companion object {
        private const val CRASH_INFO_KEY = "CRASH_INFO_KEY"

        fun startActivity(context: Context, log: String) {
            context.startActivity(Intent(context, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CRASH_INFO_KEY, log)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashInfo = getCrashInfo(true, intent)
        setContent {
            val isNight = isSystemInDarkTheme()
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isNight) Color.Black else Color.White,
                contentColor = if (isNight) Color.White else Color.Black
            ) {
                CrashUI(context = this@CrashActivity, crashInfo = crashInfo) {
                    val intent = Intent(this@CrashActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    @Composable
    fun CrashUI(context: Activity, crashInfo: String, onRestart: () -> Unit) {
        val toast = stringResource(R.string.tip_copied)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                context.finish()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "←",
                            fontSize = 24.sp,
                            color = Color(0xFFFF203C)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.crash_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black
                    )
                    Text(
                        text = "Σ(っ °Д °;)っ " + stringResource(R.string.crash_subtitle),
                        fontSize = 14.sp,
                        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        tonalElevation = 7.dp
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 20.dp)
                                .padding(vertical = 25.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = crashInfo.trim(),
                                    Modifier.alpha(0.8f),
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val shape = RoundedCornerShape(12.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(45.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF203C),
                                    Color(0xFFFF546A)
                                )
                            ),
                            shape = shape
                        )
                        .clip(shape)
                        .clickable(onClick = {
                            ClipboardUtils.copyText(getCrashInfo(true, intent))
                            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.crash_copy_details),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp)
                        .height(45.dp)
                        .background(
                            color = (if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray).copy(alpha = 0.25f),
                            shape = shape
                        )
                        .clip(shape)
                        .clickable(onClick = {
                            ClipboardUtils.copyText(getCrashInfo(false, intent))
                            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.crash_copy_brief),
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF203C),
                                    Color(0xFFFF546A)
                                )
                            )
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp)
                        .height(45.dp)
                        .background(
                            color = (if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray).copy(alpha = 0.25f),
                            shape = shape
                        )
                        .clip(shape)
                        .clickable(onClick = onRestart),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.crash_restart_app),
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF203C),
                                    Color(0xFFFF546A)
                                )
                            )
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }


    private fun getCrashInfo(details: Boolean, intent: Intent): String {
        val currentDate = Date()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val versionName = AppUtils.getAppVersionName()
        val versionCode = AppUtils.getAppVersionCode().toString()
        var errorDetails: String? = ""
        errorDetails += "Application Config: \n"
        errorDetails += "- Build Version: $versionName \n"
        errorDetails += "- Build Code: $versionCode \n"
        errorDetails += "- Current Date: ${dateFormat.format(currentDate)} \n"
        errorDetails += "\nDevice Config: \n"
        errorDetails += "- Model: ${DeviceUtils.getModel()} \n"
        errorDetails += "- Manufacturer: ${DeviceUtils.getManufacturer()} \n"
        errorDetails += "- SDK: ${DeviceUtils.getSDKVersionCode()} \n"
        errorDetails += "- ABIs: ${DeviceUtils.getABIs().joinToString(", ")} \n"
        errorDetails += "- Rooted: ${DeviceUtils.isDeviceRooted()} \n"
        errorDetails += "- Tablet: ${DeviceUtils.isTablet()} \n"
        errorDetails += "- Emulator: ${DeviceUtils.isEmulator()} \n"

        val tracks = intent.extras?.getString(CRASH_INFO_KEY)

        if (details) {
            errorDetails += "\nStack Trace: \n"
            errorDetails += tracks?.trim()
        } else {
            val trace = tracks?.subStringX(null, " at ")
            val cause =
                tracks?.subStringX("Caused by:", " at ")
            if (trace != null) {
                errorDetails += "\nStack Trace: \n"
                errorDetails += trace.trim()
            }
            if (cause != null) {
                errorDetails += "\nCaused by: "
                errorDetails += cause.trim()
            }
        }
        return errorDetails ?: "Get crash info failed!"
    }
}