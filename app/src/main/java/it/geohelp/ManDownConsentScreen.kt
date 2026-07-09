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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import it.geohelp.data.consents.ConsentsRepository
import it.geohelp.ui.components.AuthorCreditsBottomEnd
import it.geohelp.ui.theme.GeoHelpBackground
import kotlinx.coroutines.launch

private fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(java.util.Locale(locale)) }
    return context.createConfigurationContext(config).resources.getString(resId)
}

/**
 * Chi ha già privacy/medici su server ma nessuna riga `man_down` (utenti pre-MAN-DOWN)
 * non passa da [ConsentsScreen]: registriamo qui il consenso dedicato.
 */
@Composable
fun ManDownConsentScreen(
    currentLanguage: String,
    onCompleted: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ConsentsRepository() }

    var accepted by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        GeoHelpBackground(imageAlpha = 0.42f, overlayAlpha = 0.46f) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = getStringForLocale(context, currentLanguage, R.string.mandown_gate_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = getStringForLocale(context, currentLanguage, R.string.mandown_gate_intro),
                        fontSize = 14.sp,
                        color = Color(0xFF424242),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                    Text(
                        text = getStringForLocale(context, currentLanguage, R.string.consents_privacy_read_link),
                        fontSize = 14.sp,
                        color = Color(0xFF1565C0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 18.dp)
                            .clickable(onClick = onOpenPrivacyPolicy),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = accepted,
                            onCheckedChange = { accepted = it; errorMessage = null },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFB71C1C))
                        )
                        Spacer(modifier = Modifier.padding(end = 4.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${getStringForLocale(context, currentLanguage, R.string.consents_man_down_label)} " +
                                    getStringForLocale(context, currentLanguage, R.string.consents_man_down_optional),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E2E2E)
                            )
                            Text(
                                text = getStringForLocale(context, currentLanguage, R.string.consents_man_down_body),
                                fontSize = 13.sp,
                                color = Color(0xFF424242),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFB00020),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        enabled = !loading,
                        onClick = {
                            errorMessage = null
                            loading = true
                            scope.launch {
                                try {
                                    repo.record(ConsentKeys.MAN_DOWN, accepted = accepted)
                                    onCompleted()
                                } catch (t: Throwable) {
                                    val msg = t.localizedMessage ?: t.javaClass.simpleName
                                    errorMessage = getStringForLocale(context, currentLanguage, R.string.consents_error_save).format(msg)
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB71C1C),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .height(52.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(2.dp)
                            )
                        } else {
                            Text(
                                text = getStringForLocale(context, currentLanguage, R.string.consents_continue_btn),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))
                }

                AuthorCreditsBottomEnd(
                    text = getStringForLocale(context, currentLanguage, R.string.splash_credits),
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}
