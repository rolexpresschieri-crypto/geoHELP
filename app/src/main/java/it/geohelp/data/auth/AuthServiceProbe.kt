package it.geohelp.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import it.geohelp.BuildConfig

/** Verifica rapida se il backend Supabase Auth risponde o è limitato (es. 402). */
internal object AuthServiceProbe {

    private val http by lazy { HttpClient(OkHttp) }

    suspend fun authHttpStatus(): Int? {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return null
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return runCatching {
            val response: HttpResponse = http.get("$base/auth/v1/health") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            }
            response.status.value
        }.getOrNull()
    }

    fun isServiceRestricted(status: Int?): Boolean = status == 402
}
