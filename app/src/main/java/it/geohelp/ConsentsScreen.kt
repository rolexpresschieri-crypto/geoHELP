package it.geohelp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.data.consents.ConsentKeys
import it.geohelp.data.consents.ConsentSnapshot
import it.geohelp.data.consents.ConsentsRepository
import it.geohelp.ui.components.AuthorCreditsBottomEnd
import it.geohelp.ui.theme.GeoHelpBackground
import kotlinx.coroutines.launch

enum class ConsentsMode {
    /** Primo accesso: privacy obbligatoria. */
    ONBOARDING,
    /** Da Help: modifica/revoca con nuovo salvataggio in storico. */
    MANAGE,
}

private fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(java.util.Locale(locale)) }
    return context.createConfigurationContext(config).resources.getString(resId)
}

@Composable
private fun stringResourceForLocale(locale: String, resId: Int): String {
    val context = LocalContext.current
    return remember(locale, resId) {
        getStringForLocale(context, locale, resId)
    }
}

@Composable
fun ConsentsScreen(
    currentLanguage: String,
    mode: ConsentsMode = ConsentsMode.ONBOARDING,
    onCompleted: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onOpenPrivacyPolicy: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ConsentsRepository() }

    var privacyAccepted by remember { mutableStateOf(false) }
    var medicalAccepted by remember { mutableStateOf(false) }
    var manDownAccepted by remember { mutableStateOf(false) }
    var initialSnapshot by remember { mutableStateOf<ConsentSnapshot?>(null) }
    var loading by remember { mutableStateOf(mode == ConsentsMode.MANAGE) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showRevokePrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        if (mode == ConsentsMode.MANAGE) {
            loading = true
            val snap = runCatching { repo.loadCurrentSnapshot() }.getOrDefault(ConsentSnapshot())
            initialSnapshot = snap
            privacyAccepted = snap.privacy
            medicalAccepted = snap.medical
            manDownAccepted = snap.manDown
            loading = false
        }
    }

    fun performSave() {
        errorMessage = null
        infoMessage = null
        if (!privacyAccepted && mode == ConsentsMode.ONBOARDING) {
            errorMessage = getStringForLocale(
                context,
                currentLanguage,
                R.string.consents_error_privacy_required,
            )
            return
        }
        saving = true
        scope.launch {
            try {
                val desired = ConsentSnapshot(
                    privacy = privacyAccepted,
                    medical = medicalAccepted,
                    manDown = manDownAccepted,
                )
                if (mode == ConsentsMode.ONBOARDING) {
                    repo.record(ConsentKeys.PRIVACY, accepted = true)
                    repo.record(ConsentKeys.MEDICAL_DATA, accepted = medicalAccepted)
                    repo.record(ConsentKeys.MAN_DOWN, accepted = manDownAccepted)
                } else {
                    val changed = repo.applySnapshot(desired)
                    if (changed) {
                        infoMessage = getStringForLocale(
                            context,
                            currentLanguage,
                            R.string.consents_saved_ok,
                        )
                    }
                }
                onCompleted()
            } catch (t: Throwable) {
                errorMessage = getStringForLocale(context, currentLanguage, R.string.consents_error_save_short)
            } finally {
                saving = false
            }
        }
    }

    if (showRevokePrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showRevokePrivacyDialog = false },
            title = {
                Text(stringResourceForLocale(currentLanguage, R.string.consents_revoke_privacy_title))
            },
            text = {
                Text(stringResourceForLocale(currentLanguage, R.string.consents_revoke_privacy_msg))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokePrivacyDialog = false
                        performSave()
                    },
                ) {
                    Text(stringResourceForLocale(currentLanguage, R.string.consents_revoke_privacy_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokePrivacyDialog = false }) {
                    Text(stringResourceForLocale(currentLanguage, R.string.consents_cancel_btn))
                }
            },
        )
    }

    val titleRes = if (mode == ConsentsMode.MANAGE) R.string.consents_manage_title else R.string.consents_title
    val subtitleRes = if (mode == ConsentsMode.MANAGE) R.string.consents_manage_subtitle else R.string.consents_subtitle
    val primaryBtnRes = if (mode == ConsentsMode.MANAGE) R.string.consents_save_btn else R.string.consents_continue_btn

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        GeoHelpBackground(imageAlpha = 0.42f, overlayAlpha = 0.46f) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFB71C1C))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (mode == ConsentsMode.MANAGE && onCancel != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 540.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onCancel, enabled = !saving) {
                                    Text(
                                        stringResourceForLocale(currentLanguage, R.string.consents_cancel_btn),
                                        color = Color(0xFF1565C0),
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResourceForLocale(currentLanguage, titleRes),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1B1B1B),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResourceForLocale(currentLanguage, subtitleRes),
                            fontSize = 14.sp,
                            color = Color(0xFF424242),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                        Text(
                            text = stringResourceForLocale(
                                currentLanguage,
                                R.string.consents_privacy_read_link,
                            ),
                            fontSize = 14.sp,
                            color = Color(0xFF1565C0),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(bottom = 18.dp)
                                .clickable(onClick = onOpenPrivacyPolicy),
                        )

                        ConsentCard(
                            title = stringResourceForLocale(currentLanguage, R.string.consents_privacy_label),
                            suffix = stringResourceForLocale(currentLanguage, R.string.consents_privacy_required),
                            body = stringResourceForLocale(currentLanguage, R.string.consents_privacy_body),
                            checked = privacyAccepted,
                            onCheckedChange = {
                                privacyAccepted = it
                                errorMessage = null
                                infoMessage = null
                            },
                            required = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ConsentCard(
                            title = stringResourceForLocale(currentLanguage, R.string.consents_medical_label),
                            suffix = stringResourceForLocale(currentLanguage, R.string.consents_medical_optional),
                            body = stringResourceForLocale(currentLanguage, R.string.consents_medical_body),
                            checked = medicalAccepted,
                            onCheckedChange = {
                                medicalAccepted = it
                                infoMessage = null
                            },
                            required = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ConsentCard(
                            title = stringResourceForLocale(currentLanguage, R.string.consents_man_down_label),
                            suffix = stringResourceForLocale(currentLanguage, R.string.consents_man_down_optional),
                            body = stringResourceForLocale(currentLanguage, R.string.consents_man_down_body),
                            checked = manDownAccepted,
                            onCheckedChange = {
                                manDownAccepted = it
                                infoMessage = null
                            },
                            required = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFB00020),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 540.dp),
                            )
                        }
                        if (infoMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = infoMessage!!,
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 540.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            enabled = !saving,
                            onClick = {
                                when {
                                    !privacyAccepted && mode == ConsentsMode.ONBOARDING -> {
                                        errorMessage = getStringForLocale(
                                            context,
                                            currentLanguage,
                                            R.string.consents_error_privacy_required,
                                        )
                                    }
                                    mode == ConsentsMode.MANAGE &&
                                        initialSnapshot?.privacy == true &&
                                        !privacyAccepted -> {
                                        showRevokePrivacyDialog = true
                                    }
                                    else -> performSave()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB71C1C),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp)
                                .height(52.dp),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.padding(2.dp),
                                )
                            } else {
                                Text(
                                    text = stringResourceForLocale(currentLanguage, primaryBtnRes),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))
                    }
                }

                if (!loading) {
                    AuthorCreditsBottomEnd(
                        text = stringResourceForLocale(currentLanguage, R.string.splash_credits),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentCard(
    title: String,
    suffix: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    required: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFB71C1C),
            ),
        )
        Spacer(modifier = Modifier.padding(end = 4.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$title $suffix",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (required) Color(0xFF1B1B1B) else Color(0xFF2E2E2E),
            )
            Text(
                text = body,
                fontSize = 13.sp,
                color = Color(0xFF424242),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
