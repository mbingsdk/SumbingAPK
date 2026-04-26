package com.sdkdev.sumbingcompanion.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdkdev.sumbingcompanion.core.WeatherClass
import com.sdkdev.sumbingcompanion.core.WeatherState
import com.sdkdev.sumbingcompanion.ui.theme.*

@Composable
fun BubbleView(
    state: WeatherState,
    weather: WeatherClass,
    countdown: Int,
    weatherDur: Int,
    alpha: Float,
    regionUncalibrated: Boolean = false,
    isAutoCapturing: Boolean = false,
    onToggle: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val borderColor = when (state) {
        WeatherState.IDLE -> BorderColor
        WeatherState.WEATHER -> AccentGreen
        WeatherState.COOLDOWN -> AccentAmber
        WeatherState.RECOOLDOWN -> AccentRed
        WeatherState.POST_CD -> AccentBlue
    }

    val bgBrush = when (state) {
        WeatherState.WEATHER -> Brush.radialGradient(listOf(AccentGreen.copy(alpha = 0.2f), BgElevated))
        WeatherState.COOLDOWN -> Brush.radialGradient(listOf(AccentAmber.copy(alpha = 0.2f), BgElevated))
        WeatherState.RECOOLDOWN -> Brush.radialGradient(listOf(AccentRed.copy(alpha = 0.2f), BgElevated))
        else -> Brush.linearGradient(listOf(BgElevated, BgElevated))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(alpha)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        // Icon Bubble
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(bgBrush)
                .border(2.dp, if (state == WeatherState.IDLE) weather.color else borderColor, CircleShape)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (weather.iconRes != null) {
                Image(
                    painter = painterResource(id = weather.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = weather.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (regionUncalibrated) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopEnd)
                        .background(AccentRed, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            if (isAutoCapturing) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopStart)
                        .background(AccentRed, CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // Info Panel - More compact and polished
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BgOverlay.copy(alpha = 0.85f))
                .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = weather.label.uppercase(),
                color = weather.color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            
            val displayTime = if (state == WeatherState.WEATHER) {
                formatTime(weatherDur.toLong() * 1000)
            } else if (state == WeatherState.IDLE) {
                "--:--"
            } else {
                formatTime(countdown.toLong() * 1000)
            }

            Text(
                text = displayTime,
                color = if (state == WeatherState.WEATHER) AccentGreen else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
