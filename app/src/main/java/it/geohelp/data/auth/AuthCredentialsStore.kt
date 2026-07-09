package it.geohelp.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Memorizza opzionalmente email e password dell'utente per il login.
 *
 * Storage: EncryptedSharedPreferences (chiave master in Android Keystore).
 * Si scrive solo se l'utente sceglie "Ricordami" sull'AuthScreen.
 *
 * NB: Supabase di per sé persiste la sessione tra riavvii (l'utente NON deve
 * loggarsi a ogni avvio). Questo store serve a comodità nei casi in cui
 * l'utente faccia logout esplicito e non voglia ridigitare le credenziali.
 */
class AuthCredentialsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Throwable) {
            // Keystore corrotto o indisponibile: evita crash all'avvio (es. dopo restore backup).
            context.applicationContext.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    /** True se l'utente ha attivato "Ricordami". Default: false. */
    fun isRememberEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER, false)

    fun savedEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun savedPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun save(email: String, password: String) {
        prefs.edit()
            .putBoolean(KEY_REMEMBER, true)
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** Disattiva "Ricordami" e rimuove email/password salvate. */
    fun clear() {
        prefs.edit()
            .putBoolean(KEY_REMEMBER, false)
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "geohelp_auth_secure_prefs"
        const val PREFS_NAME_FALLBACK = "geohelp_auth_prefs_fallback"
        const val KEY_REMEMBER = "remember"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
    }
}
