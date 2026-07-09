package it.geohelp.mandown

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import it.geohelp.data.supabase.Supabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Invio SMS MAN DOWN via Edge Function [send-mandown-sms] (Twilio).
 * Richiede sessione Auth attiva e rete dati.
 */
class ManDownTwilioRepository {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(body: String, destinations: List<String>): TwilioSmsResponse? {
        if (!Supabase.isConfigured) {
            Log.w(TAG, "Supabase non configurato — skip Twilio")
            return null
        }
        if (Supabase.client.auth.currentUserOrNull() == null) {
            Log.w(TAG, "Utente non loggato — skip Twilio")
            return null
        }
        val dests = destinations.map { it.trim() }.filter { it.startsWith("+") }
        if (body.isBlank() || dests.isEmpty()) return null

        return try {
            val http = Supabase.client.functions.invoke(
                function = FUNCTION_NAME,
                body = TwilioSmsRequest(body = body, destinations = dests),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                },
            )
            decodeResponse(http.bodyAsText())
        } catch (e: RestException) {
            Log.w(TAG, "invoke $FUNCTION_NAME HTTP: ${e.error}")
            decodeResponse(e.description ?: e.message)
        } catch (e: Exception) {
            Log.w(TAG, "invoke $FUNCTION_NAME fallito: ${e.message}")
            null
        }
    }

    private fun decodeResponse(raw: String?): TwilioSmsResponse? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<TwilioSmsResponse>(raw) }
            .onFailure { Log.w(TAG, "parse risposta Twilio: ${it.message}") }
            .getOrNull()
    }

    companion object {
        private const val TAG = "ManDownTwilio"
        private const val FUNCTION_NAME = "send-mandown-sms"
    }
}

@Serializable
private data class TwilioSmsRequest(
    val body: String,
    val destinations: List<String>,
)

@Serializable
data class TwilioSmsResponse(
    val ok: Boolean = false,
    val sent: Int = 0,
    val failed: Int = 0,
    val from: String? = null,
    val results: List<TwilioSmsDestResult> = emptyList(),
)

@Serializable
data class TwilioSmsDestResult(
    val to: String,
    val ok: Boolean,
    val error: String? = null,
)
