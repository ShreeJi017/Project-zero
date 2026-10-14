package com.zerogravity.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ZeroGravityAccent = Color(0xFF92A7FF)
private val NightColors = darkColorScheme(
    primary = Color(0xFFB8C7FF),
    onPrimary = Color(0xFF101B38),
    secondary = Color(0xFF8DE1D8),
    onSecondary = Color(0xFF06201E),
    background = Color(0xFF07090F),
    onBackground = Color(0xFFF2F3F8),
    surface = Color(0xFF0E111A),
    onSurface = Color(0xFFF2F3F8),
    surfaceVariant = Color(0xFF191D29),
    onSurfaceVariant = Color(0xFFA8ADBC),
    outline = Color(0xFF414758),
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF354B91),
    onPrimary = Color.White,
    secondary = Color(0xFF176A63),
    onSecondary = Color.White,
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF151823),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151823),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF4F5565),
    outline = Color(0xFF73798A),
)

@Composable
fun ZeroGravityTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightColors else DayColors,
        content = content,
    )
}
