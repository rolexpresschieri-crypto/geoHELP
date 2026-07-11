package it.geohelp.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella public.profiles (NON sensibile, in chiaro).
 *
 * Per privacy raccogliamo il minimo:
 *   - first_name: richiesto al primo onboarding.
 *   - birth_year: opzionale (solo l'anno, non la data completa).
 *   - user_phone: cellulare utente E.164 (obbligatorio per profilo completo).
 *   - gender, emergency_contact_phone: opzionali.
 *
 * I campi LEGACY (last_name, birth_date) restano in DB per backward compat dello
 * schema iniziale ma NON vengono più popolati né mostrati dall'app.
 */
@Serializable
data class Profile(
    val id: String,
    /** Email di login (allineata a auth.users). */
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("birth_year") val birthYear: Int? = null,
    val gender: String? = null,
    @SerialName("user_phone") val userPhone: String? = null,
    @SerialName("emergency_contact_name")  val emergencyContactName: String? = null,
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String? = null,
    @SerialName("preferred_language") val preferredLanguage: String = "it",
    @SerialName("can_manage_sos_recipients") val canManageSosRecipients: Boolean = false,
    @SerialName("can_manage_ordinances") val canManageOrdinances: Boolean = false,
) {
    /** Solo il nome di battesimo: usato negli SMS / UI. Vuoto se non compilato. */
    val displayName: String
        get() = firstName?.trim().orEmpty()

    /** Profilo completo: nome, anno di nascita e telefono utente. */
    val isComplete: Boolean
        get() = !firstName.isNullOrBlank() &&
            birthYear != null &&
            UserPhone.isValidE164(userPhone)
}
