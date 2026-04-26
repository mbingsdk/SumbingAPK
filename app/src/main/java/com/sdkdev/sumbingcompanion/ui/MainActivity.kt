package com.sdkdev.sumbingcompanion.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sdkdev.sumbingcompanion.ui.overlay.FloatingWindowService
import com.sdkdev.sumbingcompanion.ui.theme.SumbingTheme

class MainActivity : ComponentActivity() {
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatingWindowService::class.java).apply {
                putExtra("projection_data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SumbingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sumbing Mobile",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        val isOverlayRunning by FloatingWindowService.isRunning.collectAsStateWithLifecycle()

                        Spacer(modifier = Modifier.height(32.dp))

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val notificationPermission = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
                            if (!notificationPermission.status.isGranted) {
                                Button(onClick = { notificationPermission.launchPermissionRequest() }) {
                                    Text("Grant Notification Permission")
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isOverlayRunning) {
                                    stopService(Intent(this@MainActivity, FloatingWindowService::class.java))
                                } else {
                                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                        startActivity(intent)
                                    } else {
                                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                                    }
                                }
                            },
                            colors = if (isOverlayRunning) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(if (isOverlayRunning) "Close Overlay" else "Launch Overlay")
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
    }
}
