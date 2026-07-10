package it.geohelp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.geohelp.ui.components.getStringForLocale
import it.geohelp.ui.navigation.GeoHelpBottomBar
import it.geohelp.ui.navigation.MainDestination

private val AuthGatedDestinations = setOf(
    MainDestination.HELP,
    MainDestination.POSITION,
    MainDestination.SETTINGS,
    MainDestination.INFO,
)

@Composable
fun MainShellScreen(
    currentLanguage: String,
    destination: MainDestination,
    onDestinationChange: (MainDestination) -> Unit,
    isLoggedIn: Boolean,
    prefetchDone: Boolean,
    hasPrivacy: Boolean,
    hasProfile: Boolean,
    hasManDownConsent: Boolean,
    hasManDownConsentRecord: Boolean,
    blockAutoLogin: Boolean,
    onLanguageSelected: (String) -> Unit,
    onAuthenticated: () -> Unit,
    onNeedPrivacyOnboarding: () -> Unit,
    onNeedProfileOnboarding: () -> Unit,
    onNeedManDownOnboarding: () -> Unit,
    onLogout: () -> Unit,
    userDisplayName: String,
    userBirthYear: Int?,
    userPhoneE164: String,
    onOpenMedical: () -> Unit,
    onEditProfile: () -> Unit,
    onManageConsents: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    hasMedicalConsent: Boolean,
    hasManDownConsentActive: Boolean,
    medicalSmsSummary: String,
    canManageSosRecipients: Boolean,
    sosRecipientsExternalReloadKey: Int,
    onSosRecipientsChanged: () -> Unit,
) {
    val context = LocalContext.current

    fun localized(resId: Int): String =
        getStringForLocale(context, currentLanguage, resId)

    fun isAppReady(): Boolean =
        isLoggedIn &&
            prefetchDone &&
            hasPrivacy &&
            hasProfile &&
            (hasManDownConsent || hasManDownConsentRecord)

    fun requestOnboardingIfNeeded(): Boolean {
        if (!prefetchDone) return false
        if (!hasPrivacy) {
            onNeedPrivacyOnboarding()
            return false
        }
        if (!hasProfile) {
            onNeedProfileOnboarding()
            return false
        }
        if (!hasManDownConsent && !hasManDownConsentRecord) {
            onNeedManDownOnboarding()
            return false
        }
        return true
    }

    fun openHelpFromSettings() {
        if (!isLoggedIn) {
            onDestinationChange(MainDestination.SETTINGS)
            return
        }
        if (requestOnboardingIfNeeded()) {
            onDestinationChange(MainDestination.HELP)
        }
    }

    @Composable
    fun HelpTabContent(shellTab: Int) {
        HelpScreen(
            currentLanguage = currentLanguage,
            onLogout = onLogout,
            onBack = { onDestinationChange(MainDestination.HOME) },
            userDisplayName = userDisplayName,
            userBirthYear = userBirthYear,
            userPhoneE164 = userPhoneE164,
            onOpenMedical = onOpenMedical,
            onEditProfile = onEditProfile,
            onManageConsents = onManageConsents,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            hasMedicalConsent = hasMedicalConsent,
            hasManDownConsent = hasManDownConsentActive,
            medicalSmsSummary = medicalSmsSummary,
            sosRecipientsExternalReloadKey = sosRecipientsExternalReloadKey,
            embeddedInShell = true,
            shellTab = shellTab,
            onShellTabChange = { tab ->
                onDestinationChange(
                    when (tab) {
                        0 -> MainDestination.HELP
                        2 -> MainDestination.POSITION
                        3 -> MainDestination.INFO
                        else -> destination
                    },
                )
            },
            showPatronageFooter = false,
            showLanguageSelector = false,
            showBottomNavigation = false,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            when {
                destination == MainDestination.HOME -> {
                    HomeScreen(
                        currentLanguage = currentLanguage,
                        showLogoutButton = isLoggedIn,
                        onLogout = onLogout,
                    )
                }

                destination in AuthGatedDestinations && !isLoggedIn -> {
                    LoginGateScreen(
                        currentLanguage = currentLanguage,
                        blockAutoLogin = blockAutoLogin,
                        onAuthenticated = onAuthenticated,
                        onLanguageSelected = onLanguageSelected,
                        onBack = { onDestinationChange(MainDestination.HOME) },
                    )
                }

                destination in AuthGatedDestinations && isLoggedIn && !prefetchDone -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                destination == MainDestination.HELP && isLoggedIn && prefetchDone -> {
                    if (!isAppReady()) {
                        requestOnboardingIfNeeded()
                    } else {
                        HelpTabContent(shellTab = 0)
                    }
                }

                destination == MainDestination.POSITION && isLoggedIn && prefetchDone -> {
                    if (!isAppReady()) {
                        requestOnboardingIfNeeded()
                    } else {
                        HelpTabContent(shellTab = 2)
                    }
                }

                destination == MainDestination.INFO && isLoggedIn && prefetchDone -> {
                    if (!isAppReady()) {
                        requestOnboardingIfNeeded()
                    } else {
                        HelpTabContent(shellTab = 3)
                    }
                }

                destination == MainDestination.SETTINGS && isLoggedIn && prefetchDone -> {
                    SettingsScreen(
                        currentLanguage = currentLanguage,
                        onOpenHelp = { openHelpFromSettings() },
                        onEditProfile = onEditProfile,
                        onManageConsents = onManageConsents,
                        onOpenMedical = onOpenMedical,
                        onBack = { onDestinationChange(MainDestination.HOME) },
                        canManageSosRecipients = canManageSosRecipients,
                        sosRecipientsReloadKey = sosRecipientsExternalReloadKey,
                        onSosRecipientsChanged = onSosRecipientsChanged,
                    )
                }

                else -> {
                    HomeScreen(
                        currentLanguage = currentLanguage,
                        showLogoutButton = isLoggedIn,
                        onLogout = onLogout,
                    )
                }
            }
        }
        GeoHelpBottomBar(
            selected = destination,
            onSelect = { onDestinationChange(it) },
            onCall112 = {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
            },
            tabSosLabel = localized(R.string.tab_sos),
            tabCall112Label = localized(R.string.tab_call_112),
            tabTrackingLabel = localized(R.string.tab_tracking),
            tabSettingsLabel = localized(R.string.tab_settings),
            tabInfoLabel = localized(R.string.tab_info_title),
            contentDescCall112 = localized(R.string.content_desc_call_112),
            contentDescTracking = localized(R.string.content_desc_tracking),
            contentDescSettings = localized(R.string.content_desc_settings),
            contentDescInfo = localized(R.string.tab_info_title),
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
