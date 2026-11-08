package io.github.latticenotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.latticenotes.data.AppSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF5C5142),
    onPrimary = Color(0xFFFFF7E8),
    primaryContainer = Color(0xFFD8CBB6),
    onPrimaryContainer = Color(0xFF302821),
    secondary = Color(0xFF746554),
    secondaryContainer = Color(0xFFE9DFCD),
    tertiary = Color(0xFFB36F35),
    background = Color(0xFFE9E1D2),
    onBackground = Color(0xFF302821),
    surface = Color(0xFFFBF6E9),
    onSurface = Color(0xFF302821),
    surfaceVariant = Color(0xFFE4DAC7),
    outline = Color(0xFF837767),
    outlineVariant = Color(0xFFC8BCA8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6C4A6),
    onPrimary = Color(0xFF352D24),
    primaryContainer = Color(0xFF514438),
    onPrimaryContainer = Color(0xFFF1E5D2),
    secondary = Color(0xFFD0C0AA),
    secondaryContainer = Color(0xFF4A4035),
    tertiary = Color(0xFFE9A76E),
    background = Color(0xFF211C17),
    onBackground = Color(0xFFEDE3D2),
    surface = Color(0xFF2B251F),
    onSurface = Color(0xFFEDE3D2),
    surfaceVariant = Color(0xFF4A4036),
    outline = Color(0xFFA99A86),
    outlineVariant = Color(0xFF574B3F),
)

@Composable
fun LatticeTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val colors = if (dark) DarkColors else LightColors
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = settings.fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(34.dp),
            ),
            content = content,
        )
    }
}
