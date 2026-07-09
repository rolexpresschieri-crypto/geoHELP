package it.geohelp.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import it.geohelp.R
import java.util.Locale

fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply {
        setLocale(Locale(locale))
    }
    return context.createConfigurationContext(config).resources.getString(resId)
}

@Composable
fun rememberHomePatronEntries(currentLanguage: String): List<PatronEntry> {
    val context = LocalContext.current
    return remember(currentLanguage) {
        listOf(
            PatronEntry(
                R.drawable.logo_comune_sestriere,
                getStringForLocale(context, currentLanguage, R.string.content_desc_patron_comune_sestriere),
                getStringForLocale(context, currentLanguage, R.string.info_patron_footer_sestriere),
                "https://www.comune.sestriere.to.it/it-it/home",
            ),
            PatronEntry(
                R.drawable.logo_consorzio_sestriere,
                getStringForLocale(context, currentLanguage, R.string.content_desc_patron_consorzio),
                getStringForLocale(context, currentLanguage, R.string.home_patron_caption_consorzio),
                "https://www.sestriere.it",
            ),
            PatronEntry(
                R.drawable.logo_comune_cesana,
                getStringForLocale(context, currentLanguage, R.string.content_desc_patron_comune_cesana),
                getStringForLocale(context, currentLanguage, R.string.info_patron_footer_cesana),
                "https://www.comune.cesana.to.it/it-it/home",
            ),
        )
    }
}

@Composable
fun rememberMunicipalPatronEntries(currentLanguage: String): List<PatronEntry> {
    val context = LocalContext.current
    return remember(currentLanguage) {
        listOf(
            PatronEntry(
                R.drawable.logo_comune_sestriere,
                getStringForLocale(context, currentLanguage, R.string.content_desc_patron_comune_sestriere),
                getStringForLocale(context, currentLanguage, R.string.info_patron_footer_sestriere),
                "https://www.comune.sestriere.to.it/it-it/home",
            ),
            PatronEntry(
                R.drawable.logo_comune_cesana,
                getStringForLocale(context, currentLanguage, R.string.content_desc_patron_comune_cesana),
                getStringForLocale(context, currentLanguage, R.string.info_patron_footer_cesana),
                "https://www.comune.cesana.to.it/it-it/home",
            ),
            PatronEntry(
                R.drawable.logo_consorzio_sestriere,
                getStringForLocale(context, currentLanguage, R.string.content_desc_patron_consorzio),
                getStringForLocale(context, currentLanguage, R.string.info_patron_footer_consorzio),
                "https://www.sestriere.it",
            ),
        )
    }
}
