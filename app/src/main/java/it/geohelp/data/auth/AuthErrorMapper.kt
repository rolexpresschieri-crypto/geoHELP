package it.geohelp.data.auth

import android.content.Context
import io.github.jan.supabase.exceptions.RestException
import it.geohelp.R

/**
 * Traduce errori Supabase Auth in messaggi brevi per l'utente (no dump tecnico).
 */
object AuthErrorMapper {

    private fun httpStatusCode(throwable: Throwable): Int? {
        (throwable as? PasswordResetHttpException)?.statusCode?.let { return it }
        val fromMessage = Regex("""\b(\d{3})\b""")
            .find(throwable.message.orEmpty())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        if (fromMessage != null) return fromMessage
        return Regex("""\b(\d{3})\b""")
            .find(combinedMessage(throwable))
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private fun looksLikeServiceRestricted(throwable: Throwable, msg: String): Boolean {
        if (httpStatusCode(throwable) == 402) return true
        return ("exceed_" in msg && "quota" in msg) ||
            "service restriction" in msg ||
            "project is paused" in msg ||
            "project paused" in msg ||
            "overdue_payment" in msg
    }

    fun looksLikeRedirectConfigError(throwable: Throwable): Boolean {
        val http = throwable as? PasswordResetHttpException ?: return false
        val msg = combinedMessage(throwable)
        return ("redirect" in msg || "redirect_to" in msg || "redirect url" in msg) &&
            (
                "allowlist" in msg ||
                    "not allowed" in msg ||
                    "bad_oauth_redirect" in msg ||
                    "invalid redirect" in msg ||
                    "redirect_uri" in msg
                )
    }

    private fun combinedMessage(throwable: Throwable): String {
        val rest = throwable as? RestException
        val http = throwable as? PasswordResetHttpException
        return buildString {
            append(throwable.message.orEmpty().lowercase())
            append(' ')
            append(throwable.localizedMessage.orEmpty().lowercase())
            append(' ')
            append(throwable.cause?.message.orEmpty().lowercase())
            append(' ')
            append(rest?.error?.lowercase().orEmpty())
            append(' ')
            append(rest?.description?.lowercase().orEmpty())
            append(' ')
            append(http?.body?.lowercase().orEmpty())
        }
    }

    fun messageResId(throwable: Throwable): Int {
        val msg = combinedMessage(throwable)
        return when {
            looksLikeServiceRestricted(throwable, msg) -> R.string.auth_error_service_restricted

            "invalid login credentials" in msg ||
                "invalid_credentials" in msg ||
                "invalid_grant" in msg -> R.string.auth_error_wrong_credentials

            "email not confirmed" in msg ||
                "email_not_confirmed" in msg -> R.string.auth_error_email_not_confirmed

            "already registered" in msg ||
                "user already registered" in msg ||
                "email address is already registered" in msg ||
                "user_already_exists" in msg -> R.string.auth_error_email_exists

            "rate limit" in msg ||
                "too many requests" in msg ||
                "over_email_send_rate_limit" in msg ||
                "email rate limit" in msg ||
                "429" in msg -> R.string.auth_error_reset_rate_limit

            "captcha" in msg -> R.string.auth_error_reset_captcha

            looksLikeRedirectConfigError(throwable) -> R.string.auth_error_reset_redirect

            "network" in msg ||
                "timeout" in msg ||
                "unable to resolve host" in msg ||
                "connection" in msg -> R.string.auth_error_network

            else -> R.string.auth_error_generic
        }
    }

    fun localizedMessage(context: Context, languageCode: String, throwable: Throwable): String {
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale(languageCode))
        }
        val localized = context.createConfigurationContext(config)
        return localized.resources.getString(messageResId(throwable))
    }

    fun localizedMessageOrServiceRestricted(
        context: Context,
        languageCode: String,
        throwable: Throwable,
        authServiceStatus: Int?,
    ): String {
        if (isServiceRestricted(authServiceStatus)) {
            return localizedMessageForRes(context, languageCode, R.string.auth_error_service_restricted)
        }
        val resId = messageResId(throwable)
        if (resId == R.string.auth_error_generic && isServiceRestricted(authServiceStatus)) {
            return localizedMessageForRes(context, languageCode, R.string.auth_error_service_restricted)
        }
        return localizedMessageForRes(context, languageCode, resId)
    }

    private fun localizedMessageForRes(context: Context, languageCode: String, resId: Int): String {
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale(languageCode))
        }
        return context.createConfigurationContext(config).resources.getString(resId)
    }

    fun isServiceRestricted(authServiceStatus: Int?): Boolean =
        authServiceStatus == 402
}
