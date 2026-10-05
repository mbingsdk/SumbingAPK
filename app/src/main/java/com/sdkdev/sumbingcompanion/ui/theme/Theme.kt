package com.sdkdev.sumbingcompanion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class SchematicColors(
    val raised: Color,
    val inset: Color,
    val border: Color,
    val muted: Color,
    val shadow: Color,
    val highlight: Color,
    val orange: Color = AccentOrange,
    val blue: Color = AccentBlue,
    val green: Color = AccentGreen,
    val amber: Color = AccentAmber,
    val red: Color = AccentRed,
)

private val LightSchematicColors = SchematicColors(
    raised = LightRaised,
    inset = LightInset,
    border = LightBorder,
    muted = LightMuted,
    shadow = LightShadow,
    highlight = LightHighlight,
)

private val DarkSchematicColors = SchematicColors(
    raised = DarkRaised,
    inset = DarkInset,
    border = DarkBorder,
    muted = DarkMuted,
    shadow = DarkShadow,
    highlight = DarkHighlight,
)

private val LocalSchematicColors = staticCompositionLocalOf { LightSchematicColors }

val MaterialTheme.schematic: SchematicColors
    @Composable get() = LocalSchematicColors.current

private val LightColorScheme = lightColorScheme(
    primary = AccentOrange,
    secondary = AccentBlue,
    tertiary = AccentGreen,
    background = LightBackground,
    surface = LightRaised,
    surfaceVariant = LightInset,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightText,
    onSurface = LightText,
    outline = LightBorder,
    error = AccentRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A1F),
    secondary = Color(0xFF67A8FF),
    tertiary = Color(0xFF52C77C),
    background = DarkBackground,
    surface = DarkRaised,
    surfaceVariant = DarkInset,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = DarkText,
    onSurface = DarkText,
    outline = DarkBorder,
    error = Color(0xFFFF7373),
)

@Composable
fun SumbingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val schematic = if (darkTheme) DarkSchematicColors else LightSchematicColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            activity.window.statusBarColor = colorScheme.background.toArgb()
            activity.window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSchematicColors provides schematic) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
