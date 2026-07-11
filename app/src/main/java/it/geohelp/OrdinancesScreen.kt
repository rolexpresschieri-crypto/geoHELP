package it.geohelp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.data.supabase.Supabase
import it.geohelp.data.supabase.TouristOrdinance
import it.geohelp.data.supabase.TouristOrdinancesRepository
import it.geohelp.ui.components.getStringForLocale
import it.geohelp.ui.theme.GeoHelpBackground
import java.text.SimpleDateFormat
import java.util.Locale

private val HeaderBlue = Color(0xFF0D47A1)

private fun formatIssuedDate(isoDate: String, language: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate.take(10)) ?: return isoDate
    SimpleDateFormat("dd/MM/yyyy", Locale(language)).format(parsed)
}.getOrDefault(isoDate)

private fun openOrdinancePdf(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.onFailure {
        Toast.makeText(context, "Impossibile aprire il PDF", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun OrdinancesScreen(currentLanguage: String) {
    val context = LocalContext.current
    val repo = remember { TouristOrdinancesRepository() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<TouristOrdinance>>(emptyList()) }

    fun localized(resId: Int): String =
        getStringForLocale(context, currentLanguage, resId)

    LaunchedEffect(Unit) {
        loading = true
        error = null
        if (!Supabase.isConfigured) {
            error = localized(R.string.ordinances_error_not_configured)
            rows = emptyList()
            loading = false
            return@LaunchedEffect
        }
        runCatching { repo.listActive() }
            .onSuccess { rows = it }
            .onFailure { e ->
                error = e.message ?: localized(R.string.ordinances_error_load)
                rows = emptyList()
            }
        loading = false
    }

    val grouped = remember(rows) { rows.groupBy { it.comune } }

    GeoHelpBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = localized(R.string.ordinances_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                text = localized(R.string.ordinances_subtitle),
                fontSize = 13.sp,
                color = Color(0xFF424242),
                modifier = Modifier.padding(bottom = 12.dp),
            )

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(text = error!!, color = Color(0xFFB00020), fontSize = 14.sp)
                }
                grouped.isEmpty() -> {
                    Text(
                        text = localized(R.string.ordinances_empty),
                        color = Color(0xFF616161),
                        fontSize = 14.sp,
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.keys.sortedBy { it.lowercase(Locale(currentLanguage)) }.forEach { comune ->
                            item(key = "header_$comune") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(HeaderBlue, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = comune.uppercase(Locale(currentLanguage)),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                            val itemsForComune = grouped[comune].orEmpty()
                            items(itemsForComune, key = { it.id }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF5F5F5))
                                        .clickable {
                                            openOrdinancePdf(context, repo.publicPdfUrl(row.pdfStoragePath))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatIssuedDate(row.issuedAt, currentLanguage),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF616161),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = row.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1B1B1B),
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = localized(R.string.ordinances_open_pdf),
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                            }
                            item(key = "spacer_$comune") {
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
