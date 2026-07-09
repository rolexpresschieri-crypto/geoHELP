package it.geohelp.data.consents

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella public.consents — storico immutabile dei consensi.
 *
 * Tipi previsti: 'privacy', 'medical_data', 'man_down'.
 * Versione: stringa tipo "v1", "v2", "2026-05-12" (consente di re-chiedere
 * il consenso quando l'informativa cambia).
 */
@Serializable
data class Consent(
    val id: Long? = null,
    @SerialName("user_id")      val userId: String,
    @SerialName("consent_type") val consentType: String,
    val version: String,
    val accepted: Boolean,
    @SerialName("accepted_at")  val acceptedAt: String? = null,
)

object ConsentKeys {
    const val PRIVACY = "privacy"
    const val MEDICAL_DATA = "medical_data"
    const val MAN_DOWN = "man_down"

    /** Versione corrente dell'informativa privacy / consensi. */
    const val CURRENT_VERSION = "v2"
}
