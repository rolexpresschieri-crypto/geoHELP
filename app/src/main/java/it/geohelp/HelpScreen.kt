package it.geohelp
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.provider.Telephony
import android.widget.Toast
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.geohelp.mandown.EmergencySmsText
import it.geohelp.mandown.ManDownForegroundService
import android.Manifest
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import java.net.URL
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.geohelp.data.sms.EmergencyTypeCatalog
import it.geohelp.data.sms.SmsEventKeys
import it.geohelp.data.sms.SmsEventsRepository
import it.geohelp.data.supabase.SosRecipientsRepository
import it.geohelp.location.LocationPermissions
import it.geohelp.trek.TrekTrailCatalog
import it.geohelp.trek.TrekTrailNavigation
import it.geohelp.trek.TrekTrailRow
import it.geohelp.trek.TrekTrailSelectionStore
import it.geohelp.ui.components.PatronEntry
import it.geohelp.ui.components.PatronageFooter
import it.geohelp.ui.components.ProminentDisclosureDialog
import it.geohelp.ui.components.ScrollDownHint
import it.geohelp.ui.theme.GeoHelpBackground
import androidx.compose.runtime.rememberCoroutineScope


// Fallback numeri SOS se Supabase non risponde: foglio GeoHELP_Config (CSV pubblico).
private const val GOOGLE_SHEET_CSV_URL = "https://docs.google.com/spreadsheets/d/17CRujo9H1Gq8TTvk64fQjE5whLU5G2jRISV2bKKMQrs/export?format=csv&gid=0"

private fun openTrailPdf(context: Context, row: TrekTrailRow) {
    when {
        !row.pdfAsset.isNullOrBlank() -> {
            runCatching {
                val assetPath = row.pdfAsset
                val fileName = assetPath.substringAfterLast('/')
                val cacheFile = File(context.cacheDir, "trek_pdf_$fileName")
                context.assets.open(assetPath).use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(context, "Impossibile aprire il PDF", Toast.LENGTH_SHORT).show()
            }
        }
        !row.pdfUrl.isNullOrBlank() -> {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(row.pdfUrl)))
        }
    }
}

private fun formatAirDistanceMeters(meters: Float): String =
    when {
        meters.isNaN() || !meters.isFinite() -> "---"
        meters >= 1000f -> String.format(Locale.US, "%.1f km", meters / 1000f)
        else -> String.format(Locale.US, "%.0f m", meters)
    }

@Composable
private fun TrekTrailsTable(
    language: String,
    userLat: Double?,
    userLon: Double?,
    rows: List<TrekTrailRow>,
    selectedTrailKey: String?,
    onOpenPdf: (TrekTrailRow) -> Unit,
    onOpenTrace: (TrekTrailRow, Float?) -> Unit,
) {
    val headerComune = stringResourceForLocale(language, R.string.trek_table_header_comune)
    val headerSentiero = stringResourceForLocale(language, R.string.trek_table_header_sentiero)
    val headerPdf = stringResourceForLocale(language, R.string.trek_table_header_pdf)
    val headerTrace = stringResourceForLocale(language, R.string.trek_table_header_trace)
    val traceLabel = stringResourceForLocale(language, R.string.trek_table_trace_btn)
    val pdfDesc = stringResourceForLocale(language, R.string.trek_table_pdf_desc)

    val grouped = remember(rows) { rows.groupBy { it.comune } }
    val wComune = 0.18f
    val wSentiero = 0.38f
    val wPdf = 0.12f
    val wTrack = 0.32f
    val mapOpenBlue = Color(0xFF0D47A1)

    fun airDistanceMeters(row: TrekTrailRow): Float? {
        val lat = userLat ?: return null
        val lon = userLon ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(lat, lon, row.startLat, row.startLon, out)
        return out[0]
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE6E9EF))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(headerComune, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.weight(wComune))
            Text(headerSentiero, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.weight(wSentiero))
            Text(headerPdf, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.weight(wPdf), textAlign = TextAlign.Center)
            Text(headerTrace, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.weight(wTrack), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
        Divider(color = Color(0xFFBDBDBD))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (comune, items) ->
                item(key = "comune_$comune") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(mapOpenBlue)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = comune.uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Divider(color = Color(0xFFBDBDBD))
                }

                items.forEachIndexed { idx, row ->
                    item(key = "trail_${comune}_$idx") {
                        val dist = airDistanceMeters(row)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Colonna comune vuota: comune già nel raggruppamento
                            Spacer(Modifier.weight(wComune))

                            Column(modifier = Modifier.weight(wSentiero)) {
                                Text(
                                    text = "${row.sentieroNome} ${row.sentieroNumero}".trim(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = row.breveDescrizione,
                                    fontSize = 11.sp,
                                    color = Color(0xFF424242),
                                    lineHeight = 14.sp,
                                    maxLines = 3,
                                )
                            }

                            Box(modifier = Modifier.weight(wPdf), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { onOpenPdf(row) },
                                    enabled = row.hasPdf(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = pdfDesc,
                                        tint = if (row.hasPdf()) Color(0xFFD32F2F) else Color(0xFFBDBDBD),
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(wTrack), contentAlignment = Alignment.Center) {
                                val isTrackSelected = selectedTrailKey == row.trackKey()
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isTrackSelected) Color(0xFFFFEB3B) else Color.Transparent,
                                ) {
                                    Text(
                                        text = traceLabel,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = Color(0xFF1B1B1B),
                                        modifier = Modifier
                                            .clickable { onOpenTrace(row, dist) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        if (dist != null) {
                            Text(
                                text = stringResourceForLocale(language, R.string.trek_table_air_distance).format(formatAirDistanceMeters(dist)),
                                fontSize = 10.sp,
                                color = Color(0xFF616161),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                            )
                        }
                        Divider(color = Color(0xFFE0E0E0))
                    }
                }
            }
        }
    }
}

