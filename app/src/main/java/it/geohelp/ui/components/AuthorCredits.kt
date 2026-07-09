package it.geohelp.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthorCredits(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.End,
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontStyle = FontStyle.Italic,
        color = Color(0xFF424242),
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Firma autore ancorata in basso a destra, sopra la barra di navigazione Android. */
@Composable
fun AuthorCreditsBottomEnd(
    text: String,
    modifier: Modifier = Modifier,
) {
    AuthorCredits(
        text = text,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = 20.dp, bottom = 24.dp),
        textAlign = TextAlign.End,
    )
}
