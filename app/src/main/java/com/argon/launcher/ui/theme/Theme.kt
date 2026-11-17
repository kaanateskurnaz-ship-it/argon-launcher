package com.argon.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// An original, ore-and-foliage inspired palette (not Minecraft's own branding - just colors that
// fit a block-game launcher without copying any specific game's look).
private val ArgonGreen = Color(0xFF3F8F5C)
private val ArgonGreenDark = Color(0xFF1F5B37)
private val ArgonAmber = Color(0xFFE0A845)
private val ArgonAmberDark = Color(0xFF8C6418)
private val ArgonStone = Color(0xFF4F5A55)

private val LightColors = lightColorScheme(
    primary = ArgonGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3EAD1),
    onPrimaryContainer = ArgonGreenDark,
    secondary = ArgonAmber,
    onSecondary = Color(0xFF3A2A06),
    secondaryContainer = Color(0xFFFCE4B0),
    onSecondaryContainer = ArgonAmberDark,
    tertiary = ArgonStone,
    background = Color(0xFFF7FBF6),
    surface = Color(0xFFF7FBF6),
    surfaceVariant = Color(0xFFE2E9E1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3A4),
    onPrimary = ArgonGreenDark,
    primaryContainer = ArgonGreenDark,
    onPrimaryContainer = Color(0xFFC3EAD1),
    secondary = Color(0xFFF1C877),
    onSecondary = ArgonAmberDark,
    secondaryContainer = ArgonAmberDark,
    onSecondaryContainer = Color(0xFFFCE4B0),
    tertiary = Color(0xFFB7C2BD),
    background = Color(0xFF12140F),
    surface = Color(0xFF181C17),
    surfaceVariant = Color(0xFF2B312A)
)

private val ArgonTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun ArgonLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArgonTypography,
        content = content
    )
}
