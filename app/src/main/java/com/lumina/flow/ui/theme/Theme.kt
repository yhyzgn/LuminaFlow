package com.lumina.flow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C5D99),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E7FF),
    secondary = Color(0xFF8C3C19),
    secondaryContainer = Color(0xFFFFDCCD),
    tertiary = Color(0xFF246A5B),
    background = Color(0xFFF6F1EA),
    surface = Color(0xFFFFFBF7),
    surfaceContainerHigh = Color(0xFFF0E3D6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC2FF),
    onPrimary = Color(0xFF002E52),
    primaryContainer = Color(0xFF123C62),
    secondary = Color(0xFFFFB695),
    secondaryContainer = Color(0xFF6D2E12),
    tertiary = Color(0xFF84D5C1),
    background = Color(0xFF151A1F),
    surface = Color(0xFF1A2026),
    surfaceContainerHigh = Color(0xFF222A33)
)

@Composable
fun LuminaFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
