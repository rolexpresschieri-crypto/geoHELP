package it.geohelp.data.supabase

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import it.geohelp.BuildConfig
import it.geohelp.trek.TrekEntryKind
import it.geohelp.trek.TrekTrailRow
import it.geohelp.trek.TrekTrkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URL
import java.util.Locale

class TrekEntriesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    fun publicFileUrl(storagePath: String): String? {
        val path = normalizeStoragePath(storagePath)
        if (path.isBlank() || !Supabase.isConfigured) return null
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$base/storage/v1/object/public/$BUCKET/$path"
    }

    suspend fun listActive(): List<TrekEntry> = withContext(Dispatchers.IO) {
        if (!Supabase.isConfigured) return@withContext emptyList()
        Supabase.client
            .from(TABLE)
            .select {
                filter { eq("is_active", true) }
                order(column = "comune", order = Order.ASCENDING)
                order(column = "name", order = Order.ASCENDING)
            }
            .decodeList<TrekEntry>()
    }

    suspend fun listActiveRows(context: Context): List<TrekTrailRow> = withContext(Dispatchers.IO) {
        runCatching {
            val remote = listActive()
            cacheWrite(context, remote)
            remote.map { it.toTrailRow() }
        }.getOrElse {
            cacheRead(context).map { it.toTrailRow() }
        }
    }

    suspend fun listAllForAdmin(): List<TrekEntry> = withContext(Dispatchers.IO) {
        Supabase.client
            .from(TABLE)
            .select {
                order(column = "kind", order = Order.ASCENDING)
                order(column = "comune", order = Order.ASCENDING)
                order(column = "name", order = Order.ASCENDING)
            }
            .decodeList<TrekEntry>()
    }

    suspend fun insertTrail(
        comune: String,
        name: String,
        number: String,
        description: String,
        startLat: Double?,
        startLon: Double?,
        pdfStoragePath: String?,
        trkStoragePath: String,
        isActive: Boolean,
    ) = withContext(Dispatchers.IO) {
        val trkPath = normalizeStoragePath(trkStoragePath)
        val coords = resolveTrailStart(startLat, startLon, trkPath)
        insert(
            kind = "trail",
            comune = comune,
            name = name,
            number = number,
            description = description,
            startLat = coords.first,
            startLon = coords.second,
            pdfStoragePath = pdfStoragePath,
            trkStoragePath = trkPath,
            iconStoragePath = null,
            isActive = isActive,
        )
    }

    suspend fun insertWaypoint(
        comune: String,
        name: String,
        description: String,
        startLat: Double,
        startLon: Double,
        iconStoragePath: String?,
        isActive: Boolean,
    ) = withContext(Dispatchers.IO) {
        insert(
            kind = "waypoint",
            comune = comune,
            name = name,
            number = "",
            description = description,
            startLat = startLat,
            startLon = startLon,
            pdfStoragePath = null,
            trkStoragePath = null,
            iconStoragePath = iconStoragePath,
            isActive = isActive,
        )
    }

    suspend fun update(entry: TrekEntry) = withContext(Dispatchers.IO) {
        val trkPath = entry.trkStoragePath?.let(::normalizeStoragePath)?.ifBlank { null }
        val coords = if (!entry.isWaypoint && trkPath != null) {
            resolveTrailStart(entry.startLat, entry.startLon, trkPath)
        } else {
            entry.startLat to entry.startLon
        }
        Supabase.client.from(TABLE).update(
            buildPayload(
                kind = entry.kind,
                comune = entry.comune,
                name = entry.name,
                number = entry.number,
                description = entry.description,
                startLat = coords.first,
                startLon = coords.second,
                pdfStoragePath = entry.pdfStoragePath,
                trkStoragePath = trkPath,
                iconStoragePath = entry.iconStoragePath,
                isActive = entry.isActive,
            ),
        ) {
            filter { eq("id", entry.id) }
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        Supabase.client.from(TABLE).delete {
            filter { eq("id", id) }
        }
    }

    fun TrekEntry.toTrailRow(): TrekTrailRow = TrekTrailRow(
        comune = displayComune(comune),
        sentieroNome = name.trim(),
        sentieroNumero = number.trim(),
        breveDescrizione = description.trim(),
        pdfUrl = pdfStoragePath?.let(::publicFileUrl),
        trkAsset = null,
        trkUrl = trkStoragePath?.let(::publicFileUrl),
        startLat = startLat,
        startLon = startLon,
        kind = if (isWaypoint) TrekEntryKind.WAYPOINT else TrekEntryKind.TRAIL,
        wpIconAsset = null,
        remoteId = id,
    )

    private suspend fun insert(
        kind: String,
        comune: String,
        name: String,
        number: String,
        description: String,
        startLat: Double,
        startLon: Double,
        pdfStoragePath: String?,
        trkStoragePath: String?,
        iconStoragePath: String?,
        isActive: Boolean,
    ) {
        Supabase.client.from(TABLE).insert(
            buildPayload(
                kind = kind,
                comune = comune,
                name = name,
                number = number,
                description = description,
                startLat = startLat,
                startLon = startLon,
                pdfStoragePath = pdfStoragePath,
                trkStoragePath = trkStoragePath,
                iconStoragePath = iconStoragePath,
                isActive = isActive,
            ),
        )
    }

    private fun buildPayload(
        kind: String,
        comune: String,
        name: String,
        number: String,
        description: String,
        startLat: Double,
        startLon: Double,
        pdfStoragePath: String?,
        trkStoragePath: String?,
        iconStoragePath: String?,
        isActive: Boolean,
    ) = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        put("comune", JsonPrimitive(displayComune(comune)))
        put("name", JsonPrimitive(name.trim()))
        put("number", JsonPrimitive(number.trim()))
        put("description", JsonPrimitive(description.trim()))
        put("start_lat", JsonPrimitive(startLat))
        put("start_lon", JsonPrimitive(startLon))
        put("pdf_storage_path", storageOrNull(pdfStoragePath))
        put("trk_storage_path", storageOrNull(trkStoragePath))
        put("icon_storage_path", storageOrNull(iconStoragePath))
        put("is_active", JsonPrimitive(isActive))
    }

    private fun storageOrNull(path: String?) =
        path?.let(::normalizeStoragePath)?.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull

    private fun resolveTrailStart(
        startLat: Double?,
        startLon: Double?,
        trkPath: String,
    ): Pair<Double, Double> {
        if (startLat != null && startLon != null && startLat.isFinite() && startLon.isFinite()) {
            return startLat to startLon
        }
        val url = publicFileUrl(trkPath) ?: error("Percorso TRK mancante")
        val parsed = TrekTrkParser.parse(URL(url).readText())
        val first = parsed.points.firstOrNull() ?: error("Il file TRK non contiene punti GPS")
        return first.latitude to first.longitude
    }

    private fun cacheWrite(context: Context, entries: List<TrekEntry>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHE, json.encodeToString(entries))
            .apply()
    }

    private fun cacheRead(context: Context): List<TrekEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHE, null)
            ?: return emptyList()
        return runCatching { json.decodeFromString<List<TrekEntry>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        const val BUCKET = "trails"
        private const val TABLE = "trek_entries"
        private const val PREFS = "geohelp_prefs"
        private const val KEY_CACHE = "trek_entries_cache_json"
    }
}

fun normalizeStoragePath(input: String): String =
    input.trim().trimStart('/').replace('\\', '/')

fun displayComune(raw: String): String {
    val key = raw.trim().uppercase()
    return when {
        key.startsWith("CESANA") -> "Cesana"
        key.startsWith("SESTRIERE") -> "Sestriere"
        raw.isBlank() -> raw
        else -> raw.trim().lowercase(Locale.getDefault()).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }
}
