package it.geohelp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.data.auth.AuthErrorMapper
import it.geohelp.data.auth.AuthRepository
import it.geohelp.ui.components.AuthorCreditsBottomEnd
import it.geohelp.ui.components.SecretTextField
import it.geohelp.ui.theme.GeoHelpBackground
import kotlinx.coroutines.launch

private fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(java.util.Locale(locale)) }
    return context.createConfigurationContext(config).resources.getString(resId)
}

/**
 * Mostrata dopo che l'utente ha aperto il link "password dimenticata" nell'email.
 * La sessione Supabase è già attiva; serve solo impostare la nuova password.
 */
@Composable
fun PasswordRecoveryScreen(
    currentLanguage: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val showSecretLabel = remember(currentLanguage) {
        getStringForLocale(context, currentLanguage, R.string.content_desc_show_secret)
    }
    val hideSecretLabel = remember(currentLanguage) {
        getStringForLocale(context, currentLanguage, R.string.content_desc_hide_secret)
    }

    GeoHelpBackground {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_geohelp),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.92f),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = getStringForLocale(context, currentLanguage, R.string.auth_recovery_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = getStringForLocale(context, currentLanguage, R.string.auth_recovery_subtitle),
                        fontSize = 14.sp,
                        color = Color(0xFF424242),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SecretTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = getStringForLocale(context, currentLanguage, R.string.auth_password),
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.fillMaxWidth(),
                        visibilityToggleDescription = showSecretLabel,
                        showDescription = showSecretLabel,
                        hideDescription = hideSecretLabel,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SecretTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = getStringForLocale(context, currentLanguage, R.string.auth_password_confirm),
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.fillMaxWidth(),
                        visibilityToggleDescription = showSecretLabel,
                        showDescription = showSecretLabel,
                        hideDescription = hideSecretLabel,
                    )

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = msg, color = Color(0xFFC62828), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            errorMessage = null
                            if (password.length < 6) {
                                errorMessage = getStringForLocale(
                                    context,
                                    currentLanguage,
                                    R.string.auth_error_password_min,
                                )
                                return@Button
                            }
                            if (password != passwordConfirm) {
                                errorMessage = getStringForLocale(
                                    context,
                                    currentLanguage,
                                    R.string.auth_error_passwords_mismatch,
                                )
                                return@Button
                            }
                            loading = true
                            scope.launch {
                                try {
                                    repo.updatePassword(password)
                                    onDone()
                                } catch (t: Throwable) {
                                    errorMessage = AuthErrorMapper.localizedMessage(
                                        context,
                                        currentLanguage,
                                        t,
                                    )
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                getStringForLocale(context, currentLanguage, R.string.auth_recovery_save_btn),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
            AuthorCreditsBottomEnd(
                text = getStringForLocale(context, currentLanguage, R.string.splash_credits),
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}
