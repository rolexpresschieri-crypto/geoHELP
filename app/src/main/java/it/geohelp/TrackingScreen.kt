package it.geohelp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.ui.components.getStringForLocale
import it.geohelp.ui.theme.GeoHelpBackground

@Composable
fun TrackingScreen(currentLanguage: String) {
    val context = LocalContext.current
    val title = getStringForLocale(context, currentLanguage, R.string.tab_tracking)
    val subtitle = getStringForLocale(context, currentLanguage, R.string.tracking_coming_soon)

    GeoHelpBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$title\n\n$subtitle",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )
        }
    }
}
