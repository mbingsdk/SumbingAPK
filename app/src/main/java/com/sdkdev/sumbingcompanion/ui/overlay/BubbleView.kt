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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    val s = MaterialTheme.schematic
    val stateColor = when (state) {
        WeatherState.IDLE -> s.muted
        WeatherState.WEATHER -> s.green
        WeatherState.COOLDOWN -> s.amber
        WeatherState.POST_CD -> s.blue
        WeatherState.RECOOLDOWN -> s.red
    }

    val displayTime = when (state) {
        WeatherState.WEATHER -> formatTime(weatherDur.toLong() * 1000L)
        WeatherState.IDLE -> "--:--"
        else -> formatTime(countdown.toLong() * 1000L)
    }

    Row(
        modifier = Modifier
            .alpha(alpha)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    8.dp,
                    CircleShape,
                    ambientColor = s.shadow,
                    spotColor = s.shadow
                )
                .clip(CircleShape)
                .background(s.raised)
                .border(1.dp, stateColor.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (weather.iconRes != null) {
                Image(
                    painter = painterResource(weather.iconRes),
                    contentDescription = weather.label,
                    modifier = Modifier.size(29.dp)
                )
            } else {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (regionUncalibrated) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .background(s.red, CircleShape)
                        .border(1.dp, s.raised, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "!",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (isAutoCapturing) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .align(Alignment.TopStart)
                        .background(s.red, CircleShape)
                        .border(1.dp, s.raised, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(7.dp))

        Column(
            modifier = Modifier
                .shadow(
                    7.dp,
                    RoundedCornerShape(16.dp),
                    ambientColor = s.shadow,
                    spotColor = s.shadow
                )
                .clip(RoundedCornerShape(16.dp))
                .background(s.raised)
                .border(1.dp, s.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(stateColor, CircleShape)
                )
                Text(
                    text = state.name,
                    color = stateColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp
                )
            }

            Text(
                text = weather.label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = displayTime,
                color = if (state == WeatherState.WEATHER) s.green else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
