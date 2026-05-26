package com.neo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark scheme: Premium charcoal base with unified NeoLime accent
private val DarkColorScheme = darkColorScheme(
    primary          = NeoLime,
    onPrimary        = NeoBlack,
    secondary        = NeoLime,            // Unified — no more NeoGreen for UI elements
    onSecondary      = NeoBlack,
    tertiary         = NeoTeal,
    onTertiary       = TextWhite,
    background       = NeoBlack,           // 0xFF0C0C0E — dark charcoal
    onBackground     = TextWhite,
    surface          = NeoGray900,         // 0xFF1C1C1F — warm charcoal
    onSurface        = TextWhite,
    surfaceVariant   = NeoGray800,         // 0xFF252528
    onSurfaceVariant = TextWhite60,
    error            = NeoRed,
    onError          = TextWhite
)

private val LightColorScheme = lightColorScheme(
    primary          = NeoLime,
    onPrimary        = NeoBlack,
    secondary        = NeoGreen,
    onSecondary      = NeoBlack,
    tertiary         = NeoOrange,
    onTertiary       = TextWhite,
    background       = NeoLightBackground,
    onBackground     = TextBlack,
    surface          = NeoLightSurface,
    onSurface        = TextBlack,
    surfaceVariant   = NeoLightBackground,
    onSurfaceVariant = TextBlack80,
    error            = NeoRed,
    onError          = TextWhite
)

@Composable
fun NeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = 0
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}