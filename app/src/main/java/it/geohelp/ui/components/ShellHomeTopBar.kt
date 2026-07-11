package it.geohelp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.R

@Composable
fun ShellHomeTopBar(
    currentLanguage: String,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeLabel = getStringForLocale(context, currentLanguage, R.string.nav_home)
    val homeDesc = getStringForLocale(context, currentLanguage, R.string.content_desc_nav_home)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xE6FFFFFF),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 2.dp, end = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateHome) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = homeDesc,
                    tint = Color(0xFF0D47A1),
                )
            }
            Text(
                text = homeLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D47A1),
            )
        }
    }
}
