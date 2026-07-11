package it.geohelp.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TouristOrdinance(
    val id: Long = 0,
    val comune: String,
    val title: String,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("pdf_storage_path") val pdfStoragePath: String,
    @SerialName("is_active") val isActive: Boolean = true,
)
