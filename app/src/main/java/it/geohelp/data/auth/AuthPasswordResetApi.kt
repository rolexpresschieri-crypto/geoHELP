package it.geohelp.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import it.geohelp.BuildConfig
import it.geohelp.data.supabase.Supabase
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Invio email recupero password via REST (risposta `{}` da /recover).
 * Evita errori di parsing del client Supabase Auth su risposta vuota.
 */
internal object AuthPasswordResetApi {

    private val http by lazy { HttpClient(OkHttp) }

    suspend fun sendRecoveryEmail(email: String) {
        val redirectCandidates = buildList {
            add(Supabase.AUTH_REDIRECT_URL)
            val webRedirect = BuildConfig.SUPABASE_AUTH_REDIRECT_URL.trim()
            if (webRedirect.isNotEmpty()) add(webRedirect)
        }.distinct()

        var lastError: Throwable? = null
        for (redirect in redirectCandidates) {
            try {
                postRecover(email, redirect)
                return
            } catch (error: PasswordResetHttpException) {
                lastError = error
                if (!AuthErrorMapper.looksLikeRedirectConfigError(error)) throw error
            }
        }

        try {
            postRecover(email, redirectTo = null)
        } catch (error: PasswordResetHttpException) {
            throw error
        }
    }

    private suspend fun postRecover(email: String, redirectTo: String?) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val body = buildJsonObject {
            put("email", email)
            if (!redirectTo.isNullOrBlank()) put("redirect_to", redirectTo)
        }.toString()
        val response = http.post("$base/auth/v1/recover") {
            if (!redirectTo.isNullOrBlank()) {
                parameter("redirect_to", redirectTo)
            }
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw PasswordResetHttpException(
                statusCode = response.status.value,
                body = response.bodyAsText(),
            )
        }
    }
}

/** Errore HTTP da POST /auth/v1/recover (messaggio grezzo per [AuthErrorMapper]). */
class PasswordResetHttpException(
    val statusCode: Int,
    val body: String,
) : Exception("recover $statusCode: $body")
