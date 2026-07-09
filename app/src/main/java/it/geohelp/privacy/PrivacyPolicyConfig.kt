package it.geohelp.privacy

import it.geohelp.BuildConfig

/**
 * URL informativa privacy: flavor **dev** → link iubenda da [local.properties];
 * flavor **prod** → asset HTML locale finché non si configura anche lì l'URL remoto.
 *
 * In `local.properties` (dev):
 *   IUBENDA_PRIVACY_URL_IT=https://www.iubenda.com/privacy-policy/…/full-legal
 *   IUBENDA_PRIVACY_URL_EN=https://www.iubenda.com/privacy-policy/…/full-legal/en
 * (opzionale: una sola `IUBENDA_PRIVACY_URL` usata per entrambe le lingue)
 */
object PrivacyPolicyConfig {

    private fun sanitizeRemoteUrl(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) return ""
        // Placeholder da template local.properties → non caricare (evita "page not found")
        if (url.contains("NUMERO", ignoreCase = true)) return ""
        if (!url.startsWith("https://www.iubenda.com/privacy-policy/", ignoreCase = true)) return url
        return url
    }

    fun url(languageCode: String): String {
        val itUrl = sanitizeRemoteUrl(BuildConfig.PRIVACY_POLICY_URL_IT)
        val enUrl = sanitizeRemoteUrl(BuildConfig.PRIVACY_POLICY_URL_EN)
        if (languageCode == "en") {
            // URL EN dedicato (da iubenda: Aggiungi lingua → English → link integrazione)
            if (enUrl.isNotEmpty() && !enUrl.equals(itUrl, ignoreCase = true)) {
                return enUrl
            }
            // Stesso link IT o EN non configurato: iubenda non ha traduzione → HTML locale EN
            return PrivacyPolicyAssets.assetUrl("en")
        }
        if (itUrl.isNotEmpty()) return itUrl
        return PrivacyPolicyAssets.assetUrl("it")
    }

    /** Carica iubenda con header lingua (solo se URL remoto). */
    fun requestHeaders(languageCode: String): Map<String, String> {
        if (!isRemoteUrl(languageCode)) return emptyMap()
        val acceptLang = if (languageCode == "en") "en-US,en;q=0.9,it;q=0.5" else "it-IT,it;q=0.9,en;q=0.5"
        return mapOf("Accept-Language" to acceptLang)
    }

    fun isRemoteUrl(languageCode: String): Boolean =
        url(languageCode).startsWith("https://", ignoreCase = true)

    /** Dev senza URL iubenda valido. */
    fun isDevMissingRemoteConfig(): Boolean =
        BuildConfig.FLAVOR == "dev" && sanitizeRemoteUrl(BuildConfig.PRIVACY_POLICY_URL_IT).isEmpty()
}
