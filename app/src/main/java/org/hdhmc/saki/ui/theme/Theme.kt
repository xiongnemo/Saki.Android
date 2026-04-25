package org.hdhmc.saki.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = MoonlitBlue,
    onPrimary = Ink,
    primaryContainer = Color(0xFF1C465A),
    onPrimaryContainer = Foam,
    secondary = MoonlitMist,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF33454E),
    onSecondaryContainer = Foam,
    tertiary = EmberGlow,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF6C4306),
    onTertiaryContainer = Foam,
    background = Night,
    onBackground = Foam,
    surface = Tide,
    onSurface = Foam,
    surfaceVariant = Color(0xFF243239),
    onSurfaceVariant = Color(0xFFD6E3EA),
    outline = Color(0xFF89969D),
    outlineVariant = Color(0xFF425157),
)

private val LightColorScheme = lightColorScheme(
    primary = HarborBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EAFF),
    onPrimaryContainer = Ink,
    secondary = HarborMist,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFDDE4),
    onSecondaryContainer = Ink,
    tertiary = EmberGold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Ink,
    background = Foam,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFD5E4EB),
    onSurfaceVariant = Color(0xFF3F4C53),
    outline = Color(0xFF6E7B82),
    outlineVariant = Color(0xFFBCCAD1),
)

@Composable
fun SakiAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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
        shapes = SakiShapes,
        content = content
    )
}
