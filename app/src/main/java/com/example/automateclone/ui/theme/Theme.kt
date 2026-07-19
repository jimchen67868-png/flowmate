package com.example.automateclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF6750A4)
private val PurpleDark = Color(0xFFD0BCFF)
private val Surface = Color(0xFFFFFBFE)
private val SurfaceDark = Color(0xFF1C1B1F)

private val LightColors = lightColorScheme(primary = Purple, surface = Surface)
private val DarkColors = darkColorScheme(primary = PurpleDark, surface = SurfaceDark)

@Composable
fun AutomateCloneTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
