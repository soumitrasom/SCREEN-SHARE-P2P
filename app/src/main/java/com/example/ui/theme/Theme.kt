package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val CyberDarkColorScheme = darkColorScheme(
    primary = VoltGreen,
    onPrimary = DeepMidnight,
    secondary = ElectricCyan,
    onSecondary = DeepMidnight,
    tertiary = ElectricPink,
    background = DeepMidnight,
    surface = CarbonCard,
    onBackground = TextLight,
    onSurface = TextLight,
    outline = TechOutline,
    surfaceVariant = DarkTint,
    onSurfaceVariant = TextLight
)

// Standard light fallback (Designed strictly for daytime readability)
private val CyberLightColorScheme = lightColorScheme(
    primary = Color(0xFF008533),
    onPrimary = TextLight,
    secondary = Color(0xFF00738C),
    onSecondary = TextLight,
    background = TextLight,
    surface = Color(0xFFE9EEFA),
    onBackground = DeepMidnight,
    onSurface = DeepMidnight,
    outline = TextSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark cyber vibe for tech stream app by default
    dynamicColor: Boolean = false, // Disable dynamic colors to firmly experience specialized branding aesthetics!
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CyberDarkColorScheme else CyberLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
