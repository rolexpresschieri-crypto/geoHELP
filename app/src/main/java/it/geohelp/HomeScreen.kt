package it.geohelp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.ui.components.HomePatronageGrid
import it.geohelp.ui.components.getStringForLocale
import it.geohelp.ui.components.rememberHomePatronEntries
import it.geohelp.ui.theme.GeoHelpBackground

private val TitleBlue = Color(0xFF0D47A1)
private val LogoDiameter = 118.dp

@Composable
fun HomeScreen(
    currentLanguage: String,
    showLogoutButton: Boolean = false,
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val patronEntries = rememberHomePatronEntries(currentLanguage)

    fun localized(resId: Int): String =
        getStringForLocale(context, currentLanguage, resId)

    GeoHelpBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showLogoutButton) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = localized(R.string.auth_logout),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = localized(R.string.home_project_line1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleBlue,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = localized(R.string.home_project_line2),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleBlue,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = localized(R.string.home_project_line3),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleBlue,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProjectLogo(
                        resId = R.drawable.logo_ansmi_nv,
                        contentDescription = localized(R.string.content_desc_logo_ansmi_nv),
                        diameter = LogoDiameter,
                    )
                    ProjectLogo(
                        resId = R.drawable.logo_ucrs_cinofili,
                        contentDescription = localized(R.string.content_desc_logo_ucrs),
                        diameter = LogoDiameter + 10.dp,
                    )
                }
                Spacer(Modifier.height(56.dp))
                PatronageSectionTitle(localized(R.string.info_patronage_footer_title))
                Spacer(Modifier.height(10.dp))
                HomePatronageGrid(
                    patrons = patronEntries,
                    onOpenUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }
        }
    }
}

@Composable
private fun PatronageSectionTitle(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF757575))
        Text(
            text = text.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = TitleBlue,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF757575))
    }
}

@Composable
private fun ProjectLogo(
    resId: Int,
    contentDescription: String,
    diameter: androidx.compose.ui.unit.Dp,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape),
    )
}
