package com.sdkdev.sumbingcompanion.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdkdev.sumbingcompanion.core.*
import com.sdkdev.sumbingcompanion.ui.RegionCalibrationActivity
import com.sdkdev.sumbingcompanion.ui.theme.*
import com.sdkdev.sumbingcompanion.viewmodel.MonitorViewModel
import com.sdkdev.sumbingcompanion.viewmodel.TrainingViewModel
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.asImageBitmap
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

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Gunakan cuaca aktif jika sedang WEATHER, jika tidak (IDLE/COOLDOWN) gunakan status siang/malam
    val weather by remember(uiState.state, uiState.lastCuaca, uiState.lastPassive) {
        derivedStateOf {
            if (uiState.state == WeatherState.WEATHER) {
                WeatherClass.fromString(uiState.lastCuaca)
            } else {
                WeatherClass.fromString(uiState.lastPassive)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panelMaxHeight = if (isLandscape) 180.dp else 360.dp

    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(BgElevated)
            .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgOverlay)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SUMBING COMPANION",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = weather.label.uppercase(),
                    color = weather.color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Text("✕", color = TextMuted, fontSize = 14.sp)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = BgOverlay,
            contentColor = AccentBlue,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AccentBlue,
                    height = 2.dp
                )
            },
            modifier = Modifier.height(42.dp)
        ) {
            val tabs = listOf(
                "Monitor" to Icons.Default.Search,
                "Kalibrasi" to Icons.Default.Edit,
                "Auto" to Icons.Default.Settings,
                "About" to Icons.Default.Build
            )
            tabs.forEachIndexed { index, (label, icon) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.padding(0.dp) // Minimize tab area padding
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxHeight().padding(horizontal = 2.dp)
                    ) {
                        Icon(icon, null, modifier = Modifier.size(14.dp))
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .heightIn(max = panelMaxHeight)
        ) {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.verticalScroll(scrollState)) {
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
                    1 -> CalibrationTab(uiState, onTestRead, monitorViewModel, onClose)
                    2 -> AutoCaptureTab(trainingViewModel, monitorViewModel, onExitService)
                    3 -> AboutTab()
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(state = uiState.state)
            
            val timeText by remember(uiState.state, uiState.weatherDur, uiState.countdown) {
                derivedStateOf {
                    val displayTime = if (uiState.state == WeatherState.WEATHER) {
                        uiState.weatherDur.toLong() * 1000
                    } else {
                        uiState.countdown.toLong() * 1000
                    }
                    formatTime(displayTime)
                }
            }
            Text(
                text = timeText,
                style = Typography.labelLarge,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = if (uiState.state == WeatherState.WEATHER) AccentGreen else AccentAmber,
                modifier = Modifier.alpha(if (uiState.state == WeatherState.RECOOLDOWN) pulseAlpha else 1.0f)
            )
        }

        val weather by remember(uiState.state, uiState.lastCuaca, uiState.lastPassive) {
            derivedStateOf {
                if (uiState.state == WeatherState.WEATHER) {
                    WeatherClass.fromString(uiState.lastCuaca)
                } else {
                    WeatherClass.fromString(uiState.lastPassive)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgOverlay, RoundedCornerShape(10.dp))
                .border(1.dp, BorderColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("GAME TIME", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    gameTime.gameTime,
                    color = AccentBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (gameTime.synced) "STAGE #${gameTime.stage}" else "NOT SYNCED",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (gameTime.synced) formatTime(gameTime.remainingSeconds.toLong() * 1000L) else "—",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = uiState.statusText,
            color = TextMuted,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            maxLines = 2
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confidence", color = TextMuted, fontSize = 9.sp)
                Text(String.format("%.1f%%", uiState.topConfidence.second * 100), color = weather.color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { uiState.topConfidence.second },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = weather.color,
                trackColor = BorderColor.copy(alpha = 0.3f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(
                onClick = onStartStop,
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) AccentRed.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                    contentColor = if (isMonitoring) AccentRed else AccentGreen
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, (if (isMonitoring) AccentRed else AccentGreen).copy(alpha = 0.4f))
            ) {
                Text(if (isMonitoring) "STOP MONITOR" else "START MONITOR", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }

            Button(
                onClick = onReset,
                modifier = Modifier.weight(0.7f).height(36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentAmber.copy(alpha = 0.12f),
                    contentColor = AccentAmber
                )
            ) {
                Text("RESET", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSync,
                enabled = !isSyncing,
                modifier = Modifier.weight(0.75f).height(36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue.copy(alpha = 0.15f),
                    contentColor = AccentBlue,
                    disabledContainerColor = BorderColor.copy(alpha = 0.15f),
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    if (isSyncing) "SYNC..." else "SYNC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CalibrationTab(uiState: MonitorUiState, onTestRead: () -> Unit, monitorViewModel: MonitorViewModel? = null, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentBlue.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Text(
                "Area deteksi harus tepat di atas icon cuaca game agar pembacaan akurat.",
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    monitorViewModel?.setCalibrating(true)
                    val intent = Intent(context, RegionCalibrationActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onClose() // Auto minimize pas kalibrasi aktif
                },
                modifier = Modifier.weight(1.3f).height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentAmber.copy(alpha = 0.15f),
                    contentColor = AccentAmber
                )
            ) {
                Text("📐 KALIBRASI", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }

            Button(
                onClick = onTestRead,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue.copy(alpha = 0.15f),
                    contentColor = AccentBlue
                )
            ) {
                Text("TEST READ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        val lastResult = uiState.topConfidence
        if (lastResult.first.isNotEmpty()) {
            val weather = WeatherClass.fromKey(lastResult.first)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgOverlay, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("HASIL TERAKHIR", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(weather.label, color = weather.color, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                    Text(
                        String.format(Locale.getDefault(), "%.1f%%", lastResult.second * 100),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.testPreview != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("REGION PREVIEW", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Image(
                            bitmap = uiState.testPreview.asImageBitmap(),
                            contentDescription = "Region Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
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
    val uiState by monitorViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(MonitorUiState()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Transparansi
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Overlay Transparency", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${(uiState.overlayAlpha * 100).toInt()}%", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = uiState.overlayAlpha,
                onValueChange = { monitorViewModel?.setOverlayAlpha(it) },
                valueRange = 0.2f..1.0f,
                modifier = Modifier.height(20.dp)
            )
        }

        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f), thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto Capture Mode", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = config.enabled,
                onCheckedChange = { viewModel.toggleAutoCapture(it) },
                modifier = Modifier.scale(0.7f).height(24.dp)
            )
        }

        // Compact Sliders
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Interval: ${config.intervalSec}s", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = config.intervalSec.toFloat(),
                    onValueChange = { viewModel.setAutoCaptureInterval(it.toInt()) },
                    valueRange = 1f..30f,
                    modifier = Modifier.height(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Limit: ${config.maxPerClass}", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = config.maxPerClass.toFloat(),
                    onValueChange = { viewModel.setAutoCaptureMax(it.toInt()) },
                    valueRange = 50f..500f,
                    modifier = Modifier.height(20.dp)
                )
            }
        }

        if (lastEvent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(AccentGreen, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Saved: ${lastEvent?.label} (${lastEvent?.totalForClass})",
                    color = AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Home, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Dataset Path:", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "/Pictures/SumbingCompanion/",
                        color = TextPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            TextButton(
                onClick = onExitService,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
            ) {
                Text("EXIT SERVICE", fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun AboutTab() {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "DEVELOPED BY",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = AccentBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f))
                ) {
                    Text(
                        "SDK-Dev",
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = AccentAmber.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.3f))
                ) {
                    Text(
                        "UNIX-Team",
                        color = AccentAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgOverlay, RoundedCornerShape(12.dp))
                .border(1.dp, BorderColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                "Sumbing Companion adalah alat bantu monitoring cuaca otomatis untuk komunitas gaming.",
                color = TextPrimary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://saweria.co/mbingsdk"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15)), // Saweria Yellow
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☕", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text("SUPPORT VIA SAWERIA", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Black)
            }
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.com/invite/Jdqhnyu2dw"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)), // Discord Blurple
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("JOIN OUR DISCORD", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.White)
        }
        
        Text(
            "Version 0.3.10.BETA",
            color = TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun Badge(state: WeatherState) {
    val bgColor = when (state) {
        WeatherState.IDLE -> BorderColor
        WeatherState.WEATHER -> AccentGreen
        WeatherState.COOLDOWN -> AccentAmber
        WeatherState.POST_CD -> AccentBlue
        WeatherState.RECOOLDOWN -> AccentRed
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = state.name, fontSize = 10.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
