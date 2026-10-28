package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF3D6BFF)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DA8FF), onPrimary = Color(0xFF001A63),
    primaryContainer = Color(0xFF213A8C), onPrimaryContainer = Color(0xFFDCE1FF),
    background = Color(0xFF05070C), surface = Color(0xFF0B0E16), surfaceVariant = Color(0xFF1A1F2B),
    onSurface = Color(0xFFE6E8F0), onSurfaceVariant = Color(0xFFB4B9C8),
    error = Color(0xFFFFB4AB),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2A55E0), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF), onPrimaryContainer = Color(0xFF001A63),
    background = Color(0xFFF6F7FB), surface = Color.White, surfaceVariant = Color(0xFFE8EAF2),
    onSurface = Color(0xFF12141A), onSurfaceVariant = Color(0xFF4A4F5E),
)

@Composable
fun TvHeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

val AccentBlue = Accent
