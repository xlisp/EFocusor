package com.efocusor.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark "observatory" palette - amber accents on near-black.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF5C4200),
    onPrimaryContainer = Color(0xFFFFE08C),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0D253F),
    background = Color(0xFF101216),
    onBackground = Color(0xFFE4E6EC),
    surface = Color(0xFF16191F),
    onSurface = Color(0xFFE4E6EC),
    surfaceVariant = Color(0xFF1F232B),
    onSurfaceVariant = Color(0xFFA8AEBB),
    outline = Color(0xFF3A3F4B),
    error = Color(0xFFCF6679),
    onError = Color(0xFF2D070F),
)

// Night-vision palette: dim red only, for use at the eyepiece.
private val NightColors = darkColorScheme(
    primary = Color(0xFFFF6E6E),
    onPrimary = Color(0xFF2A0505),
    primaryContainer = Color(0xFF3A0D0D),
    onPrimaryContainer = Color(0xFFFFB4B4),
    secondary = Color(0xFFFF8A80),
    onSecondary = Color(0xFF2A0505),
    background = Color(0xFF120808),
    onBackground = Color(0xFFD32F2F),
    surface = Color(0xFF170B0B),
    onSurface = Color(0xFFD32F2F),
    surfaceVariant = Color(0xFF231010),
    onSurfaceVariant = Color(0xFFA03030),
    outline = Color(0xFF4A1C1C),
    error = Color(0xFFFF5252),
    onError = Color(0xFF2A0505),
)

@Composable
fun EFocusorTheme(nightMode: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        nightMode -> NightColors
        isSystemInDarkTheme() -> DarkColors
        else -> DarkColors // the app is always dark - it is used outdoors at night
    }
    MaterialTheme(colorScheme = colors, content = content)
}
