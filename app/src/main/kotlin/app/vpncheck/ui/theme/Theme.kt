package app.vpncheck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0F4C81)
val NavyLight = Color(0xFFB9D6F2)
val Good = Color(0xFF1B8A4B)
val GoodContainer = Color(0xFFDDF5E5)
val Bad = Color(0xFFB3261E)
val BadContainer = Color(0xFFF9DEDC)
val Neutral = Color(0xFF8A8F98)

private val LightScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color(0xFF00203A),
    secondary = Color(0xFF3B5B7A),
    surface = Color(0xFFFBFCFE),
    surfaceVariant = Color(0xFFE6EBF1),
    onSurfaceVariant = Color(0xFF3F4750),
    error = Bad,
    errorContainer = BadContainer,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FC1F0),
    onPrimary = Color(0xFF00304F),
    primaryContainer = Color(0xFF1D466B),
    onPrimaryContainer = NavyLight,
    secondary = Color(0xFFA8C3DD),
    surface = Color(0xFF111418),
    surfaceVariant = Color(0xFF262B32),
    onSurfaceVariant = Color(0xFFC3C8D0),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF6E2A26),
)

@Composable
fun VpnCheckTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun goodColor(): Color = if (isSystemInDarkTheme()) Color(0xFF6FD79A) else Good

@Composable
fun goodContainer(): Color = if (isSystemInDarkTheme()) Color(0xFF15402A) else GoodContainer

@Composable
fun warnColor(): Color = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB26A00)
