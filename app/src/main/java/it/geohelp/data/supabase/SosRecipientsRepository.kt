package it.geohelp.data.supabase

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository per la tabella `sos_recipients`.
 * Lettura per tutti (solo righe active); gestione admin se `profiles.can_manage_sos_recipients`.
 */
class SosRecipientsRepository {

    /** Tutti i destinatari attivi, ordinati per sort_order ASC. */
    suspend fun listActive(): List<SosRecipient> = withContext(Dispatchers.IO) {
        Supabase.client
            .from("sos_recipients")
            .select {
                filter { eq("active", true) }
                order(column = "sort_order", order = Order.ASCENDING)
            }
            .decodeList<SosRecipient>()
    }

    /** Solo i numeri primari attivi, max [limit]. */
    suspend fun listPrimary(limit: Int = 2): List<SosRecipient> =
        listActive().filter { it.isPrimary }.take(limit)

    /** Primo backup attivo, se presente. */
    suspend fun firstBackup(): SosRecipient? =
        listActive().firstOrNull { it.isBackup }

    /** Tutte le righe (anche inactive): richiede `can_manage_sos_recipients` su profiles. */
    suspend fun listAllForAdmin(): List<SosRecipient> = withContext(Dispatchers.IO) {
        Supabase.client
            .from("sos_recipients")
            .select {
                order(column = "sort_order", order = Order.ASCENDING)
            }
            .decodeList<SosRecipient>()
    }

    /** Esclude/include un destinatario (`active` su Supabase). */
    suspend fun setActive(id: Long, active: Boolean) = withContext(Dispatchers.IO) {
        Supabase.client.from("sos_recipients").update(
            buildJsonObject { put("active", JsonPrimitive(active)) },
        ) {
            filter { eq("id", id) }
        }
    }
}
