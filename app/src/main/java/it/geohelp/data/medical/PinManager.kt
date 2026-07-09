package it.geohelp.data.medical

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * PIN locale per la schermata "Dati medici", **uno per account** (id Supabase + email).
 *
 * Cambiando login sullo stesso telefono ogni utente ha il proprio PIN; non si riusa
 * quello di un altro account. I dati medici restano su Supabase per utente (RLS).
 */
class PinManager(
    context: Context,
    userId: String,
    userEmail: String,
) {

    /** Chiave stabile per le prefs (id auth; fallback email normalizzata). */
    private val accountKey: String = when {
        userId.isNotBlank() -> userId
        userEmail.isNotBlank() -> userEmail.trim().lowercase()
        else -> ""
    }

    private val emailNorm: String = userEmail.trim().lowercase()

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasPin(): Boolean = accountKey.isNotBlank() && prefs.getString(prefsKey(), null) != null

    fun setPin(pin: CharArray) {
        require(accountKey.isNotBlank()) { "PIN medico: account non identificato" }
        prefs.edit().putString(prefsKey(), hash(pin)).apply()
    }

    fun verify(pin: CharArray): Boolean {
        if (accountKey.isBlank()) return false
        val stored = prefs.getString(prefsKey(), null) ?: return false
        return stored == hash(pin)
    }

    fun changePinVerified(oldPin: CharArray, newPin: CharArray): Boolean {
        if (!verify(oldPin)) return false
        setPin(newPin)
        return true
    }

    /** Cancella solo l'hash del PIN per questo account. */
    fun clear() {
        if (accountKey.isBlank()) return
        prefs.edit().remove(prefsKey()).apply()
    }

    private fun prefsKey(): String = "${KEY_HASH_PREFIX}_$accountKey"

    private fun hash(pin: CharArray): String {
        val bytes = (PIN_SALT + accountKey + "::" + emailNorm + "::" + String(pin))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS_NAME = "geohelp_medical_secure_prefs"
        const val KEY_HASH_PREFIX = "pin_hash_v3"
        const val PIN_SALT = "geohelp::medical-pin::v3::"
        /** @deprecated Chiave globale pre-account; non più letta. */
        const val LEGACY_KEY_HASH = "pin_hash_v2"
    }
}
