package com.flightcompare.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue80,
    secondary = BlueGrey40,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = BlueGrey80,
    tertiary = Green40,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = Green80,
    error = androidx.compose.ui.graphics.Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue40,
    primaryContainer = Blue40,
    secondary = BlueGrey80,
    onSecondary = BlueGrey40,
    secondaryContainer = BlueGrey40,
    tertiary = Green80,
    onTertiary = Green40,
    tertiaryContainer = Green40,
)

@Composable
fun FlightCompareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
