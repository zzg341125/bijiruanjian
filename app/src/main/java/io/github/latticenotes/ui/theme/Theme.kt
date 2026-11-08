package io.github.latticenotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.latticenotes.data.AppSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF305C4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E4CC),
    onPrimaryContainer = Color(0xFF082018),
    secondary = Color(0xFF546A5E),
    secondaryContainer = Color(0xFFD7E8DE),
    tertiary = Color(0xFF9A5B32),
    background = Color(0xFFF8F4EA),
    onBackground = Color(0xFF20231F),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF20231F),
    surfaceVariant = Color(0xFFE8E1D4),
    outline = Color(0xFF7A776E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCFB1),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF174D3B),
    onPrimaryContainer = Color(0xFFB8E4CC),
    secondary = Color(0xFFBBCBBF),
    secondaryContainer = Color(0xFF3C4B42),
    tertiary = Color(0xFFF0B58A),
    background = Color(0xFF121613),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF181D19),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF3E463F),
    outline = Color(0xFF8C948C),
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
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
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
