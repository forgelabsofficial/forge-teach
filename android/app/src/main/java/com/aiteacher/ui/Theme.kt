package com.aiteacher.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF4F46E5),
    background = Color(0xFFF4F6FB),
    surface = Color.White,
    onSurface = Color(0xFF111827),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
)

@Composable
fun AiTeacherTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
