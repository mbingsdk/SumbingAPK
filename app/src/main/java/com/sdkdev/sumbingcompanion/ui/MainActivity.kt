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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sdkdev.sumbingcompanion.BuildConfig
import com.sdkdev.sumbingcompanion.ui.overlay.FloatingWindowService
import com.sdkdev.sumbingcompanion.ui.theme.*

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SumbingTheme {
                val isOverlayRunning by FloatingWindowService.isRunning.collectAsStateWithLifecycle()
                val notificationPermission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else null

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SchematicLauncher(
                        overlayRunning = isOverlayRunning,
                        notificationsGranted = notificationPermission?.status?.isGranted ?: true,
                        onRequestNotifications = { notificationPermission?.launchPermissionRequest() },
                        onOverlayAction = {
                            if (isOverlayRunning) {
                                stopService(Intent(this@MainActivity, FloatingWindowService::class.java))
                            } else if (!Settings.canDrawOverlays(this@MainActivity)) {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            } else {
                                val manager =
                                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                projectionLauncher.launch(manager.createScreenCaptureIntent())
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SchematicLauncher(
    overlayRunning: Boolean,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onOverlayAction: () -> Unit
) {
    val s = MaterialTheme.schematic
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                TechLabel("SUMBING SYSTEM / MOBILE")
                Text(
                    "Companion Console",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
            StatusChip(
                text = if (overlayRunning) "ONLINE" else "STANDBY",
                color = if (overlayRunning) s.green else s.muted
            )
        }

        NeuRaisedCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 30.dp,
            padding = 20.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuInsetCard(
                    modifier = Modifier.size(66.dp),
                    radius = 22.dp,
                    padding = 0.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    TechLabel("MONITOR NODE")
                    Text(
                        if (overlayRunning) "Overlay aktif" else "Siap monitoring",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (overlayRunning)
                            "Weather cycle, Game Time, dan Sync aktif di overlay."
                        else
                            "Launch overlay untuk membaca cuaca dan membuka control panel.",
                        color = s.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeuValueBlock(
                    label = "CYCLE",
                    value = "11:29",
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
                NeuValueBlock(
                    label = "ENGINE",
                    value = "TFLITE",
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(Modifier.height(14.dp))

            NeuActionButton(
                text = if (overlayRunning) "CLOSE OVERLAY" else "LAUNCH OVERLAY",
                onClick = onOverlayAction,
                modifier = Modifier.fillMaxWidth(),
                icon = if (overlayRunning) Icons.Default.Home else Icons.Default.PlayArrow,
                accent = if (overlayRunning) s.red else s.green,
                filled = true
            )
        }

        NeuRaisedCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 26.dp,
            padding = 18.dp
        ) {
            TechLabel("SYSTEM CHECK")
            Spacer(Modifier.height(12.dp))

            SystemCheckRow(
                icon = Icons.Default.Settings,
                title = "Overlay permission",
                subtitle = "Dibutuhkan untuk floating monitor.",
                ok = Settings.canDrawOverlays(context)
            )
            Spacer(Modifier.height(10.dp))
            SystemCheckRow(
                icon = Icons.Default.Build,
                title = "Notifications",
                subtitle = "Foreground service status.",
                ok = notificationsGranted,
                actionLabel = if (!notificationsGranted) "GRANT" else null,
                onAction = onRequestNotifications
            )
            Spacer(Modifier.height(10.dp))
            SystemCheckRow(
                icon = Icons.Default.Settings,
                title = "UNIX timer",
                subtitle = "Sync countdown + staged Game Time.",
                ok = true
            )
        }

        Spacer(Modifier.height(2.dp))

        NeuInsetCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 20.dp,
            padding = 14.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    TechLabel("BUILD")
                    Text(
                        BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    TechLabel("PROFILE")
                    Text(
                        "NEUMORPHIC SCHEMATIC",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemCheckRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    ok: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val s = MaterialTheme.schematic

    NeuInsetCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 17.dp,
        padding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (ok) s.green else s.amber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(subtitle, color = s.muted, fontSize = 11.sp)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            } else {
                StatusChip(
                    text = if (ok) "READY" else "CHECK",
                    color = if (ok) s.green else s.amber
                )
            }
        }
    }
}
