package it.geohelp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import it.geohelp.ui.theme.geoHelpFilterChipColors
import it.geohelp.ui.theme.geoHelpOutlinedFieldColors
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.data.profile.ProfileRepository
import it.geohelp.data.profile.UserPhone
import it.geohelp.ui.components.AuthorCreditsBottomEnd
import it.geohelp.ui.theme.GeoHelpBackground
import java.util.Locale
import kotlinx.coroutines.launch

/** Sfondo della "pillola" del titolo "Profilo utente": verde scuro su bianco. */
private val ProfileTitlePillBackground = Color(0xFF1B5E20)

private fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale(locale)) }
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
 * Onboarding post-login (privacy by design):
 *   - solo nome (no cognome)
 *   - solo anno di nascita (no data completa)
 *   - sesso e contatto familiare opzionali
 *
 * Quando [isEdit] è true viene mostrato anche un pulsante "Indietro" che chiude la
 * schermata senza salvare (usato dalla HelpScreen come "Modifica profilo").
 */
@Composable
fun OnboardingProfileScreen(
    currentLanguage: String,
    onCompleted: () -> Unit,
    isEdit: Boolean = false,
    onCancel: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ProfileRepository() }

    var firstName by remember { mutableStateOf("") }
    /** 4 cifre dell'anno, oppure stringa vuota. */
    var birthYearText by remember { mutableStateOf("") }
    /** `M`, `F`, `X` o null (non indicato). */
    var selectedGender by remember { mutableStateOf<String?>(null) }
    var userPhoneNational by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val p = runCatching { repo.getMine() }.getOrNull() ?: return@LaunchedEffect
        firstName = p.firstName.orEmpty()
        birthYearText = p.birthYear?.toString().orEmpty()
        selectedGender = p.gender?.trim()?.uppercase()?.takeIf { it in setOf("M", "F", "X") }
        userPhoneNational = UserPhone.nationalDigitsFromStored(p.userPhone)
        emergencyPhone = p.emergencyContactPhone.orEmpty()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        GeoHelpBackground(imageAlpha = 0.42f, overlayAlpha = 0.42f) {
            Box(modifier = Modifier.fillMaxSize()) {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .widthIn(max = 460.dp)
                        .verticalScroll(scroll)
                        .padding(top = 80.dp, start = 24.dp, end = 24.dp, bottom = 88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = ProfileTitlePillBackground,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResourceForLocale(currentLanguage, R.string.profile_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text(stringResourceForLocale(currentLanguage, R.string.profile_first_name)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = geoHelpOutlinedFieldColors(),
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = birthYearText,
                                onValueChange = { raw ->
                                    birthYearText = raw.filter(Char::isDigit).take(4)
                                },
                                label = {
                                    Text(stringResourceForLocale(currentLanguage, R.string.profile_birth_year))
                                },
                                placeholder = {
                                    Text(
                                        stringResourceForLocale(currentLanguage, R.string.profile_birth_year_hint),
                                        color = Color(0xFF9E9E9E)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = geoHelpOutlinedFieldColors(),
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.profile_gender),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF1B1B1B),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "M" to R.string.profile_gender_m,
                                    "F" to R.string.profile_gender_f,
                                    "X" to R.string.profile_gender_x,
                                ).forEach { (code, labelRes) ->
                                    FilterChip(
                                        selected = selectedGender == code,
                                        onClick = {
                                            selectedGender = if (selectedGender == code) null else code
                                        },
                                        label = {
                                            Text(stringResourceForLocale(currentLanguage, labelRes))
                                        },
                                        colors = geoHelpFilterChipColors(),
                                    )
                                }
                            }
                            TextButton(
                                onClick = { selectedGender = null },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResourceForLocale(currentLanguage, R.string.profile_gender_unspecified))
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.profile_user_phone),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1B1B1B),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResourceForLocale(
                                        currentLanguage,
                                        R.string.profile_user_phone_prefix,
                                    ),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                OutlinedTextField(
                                    value = userPhoneNational,
                                    onValueChange = { v ->
                                        userPhoneNational = v.filter { it.isDigit() }.take(10)
                                    },
                                    label = {
                                        Text(
                                            stringResourceForLocale(
                                                currentLanguage,
                                                R.string.profile_user_phone_hint,
                                            ),
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    colors = geoHelpOutlinedFieldColors(),
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = emergencyPhone,
                                onValueChange = { emergencyPhone = it },
                                label = { Text(stringResourceForLocale(currentLanguage, R.string.profile_emergency_phone)) },
                                placeholder = {
                                    Text(
                                        stringResourceForLocale(currentLanguage, R.string.profile_emergency_phone_hint),
                                        color = Color(0xFF9E9E9E)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = geoHelpOutlinedFieldColors(),
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFB00020),
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            if (isEdit) {
                                TextButton(
                                    onClick = onCancel,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResourceForLocale(currentLanguage, R.string.medical_back))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Button(
                                enabled = !loading,
                                onClick = {
                                    errorMessage = null
                                    if (firstName.isBlank()) {
                                        errorMessage = getStringForLocale(
                                            context,
                                            currentLanguage,
                                            R.string.profile_error_required
                                        )
                                        return@Button
                                    }
                                    val currentYear = java.time.Year.now().value
                                    val birthYear: Int = run {
                                        val y = birthYearText.toIntOrNull()
                                        if (y == null || y < 1900 || y > currentYear) {
                                            errorMessage = getStringForLocale(
                                                context,
                                                currentLanguage,
                                                R.string.profile_error_birth_year
                                            )
                                            return@Button
                                        }
                                        y
                                    }
                                    val userE164 = UserPhone.toE164Italy(userPhoneNational)
                                    if (userE164 == null) {
                                        errorMessage = getStringForLocale(
                                            context,
                                            currentLanguage,
                                            R.string.profile_error_user_phone,
                                        )
                                        return@Button
                                    }
                                    loading = true
                                    scope.launch {
                                        try {
                                            repo.saveBasics(
                                                firstName = firstName,
                                                birthYear = birthYear,
                                                userPhone = userE164,
                                                gender = selectedGender,
                                                emergencyContactPhone = emergencyPhone.ifBlank { null }
                                            )
                                            onCompleted()
                                        } catch (t: Throwable) {
                                            val msg = t.localizedMessage ?: t.javaClass.simpleName
                                            errorMessage = getStringForLocale(
                                                context,
                                                currentLanguage,
                                                R.string.profile_error_save
                                            ).format(msg)
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
                                        modifier = Modifier.padding(2.dp)
                                    )
                                } else {
                                    Text(
                                        text = stringResourceForLocale(currentLanguage, R.string.profile_save_btn),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.profile_subtitle),
                        fontSize = 13.sp,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                }
                AuthorCreditsBottomEnd(
                    text = stringResourceForLocale(currentLanguage, R.string.splash_credits),
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}
