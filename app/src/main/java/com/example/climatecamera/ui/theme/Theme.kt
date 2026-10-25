package com.example.climatecamera.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60), onPrimary = Color.White,
    primaryContainer = Color(0xFFBAF0DF), onPrimaryContainer = Color(0xFF002E26),
    secondary = Color(0xFF4C635D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8DF), onSecondaryContainer = Color(0xFF09201A),
    tertiary = Color(0xFF805400), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB2), onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFF4F8F5), onBackground = Color(0xFF18211E),
    surface = Color(0xFFF9FCF9), onSurface = Color(0xFF18211E),
    surfaceVariant = Color(0xFFE0EAE4), onSurfaceVariant = Color(0xFF404A45),
    outline = Color(0xFF707A74), outlineVariant = Color(0xFFBFCAC3),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5BF), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045), onPrimaryContainer = Color(0xFFBAF0DF),
    secondary = Color(0xFFB4CCC2), onSecondary = Color(0xFF20352D),
    secondaryContainer = Color(0xFF364B42), onSecondaryContainer = Color(0xFFD0E8DF),
    tertiary = Color(0xFFF5BD70), onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF624000), onTertiaryContainer = Color(0xFFFFDDB2),
    background = Color(0xFF101815), onBackground = Color(0xFFDEE8E1),
    surface = Color(0xFF151F1A), onSurface = Color(0xFFDEE8E1),
    surfaceVariant = Color(0xFF35413A), onSurfaceVariant = Color(0xFFBFCAC3),
    outline = Color(0xFF89958D), outlineVariant = Color(0xFF404C44),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun ClimateCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content)
}
