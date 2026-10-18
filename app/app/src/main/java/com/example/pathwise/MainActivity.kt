package com.example.pathwise

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.theme.PathWiseTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PathWise"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            Log.w(TAG, "enableEdgeToEdge handled: ${e.message}")
        }

        setContent {
            var startupError by remember { mutableStateOf<String?>(null) }

            DisposableEffect(Unit) {
                val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
                    Log.e(TAG, "Uncaught exception: ${throwable.message}", throwable)
                    runOnUiThread {
                        startupError = "${throwable::class.simpleName}: ${throwable.message}\n\n" +
                                throwable.stackTrace.take(8).joinToString("\n")
                    }
                }
                onDispose {
                    Thread.setDefaultUncaughtExceptionHandler(oldHandler)
                }
            }

            if (startupError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF050D1A))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PathWise Recovery HUD",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = startupError ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { startupError = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                            ) {
                                Text("Retry Launch", color = Color(0xFF050D1A))
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        deleteDatabase("pathwise.db")
                                    } catch (_: Throwable) {}
                                    startupError = null
                                }
                            ) {
                                Text("Reset DB", color = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            } else {
                PathWiseTheme {
                    MainNavigation()
                }
            }
        }
    }
}
