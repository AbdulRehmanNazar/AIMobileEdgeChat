package com.example.edgedevicedemo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Evergreen,
    onPrimary = Ink,
    primaryContainer = EvergreenDeep,
    onPrimaryContainer = Mist,
    secondary = Amber,
    onSecondary = Ink,
    tertiary = Coral,
    onTertiary = Ink,
    background = Ink,
    onBackground = Fog,
    surface = Charcoal,
    onSurface = Fog,
    surfaceVariant = Slate,
    onSurfaceVariant = Moss,
    outline = Stone
)

private val LightColorScheme = lightColorScheme(
    primary = EvergreenDeep,
    onPrimary = Mist,
    primaryContainer = Color(0xFFD8F8E8),
    onPrimaryContainer = EvergreenDeep,
    secondary = Amber,
    onSecondary = Ink,
    tertiary = Coral,
    onTertiary = Mist,
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Fog,
    onSurfaceVariant = Color(0xFF556158),
    outline = Color(0xFFB5C0B7)
)

@Composable
fun EdgeDeviceDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
