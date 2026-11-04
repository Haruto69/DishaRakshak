package com.example.drdisplay.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFACC7FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF23477C),
    onPrimaryContainer = Color(0xFFD9E5FF),
    secondary = Color(0xFFBEC8DA),
    secondaryContainer = Color(0xFF39475A),
    onSecondaryContainer = Color(0xFFDAE4F6),
    tertiary = Color(0xFFD2BDE3),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121418),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474F),
    onSurfaceVariant = Color(0xFFC3C6CF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF285BA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF102F61),
    secondary = Color(0xFF515F75),
    secondaryContainer = Color(0xFFDEE7F6),
    onSecondaryContainer = Color(0xFF253348),
    tertiary = Color(0xFF665879),
    background = Color.White,
    onBackground = Color(0xFF191C20),
    surface = Color.White,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E3E8),
    onSurfaceVariant = Color(0xFF44474E)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun DRDisplayTheme(
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