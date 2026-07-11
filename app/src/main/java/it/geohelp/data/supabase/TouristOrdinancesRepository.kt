package it.geohelp.data.supabase

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import it.geohelp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TouristOrdinancesRepository {

    fun publicPdfUrl(storagePath: String): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val path = finalizeOrdinanceStoragePath(storagePath)
        return "$base/storage/v1/object/public/ordinances/$path"
    }

    suspend fun listActive(): List<TouristOrdinance> = withContext(Dispatchers.IO) {
        Supabase.client
            .from("tourist_ordinances")
            .select {
                filter { eq("is_active", true) }
                order(column = "comune", order = Order.ASCENDING)
                order(column = "issued_at", order = Order.DESCENDING)
            }
            .decodeList<TouristOrdinance>()
    }

    suspend fun listAllForAdmin(): List<TouristOrdinance> = withContext(Dispatchers.IO) {
        Supabase.client
            .from("tourist_ordinances")
            .select {
                order(column = "comune", order = Order.ASCENDING)
                order(column = "issued_at", order = Order.DESCENDING)
            }
            .decodeList<TouristOrdinance>()
    }

    suspend fun insert(
        comune: String,
        title: String,
        issuedAt: String,
        pdfStoragePath: String,
        isActive: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        Supabase.client.from("tourist_ordinances").insert(
            buildJsonObject {
                put("comune", JsonPrimitive(comune.trim().uppercase()))
                put("title", JsonPrimitive(title.trim().uppercase()))
                put("issued_at", JsonPrimitive(issuedAt.trim()))
                put("pdf_storage_path", JsonPrimitive(finalizeOrdinanceStoragePath(pdfStoragePath)))
                put("is_active", JsonPrimitive(isActive))
            },
        )
    }

    suspend fun update(
        id: Long,
        comune: String,
        title: String,
        issuedAt: String,
        pdfStoragePath: String,
        isActive: Boolean,
    ) = withContext(Dispatchers.IO) {
        Supabase.client.from("tourist_ordinances").update(
            buildJsonObject {
                put("comune", JsonPrimitive(comune.trim().uppercase()))
                put("title", JsonPrimitive(title.trim().uppercase()))
                put("issued_at", JsonPrimitive(issuedAt.trim()))
                put("pdf_storage_path", JsonPrimitive(finalizeOrdinanceStoragePath(pdfStoragePath)))
                put("is_active", JsonPrimitive(isActive))
            },
        ) {
            filter { eq("id", id) }
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        Supabase.client.from("tourist_ordinances").delete {
            filter { eq("id", id) }
        }
    }
}
