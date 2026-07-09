package it.geohelp.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Caricamento leggero dopo lo splash: stesso sfondo, niente logo né 3s di attesa. */
@Composable
fun BootstrapLoadingOverlay() {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        GeoHelpBackground(imageAlpha = 0.42f, overlayAlpha = 0.42f) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFB71C1C))
            }
        }
    }
}
