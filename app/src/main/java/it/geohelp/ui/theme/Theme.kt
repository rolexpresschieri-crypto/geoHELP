package it.geohelp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette fissa chiara: l'app usa sfondi foto + card bianche ovunque.
 * Non seguiamo dark mode / dynamic color di sistema (evita testo bianco su bianco su Samsung e altri OEM).
 */
private val GeoHelpLightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    secondary = Color(0xFF1976D2),
    onSecondary = Color.White,
    tertiary = Color(0xFFB71C1C),
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = GeoHelpFormText,
    surface = Color.White,
    onSurface = GeoHelpFormText,
    onSurfaceVariant = GeoHelpFormTextMuted,
    outline = GeoHelpFormBorder,
    error = Color(0xFFB71C1C),
    onError = Color.White,
)

@Composable
fun GeoHELPTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GeoHelpLightColorScheme,
        typography = Typography,
        content = content,
    )
}
