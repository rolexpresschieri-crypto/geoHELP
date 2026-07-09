package it.geohelp.data.profile

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import it.geohelp.data.supabase.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository per la tabella public.profiles.
 *
 * RLS lato Supabase garantisce che ogni utente legga/scriva SOLO la propria riga
 * (auth.uid() = id). Il client si limita quindi a fare select/update senza
 * passare esplicitamente il filtro user_id.
 */
class ProfileRepository {

    private fun currentUserId(): String? =
        Supabase.client.auth.currentUserOrNull()?.id

    /** Allinea profiles.email con l'email della sessione Auth corrente. */
    suspend fun syncEmailFromAuth() = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext
        val email = Supabase.client.auth.currentUserOrNull()?.email?.trim()?.ifBlank { null }
            ?: return@withContext
        Supabase.client.from("profiles").update(
            buildJsonObject { put("email", JsonPrimitive(email)) },
        ) {
            filter { eq("id", uid) }
        }
    }

    suspend fun getMine(): Profile? = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext null
        val rows = Supabase.client.from("profiles")
            .select {
                filter { eq("id", uid) }
                limit(1)
            }
            .decodeList<Profile>()
        rows.firstOrNull()
    }

    /**
     * Aggiorna l'anagrafica minima:
     *   - [firstName]: richiesto.
     *   - [birthYear]: opzionale, 1900..annoCorrente.
     *   - [gender]: uno tra `M`, `F`, `X` o null.
     *   - [userPhone]: cellulare utente E.164 (obbligatorio per profilo completo).
     *   - [emergencyContactPhone]: telefono di un familiare, opzionale.
     *
     * Le colonne legacy `last_name` e `birth_date` non esistono più sul DB
     * (rimosse dalla migrazione 0005); l'app non le invia.
     */
    suspend fun saveBasics(
        firstName: String,
        birthYear: Int? = null,
        userPhone: String? = null,
        gender: String? = null,
        emergencyContactPhone: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: error("Utente non autenticato")
        val genderNorm = gender?.trim()?.uppercase()?.takeIf { it in setOf("M", "F", "X") }
        val userE164 = userPhone?.trim()?.ifBlank { null }
        val familyPhone = emergencyContactPhone?.trim()?.ifBlank { null }
        val payload = buildJsonObject {
            put("first_name", JsonPrimitive(firstName.trim()))
            if (birthYear != null) {
                put("birth_year", JsonPrimitive(birthYear))
            } else {
                put("birth_year", JsonNull)
            }
            if (userE164 != null) {
                put("user_phone", JsonPrimitive(userE164))
            } else {
                put("user_phone", JsonNull)
            }
            if (genderNorm != null) {
                put("gender", JsonPrimitive(genderNorm))
            } else {
                put("gender", JsonNull)
            }
            if (familyPhone != null) {
                put("emergency_contact_phone", JsonPrimitive(familyPhone))
            } else {
                put("emergency_contact_phone", JsonNull)
            }
        }
        Supabase.client.from("profiles").update(payload) {
            filter { eq("id", uid) }
        }
    }

    suspend fun isProfileComplete(): Boolean = getMine()?.isComplete == true
}
