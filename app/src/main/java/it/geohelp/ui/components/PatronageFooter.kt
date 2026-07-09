package it.geohelp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PatronEntry(
    @DrawableRes val logoResId: Int,
    val contentDescription: String,
    val caption: String,
    val url: String,
)

/** Tre patrocini in basso (Comune Sestriere, Cesana, Consorzio turismo). */
@Composable
fun PatronageFooter(
    title: String,
    patrons: List<PatronEntry>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (title.isNotBlank()) 6.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
        ) {
            patrons.forEach { patron ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onOpenUrl(patron.url) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        shadowElevation = 1.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = patron.logoResId),
                                contentDescription = patron.contentDescription,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text(
                        text = patron.caption,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF424242),
                        textAlign = TextAlign.Center,
                        lineHeight = 11.sp,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/** Patrocini in home: due in prima riga, uno centrato sotto (più grandi). */
@Composable
fun HomePatronageGrid(
    patrons: List<PatronEntry>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (patrons.size < 3) {
        PatronageFooter(title = "", patrons = patrons, onOpenUrl = onOpenUrl, modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            HomePatronCard(patrons[0], onOpenUrl, Modifier.weight(1f))
            HomePatronCard(patrons[1], onOpenUrl, Modifier.weight(1f))
        }
        HomePatronCard(
            patron = patrons[2],
            onOpenUrl = onOpenUrl,
            modifier = Modifier.fillMaxWidth(0.62f),
        )
    }
}

@Composable
private fun HomePatronCard(
    patron: PatronEntry,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clickable { onOpenUrl(patron.url) },
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = patron.logoResId),
                    contentDescription = patron.contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            text = patron.caption,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1B1B1B),
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
        )
    }
}