/** (primari max 2, backup opzionale) */
private suspend fun loadSosRecipientsFromGoogleSheet(): Pair<List<String>, String> {
    if (GOOGLE_SHEET_CSV_URL.contains("TUO_SHEET_ID")) return listOf("112") to ""
    return try {
        val text = withContext(Dispatchers.IO) {
            URL(GOOGLE_SHEET_CSV_URL).openStream().bufferedReader().use { it.readText() }
        }
        val lines = text.split("\n", "\r\n").map { it.trim() }.filter { it.isNotBlank() }
        fun firstColumn(line: String): String =
            line.split(",").map { it.trim().trim('"') }.firstOrNull().orEmpty()

        val primaries = mutableListOf<String>()
        var backupFromLabels: String? = null
        for (line in lines) {
            val parts = line.split(",").map { it.trim().trim('"') }
            val colA = parts.getOrNull(0).orEmpty()
            val colB = parts.getOrNull(1)?.lowercase(Locale.getDefault()).orEmpty()
            if (!colA.any(Char::isDigit)) continue
            when {
                colB.contains("backup") -> backupFromLabels = colA
                colB.contains("primary") -> primaries.add(colA)
            }
        }

        when {
            primaries.isNotEmpty() ->
                primaries.distinct().take(2) to backupFromLabels?.trim().orEmpty()
            lines.size == 1 && lines[0].contains(",") -> {
                val parts = lines[0].split(",").map { it.trim().trim('"') }
                val p0 = parts.getOrNull(0)?.takeIf { it.any(Char::isDigit) } ?: "112"
                val p1 = parts.getOrNull(1)?.takeIf { it.any(Char::isDigit) }.orEmpty()
                listOf(p0) to p1
            }
            lines.size >= 2 -> {
                val skip = if (lines[0].any(Char::isDigit)) 0 else 1
                val p0 = firstColumn(lines.getOrNull(skip) ?: "").takeIf { it.any(Char::isDigit) } ?: "112"
                val p1 = firstColumn(lines.getOrNull(skip + 1) ?: "").takeIf { it.any(Char::isDigit) }.orEmpty()
                listOf(p0) to p1
            }
            else -> listOf("112") to ""
        }
    } catch (_: Exception) {
        listOf("112") to ""
    }
}

/** Supabase `sos_recipients` (fonte ufficiale); fallback sul foglio Google. */
private suspend fun loadSosRecipients(): Pair<List<String>, String> {
    try {
        val rows = SosRecipientsRepository().listActive()
        val primaries = rows
            .filter { it.isPrimary }
            .map { it.phone.trim() }
            .filter { it.any(Char::isDigit) }
            .distinct()
            .take(2)
        val backup = rows
            .firstOrNull { it.isBackup }
            ?.phone
            ?.trim()
            .orEmpty()
        if (primaries.isNotEmpty()) {
            return primaries to backup
        }
    } catch (_: Exception) {
        // rete / RLS / non loggato → fallback
    }
    return loadSosRecipientsFromGoogleSheet()
}

private fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(java.util.Locale(locale)) }
    return context.createConfigurationContext(config).resources.getString(resId)
}

@Composable
private fun stringResourceForLocale(locale: String, resId: Int): String {
    val context = LocalContext.current
    return remember(locale, resId) {
        getStringForLocale(context, locale, resId)
    }
}

@Composable
private fun LocationValueRow(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
            append(label)
            append(": ")
            pop()
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(value)
            pop()
        },
        fontSize = 16.sp
    )
}

