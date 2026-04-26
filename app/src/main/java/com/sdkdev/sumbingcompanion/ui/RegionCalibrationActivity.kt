package com.sdkdev.sumbingcompanion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import kotlinx.coroutines.launch

class RegionCalibrationActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settingsRepository = SettingsRepository(this)

        // Reset calibration state when finished
        val onFinished = {
            settingsRepository.setCalibrating(false)
            finish()
        }

        setContent {
            RegionCalibrationScreen(
                onSave = { region ->
                    lifecycleScope.launch {
                        settingsRepository.saveRegion(region)
                        onFinished()
                    }
                },
                onCancel = { onFinished() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure state is reset even if user kills activity through system
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color(0xFF00FF88).copy(alpha = 0.1f),
                    topLeft = Offset(rectX, rectY),
                    size = Size(rectW, rectH)
                )
                drawRect(
                    color = Color(0xFF00FF88),
                    topLeft = Offset(rectX, rectY),
                    size = Size(rectW, rectH),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Corner handles
                val handleRadius = 4.dp.toPx()
                drawCircle(Color(0xFF00FF88), handleRadius, Offset(rectX, rectY))
                drawCircle(Color(0xFF00FF88), handleRadius, Offset(rectX + rectW, rectY))
                drawCircle(Color(0xFF00FF88), handleRadius, Offset(rectX, rectY + rectH))
                drawCircle(Color(0xFF00FF88), handleRadius, Offset(rectX + rectW, rectY + rectH))
            }
        }

        // Instructions
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Drag untuk pilih area icon cuaca  •  Tap Back untuk batal",
                color = Color.White,
                fontSize = 14.sp
            )
        }

        // Preview and Buttons
        if (regionSelected && !isDragging && rectW > 10 && rectH > 10) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center) // Changed from BottomCenter for better visibility
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Area Terpilih",
                    color = Color(0xFF00FF88),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Posisi: ${rectX.toInt()}, ${rectY.toInt()}",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Text(
                    text = "Ukuran: ${rectW.toInt()} × ${rectH.toInt()}",
                    color = Color.White,
                    fontSize = 13.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row {
                    Button(
                        onClick = { regionSelected = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("✗ Ulangi")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            onSave(CaptureRegion(rectX.toInt(), rectY.toInt(), rectW.toInt(), rectH.toInt()))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("✓ SIMPAN", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
