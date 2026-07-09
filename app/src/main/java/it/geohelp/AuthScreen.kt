package it.geohelp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import it.geohelp.data.auth.AuthCredentialsStore
import it.geohelp.data.auth.AuthErrorMapper
import it.geohelp.data.auth.AuthRepository
import it.geohelp.data.auth.SignUpOutcome
import it.geohelp.data.supabase.Supabase
import it.geohelp.ui.components.AuthorCredits
import it.geohelp.ui.components.SecretTextField
import it.geohelp.ui.theme.GeoHelpBackground
import kotlinx.coroutines.launch

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

/**
 * Schermata Login / Registrazione obbligatoria all'avvio.
 *
 * Mantiene lo stesso sfondo del resto dell'app ([GeoHelpBackground]).
 * Una volta che la sessione Supabase risulta attiva, viene invocata
 * [onAuthenticated] (MainActivity proseguirà sulla HelpScreen).
 */
@Composable
fun AuthScreen(
    currentLanguage: String,
    blockAutoLogin: Boolean = false,
    embeddedInHome: Boolean = false,
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }
    val credentials = remember { AuthCredentialsStore(context) }
    val showSecretLabel = remember(currentLanguage) {
        getStringForLocale(context, currentLanguage, R.string.content_desc_show_secret)
    }
    val hideSecretLabel = remember(currentLanguage) {
        getStringForLocale(context, currentLanguage, R.string.content_desc_hide_secret)
    }

    // Se la sessione esiste (es. login già fatto in passato), salta subito avanti.
    val sessionStatus by repo.sessionStatus.collectAsState()
    LaunchedEffect(sessionStatus, blockAutoLogin, embeddedInHome) {
        if (!embeddedInHome && !blockAutoLogin && repo.hasActiveSession()) {
            onAuthenticated()
        }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = login, 1 = signup
    // Prefill email/password se l'utente aveva attivato "Ricordami".
    var email by remember { mutableStateOf(credentials.savedEmail().orEmpty()) }
    var password by remember { mutableStateOf(credentials.savedPassword().orEmpty()) }
    var passwordConfirm by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(credentials.isRememberEnabled()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    suspend fun mapAuthError(throwable: Throwable): String {
        val serviceStatus = repo.authServiceHttpStatus()
        return AuthErrorMapper.localizedMessageOrServiceRestricted(
            context,
            currentLanguage,
            throwable,
            serviceStatus,
        )
    }

    LaunchedEffect(Unit) {
        if (!Supabase.isConfigured) return@LaunchedEffect
        if (repo.isAuthServiceRestricted(repo.authServiceHttpStatus())) {
            errorMessage = getStringForLocale(
                context,
                currentLanguage,
                R.string.auth_error_service_restricted,
            )
        }
    }

    fun clearForOtherAccount() {
        credentials.clear()
        email = ""
        password = ""
        passwordConfirm = ""
        rememberMe = false
        errorMessage = null
        infoMessage = null
    }

    val authFormCard: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = {
                                        selectedTab = 0
                                        errorMessage = null
                                        infoMessage = null
                                    },
                                    text = {
                                        Text(
                                            stringResourceForLocale(currentLanguage, R.string.auth_tab_login),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        selectedTab = 1
                                        errorMessage = null
                                        infoMessage = null
                                    },
                                    text = {
                                        Text(
                                            stringResourceForLocale(currentLanguage, R.string.auth_tab_signup),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { newEmail ->
                                    if (newEmail != email) {
                                        password = ""
                                        passwordConfirm = ""
                                    }
                                    email = newEmail
                                },
                                label = { Text(stringResourceForLocale(currentLanguage, R.string.auth_email)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SecretTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = stringResourceForLocale(currentLanguage, R.string.auth_password),
                                keyboardType = KeyboardType.Password,
                                imeAction = if (selectedTab == 1) ImeAction.Next else ImeAction.Done,
                                modifier = Modifier.fillMaxWidth(),
                                visibilityToggleDescription = showSecretLabel,
                                showDescription = showSecretLabel,
                                hideDescription = hideSecretLabel,
                            )

                            if (selectedTab == 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SecretTextField(
                                    value = passwordConfirm,
                                    onValueChange = { passwordConfirm = it },
                                    label = stringResourceForLocale(
                                        currentLanguage,
                                        R.string.auth_password_confirm,
                                    ),
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.fillMaxWidth(),
                                    visibilityToggleDescription = showSecretLabel,
                                    showDescription = showSecretLabel,
                                    hideDescription = hideSecretLabel,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { rememberMe = !rememberMe },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFB71C1C),
                                        uncheckedColor = Color(0xFF757575)
                                    )
                                )
                                Text(
                                    text = stringResourceForLocale(
                                        currentLanguage,
                                        R.string.auth_remember
                                    ),
                                    fontSize = 13.sp,
                                    color = Color(0xFF1B1B1B),
                                )
                            }

                            if (selectedTab == 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResourceForLocale(
                                        currentLanguage,
                                        R.string.auth_forgot_password,
                                    ),
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    modifier = Modifier.clickable(enabled = !loading) {
                                        errorMessage = null
                                        infoMessage = null
                                        if (email.isBlank()) {
                                            errorMessage = getStringForLocale(
                                                context,
                                                currentLanguage,
                                                R.string.auth_forgot_password_need_email,
                                            )
                                            return@clickable
                                        }
                                        loading = true
                                        scope.launch {
                                            try {
                                                repo.resetPassword(email)
                                                infoMessage = getStringForLocale(
                                                    context,
                                                    currentLanguage,
                                                    R.string.auth_forgot_password_sent,
                                                )
                                            } catch (t: Throwable) {
                                                errorMessage = mapAuthError(t)
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    },
                                )
                            }

                            if (email.isNotBlank() || password.isNotBlank() || rememberMe) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResourceForLocale(
                                        currentLanguage,
                                        R.string.auth_use_other_account,
                                    ),
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    modifier = Modifier.clickable(enabled = !loading) {
                                        clearForOtherAccount()
                                    },
                                )
                            }

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFB00020),
                                    fontSize = 13.sp
                                )
                            }
                            if (infoMessage != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = infoMessage!!,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                enabled = !loading && Supabase.isConfigured,
                                onClick = {
                                    errorMessage = null
                                    infoMessage = null

                                    if (email.isBlank() || password.isBlank()) {
                                        errorMessage = getStringForLocale(
                                            context, currentLanguage, R.string.auth_error_fields
                                        )
                                        return@Button
                                    }
                                    if (password.length < 6) {
                                        errorMessage = getStringForLocale(
                                            context, currentLanguage, R.string.auth_error_password_min
                                        )
                                        return@Button
                                    }
                                    if (selectedTab == 1 && password != passwordConfirm) {
                                        errorMessage = getStringForLocale(
                                            context, currentLanguage, R.string.auth_error_passwords_mismatch
                                        )
                                        return@Button
                                    }

                                    loading = true
                                    scope.launch {
                                        try {
                                            if (selectedTab == 0) {
                                                repo.signIn(email, password)
                                                if (rememberMe) {
                                                    credentials.save(email, password)
                                                } else {
                                                    credentials.clear()
                                                }
                                                onAuthenticated()
                                            } else {
                                                when (repo.signUp(email, password)) {
                                                    SignUpOutcome.LOGGED_IN -> {
                                                        if (rememberMe) {
                                                            credentials.save(email, password)
                                                        } else {
                                                            credentials.clear()
                                                        }
                                                        onAuthenticated()
                                                    }
                                                    SignUpOutcome.CONFIRM_EMAIL -> {
                                                        selectedTab = 0
                                                        passwordConfirm = ""
                                                        infoMessage = getStringForLocale(
                                                            context,
                                                            currentLanguage,
                                                            R.string.auth_signup_success_confirm,
                                                        )
                                                    }
                                                    SignUpOutcome.EMAIL_ALREADY_EXISTS -> {
                                                        selectedTab = 0
                                                        passwordConfirm = ""
                                                        errorMessage = getStringForLocale(
                                                            context,
                                                            currentLanguage,
                                                            R.string.auth_error_email_exists,
                                                        )
                                                    }
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            errorMessage = mapAuthError(t)
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
                                    .height(52.dp)
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = stringResourceForLocale(
                                            currentLanguage,
                                            if (selectedTab == 0) R.string.auth_login_btn
                                            else R.string.auth_signup_btn
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
    }

    if (embeddedInHome) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_geohelp),
                contentDescription = "geoHELP",
                modifier = Modifier.size(88.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.auth_welcome),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.auth_subtitle),
                fontSize = 13.sp,
                color = Color(0xFF424242),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            authFormCard()
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        GeoHelpBackground(
            imageAlpha = 0.42f,
            overlayAlpha = 0.42f
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .widthIn(max = 420.dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_geohelp),
                        contentDescription = "geoHELP",
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.auth_welcome),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.auth_subtitle),
                        fontSize = 14.sp,
                        color = Color(0xFF424242),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    authFormCard()

                    AuthorCredits(
                        text = stringResourceForLocale(currentLanguage, R.string.splash_credits),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}
