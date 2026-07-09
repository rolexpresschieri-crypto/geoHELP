package it.geohelp.privacy

import it.geohelp.data.consents.ConsentKeys

object PrivacyPolicyAssets {
  const val VERSION = ConsentKeys.CURRENT_VERSION

  fun htmlAssetPath(languageCode: String): String =
    if (languageCode == "en") "privacy/privacy_v2_en.html" else "privacy/privacy_v2_it.html"

  fun assetUrl(languageCode: String): String =
    "file:///android_asset/${htmlAssetPath(languageCode)}"
}
