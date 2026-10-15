package it.geohelp.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrekEntry(
    val id: Long = 0,
    val kind: String,
    val comune: String,
    val name: String,
    val number: String = "",
    val description: String = "",
    @SerialName("start_lat") val startLat: Double,
    @SerialName("start_lon") val startLon: Double,
    @SerialName("pdf_storage_path") val pdfStoragePath: String? = null,
    @SerialName("trk_storage_path") val trkStoragePath: String? = null,
    @SerialName("icon_storage_path") val iconStoragePath: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    val isWaypoint: Boolean get() = kind.equals("waypoint", ignoreCase = true)
}
