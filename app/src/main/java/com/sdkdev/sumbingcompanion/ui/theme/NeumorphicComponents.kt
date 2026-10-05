package com.sdkdev.sumbingcompanion.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NeuRaisedCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val s = MaterialTheme.schematic
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(radius), ambientColor = s.shadow, spotColor = s.shadow)
            .clip(RoundedCornerShape(radius))
            .background(s.raised)
            .border(1.dp, s.highlight.copy(alpha = 0.55f), RoundedCornerShape(radius))
            .padding(padding),
        content = content
    )
}

@Composable
fun NeuInsetCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val s = MaterialTheme.schematic
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(s.inset)
            .border(1.dp, s.border.copy(alpha = 0.75f), RoundedCornerShape(radius))
            .padding(padding),
        content = content
    )
}

@Composable
fun TechLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.schematic.muted
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp
    )
}

@Composable
fun NeuActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false,
    enabled: Boolean = true
) {
    val s = MaterialTheme.schematic
    val shape = RoundedCornerShape(16.dp)
    val bg = if (filled) accent else s.raised
    val fg = if (filled) Color.White else accent

    Row(
        modifier = modifier
            .shadow(
                if (enabled) 6.dp else 0.dp,
                shape,
                ambientColor = s.shadow,
                spotColor = s.shadow
            )
            .clip(shape)
            .background(if (enabled) bg else s.inset)
            .border(
                BorderStroke(
                    1.dp,
                    if (filled) accent.copy(alpha = 0.45f) else s.border
                ),
                shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) fg else s.muted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            color = if (enabled) fg else s.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val s = MaterialTheme.schematic
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun NeuValueBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: String? = null
) {
    NeuInsetCard(
        modifier = modifier,
        radius = 18.dp,
        padding = 13.dp
    ) {
        TechLabel(label)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                value,
                color = valueColor,
                fontSize = 24.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp
            )
            if (!trailing.isNullOrBlank()) {
                Text(
                    trailing,
                    color = MaterialTheme.schematic.muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
