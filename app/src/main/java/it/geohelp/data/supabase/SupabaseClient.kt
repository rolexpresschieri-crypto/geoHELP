package it.geohelp.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import it.geohelp.BuildConfig

/**
 * Singleton client Supabase per geoHELP.
 *
 * URL e anon key arrivano da [BuildConfig], a loro volta letti da local.properties
 * in base al flavor (dev/prod). Se le chiavi non sono configurate, [isConfigured]
 * è false e l'app deve evitare di fare chiamate (mantenere il flusso legacy).
 */
object Supabase {

    /** Deeplink per conferma email / reset password (deve essere in Supabase → Redirect URLs). */
    const val AUTH_SCHEME = "it.geohelp"
    const val AUTH_HOST = "login-callback"
    const val AUTH_REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        require(isConfigured) {
            "Supabase non configurato: imposta SUPABASE_URL_${BuildConfig.FLAVOR.uppercase()} " +
                "e SUPABASE_ANON_KEY_${BuildConfig.FLAVOR.uppercase()} in local.properties."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = AUTH_SCHEME
                host = AUTH_HOST
                flowType = FlowType.IMPLICIT
            }
            install(Postgrest)
            install(Functions)
        }
    }
}
