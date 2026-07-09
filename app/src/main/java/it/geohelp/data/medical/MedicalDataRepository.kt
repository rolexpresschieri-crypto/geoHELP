package it.geohelp.data.medical

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import it.geohelp.data.supabase.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository per la tabella public.medical_data.
 *
 * I dati medici sono salvati in colonne strutturate, in chiaro a livello DB,
 * protetti da:
 *  - RLS (ogni utente vede solo la propria riga; auth.uid() = user_id)
 *  - trasporto HTTPS
 *  - consenso esplicito alla liberatoria (consents.medical_data accepted)
 *
 * Il team admin (service_role) può consultare i dati per finalità di soccorso,
 * come da informativa. Il PIN locale dell'app è solo un lock di accesso.
 */
class MedicalDataRepository {

    @Serializable
    private data class Row(
        @SerialName("user_id") val userId: String? = null,
        @SerialName("conditions") val conditions: String? = null,
        @SerialName("pacemaker") val pacemaker: Boolean = false,
        @SerialName("blood_group") val bloodGroup: String? = null,
        @SerialName("allergies") val allergies: String? = null,
        @SerialName("therapies") val therapies: String? = null,
        @SerialName("notes") val notes: String? = null,
    )

    private fun currentUserId(): String? =
        Supabase.client.auth.currentUserOrNull()?.id

    /** Carica i dati medici dell'utente corrente. [MedicalData.EMPTY] se non esistono. */
    suspend fun load(): MedicalData = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext MedicalData.EMPTY
        val rows = Supabase.client.from("medical_data")
            .select {
                filter { eq("user_id", uid) }
                limit(1)
            }
            .decodeList<Row>()
        val r = rows.firstOrNull() ?: return@withContext MedicalData.EMPTY
        MedicalData(
            conditions = r.conditions.orEmpty(),
            pacemaker = r.pacemaker,
            bloodGroup = r.bloodGroup?.trim()?.takeIf { it.isNotEmpty() },
            allergies = r.allergies.orEmpty(),
            therapies = r.therapies.orEmpty(),
            notes = r.notes.orEmpty(),
        )
    }

    /**
     * Salva (upsert) i dati medici per l'utente corrente.
     * I campi testuali blank vengono salvati come NULL in DB.
     */
    suspend fun save(data: MedicalData): Unit = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: error("Utente non autenticato")
        fun JsonPrimitiveOrNull(s: String) = s.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)
        val bloodGroupNorm = data.bloodGroup
            ?.trim()
            ?.takeIf { it in MedicalData.BLOOD_GROUPS }
        val payload = buildJsonObject {
            put("user_id", JsonPrimitive(uid))
            put("pacemaker", JsonPrimitive(data.pacemaker))
            put("conditions", JsonPrimitiveOrNull(data.conditions) ?: JsonNull)
            put("blood_group", bloodGroupNorm?.let(::JsonPrimitive) ?: JsonNull)
            put("allergies", JsonPrimitiveOrNull(data.allergies) ?: JsonNull)
            put("therapies", JsonPrimitiveOrNull(data.therapies) ?: JsonNull)
            put("notes", JsonPrimitiveOrNull(data.notes) ?: JsonNull)
        }
        Supabase.client.from("medical_data").upsert(payload)
    }

    /** Cancella la riga dell'utente corrente (usato in fase di logout/delete account). */
    suspend fun deleteMine(): Unit = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext
        Supabase.client.from("medical_data").delete {
            filter { eq("user_id", uid) }
        }
    }
}
