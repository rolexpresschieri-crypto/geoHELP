package it.geohelp

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import it.geohelp.admin.SosRecipientsAdminSection
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.ui.components.getStringForLocale
import it.geohelp.ui.theme.GeoHelpBackground

@Composable
fun LoginGateScreen(
    currentLanguage: String,
    blockAutoLogin: Boolean,
    onAuthenticated: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    fun localized(resId: Int): String =
        getStringForLocale(context, currentLanguage, resId)

    GeoHelpBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = localized(R.string.back),
                        tint = Color(0xFF1B1B1B),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LanguageFlag(
                        resId = R.drawable.flag_it,
                        contentDescription = "Italiano",
                        onClick = { onLanguageSelected("it") },
                    )
                    Spacer(Modifier.size(8.dp))
                    LanguageFlag(
                        resId = R.drawable.flag_en,
                        contentDescription = "English",
                        onClick = { onLanguageSelected("en") },
                        contentScale = ContentScale.Crop,
                        visualScale = 1.18f,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AuthScreen(
                currentLanguage = currentLanguage,
                blockAutoLogin = blockAutoLogin,
                embeddedInHome = true,
                onAuthenticated = onAuthenticated,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    currentLanguage: String,
    onOpenHelp: () -> Unit,
    onEditProfile: () -> Unit,
    onManageConsents: () -> Unit,
    onOpenMedical: () -> Unit,
    onBack: () -> Unit,
    canManageSosRecipients: Boolean = false,
    sosRecipientsReloadKey: Int = 0,
    onSosRecipientsChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    fun localized(resId: Int): String =
        getStringForLocale(context, currentLanguage, resId)

    GeoHelpBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = localized(R.string.back),
                    )
                }
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF0D47A1),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = localized(R.string.tab_settings),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1B),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            val highlightedBoxColor = Color(0xFFFFF4B8)
            SettingsButton(
                label = localized(R.string.tab_help),
                onClick = onOpenHelp,
                containerColor = highlightedBoxColor,
            )
            SettingsButton(
                label = localized(R.string.profile_edit_btn),
                onClick = onEditProfile,
                containerColor = highlightedBoxColor,
            )
            SettingsButton(
                label = localized(R.string.consents_manage_open_btn),
                onClick = onManageConsents,
                containerColor = highlightedBoxColor,
            )
            SettingsButton(
                label = localized(R.string.medical_title),
                onClick = onOpenMedical,
                containerColor = highlightedBoxColor,
            )
            if (canManageSosRecipients) {
                Spacer(Modifier.height(12.dp))
                SosRecipientsAdminSection(
                    currentLanguage = currentLanguage,
                    reloadKey = sosRecipientsReloadKey,
                    onRecipientsChanged = onSosRecipientsChanged,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsButton(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    containerColor: Color = Color.White.copy(alpha = 0.92f),
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) Color(0xFFB71C1C) else containerColor,
            contentColor = if (destructive) Color.White else Color(0xFF1B1B1B),
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LanguageFlag(
    resId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    contentScale: ContentScale = ContentScale.FillBounds,
    visualScale: Float = 1f,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = visualScale
                    scaleY = visualScale
                },
        )
    }
}
