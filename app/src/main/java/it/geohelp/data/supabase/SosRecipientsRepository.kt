package it.geohelp.data.supabase

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository per la tabella `sos_recipients`.
 * Lettura numeri attivi per tutti; CRUD admin se `profiles.can_manage_sos_recipients`.
 */
class SosRecipientsRepository {

    companion object {
        const val MAX_RECIPIENTS = 3
    }

    /** Numeri attivi per SOS / MAN DOWN (max [limit], ordine sort_order). */
    suspend fun listActivePhones(limit: Int = MAX_RECIPIENTS): List<String> = withContext(Dispatchers.IO) {
        listActive()
            .map { it.phone.trim() }
            .filter { it.any(Char::isDigit) }
            .distinct()
            .take(limit)
    }

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

    /** Tutte le righe (anche inactive): richiede `can_manage_sos_recipients` su profiles. */
    suspend fun listAllForAdmin(): List<SosRecipient> = withContext(Dispatchers.IO) {
        Supabase.client
            .from("sos_recipients")
            .select {
                order(column = "sort_order", order = Order.ASCENDING)
            }
            .decodeList<SosRecipient>()
    }

    suspend fun insert(
        label: String?,
        phone: String,
        sortOrder: Int,
        active: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        Supabase.client.from("sos_recipients").insert(
            buildJsonObject {
                putLabel(label)
                put("phone", JsonPrimitive(phone.trim()))
                put("role", JsonPrimitive("primary"))
                put("active", JsonPrimitive(active))
                put("sort_order", JsonPrimitive(sortOrder))
            },
        )
    }

    suspend fun update(
        id: Long,
        label: String?,
        phone: String,
        active: Boolean,
        sortOrder: Int,
    ) = withContext(Dispatchers.IO) {
        Supabase.client.from("sos_recipients").update(
            buildJsonObject {
                putLabel(label)
                put("phone", JsonPrimitive(phone.trim()))
                put("active", JsonPrimitive(active))
                put("sort_order", JsonPrimitive(sortOrder))
            },
        ) {
            filter { eq("id", id) }
        }
    }

    suspend fun setActive(id: Long, active: Boolean) = withContext(Dispatchers.IO) {
        Supabase.client.from("sos_recipients").update(
            buildJsonObject { put("active", JsonPrimitive(active)) },
        ) {
            filter { eq("id", id) }
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        Supabase.client.from("sos_recipients").delete {
            filter { eq("id", id) }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putLabel(label: String?) {
        val trimmedLabel = label?.trim().orEmpty()
        if (trimmedLabel.isBlank()) {
            put("label", JsonNull)
        } else {
            put("label", JsonPrimitive(trimmedLabel))
        }
    }
}
