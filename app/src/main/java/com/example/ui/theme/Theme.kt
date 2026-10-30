package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AppColorPalette
import com.example.model.AppThemeMode

@Composable
fun ZakkirniTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    palette: AppColorPalette = AppColorPalette.GOLD_MIDNIGHT,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val primary = Color(palette.primaryHex)
    val secondary = Color(palette.secondaryHex)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF090D16),
            primaryContainer = primary.copy(alpha = 0.2f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color(0xFF090D16),
            secondaryContainer = secondary.copy(alpha = 0.2f),
            onSecondaryContainer = secondary,
            background = MidnightBg,
            onBackground = Color(0xFFF8FAFC),
            surface = MidnightSurface,
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = MidnightCard,
            onSurfaceVariant = Color(0xFF94A3B8),
            error = Color(0xFFEF4444)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.15f),
            onPrimaryContainer = Color(0xFF0F172A),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.copy(alpha = 0.15f),
            onSecondaryContainer = Color(0xFF0F172A),
            background = LightBg,
            onBackground = Color(0xFF0F172A),
            surface = LightSurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = LightCard,
            onSurfaceVariant = Color(0xFF475569),
            error = Color(0xFFDC2626)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZakkirniTypography,
        content = content
    )
}
