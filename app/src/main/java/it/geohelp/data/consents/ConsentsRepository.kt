package it.geohelp.data.consents

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import it.geohelp.data.supabase.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository per la tabella public.consents.
 *
 * I consensi sono uno storico immutabile: nuova riga per ogni accettazione
 * o revoca, distinta per (consent_type, version, accepted, accepted_at).
 * Lo stato corrente per (type, version) è dato dalla riga più recente.
 */
class ConsentsRepository {

    private fun currentUserId(): String? =
        Supabase.client.auth.currentUserOrNull()?.id

    /**
     * Salva un nuovo consenso. Crea una riga nuova: non sovrascrive lo storico.
     */
    suspend fun record(
        consentType: String,
        accepted: Boolean,
        version: String = ConsentKeys.CURRENT_VERSION,
    ): Unit = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: error("Utente non autenticato")
        Supabase.client.from("consents").insert(
            Consent(
                userId = uid,
                consentType = consentType,
                version = version,
                accepted = accepted,
            )
        )
    }

    suspend fun loadCurrentSnapshot(
        version: String = ConsentKeys.CURRENT_VERSION,
    ): ConsentSnapshot = withContext(Dispatchers.IO) {
        ConsentSnapshot(
            privacy = isCurrentlyAccepted(ConsentKeys.PRIVACY, version),
            medical = isCurrentlyAccepted(ConsentKeys.MEDICAL_DATA, version),
            manDown = isCurrentlyAccepted(ConsentKeys.MAN_DOWN, version),
        )
    }

    /**
     * Scrive una **nuova riga** in storico solo per i consensi cambiati rispetto allo stato attuale.
     * @return true se almeno un consenso è stato aggiornato.
     */
    suspend fun applySnapshot(
        desired: ConsentSnapshot,
        version: String = ConsentKeys.CURRENT_VERSION,
    ): Boolean = withContext(Dispatchers.IO) {
        val current = loadCurrentSnapshot(version)
        var changed = false
        if (desired.privacy != current.privacy) {
            record(ConsentKeys.PRIVACY, desired.privacy, version)
            changed = true
        }
        if (desired.medical != current.medical) {
            record(ConsentKeys.MEDICAL_DATA, desired.medical, version)
            changed = true
        }
        if (desired.manDown != current.manDown) {
            record(ConsentKeys.MAN_DOWN, desired.manDown, version)
            changed = true
        }
        changed
    }

    /**
     * True se esiste almeno una riga nello storico per questo tipo (qualsiasi versione),
     * utile per non mostrare di nuovo il gate MAN DOWN a chi ha già risposto.
     */
    suspend fun hasAnyConsentForType(
        consentType: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext false
        val rows = Supabase.client.from("consents")
            .select {
                filter {
                    eq("user_id", uid)
                    eq("consent_type", consentType)
                }
                limit(1)
            }
            .decodeList<Consent>()
        rows.isNotEmpty()
    }

    /**
     * Restituisce lo stato corrente per il tipo+versione: true se l'ultima
     * riga è `accepted = true`, false se ultima riga è false o non esiste.
     */
    suspend fun isCurrentlyAccepted(
        consentType: String,
        version: String = ConsentKeys.CURRENT_VERSION,
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUserId() ?: return@withContext false
        val rows = Supabase.client.from("consents")
            .select {
                filter {
                    eq("user_id", uid)
                    eq("consent_type", consentType)
                    eq("version", version)
                }
                order(column = "accepted_at", order = Order.DESCENDING)
                limit(1)
            }
            .decodeList<Consent>()
        rows.firstOrNull()?.accepted == true
    }
}