@Composable
fun HelpScreen(
    onSendPrimary: (String, String, String) -> Unit,
    onSendBackup: (String, String, String) -> Unit,
    onLanguageSelected: (String) -> Unit = {},
    currentLanguage: String = "it",
    onLogout: () -> Unit = {},
    onBack: () -> Unit = {},
    userDisplayName: String = "",
    userBirthYear: Int? = null,
    userPhoneE164: String = "",
    onOpenMedical: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onManageConsents: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    hasMedicalConsent: Boolean = false,
    hasManDownConsent: Boolean = false,
    medicalSmsSummary: String = "",
    /** Tap sul logo HELP → schermata admin (gestita da MainActivity). */
    onAdminLogoTap: () -> Unit = {},
    /** Incrementare dopo modifica destinatari SOS in admin. */
    sosRecipientsExternalReloadKey: Int = 0,
    embeddedInShell: Boolean = false,
    shellTab: Int = 0,
    onShellTabChange: (Int) -> Unit = {},
    showPatronageFooter: Boolean = true,
    showLanguageSelector: Boolean = true,
    showBottomNavigation: Boolean = true,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResourceForLocale(currentLanguage, R.string.logout_dialog_title)) },
            text = { Text(stringResourceForLocale(currentLanguage, R.string.logout_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.logout_dialog_confirm),
                        color = Color(0xFFB71C1C),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResourceForLocale(currentLanguage, R.string.logout_dialog_cancel))
                }
            }
        )
    }
    val inputSurfaceColor = Color(0xFFF2F4F7)
    val inputBorderColor = Color(0xFFD4DAE2)
    var notes by remember { mutableStateOf("") }
    var emergencyCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val smsEventsRepository = remember { SmsEventsRepository() }
    var selectedTab by remember { mutableStateOf(0) }
    val activeTab = if (embeddedInShell) shellTab else selectedTab
    var latitude by remember { mutableStateOf<String?>(null) }
    var longitude by remember { mutableStateOf<String?>(null) }
    var altitude by remember { mutableStateOf<String?>(null) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    /** Fino a 2 numeri primari (stesso messaggio, destinatari multipli nell’intent SMS). */
    var primaryRecipients by remember { mutableStateOf(listOf("112")) }
    /** Vuoto = nessun backup configurato (pulsante backup disattivato). */
    var backupNumber by remember { mutableStateOf("112") }
    var showDisclaimer by remember { mutableStateOf(false) }
    var pendingSmsPrimary by remember { mutableStateOf(false) } // true = primario, false = backup
    val context = LocalContext.current
    val profileReadyForSos = userDisplayName.isNotBlank() && userPhoneE164.isNotBlank()

    val prefs = remember {
        context.getSharedPreferences(ManDownForegroundService.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var manDownArmed by remember {
        mutableStateOf(prefs.getBoolean(ManDownForegroundService.PREF_MAN_DOWN_ARMED, false))
    }
    var manDownCountdownSec by remember { mutableStateOf<Int?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var sosRecipientsReloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(sosRecipientsReloadKey, sosRecipientsExternalReloadKey) {
        val (primaries, backup) = loadSosRecipients()
        primaryRecipients = primaries
        backupNumber = backup
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                manDownArmed = prefs.getBoolean(ManDownForegroundService.PREF_MAN_DOWN_ARMED, false)
                sosRecipientsReloadKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun cancelManDownCountdownFromUi() {
        context.startService(
            Intent(context, ManDownForegroundService::class.java)
                .setAction(ManDownForegroundService.ACTION_CANCEL_COUNTDOWN)
        )
        manDownCountdownSec = null
    }

    fun buildManDownSmsDestinationsCsv(): String {
        val dest = mutableListOf<String>()
        primaryRecipients
            .map { it.trim() }
            .filter { it.any(Char::isDigit) }
            .distinct()
            .take(2)
            .forEach { dest.add(it) }
        val backup = backupNumber.trim()
        if (backup.any(Char::isDigit) && backup !in dest) {
            dest.add(backup)
        }
        return (if (dest.isEmpty()) listOf("112") else dest).take(3).joinToString(",")
    }

    fun armManDownService() {
        val csv = buildManDownSmsDestinationsCsv()
        ContextCompat.startForegroundService(
            context,
            ManDownForegroundService.buildArmIntent(
                context = context,
                lang = currentLanguage,
                displayName = userDisplayName,
                birthYear = userBirthYear,
                userPhoneE164 = userPhoneE164,
                hasMedicalConsent = hasMedicalConsent,
                medicalSummary = medicalSmsSummary,
                primaryNumbersCsv = csv,
            )
        )
        manDownArmed = true
    }

    val postNotifGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    var showNotifDisclosure by remember { mutableStateOf(false) }
    var showLocationFineDisclosure by remember { mutableStateOf(false) }
    var showLocationBackgroundDisclosure by remember { mutableStateOf(false) }
    var pendingArmAfterBackground by remember { mutableStateOf(false) }
    var locationGrantContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun finishManDownEnableFlow() {
        if (!hasManDownConsent || !profileReadyForSos) return
        if (Build.VERSION.SDK_INT >= 33 && !postNotifGranted) {
            showNotifDisclosure = true
            return
        }
        if (!LocationPermissions.hasFineLocation(context)) {
            locationGrantContinuation = { finishManDownEnableFlow() }
            showLocationFineDisclosure = true
            return
        }
        if (LocationPermissions.shouldRequestBackgroundLocation(context)) {
            pendingArmAfterBackground = true
            showLocationBackgroundDisclosure = true
            return
        }
        armManDownService()
    }

    fun disarmManDownFromUi() {
        context.startService(
            Intent(context, ManDownForegroundService::class.java)
                .setAction(ManDownForegroundService.ACTION_DISARM),
        )
        manDownArmed = false
    }

    fun requestLocationWithDisclosure(onGranted: () -> Unit) {
        if (LocationPermissions.hasFineLocation(context)) {
            onGranted()
            return
        }
        locationGrantContinuation = onGranted
        showLocationFineDisclosure = true
    }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val ok = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (ok) {
            val cont = locationGrantContinuation
            locationGrantContinuation = null
            cont?.invoke()
        }
    }

    val postNotifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            finishManDownEnableFlow()
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val msgRes = if (granted) {
            R.string.location_background_toast_granted
        } else {
            R.string.location_background_toast_denied
        }
        Toast.makeText(
            context,
            getStringForLocale(context, currentLanguage, msgRes),
            Toast.LENGTH_LONG,
        ).show()
        if (pendingArmAfterBackground) {
            pendingArmAfterBackground = false
            armManDownService()
        }
    }

    LaunchedEffect(hasManDownConsent) {
        if (!hasManDownConsent && manDownArmed) {
            disarmManDownFromUi()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ManDownForegroundService.ACTION_COUNTDOWN_TICK -> {
                        manDownCountdownSec = intent.getIntExtra(ManDownForegroundService.EXTRA_COUNTDOWN_SEC, 0)
                    }
                    ManDownForegroundService.ACTION_COUNTDOWN_CANCELLED -> {
                        manDownCountdownSec = null
                    }
                    ManDownForegroundService.ACTION_SMS_SENT -> {
                        manDownCountdownSec = null
                        Toast.makeText(
                            context,
                            getStringForLocale(context, currentLanguage, R.string.mandown_toast_sms_sent),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ManDownForegroundService.ACTION_SMS_OPEN_MESSAGES -> {
                        manDownCountdownSec = null
                        Toast.makeText(
                            context,
                            getStringForLocale(context, currentLanguage, R.string.mandown_toast_open_messages),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    ManDownForegroundService.ACTION_SMS_FAILED -> {
                        Toast.makeText(
                            context,
                            getStringForLocale(context, currentLanguage, R.string.mandown_toast_sms_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ManDownForegroundService.ACTION_COUNTDOWN_TICK)
            addAction(ManDownForegroundService.ACTION_COUNTDOWN_CANCELLED)
            addAction(ManDownForegroundService.ACTION_SMS_SENT)
            addAction(ManDownForegroundService.ACTION_SMS_OPEN_MESSAGES)
            addAction(ManDownForegroundService.ACTION_SMS_FAILED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    BackHandler(enabled = manDownCountdownSec != null) {
        cancelManDownCountdownFromUi()
    }

    fun updateLocationState(location: Location?) {
        if (location != null) {
            latitude = String.format(Locale.US, "%.5f", location.latitude)
            longitude = String.format(Locale.US, "%.5f", location.longitude)
            altitude = if (location.hasAltitude()) {
                String.format(Locale.US, "%.0f m", location.altitude)
            } else {
                "---"
            }
            accuracy = if (location.hasAccuracy()) location.accuracy else null
        } else {
            latitude = null
            longitude = null
            altitude = null
            accuracy = null
        }
    }

    fun accuracyColor(value: Float?): Color = when {
        value == null -> Color(0xFF9E9E9E)
        value <= 20f -> Color(0xFF2E7D32)
        value <= 50f -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }


    fun sendSms(primary: Boolean) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        var lat = "---"
        var lon = "---"
        var alt = "---"
        if (hasPermission) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val location = providers
                .mapNotNull { locationManager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
            if (location != null) {
                lat = String.format(Locale.US, "%.5f", location.latitude)
                lon = String.format(Locale.US, "%.5f", location.longitude)
                if (location.hasAltitude()) {
                    alt = String.format(Locale.US, "%.0f m", location.altitude)
                }
            }
        }
        val message = EmergencySmsText.buildHelpSmsBody(
            context = context,
            languageCode = currentLanguage,
            displayName = userDisplayName,
            birthYear = userBirthYear,
            userPhoneE164 = userPhoneE164,
            emergencyCode = emergencyCode.orEmpty(),
            notes = notes,
            latitude = lat,
            longitude = lon,
            altitude = alt,
            hasMedicalConsent = hasMedicalConsent,
            medicalSmsSummary = medicalSmsSummary,
        )
        val number = if (primary) {
            primaryRecipients.filter { it.any(Char::isDigit) }.distinct().take(2)
                .joinToString(",")
                .ifBlank { "112" }
        } else {
            backupNumber.trim()
        }
        if (!primary && number.isBlank()) {
            Toast.makeText(
                context,
                getStringForLocale(context, currentLanguage, R.string.toast_no_backup_configured),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val smsto = number.replace(',', ';')
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$smsto")).apply {
            putExtra("sms_body", message)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        Telephony.Sms.getDefaultSmsPackage(context)?.let { intent.setPackage(it) }
        if (intent.resolveActivity(context.packageManager) == null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse("sms:$smsto")
        }
        context.startActivity(intent)
        val destCount = if (primary) {
            primaryRecipients.filter { it.any(Char::isDigit) }.distinct().size.coerceAtLeast(1)
        } else {
            1
        }
        val code = emergencyCode
        if (code != null && EmergencyTypeCatalog.isValidManualCode(code)) {
            scope.launch {
                smsEventsRepository.record(
                    channel = SmsEventKeys.CHANNEL_MANUAL,
                    messageKind = SmsEventKeys.KIND_PREPARED,
                    emergencyType = code,
                    outcome = SmsEventKeys.OUTCOME_OK,
                    destCount = destCount,
                    recipientRole = if (primary) SmsEventKeys.ROLE_PRIMARY else SmsEventKeys.ROLE_BACKUP,
                )
            }
        }
    }


    val emergencyOptions = listOf(
        EmergencyTypeCatalog.ORDERED[0].code to Icons.Default.Warning,
        EmergencyTypeCatalog.ORDERED[1].code to Icons.Default.Favorite,
        EmergencyTypeCatalog.ORDERED[2].code to Icons.Default.DirectionsCar,
        EmergencyTypeCatalog.ORDERED[3].code to Icons.Default.Search,
        EmergencyTypeCatalog.ORDERED[4].code to Icons.Default.Cloud,
        EmergencyTypeCatalog.ORDERED[5].code to Icons.Default.MoreHoriz,
    )
    val patronEntries = remember(currentLanguage) {
        if (!showPatronageFooter) {
            emptyList()
        } else {
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
    fun openPatronUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    GeoHelpBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> {
                        val scrollState = rememberScrollState()
                        val showScrollHint by remember {
                            derivedStateOf {
                                scrollState.maxValue > 0 &&
                                    scrollState.value < scrollState.maxValue - 8
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                // Logo a sinistra, bandiere IT sopra e EN sotto (EN più grande, più vicina a IT). Altezza fissa.
                val flagSizeIt = 40.dp
                val flagSizeEn = 40.dp
                val flagSpacing = 4.dp
                val headerHeight = 192.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.logo_geohelp),
                            contentDescription = "GeoHELP",
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(top = 4.dp)
                                .clickable { onAdminLogoTap() },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResourceForLocale(currentLanguage, R.string.help_title),
                            color = Color(0xFFD50000),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        )
                    }
                    Column(
                        modifier = Modifier.padding(top = if (embeddedInShell) 8.dp else 36.dp),
                        verticalArrangement = Arrangement.spacedBy(flagSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showLanguageSelector) {
                        Box(
                            modifier = Modifier
                                .size(flagSizeIt)
                                .clickable { onLanguageSelected("it") }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.flag_it),
                                contentDescription = "Italiano",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(flagSizeEn)
                                .clickable { onLanguageSelected("en") }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.flag_en),
                                contentDescription = "English",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (embeddedInShell) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResourceForLocale(currentLanguage, R.string.back),
                                    tint = Color(0xFF1B1B1B),
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.heightIn(min = 44.dp),
                                border = BorderStroke(1.5.dp, Color(0xFFB71C1C)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFB71C1C)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResourceForLocale(currentLanguage, R.string.header_logout),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.request_help),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            notes = ""
                            emergencyCode = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D4D10),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(stringResourceForLocale(currentLanguage, R.string.cancel_fields), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Nominativo: preso dal profilo registrato, mostrato in sola lettura.
                if (userDisplayName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = inputSurfaceColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF424242)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = userDisplayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1B1B1B),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onEditProfile,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.profile_edit_btn),
                                color = Color(0xFF1565C0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    OutlinedButton(
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, inputBorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = inputSurfaceColor,
                            contentColor = Color(0xFF1565C0)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResourceForLocale(currentLanguage, R.string.profile_edit_btn),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = stringResourceForLocale(currentLanguage, R.string.emergency_type_label),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
                // Griglia 2 x 3
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    emergencyOptions.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (code, icon) ->
                                val labelResId = EmergencyTypeCatalog.ORDERED
                                    .first { it.code == code }
                                    .labelResId
                                val selected = emergencyCode == code
                                AssistChip(
                                    onClick = { emergencyCode = code },
                                    label = {
                                        Text(
                                            text = stringResourceForLocale(currentLanguage, labelResId),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (selected) Color(0xFF1976D2) else Color.White,
                                        labelColor = if (selected) Color.White else Color.Black,
                                        leadingIconContentColor = if (selected) Color.White else Color(0xFF1976D2)
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Note
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.notes_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = inputSurfaceColor,
                        unfocusedContainerColor = inputSurfaceColor,
                        disabledContainerColor = inputSurfaceColor,
                        focusedBorderColor = inputBorderColor,
                        unfocusedBorderColor = inputBorderColor
                    )
                )
                Spacer(Modifier.height(28.dp))
                if (hasMedicalConsent) {
                    OutlinedButton(
                        onClick = onOpenMedical,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.5.dp, Color(0xFFB71C1C)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFB71C1C),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResourceForLocale(currentLanguage, R.string.medical_open_btn),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (hasManDownConsent) {
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.mandown_section_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.mandown_section_hint),
                        fontSize = 12.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(inputSurfaceColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFB71C1C),
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.mandown_always_on_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1B1B1B)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.mandown_always_on_detail),
                                fontSize = 12.sp,
                                color = Color(0xFF424242),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        !profileReadyForSos -> Text(
                            text = stringResourceForLocale(currentLanguage, R.string.fill_required_profile),
                            fontSize = 12.sp,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        manDownArmed -> Text(
                            text = stringResourceForLocale(currentLanguage, R.string.mandown_status_active),
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        else -> Text(
                            text = stringResourceForLocale(currentLanguage, R.string.mandown_status_needs_permissions),
                            fontSize = 12.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (manDownArmed) {
                        OutlinedButton(
                            onClick = { disarmManDownFromUi() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF424242)),
                        ) {
                            Text(
                                stringResourceForLocale(currentLanguage, R.string.mandown_disable_btn),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Button(
                            onClick = { finishManDownEnableFlow() },
                            enabled = profileReadyForSos,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        ) {
                            Text(
                                stringResourceForLocale(currentLanguage, R.string.mandown_enable_btn),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                // Pulsanti SMS: prepara testo come [GeoHelp] e apre app Messaggi per invio manuale
                val fillRequiredEmergency = stringResourceForLocale(currentLanguage, R.string.fill_required)
                val fillRequiredProfile = stringResourceForLocale(currentLanguage, R.string.fill_required_profile)
                Button(
                    onClick = {
                        if (!profileReadyForSos) {
                            Toast.makeText(context, fillRequiredProfile, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (emergencyCode.isNullOrBlank()) {
                            Toast.makeText(context, fillRequiredEmergency, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        pendingSmsPrimary = true
                        requestLocationWithDisclosure { showDisclaimer = true }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD50000)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResourceForLocale(currentLanguage, R.string.send_sms_primary))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (!profileReadyForSos) {
                            Toast.makeText(context, fillRequiredProfile, Toast.LENGTH_LONG).show()
                            return@OutlinedButton
                        }
                        if (emergencyCode.isNullOrBlank()) {
                            Toast.makeText(context, fillRequiredEmergency, Toast.LENGTH_LONG).show()
                            return@OutlinedButton
                        }
                        pendingSmsPrimary = false
                        requestLocationWithDisclosure { showDisclaimer = true }
                    },
                    enabled = backupNumber.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, inputBorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = inputSurfaceColor,
                        contentColor = Color(0xFF37474F),
                        disabledContainerColor = inputSurfaceColor,
                        disabledContentColor = Color(0xFF9E9E9E)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResourceForLocale(currentLanguage, R.string.send_sms_backup))
                }
                        }
                            if (showScrollHint) {
                                ScrollDownHint(
                                    message = stringResourceForLocale(
                                        currentLanguage,
                                        R.string.help_scroll_hint,
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                    2 -> {
                        val hasFineLocation = LocationPermissions.hasFineLocation(context)
                        val positionScrollState = rememberScrollState()
                        DisposableEffect(activeTab, hasFineLocation) {
                            if (activeTab != 2 || !hasFineLocation) {
                                onDispose { }
                            } else {
                                    val locationManager =
                                        context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                                    val providers = listOf(
                                        LocationManager.GPS_PROVIDER,
                                        LocationManager.NETWORK_PROVIDER
                                    )

                                    val listener = LocationListener { location ->
                                        updateLocationState(location)
                                    }

                                    val lastKnown = providers
                                        .mapNotNull { provider ->
                                            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                                        }
                                        .maxByOrNull { it.time }
                                    updateLocationState(lastKnown)

                                    providers.forEach { provider ->
                                        runCatching {
                                            locationManager.requestLocationUpdates(
                                                provider,
                                                2000L,
                                                1f,
                                                listener
                                            )
                                        }
                                    }

                                    onDispose {
                                        runCatching { locationManager.removeUpdates(listener) }
                                    }
                            }
                        }

                        Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(positionScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!hasFineLocation) {
                            Spacer(Modifier.height(24.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFFFF3E0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResourceForLocale(
                                            currentLanguage,
                                            R.string.position_gps_needed_hint,
                                        ),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = { requestLocationWithDisclosure { } },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFB71C1C),
                                            contentColor = Color.White,
                                        ),
                                        shape = RoundedCornerShape(999.dp),
                                    ) {
                                        Text(
                                            stringResourceForLocale(
                                                currentLanguage,
                                                R.string.position_enable_gps_btn,
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.logo_geohelp),
                            contentDescription = "GeoHELP",
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(top = 12.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        val accuracyText = accuracy?.let { String.format(Locale.US, "%.0f m", it) } ?: "---"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(accuracyColor(accuracy), CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Acc: $accuracyText",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = stringResourceForLocale(currentLanguage, R.string.your_position),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))
                        val latText = latitude ?: "---"
                        val lonText = longitude ?: "---"
                        val altText = altitude ?: "---"
                        val latDouble = latitude?.toDoubleOrNull()
                        val lonDouble = longitude?.toDoubleOrNull()
                        val trekRows = remember { TrekTrailCatalog.trails() }
                        var selectedTrailKey by remember {
                            mutableStateOf(TrekTrailSelectionStore.loadActiveTrailKey(context))
                        }
                        LaunchedEffect(selectedTrailKey) {
                            TrekTrailSelectionStore.saveActiveTrailKey(context, selectedTrailKey)
                        }
                        val activeTrail = remember(selectedTrailKey, trekRows) {
                            TrekTrailSelectionStore.findTrail(trekRows, selectedTrailKey)
                        }
                        fun launchMap(trail: TrekTrailRow? = activeTrail) {
                            val intent = TrekTrailNavigation.createMapIntent(
                                context = context,
                                latText = latText,
                                lonText = lonText,
                                language = currentLanguage,
                                userDisplayName = userDisplayName,
                                trail = trail,
                            )
                            context.startActivity(intent)
                        }
                        // Coordinate sempre visibili
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFE0E3EB),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LocationValueRow(
                                    label = stringResourceForLocale(currentLanguage, R.string.latitude),
                                    value = latText
                                )
                                LocationValueRow(
                                    label = stringResourceForLocale(currentLanguage, R.string.longitude),
                                    value = lonText
                                )
                                LocationValueRow(
                                    label = stringResourceForLocale(currentLanguage, R.string.altitude),
                                    value = altText
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = {
                                    if (latitude != null && longitude != null) {
                                        launchMap()
                                    }
                                },
                                modifier = Modifier.height(50.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0D47A1),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = stringResourceForLocale(currentLanguage, R.string.content_desc_open_map)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResourceForLocale(currentLanguage, R.string.open_on_map))
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0D4D10),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.trek_trails_section_title),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF5F5F5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(330.dp)
                                .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(16.dp)),
                        ) {
                            TrekTrailsTable(
                                language = currentLanguage,
                                userLat = latDouble,
                                userLon = lonDouble,
                                rows = trekRows,
                                selectedTrailKey = selectedTrailKey,
                                onOpenPdf = { row -> openTrailPdf(context, row) },
                                onOpenTrace = { row, _ ->
                                    val key = row.trackKey()
                                    if (selectedTrailKey == key) {
                                        selectedTrailKey = null
                                    } else {
                                        selectedTrailKey = key
                                        launchMap(row)
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    }
                    3 -> {
                        val infoScrollState = rememberScrollState()
                        val urlVialattea = "https://www.vialattea.it/"
                        val urlMeteo = "https://www.meteo.it"
                        val urlWebcamVialattea = "https://www.webcamvialattea.it/"
                        val urlDaedove = "https://www.daedove.it/"
                        val infoMeteoTileMaxHeightDp = 36.dp
                        fun openUrl(url: String) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(infoScrollState)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.logo_geohelp),
                                contentDescription = "GeoHELP",
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(80.dp)
                                    .padding(top = 16.dp)
                            )
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.tab_info_title),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.info_partners_intro),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF424242),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { openUrl(urlVialattea) },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.logo_vialattea),
                                        contentDescription = "VIALATTEA",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 72.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { openUrl(urlMeteo) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.logo_meteo_it),
                                            contentDescription = "meteo.it",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = infoMeteoTileMaxHeightDp)
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { openUrl(urlWebcamVialattea) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.logo_webcam_vialattea),
                                            contentDescription = stringResourceForLocale(
                                                currentLanguage,
                                                R.string.content_desc_webcam_vialattea
                                            ),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 42.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = stringResourceForLocale(
                                                currentLanguage,
                                                R.string.info_webcam_tile_title
                                            ),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 12.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                            Text(
                                text = stringResourceForLocale(
                                    currentLanguage,
                                    R.string.info_webcam_site_notice
                                ),
                                fontSize = 10.sp,
                                color = Color(0xFF616161),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                lineHeight = 14.sp
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { openUrl(urlDaedove) },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.logo_daedove),
                                        contentDescription = "DAEdove",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 72.dp)
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF5F5F5)
                            ) {
                                Text(
                                    text = stringResourceForLocale(currentLanguage, R.string.info_official_sources_block),
                                    fontSize = 10.sp,
                                    color = Color(0xFF616161),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    lineHeight = 14.sp
                                )
                            }
                            Text(
                                text = stringResourceForLocale(currentLanguage, R.string.info_government_disclaimer),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color(0xFF424242),
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                                lineHeight = 15.sp
                            )
                            Spacer(Modifier.height(88.dp))
                        }
                    }
                    else -> {
                        Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResourceForLocale(currentLanguage, R.string.coming_soon), fontSize = 16.sp)
                    }
                    }
                }
            }
            if (showNotifDisclosure) {
                ProminentDisclosureDialog(
                    title = stringResourceForLocale(currentLanguage, R.string.disclosure_notifications_title),
                    body = stringResourceForLocale(currentLanguage, R.string.disclosure_notifications_body),
                    acceptLabel = stringResourceForLocale(currentLanguage, R.string.disclosure_accept),
                    declineLabel = stringResourceForLocale(currentLanguage, R.string.disclosure_decline),
                    onAccept = {
                        showNotifDisclosure = false
                        postNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onDecline = { showNotifDisclosure = false },
                )
            }
            if (showLocationFineDisclosure) {
                ProminentDisclosureDialog(
                    title = stringResourceForLocale(currentLanguage, R.string.disclosure_location_fine_title),
                    body = stringResourceForLocale(currentLanguage, R.string.disclosure_location_fine_body),
                    acceptLabel = stringResourceForLocale(currentLanguage, R.string.disclosure_accept),
                    declineLabel = stringResourceForLocale(currentLanguage, R.string.disclosure_decline),
                    onAccept = {
                        showLocationFineDisclosure = false
                        fineLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onDecline = {
                        showLocationFineDisclosure = false
                        locationGrantContinuation = null
                    },
                )
            }
            if (showLocationBackgroundDisclosure) {
                ProminentDisclosureDialog(
                    title = stringResourceForLocale(currentLanguage, R.string.disclosure_location_background_title),
                    body = stringResourceForLocale(currentLanguage, R.string.disclosure_location_background_body),
                    acceptLabel = stringResourceForLocale(currentLanguage, R.string.disclosure_accept),
                    declineLabel = stringResourceForLocale(currentLanguage, R.string.disclosure_decline),
                    onAccept = {
                        showLocationBackgroundDisclosure = false
                        LocationPermissions.backgroundPermission()?.let { perm ->
                            backgroundLocationLauncher.launch(perm)
                        } ?: run {
                            pendingArmAfterBackground = false
                            armManDownService()
                        }
                    },
                    onDecline = {
                        showLocationBackgroundDisclosure = false
                        pendingArmAfterBackground = false
                    },
                )
            }
            // Disclaimer Privacy Policy prima dell'invio SMS
            if (showDisclaimer) {
                AlertDialog(
                    onDismissRequest = { showDisclaimer = false },
                    title = {
                        Text(stringResourceForLocale(currentLanguage, R.string.privacy_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column {
                            Text(
                                stringResourceForLocale(currentLanguage, R.string.privacy_disclaimer),
                                fontSize = 14.sp,
                            )
                            TextButton(onClick = {
                                showDisclaimer = false
                                onOpenPrivacyPolicy()
                            }) {
                                Text(
                                    stringResourceForLocale(currentLanguage, R.string.privacy_read_full),
                                    color = Color(0xFF1565C0),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDisclaimer = false
                                sendSms(pendingSmsPrimary)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
                        ) {
                            Text(stringResourceForLocale(currentLanguage, R.string.accept))
                        }
                    },
                    containerColor = Color(0xFFE8F5E9)
                )
            }
            if (manDownCountdownSec != null) {
                val sec = manDownCountdownSec!!
                AlertDialog(
                    onDismissRequest = { },
                    properties = DialogProperties(
                        dismissOnClickOutside = false,
                        dismissOnBackPress = true
                    ),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFFB71C1C),
                    titleContentColor = Color.White,
                    textContentColor = Color.White,
                    tonalElevation = 12.dp,
                    title = {
                        Text(
                            stringResourceForLocale(currentLanguage, R.string.mandown_dialog_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Text(
                            getStringForLocale(context, currentLanguage, R.string.mandown_dialog_message)
                                .format(sec),
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { cancelManDownCountdownFromUi() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFEB3B),
                                contentColor = Color(0xFF1B1B1B)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp
                            )
                        ) {
                            Text(
                                stringResourceForLocale(currentLanguage, R.string.mandown_dialog_cancel),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1B1B1B)
                            )
                        }
                    },
                )
            }
            if (showPatronageFooter && patronEntries.isNotEmpty()) {
            PatronageFooter(
                title = stringResourceForLocale(currentLanguage, R.string.info_patronage_footer_title),
                patrons = patronEntries,
                onOpenUrl = { url -> openPatronUrl(url) },
                modifier = Modifier.padding(bottom = 2.dp),
            )
            }
            if (showBottomNavigation) {
            // Barra di navigazione in basso in stile menu, leggermente evidenziata
            NavigationBar(
                containerColor = Color(0xFFE0E0E0),
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Text(stringResourceForLocale(currentLanguage, R.string.tab_sos), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    },
                    label = {
                        Text(stringResourceForLocale(currentLanguage, R.string.tab_help), fontSize = 11.sp)
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0D4D10),
                        selectedTextColor = Color(0xFF0D4D10),
                        indicatorColor = Color(0xFFA5D6A7),
                        unselectedIconColor = Color(0xFF757575),
                        unselectedTextColor = Color(0xFF757575)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 0
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                        context.startActivity(intent)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = stringResourceForLocale(currentLanguage, R.string.content_desc_call_112)
                        )
                    },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.tab_call_112), fontSize = 11.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0D4D10),
                        selectedTextColor = Color(0xFF0D4D10),
                        indicatorColor = Color(0xFFA5D6A7),
                        unselectedIconColor = Color(0xFF757575),
                        unselectedTextColor = Color(0xFF757575)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = stringResourceForLocale(currentLanguage, R.string.content_desc_position)
                        )
                    },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.tab_position), fontSize = 11.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0D4D10),
                        selectedTextColor = Color(0xFF0D4D10),
                        indicatorColor = Color(0xFFA5D6A7),
                        unselectedIconColor = Color(0xFF757575),
                        unselectedTextColor = Color(0xFF757575)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResourceForLocale(currentLanguage, R.string.tab_info_title)
                        )
                    },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.tab_info_title), fontSize = 11.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0D4D10),
                        selectedTextColor = Color(0xFF0D4D10),
                        indicatorColor = Color(0xFFA5D6A7),
                        unselectedIconColor = Color(0xFF757575),
                        unselectedTextColor = Color(0xFF757575)
                    )
                )
            }
            }
        }
    }
}