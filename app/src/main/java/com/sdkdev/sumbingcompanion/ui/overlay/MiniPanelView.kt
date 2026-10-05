package com.sdkdev.sumbingcompanion.ui.overlay

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdkdev.sumbingcompanion.BuildConfig
import com.sdkdev.sumbingcompanion.core.*
import com.sdkdev.sumbingcompanion.ui.RegionCalibrationActivity
import com.sdkdev.sumbingcompanion.ui.theme.*
import com.sdkdev.sumbingcompanion.viewmodel.MonitorViewModel
import com.sdkdev.sumbingcompanion.viewmodel.TrainingViewModel
import java.util.Locale

@Composable
fun MiniPanelView(
    uiState: MonitorUiState,
    isMonitoring: Boolean,
    trainingViewModel: TrainingViewModel,
    monitorViewModel: MonitorViewModel,
    onStartStop: () -> Unit,
    onReset: () -> Unit,
    onTestRead: () -> Unit,
    onClose: () -> Unit,
    onExitService: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val gameTime by monitorViewModel.gameTime.collectAsState()
    val isSyncing by monitorViewModel.isSyncing.collectAsState()
    val s = MaterialTheme.schematic

    val infiniteTransition = rememberInfiniteTransition(label = "monitor-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recooldown-pulse"
    )

    val weather = if (uiState.state == WeatherState.WEATHER) {
        WeatherClass.fromString(uiState.lastCuaca)
    } else {
        WeatherClass.fromString(uiState.lastPassive)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panelMaxHeight = if (isLandscape) 205.dp else 430.dp

    Column(
        modifier = Modifier
            .width(304.dp)
            .shadow(
                14.dp,
                RoundedCornerShape(26.dp),
                ambientColor = s.shadow,
                spotColor = s.shadow
            )
            .clip(RoundedCornerShape(26.dp))
            .background(s.raised)
            .border(1.dp, s.highlight.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TechLabel("SUMBING / MONITOR NODE")
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        weather.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    StatusChip(
                        text = uiState.state.name,
                        color = stateColor(uiState.state)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(s.inset)
                    .border(1.dp, s.border, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "×",
                    color = s.muted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SchematicTabBar(
            selected = selectedTab,
            onSelected = { selectedTab = it }
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(max = panelMaxHeight)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                when (selectedTab) {
                    0 -> MonitoringTab(
                        uiState = uiState,
                        gameTime = gameTime,
                        isMonitoring = isMonitoring,
                        isSyncing = isSyncing,
                        pulseAlpha = pulseAlpha,
                        onStartStop = onStartStop,
                        onReset = onReset,
                        onSync = { monitorViewModel.syncFromServer() }
                    )

                    1 -> CalibrationTab(
                        uiState = uiState,
                        onTestRead = onTestRead,
                        monitorViewModel = monitorViewModel,
                        onClose = onClose
                    )

                    2 -> AutoCaptureTab(
                        viewModel = trainingViewModel,
                        monitorViewModel = monitorViewModel,
                        onExitService = onExitService
                    )

                    3 -> AboutTab()
                }
            }
        }
    }
}

@Composable
private fun SchematicTabBar(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    val s = MaterialTheme.schematic
    val tabs = listOf(
        Triple("MON", Icons.Default.Search, 0),
        Triple("CAL", Icons.Default.Edit, 1),
        Triple("AUTO", Icons.Default.Settings, 2),
        Triple("INFO", Icons.Default.Build, 3)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEach { (label, icon, index) ->
            val active = selected == index

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) s.inset else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else s.border.copy(alpha = 0.45f),
                        RoundedCornerShape(13.dp)
                    )
                    .clickable { onSelected(index) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (active) MaterialTheme.colorScheme.primary else s.muted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    color = if (active) MaterialTheme.colorScheme.primary else s.muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun MonitoringTab(
    uiState: MonitorUiState,
    gameTime: GameTimeSnapshot,
    isMonitoring: Boolean,
    isSyncing: Boolean,
    pulseAlpha: Float,
    onStartStop: () -> Unit,
    onReset: () -> Unit,
    onSync: () -> Unit
) {
    val s = MaterialTheme.schematic
    val weather = if (uiState.state == WeatherState.WEATHER) {
        WeatherClass.fromString(uiState.lastCuaca)
    } else {
        WeatherClass.fromString(uiState.lastPassive)
    }

    val displayTime = if (uiState.state == WeatherState.WEATHER) {
        formatTime(uiState.weatherDur.toLong() * 1000L)
    } else {
        formatTime(uiState.countdown.toLong() * 1000L)
    }

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NeuValueBlock(
                label = if (uiState.state == WeatherState.WEATHER) "WEATHER DUR." else "COUNTDOWN",
                value = displayTime,
                modifier = Modifier.weight(1f).alpha(
                    if (uiState.state == WeatherState.RECOOLDOWN) pulseAlpha else 1f
                ),
                valueColor = if (uiState.state == WeatherState.WEATHER) s.green else stateColor(uiState.state)
            )

            NeuValueBlock(
                label = "GAME TIME",
                value = if (gameTime.synced) gameTime.gameTime else "—",
                trailing = if (gameTime.synced) "#\${gameTime.stage}" else null,
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.secondary
            )
        }

        NeuInsetCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 18.dp,
            padding = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    TechLabel("WEATHER SIGNAL")
                    Text(
                        weather.label,
                        color = weather.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    TechLabel("CONFIDENCE")
                    Text(
                        String.format(Locale.getDefault(), "%.1f%%", uiState.topConfidence.second * 100f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(9.dp))

            LinearProgressIndicator(
                progress = { uiState.topConfidence.second.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = weather.color,
                trackColor = s.border.copy(alpha = 0.45f)
            )

            if (gameTime.synced) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = s.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TechLabel("STAGE REMAINING")
                    Text(
                        formatTime(gameTime.remainingSeconds.toLong() * 1000L),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Text(
            text = uiState.statusText,
            color = s.muted,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = 2
        )

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeuActionButton(
                text = if (isMonitoring) "STOP" else "START",
                onClick = onStartStop,
                modifier = Modifier.weight(1.15f),
                accent = if (isMonitoring) s.red else s.green,
                filled = true
            )

            NeuActionButton(
                text = "RESET",
                onClick = onReset,
                modifier = Modifier.weight(0.9f),
                accent = s.amber
            )

            NeuActionButton(
                text = if (isSyncing) "SYNC..." else "SYNC",
                onClick = onSync,
                modifier = Modifier.weight(0.9f),
                accent = s.blue,
                enabled = !isSyncing
            )
        }
    }
}

@Composable
fun CalibrationTab(
    uiState: MonitorUiState,
    onTestRead: () -> Unit,
    monitorViewModel: MonitorViewModel? = null,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = MaterialTheme.schematic

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        NeuInsetCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 18.dp,
            padding = 12.dp
        ) {
            TechLabel("DETECTION REGION")
            Spacer(Modifier.height(4.dp))
            Text(
                "Pilih area tepat di atas icon cuaca game. Region kecil dan presisi biasanya lebih stabil.",
                color = s.muted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuActionButton(
                text = "CALIBRATE",
                onClick = {
                    monitorViewModel?.setCalibrating(true)
                    context.startActivity(
                        Intent(context, RegionCalibrationActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    onClose()
                },
                modifier = Modifier.weight(1f),
                accent = s.amber,
                filled = true
            )

            NeuActionButton(
                text = "TEST READ",
                onClick = onTestRead,
                modifier = Modifier.weight(1f),
                accent = s.blue
            )
        }

        val lastResult = uiState.topConfidence
        if (lastResult.first.isNotEmpty()) {
            val weather = WeatherClass.fromKey(lastResult.first)

            NeuRaisedCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 20.dp,
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        TechLabel("LAST DETECTION")
                        Text(
                            weather.label,
                            color = weather.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    StatusChip(
                        text = String.format(Locale.getDefault(), "%.1f%%", lastResult.second * 100f),
                        color = weather.color
                    )
                }

                uiState.testPreview?.let { preview ->
                    Spacer(Modifier.height(10.dp))
                    TechLabel("REGION PREVIEW")
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Region Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCaptureTab(
    viewModel: TrainingViewModel,
    monitorViewModel: MonitorViewModel? = null,
    onExitService: () -> Unit
) {
    val config by viewModel.autoCaptureConfig.collectAsState()
    val lastEvent by viewModel.lastCaptureEvent.collectAsState(initial = null)
    val uiState by monitorViewModel?.uiState?.collectAsState()
        ?: remember { mutableStateOf(MonitorUiState()) }
    val s = MaterialTheme.schematic

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        NeuInsetCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 18.dp,
            padding = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TechLabel("OVERLAY OPACITY")
                Text(
                    "\${(uiState.overlayAlpha * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Slider(
                value = uiState.overlayAlpha,
                onValueChange = { monitorViewModel?.setOverlayAlpha(it) },
                valueRange = 0.2f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = s.border
                )
            )
        }

        NeuRaisedCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 19.dp,
            padding = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    TechLabel("DATASET CAPTURE")
                    Text(
                        "Auto Capture",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = viewModel::toggleAutoCapture,
                    modifier = Modifier.scale(0.82f)
                )
            }

            Spacer(Modifier.height(8.dp))

            TechLabel("INTERVAL  \${config.intervalSec}s")
            Slider(
                value = config.intervalSec.toFloat(),
                onValueChange = { viewModel.setAutoCaptureInterval(it.toInt()) },
                valueRange = 1f..30f
            )

            TechLabel("LIMIT  \${config.maxPerClass}")
            Slider(
                value = config.maxPerClass.toFloat(),
                onValueChange = { viewModel.setAutoCaptureMax(it.toInt()) },
                valueRange = 50f..500f
            )
        }

        lastEvent?.let { event ->
            NeuInsetCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 16.dp,
                padding = 10.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(s.green, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Saved: \${event.label} (\${event.totalForClass})",
                        color = s.green,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        NeuInsetCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 17.dp,
            padding = 11.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Home,
                    null,
                    tint = s.muted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TechLabel("DATASET PATH")
                    Text(
                        "/Pictures/SumbingCompanion/",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 9.sp
                    )
                }
                TextButton(onClick = onExitService) {
                    Text(
                        "EXIT",
                        color = s.red,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AboutTab() {
    val context = LocalContext.current
    val s = MaterialTheme.schematic

    Column(
        verticalArrangement = Arrangement.spacedBy(11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeuRaisedCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 22.dp,
            padding = 15.dp
        ) {
            TechLabel("SYSTEM IDENTITY")
            Spacer(Modifier.height(5.dp))
            Text(
                "Sumbing Companion",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Weather monitoring console untuk komunitas UNIX Team.",
                color = s.muted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(11.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusChip("SDK-DEV", s.blue)
                StatusChip("UNIX-TEAM", MaterialTheme.colorScheme.primary)
                StatusChip(BuildConfig.VERSION_NAME, s.green)
            }
        }

        NeuActionButton(
            text = "SUPPORT VIA SAWERIA",
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://saweria.co/mbingsdk")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            accent = s.amber
        )

        NeuActionButton(
            text = "JOIN DISCORD",
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://discord.com/invite/Jdqhnyu2dw")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            accent = Color(0xFF5865F2),
            filled = true
        )
    }
}

@Composable
fun Badge(state: WeatherState) {
    StatusChip(
        text = state.name,
        color = stateColor(state)
    )
}

@Composable
private fun stateColor(state: WeatherState): Color {
    val s = MaterialTheme.schematic
    return when (state) {
        WeatherState.IDLE -> s.muted
        WeatherState.WEATHER -> s.green
        WeatherState.COOLDOWN -> s.amber
        WeatherState.POST_CD -> s.blue
        WeatherState.RECOOLDOWN -> s.red
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
