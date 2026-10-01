package com.example.siapel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F5FE), // Light Blue for container
    onPrimaryContainer = PrimaryBlue,
    secondary = PrimaryNavy,
    onSecondary = Color.White,
    secondaryContainer = Surface, // #F5F7F9
    onSecondaryContainer = TextPrimary,
    tertiary = PrimaryNavy,
    onTertiary = Color.White,
    tertiaryContainer = Surface,
    onTertiaryContainer = TextPrimary,
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Surface, // #F5F7F9
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = Color.White,
    outline = Border,
    outlineVariant = Border
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004D73),
    onPrimaryContainer = Color.White,
    secondary = SurfaceDark,
    onSecondary = TextPrimaryDark,
    secondaryContainer = CardDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = PrimaryBlueDark,
    onTertiary = Color.White,
    tertiaryContainer = CardDark,
    onTertiaryContainer = TextPrimaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    error = ErrorDark,
    onError = Color(0xFF690005),
    outline = BorderDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark
)

@Composable
fun SIAPELTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
