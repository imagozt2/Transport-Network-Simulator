package com.rmm.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = RMMBlue,
    onPrimary = Color.White,
    primaryContainer = RMMBlueContainer,
    onPrimaryContainer = RMMNavy,
    secondary = RMMBlueDark,
    onSecondary = Color.White,
    background = RMMSurface,
    onBackground = RMMNavy,
    surface = Color.White,
    onSurface = RMMNavy,
    surfaceVariant = RMMSurfaceVariant,
    onSurfaceVariant = RMMSlate,
    outline = RMMOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = RMMBlue,
    onPrimary = Color.White,
    primaryContainer = RMMBlueContainerDark,
    onPrimaryContainer = Color(0xFFDCEEFF),
    secondary = Color(0xFF72BFFF),
    onSecondary = RMMNavy,
    background = RMMNavy,
    onBackground = Color.White,
    surface = RMMDarkSurface,
    onSurface = Color.White,
    surfaceVariant = RMMDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC4CEDA),
    outline = RMMDarkOutline,
)

@Composable
fun RMMAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
        typography = RMMTypography,
        shapes = RMMShapes,
        content = content,
    )
}
