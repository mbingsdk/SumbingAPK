package com.sdkdev.sumbingcompanion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.sdkdev.sumbingcompanion.core.CaptureRegion
import com.sdkdev.sumbingcompanion.data.SettingsRepository
import com.sdkdev.sumbingcompanion.ui.theme.*
import kotlinx.coroutines.launch

class RegionCalibrationActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settingsRepository = SettingsRepository(this)

        val onFinished = {
            settingsRepository.setCalibrating(false)
            finish()
        }

        setContent {
            SumbingTheme {
                RegionCalibrationScreen(
                    onSave = { region ->
                        lifecycleScope.launch {
                            settingsRepository.saveRegion(region)
                            onFinished()
                        }
                    },
                    onCancel = onFinished
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsRepository.setCalibrating(false)
    }
}

@Composable
fun RegionCalibrationScreen(
    onSave: (CaptureRegion) -> Unit,
    onCancel: () -> Unit
) {
    var startPoint by remember { mutableStateOf(Offset.Zero) }
    var currentPoint by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var regionSelected by remember { mutableStateOf(false) }

    val s = MaterialTheme.schematic
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startPoint = offset
                        currentPoint = offset
                        isDragging = true
                        regionSelected = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentPoint += dragAmount
                    },
                    onDragEnd = {
                        isDragging = false
                        regionSelected = true
                    }
                )
            }
    ) {
        val rectX = minOf(startPoint.x, currentPoint.x)
        val rectY = minOf(startPoint.y, currentPoint.y)
        val rectW = kotlin.math.abs(currentPoint.x - startPoint.x)
        val rectH = kotlin.math.abs(currentPoint.y - startPoint.y)

        if (isDragging || regionSelected) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = accent.copy(alpha = 0.12f),
                    topLeft = Offset(rectX, rectY),
                    size = Size(rectW, rectH)
                )
                drawRect(
                    color = accent,
                    topLeft = Offset(rectX, rectY),
                    size = Size(rectW, rectH),
                    style = Stroke(width = 2.dp.toPx())
                )

                val handleRadius = 4.dp.toPx()
                drawCircle(accent, handleRadius, Offset(rectX, rectY))
                drawCircle(accent, handleRadius, Offset(rectX + rectW, rectY))
                drawCircle(accent, handleRadius, Offset(rectX, rectY + rectH))
                drawCircle(accent, handleRadius, Offset(rectX + rectW, rectY + rectH))
            }
        }

        NeuRaisedCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            radius = 22.dp,
            padding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Default.Edit,
                    null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TechLabel("REGION CALIBRATION")
                    Text(
                        "Drag tepat di area icon cuaca",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Buat kotak sekecil mungkin tapi tetap mencakup seluruh icon. Tekan batal kalau mau kembali.",
                color = s.muted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        if (regionSelected && !isDragging && rectW > 10f && rectH > 10f) {
            NeuRaisedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                radius = 24.dp,
                padding = 16.dp
            ) {
                TechLabel("SELECTED REGION")
                Spacer(Modifier.height(4.dp))

                Text(
                    "${rectW.toInt()} × ${rectH.toInt()} px",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "X ${rectX.toInt()}  •  Y ${rectY.toInt()}",
                    color = s.muted,
                    fontSize = 10.sp
                )

                Spacer(Modifier.height(13.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuActionButton(
                        text = "RETRY",
                        onClick = { regionSelected = false },
                        modifier = Modifier.weight(1f),
                        accent = s.amber
                    )
                    NeuActionButton(
                        text = "SAVE REGION",
                        onClick = {
                            onSave(
                                CaptureRegion(
                                    rectX.toInt(),
                                    rectY.toInt(),
                                    rectW.toInt(),
                                    rectH.toInt()
                                )
                            )
                        },
                        modifier = Modifier.weight(1.25f),
                        accent = s.green,
                        filled = true
                    )
                }

                Spacer(Modifier.height(7.dp))

                NeuActionButton(
                    text = "CANCEL",
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    accent = s.red
                )
            }
        }
    }
}
