package it.geohelp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import it.geohelp.admin.AdminSosPinDialog
import it.geohelp.admin.AdminSosRecipientsScreen
import io.github.jan.supabase.auth.handleDeeplinks
import it.geohelp.data.auth.AuthRepository
import it.geohelp.data.supabase.Supabase
import it.geohelp.data.consents.ConsentKeys
import it.geohelp.data.consents.ConsentsRepository
import it.geohelp.data.medical.MedicalData
import it.geohelp.data.medical.MedicalDataRepository
import it.geohelp.data.profile.Profile
import it.geohelp.data.profile.ProfileRepository
import it.geohelp.mandown.ManDownForegroundService
import it.geohelp.privacy.PrivacyPolicyScreen
import android.content.res.Configuration
import it.geohelp.R
import it.geohelp.ui.navigation.MainDestination
import it.geohelp.ui.theme.GeoHELPTheme
import java.util.Locale
import kotlinx.coroutines.launch

/** Anno di nascita (Int) dal profilo, oppure null se non valorizzato. */
private fun Profile?.toBirthYear(): Int? = this?.birthYear

class MainActivity : ComponentActivity() {

    private var onPasswordRecoveryFromLink: ((Boolean) -> Unit)? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocale(newBase))
    }

    private fun applyLocale(context: Context): Context {
        val prefs = context.getSharedPreferences("geohelp_prefs", MODE_PRIVATE)
        val lang = prefs.getString("lang", "it") ?: "it"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val prefs = getSharedPreferences("geohelp_prefs", MODE_PRIVATE)

        setContent {
            val scope = rememberCoroutineScope()

            var showSplash by remember { mutableStateOf(true) }
            var currentLang by remember { mutableStateOf(prefs.getString("lang", "it") ?: "it") }
            var showPasswordRecovery by remember { mutableStateOf(false) }

            val authRepo = remember { AuthRepository() }

            LaunchedEffect(Unit) {
                onPasswordRecoveryFromLink = { showPasswordRecovery = it }
                handleAuthDeepLink(intent)
            }
            val profileRepo = remember { ProfileRepository() }
            val consentsRepo = remember { ConsentsRepository() }
            val medicalRepo = remember { MedicalDataRepository() }

            // Sessione letta subito (stesso frame del primo remember).
            var isLoggedIn by remember { mutableStateOf(authRepo.hasActiveSession()) }
            /** Dopo login: consensi + profilo caricati da rete. */
            var prefetchDone by remember { mutableStateOf(false) }
            var hasPrivacy by remember { mutableStateOf(false) }
            var hasProfile by remember { mutableStateOf(false) }
            var userDisplayName by remember { mutableStateOf("") }
            /** Anno di nascita (solo l'anno) per la riga "Nato" nelle SMS. */
            var userBirthYear by remember { mutableStateOf<Int?>(null) }
            var userPhoneE164 by remember { mutableStateOf("") }
            var hasMedicalConsent by remember { mutableStateOf(false) }
            var hasManDownConsent by remember { mutableStateOf(false) }
            /** True se esiste già una riga `man_down` in storico (anche rifiuto). */
            var hasManDownConsentRecord by remember { mutableStateOf(false) }
            var medicalSmsSummary by remember { mutableStateOf("") }
            var showMedical by remember { mutableStateOf(false) }
            /** Quando true, l'utente sta modificando il profilo dalla HelpScreen. */
            var showProfileEditor by remember { mutableStateOf(false) }
            var showConsentsManager by remember { mutableStateOf(false) }
            var showPrivacyPolicy by remember { mutableStateOf(false) }
            var showAdminSosPin by remember { mutableStateOf(false) }
            var showAdminSos by remember { mutableStateOf(false) }
            var sosRecipientsReloadKey by remember { mutableIntStateOf(0) }
            var showPrivacyOnboarding by remember { mutableStateOf(false) }
            var showProfileOnboarding by remember { mutableStateOf(false) }
            var showManDownOnboarding by remember { mutableStateOf(false) }
            var shellDestinationName by rememberSaveable { mutableStateOf(MainDestination.HOME.name) }
            val shellDestination = remember(shellDestinationName) {
                MainDestination.entries.firstOrNull { it.name == shellDestinationName }
                    ?: MainDestination.HOME
            }
            fun reloadConsentFlags() {
                scope.launch {
                    hasPrivacy = runCatching {
                        consentsRepo.isCurrentlyAccepted(ConsentKeys.PRIVACY)
                    }.getOrDefault(false)
                    hasMedicalConsent = runCatching {
                        consentsRepo.isCurrentlyAccepted(ConsentKeys.MEDICAL_DATA)
                    }.getOrDefault(false)
                    hasManDownConsent = runCatching {
                        consentsRepo.isCurrentlyAccepted(ConsentKeys.MAN_DOWN)
                    }.getOrDefault(false)
                    hasManDownConsentRecord = runCatching {
                        consentsRepo.hasAnyConsentForType(ConsentKeys.MAN_DOWN)
                    }.getOrDefault(false)
                    if (!hasManDownConsent) {
                        runCatching { ManDownForegroundService.disarm(this@MainActivity) }
                    }
                    if (!hasMedicalConsent) {
                        showMedical = false
                        medicalSmsSummary = ""
                    } else {
                        medicalSmsSummary = runCatching {
                            medicalRepo.load().toSmsSummary()
                        }.getOrDefault("")
                    }
                }
            }

            LaunchedEffect(isLoggedIn) {
                if (!isLoggedIn) {
                    prefetchDone = true
                    hasManDownConsent = false
                    hasManDownConsentRecord = false
                    return@LaunchedEffect
                }
                prefetchDone = false
                hasPrivacy = runCatching {
                    consentsRepo.isCurrentlyAccepted(ConsentKeys.PRIVACY)
                }.getOrDefault(false)
                hasMedicalConsent = runCatching {
                    consentsRepo.isCurrentlyAccepted(ConsentKeys.MEDICAL_DATA)
                }.getOrDefault(false)
                hasManDownConsent = runCatching {
                    consentsRepo.isCurrentlyAccepted(ConsentKeys.MAN_DOWN)
                }.getOrDefault(false)
                hasManDownConsentRecord = runCatching {
                    consentsRepo.hasAnyConsentForType(ConsentKeys.MAN_DOWN)
                }.getOrDefault(false)
                runCatching { profileRepo.syncEmailFromAuth() }
                val profile = runCatching { profileRepo.getMine() }.getOrNull()
                hasProfile = profile?.isComplete == true
                userDisplayName = profile?.displayName.orEmpty()
                userBirthYear = profile.toBirthYear()
                userPhoneE164 = profile?.userPhone?.trim().orEmpty()
                medicalSmsSummary = if (hasMedicalConsent) {
                    runCatching { medicalRepo.load().toSmsSummary() }.getOrDefault("")
                } else {
                    ""
                }
                prefetchDone = true
            }

            LaunchedEffect(hasProfile) {
                if (hasProfile && (userDisplayName.isBlank() || userPhoneE164.isBlank())) {
                    val p = runCatching { profileRepo.getMine() }.getOrNull()
                    userDisplayName = p?.displayName.orEmpty()
                    userBirthYear = p.toBirthYear()
                    userPhoneE164 = p?.userPhone?.trim().orEmpty()
                }
            }

            val privacyPolicyTitle = remember(currentLang) {
                val config = Configuration(resources.configuration).apply {
                    setLocale(Locale(currentLang))
                }
                createConfigurationContext(config).getString(R.string.privacy_title)
            }

            fun localizedString(resId: Int): String {
                val config = Configuration(resources.configuration).apply {
                    setLocale(Locale(currentLang))
                }
                return createConfigurationContext(config).getString(resId)
            }

            GeoHELPTheme {
                if (showAdminSosPin) {
                    AdminSosPinDialog(
                        currentLanguage = currentLang,
                        onDismiss = { showAdminSosPin = false },
                        onSuccess = {
                            showAdminSosPin = false
                            showAdminSos = true
                        },
                    )
                }

                when {
                    showSplash -> SplashScreen(onDone = { showSplash = false })

                    showPrivacyPolicy -> PrivacyPolicyScreen(
                        currentLanguage = currentLang,
                        title = privacyPolicyTitle,
                        onBack = { showPrivacyPolicy = false },
                    )

                    showPasswordRecovery -> PasswordRecoveryScreen(
                        currentLanguage = currentLang,
                        onDone = {
                            showPasswordRecovery = false
                            isLoggedIn = authRepo.hasActiveSession()
                        },
                    )

                    showPrivacyOnboarding -> ConsentsScreen(
                        currentLanguage = currentLang,
                        mode = ConsentsMode.ONBOARDING,
                        onCompleted = {
                            showPrivacyOnboarding = false
                            reloadConsentFlags()
                        },
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                    )

                    showProfileOnboarding -> OnboardingProfileScreen(
                        currentLanguage = currentLang,
                        onCompleted = {
                            showProfileOnboarding = false
                            hasProfile = true
                        },
                    )

                    showManDownOnboarding -> ManDownConsentScreen(
                        currentLanguage = currentLang,
                        onCompleted = {
                            showManDownOnboarding = false
                            reloadConsentFlags()
                        },
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                    )

                    showConsentsManager -> ConsentsScreen(
                        currentLanguage = currentLang,
                        mode = ConsentsMode.MANAGE,
                        onCancel = { showConsentsManager = false },
                        onCompleted = {
                            showConsentsManager = false
                            reloadConsentFlags()
                        },
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                    )

                    showProfileEditor -> OnboardingProfileScreen(
                        currentLanguage = currentLang,
                        isEdit = true,
                        onCancel = { showProfileEditor = false },
                        onCompleted = {
                            scope.launch {
                                val p = runCatching { profileRepo.getMine() }.getOrNull()
                                userDisplayName = p?.displayName.orEmpty()
                                userBirthYear = p.toBirthYear()
                                userPhoneE164 = p?.userPhone?.trim().orEmpty()
                                showProfileEditor = false
                            }
                        }
                    )

                    showMedical -> MedicalScreen(
                        currentLanguage = currentLang,
                        onBack = { showMedical = false },
                        onMedicalDataChanged = { data: MedicalData? ->
                            medicalSmsSummary = data?.toSmsSummary().orEmpty()
                        }
                    )

                    showAdminSos -> AdminSosRecipientsScreen(
                        currentLanguage = currentLang,
                        onBack = {
                            showAdminSos = false
                            sosRecipientsReloadKey++
                        },
                    )

                    else -> MainShellScreen(
                        currentLanguage = currentLang,
                        destination = shellDestination,
                        onDestinationChange = { shellDestinationName = it.name },
                        isLoggedIn = isLoggedIn,
                        prefetchDone = prefetchDone,
                        hasPrivacy = hasPrivacy,
                        hasProfile = hasProfile,
                        hasManDownConsent = hasManDownConsent,
                        hasManDownConsentRecord = hasManDownConsentRecord,
                        blockAutoLogin = showPasswordRecovery,
                        onLanguageSelected = { langCode ->
                            prefs.edit().putString("lang", langCode).apply()
                            currentLang = langCode
                            recreate()
                        },
                        onAuthenticated = { isLoggedIn = true },
                        onNeedPrivacyOnboarding = { showPrivacyOnboarding = true },
                        onNeedProfileOnboarding = { showProfileOnboarding = true },
                        onNeedManDownOnboarding = { showManDownOnboarding = true },
                        onSendPrimary = { _: String, _: String, _: String -> },
                        onSendBackup = { _: String, _: String, _: String -> },
                        onLogout = {
                            scope.launch {
                                runCatching { ManDownForegroundService.disarm(this@MainActivity) }
                                runCatching { authRepo.signOut() }
                                isLoggedIn = false
                                hasPrivacy = false
                                hasProfile = false
                                hasMedicalConsent = false
                                hasManDownConsent = false
                                hasManDownConsentRecord = false
                                userDisplayName = ""
                                userBirthYear = null
                                userPhoneE164 = ""
                                medicalSmsSummary = ""
                                showMedical = false
                                shellDestinationName = MainDestination.HOME.name
                            }
                        },
                        userDisplayName = userDisplayName,
                        userBirthYear = userBirthYear,
                        userPhoneE164 = userPhoneE164,
                        onOpenMedical = { showMedical = true },
                        onEditProfile = { showProfileEditor = true },
                        onManageConsents = { showConsentsManager = true },
                        onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                        hasMedicalConsent = hasMedicalConsent,
                        hasManDownConsentActive = hasManDownConsent,
                        medicalSmsSummary = medicalSmsSummary,
                        onAdminLogoTap = { showAdminSosPin = true },
                        sosRecipientsExternalReloadKey = sosRecipientsReloadKey,
                    )
                }
            }
        }
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        if (!Supabase.isConfigured) return
        val data: Uri = intent?.data ?: return
        if (data.scheme != Supabase.AUTH_SCHEME || data.host != Supabase.AUTH_HOST) return
        val isRecovery = isPasswordRecoveryDeepLink(data)
        try {
            Supabase.client.handleDeeplinks(intent) {
                if (isRecovery) {
                    onPasswordRecoveryFromLink?.invoke(true)
                }
            }
        } catch (_: Throwable) {
            // Link reset/conferma scaduto, già usato o malformato: non chiudere l'app.
        }
    }

    private fun isPasswordRecoveryDeepLink(data: Uri): Boolean {
        val fragment = data.fragment.orEmpty()
        val query = data.query.orEmpty()
        return fragment.contains("type=recovery", ignoreCase = true) ||
            query.contains("type=recovery", ignoreCase = true)
    }

}
