package it.geohelp.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `public.sos_recipients` su Supabase.
 *
 * Sostituisce a regime i numeri letti dal CSV pubblico di Google Sheet.
 */
@Serializable
data class SosRecipient(
    val id: Long? = null,
    val label: String? = null,
    val phone: String,
    val role: String,
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int? = null,
) {
    val isPrimary: Boolean get() = role.equals("primary", ignoreCase = true)
    val isBackup: Boolean  get() = role.equals("backup",  ignoreCase = true)
}
