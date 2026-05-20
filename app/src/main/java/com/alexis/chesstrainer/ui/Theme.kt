package com.alexis.chesstrainer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TrainerColors = lightColorScheme(
    primary = Color(0xFF174B3F),
    onPrimary = Color.White,
    secondary = Color(0xFF875239),
    onSecondary = Color.White,
    tertiary = Color(0xFF315B7D),
    background = Color(0xFFF5F7F2),
    onBackground = Color(0xFF19231F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF19231F),
    surfaceVariant = Color(0xFFE4E8DE),
    onSurfaceVariant = Color(0xFF435047),
    outline = Color(0xFF7B857C)
)

@Composable
fun ChessTrainerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrainerColors,
        content = content
    )
}
