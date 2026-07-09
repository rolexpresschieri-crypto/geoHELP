package it.geohelp.data.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import it.geohelp.data.supabase.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Repository che incapsula le chiamate Supabase Auth.
 *
 * Per ora gestiamo solo email/password. Google/Facebook OAuth e magic link
 * verranno aggiunti in seguito.
 */
class AuthRepository {

    val sessionStatus: StateFlow<SessionStatus>
        get() = if (Supabase.isConfigured) {
            Supabase.client.auth.sessionStatus
        } else {
            notConfiguredSessionStatus
        }

    fun hasActiveSession(): Boolean {
        if (!Supabase.isConfigured) return false
        return Supabase.client.auth.currentSessionOrNull() != null
    }

    /**
     * Effettua login con email/password.
     * Lancia eccezione in caso di credenziali errate o errore rete.
     */
    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        Supabase.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Crea un nuovo account email/password.
     *
     * Se in Supabase è attiva la conferma email, il metodo ritorna senza creare
     * sessione: l'utente deve confermare la mail prima di poter accedere.
     * Se invece la conferma è disattivata, l'utente risulta già loggato.
     */
    suspend fun signUp(email: String, password: String): SignUpOutcome = withContext(Dispatchers.IO) {
        val userInfo = Supabase.client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        when {
            Supabase.client.auth.currentSessionOrNull() != null -> SignUpOutcome.LOGGED_IN
            userInfo?.identities.isNullOrEmpty() -> SignUpOutcome.EMAIL_ALREADY_EXISTS
            else -> SignUpOutcome.CONFIRM_EMAIL
        }
    }

    /** Invia email di reset password (se l'indirizzo è registrato). */
    suspend fun resetPassword(email: String) = withContext(Dispatchers.IO) {
        val trimmed = email.trim()
        require(trimmed.isNotEmpty()) { "Email must not be blank" }
        // REST diretto: supabase-kt può fallire sul body `{}` di /recover nonostante HTTP 200.
        AuthPasswordResetApi.sendRecoveryEmail(trimmed)
    }

    /** Imposta la nuova password dopo aver aperto il link di recupero nell'app. */
    suspend fun updatePassword(newPassword: String) = withContext(Dispatchers.IO) {
        Supabase.client.auth.updateUser {
            password = newPassword
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        Supabase.client.auth.signOut()
    }

    suspend fun authServiceHttpStatus(): Int? = withContext(Dispatchers.IO) {
        AuthServiceProbe.authHttpStatus()
    }

    fun isAuthServiceRestricted(status: Int?): Boolean = AuthServiceProbe.isServiceRestricted(status)

    private companion object {
        val notConfiguredSessionStatus =
            MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated())
    }
}
