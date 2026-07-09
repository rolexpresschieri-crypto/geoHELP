package it.geohelp

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import it.geohelp.location.LocationPermissions
import it.geohelp.trek.TrekTrkParser
import it.geohelp.ui.theme.GeoHELPTheme
import org.osmdroid.config.Configuration as OsmdroidConfiguration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class MapBasemap(val maxZoom: Double) {
    STREETS(19.0),
    TOPOGRAPHIC(17.0),
    SATELLITE(19.0),
    ;

    fun supportsTrailsOverlay(): Boolean = this == STREETS || this == TOPOGRAPHIC

    fun tileSource(): ITileSource = when (this) {
        STREETS -> TileSourceFactory.MAPNIK
        TOPOGRAPHIC -> TILE_OPEN_TOPO_MAP
        SATELLITE -> TILE_ESRI_WORLD_IMAGERY
    }
}

private data class DeviceFix(
    val point: GeoPoint,
    val accuracyM: Float,
    val speedMps: Float,
    val gpsBearing: Float?,
    val altitudeM: Double?,
    val fixAtMillis: Long,
    val provider: String?,
)

private data class CalibrationGuideRow(
    val titleRes: Int,
    val bodyRes: Int,
)

private const val MIN_SPEED_FOR_GPS_HEADING_MPS = 1.5f
private const val COMPASS_HEADING_OFFSET_KEY = "geohelp.compass_heading_offset_deg"
private const val MIN_FIX_DISTANCE_TO_UPDATE_M = 4f
private const val MIN_STATIONARY_SPEED_MPS = 0.8f
private const val MAP_ROTATION_UPDATE_THRESHOLD_DEG = 1.5f
private const val COMPASS_SMOOTHING_ALPHA = 0.18f
private const val TRAIL_START_REACHED_DISTANCE_M = 40f

private val MAP_SCALE_METRIC_STEPS = intArrayOf(
    15_000_000, 8_000_000, 4_000_000, 2_000_000, 1_000_000, 500_000, 250_000, 100_000,
    50_000, 25_000, 15_000, 8_000, 4_000, 2_000, 1_000, 500, 250, 100, 50, 25, 10, 5, 2, 1,
)

private val CALIBRATION_GUIDE_ROWS = listOf(
    CalibrationGuideRow(R.string.map_calib_guide_1_title, R.string.map_calib_guide_1_body),
    CalibrationGuideRow(R.string.map_calib_guide_2_title, R.string.map_calib_guide_2_body),
    CalibrationGuideRow(R.string.map_calib_guide_3_title, R.string.map_calib_guide_3_body),
    CalibrationGuideRow(R.string.map_calib_guide_4_title, R.string.map_calib_guide_4_body),
    CalibrationGuideRow(R.string.map_calib_guide_5_title, R.string.map_calib_guide_5_body),
    CalibrationGuideRow(R.string.map_calib_guide_6_title, R.string.map_calib_guide_6_body),
)

private fun loadCompassHeadingOffset(context: Context): Float =
    context.getSharedPreferences("geohelp_prefs", Context.MODE_PRIVATE)
        .getFloat(COMPASS_HEADING_OFFSET_KEY, 0f)

private fun saveCompassHeadingOffset(context: Context, degrees: Float) {
    context.getSharedPreferences("geohelp_prefs", Context.MODE_PRIVATE)
        .edit()
        .putFloat(COMPASS_HEADING_OFFSET_KEY, degrees)
        .apply()
}

private fun navigatorMarkerLabel(userDisplayName: String): String {
    val name = userDisplayName.trim()
    return if (name.isEmpty()) "GPS" else name.uppercase(Locale.getDefault())
}

private fun formatOutdoorScaleDistance(language: String, meters: Int): String {
    if (meters < 1000) return "$meters,0 m"
    val km = meters / 1000.0
    if (km >= 10) return "${km.roundToInt()} km"
    val locale = Locale(language)
    return String.format(locale, "%.1f km", km).replace('.', ',')
}

private fun formatOutdoorElevation(context: Context, language: String, altitudeM: Double?): String? {
    if (altitudeM == null || !altitudeM.isFinite() || altitudeM <= 0) return null
    val rounded = altitudeM.roundToInt()
    val grouped = NumberFormat.getIntegerInstance(Locale(language)).format(rounded)
    return context.getStringForAppLanguageFormatted(language, R.string.map_elevation_format, grouped)
}

private fun pickMapScaleBar(mapView: MapView, maxBarWidthPx: Float): Pair<Int, Float>? {
    val center = mapView.mapCenter as? GeoPoint ?: return null
    if (center.latitude.absoluteValue > 85) return null
    val zoom = mapView.zoomLevelDouble
    val metersPerPixel = 156543.03392 * cos(Math.toRadians(center.latitude)) / 2.0.pow(zoom)
    if (metersPerPixel <= 0) return null
    for (metric in MAP_SCALE_METRIC_STEPS.reversed()) {
        val barPx = (metric / metersPerPixel).toFloat()
        if (barPx <= maxBarWidthPx) {
            return metric to barPx
        }
    }
    return null
}

private val Double.absoluteValue: Double get() = kotlin.math.abs(this)

/** Raster “World Imagery” (ortofoto mondiale). */
private val TILE_ESRI_WORLD_IMAGERY: ITileSource = object : OnlineTileSourceBase(
    "Esri.WorldImagery",
    1,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri et al."
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$y/$x"
    }
}

/** OpenTopoMap — curve di quota (come TocAppBuild). */
private val TILE_OPEN_TOPO_MAP: ITileSource = object : OnlineTileSourceBase(
    "OpenTopoMap",
    0,
    17,
    256,
    ".png",
    arrayOf("https://tile.opentopomap.org/"),
    "© OSM, SRTM · OpenTopoMap (CC-BY-SA)"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$x/$y$mImageFilenameEnding"
    }
}

/** Overlay sentieri escursionistici (CC BY-SA, waymarkedtrails.org). */
private val TILE_WAYMARKED_TRAILS_HIKING: ITileSource = object : OnlineTileSourceBase(
    "WaymarkedTrails.Hiking",
    0,
    17,
    256,
    ".png",
    arrayOf("https://tile.waymarkedtrails.org/hiking/"),
    "sentieri © waymarkedtrails.org (CC BY-SA)"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$x/$y$mImageFilenameEnding"
    }
}

private class MapGestureTracker {
    @Volatile
    var lastProgrammaticAtMs: Long = 0
    var onUserMapGesture: () -> Unit = {}
    var onMapChanged: () -> Unit = {}

    fun markProgrammatic() {
        lastProgrammaticAtMs = SystemClock.uptimeMillis()
    }

    fun isProgrammaticRecent(): Boolean =
        SystemClock.uptimeMillis() - lastProgrammaticAtMs < 350L

    private fun notifyUserGesture() {
        if (!isProgrammaticRecent()) {
            onUserMapGesture()
        }
    }

    fun onUserPan() = notifyUserGesture()

    fun onUserZoom() = notifyUserGesture()
}

private fun MapView.centerOnProgrammatic(point: GeoPoint, tracker: MapGestureTracker) {
    tracker.markProgrammatic()
    controller.setCenter(point)
    invalidate()
}

private fun MapView.attachNavigatorGestures(tracker: MapGestureTracker) {
    var touchDownX = 0f
    var touchDownY = 0f
    var panDetected = false
    var multiTouch = false
    var userTouchActive = false
    addMapListener(object : MapListener {
        override fun onScroll(event: ScrollEvent?): Boolean {
            tracker.onMapChanged()
            if (event != null && userTouchActive && !tracker.isProgrammaticRecent()) {
                tracker.onUserPan()
            }
            return true
        }

        override fun onZoom(event: ZoomEvent?): Boolean {
            tracker.onMapChanged()
            if (event != null && userTouchActive && !tracker.isProgrammaticRecent()) {
                tracker.onUserZoom()
            }
            return true
        }
    })
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                userTouchActive = true
                multiTouch = false
                panDetected = false
                touchDownX = event.x
                touchDownY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                panDetected = true
                tracker.onUserZoom()
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTouch || event.pointerCount > 1) {
                    return@setOnTouchListener false
                }
                if (!panDetected && !tracker.isProgrammaticRecent()) {
                    if (abs(event.x - touchDownX) > 12f || abs(event.y - touchDownY) > 12f) {
                        panDetected = true
                        tracker.onUserPan()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                userTouchActive = false
                multiTouch = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 1) {
                    multiTouch = false
                }
            }
        }
        false
    }
}

private fun normalizeHeadingDegrees(degrees: Float?): Float? {
    if (degrees == null || degrees.isNaN() || !degrees.isFinite()) return null
    return ((degrees % 360f) + 360f) % 360f
}

private fun angleDeltaDegrees(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

private fun smoothHeadingDegrees(current: Float?, target: Float, alpha: Float): Float {
    val normalizedTarget = normalizeHeadingDegrees(target) ?: return current ?: target
    val normalizedCurrent = normalizeHeadingDegrees(current) ?: return normalizedTarget
    val delta = angleDeltaDegrees(normalizedCurrent, normalizedTarget)
    return normalizeHeadingDegrees(normalizedCurrent + delta * alpha) ?: normalizedTarget
}

private fun shouldAcceptFix(previous: DeviceFix?, candidate: DeviceFix): Boolean {
    previous ?: return true

    val distanceM = previous.point.distanceToAsDouble(candidate.point).toFloat()
    val candidateAccuracy = candidate.accuracyM.takeIf { it.isFinite() && it > 0f } ?: 9999f
    val previousAccuracy = previous.accuracyM.takeIf { it.isFinite() && it > 0f } ?: 9999f
    val isGpsCandidate = candidate.provider == LocationManager.GPS_PROVIDER
    val isGpsPrevious = previous.provider == LocationManager.GPS_PROVIDER

    if (isGpsCandidate && !isGpsPrevious) return true
    if (!isGpsCandidate && isGpsPrevious && distanceM < max(previousAccuracy, candidateAccuracy)) return false

    val isMoving = max(previous.speedMps, candidate.speedMps) >= MIN_STATIONARY_SPEED_MPS
    val movementThreshold = if (isMoving) {
        max(2f, minOf(previousAccuracy, candidateAccuracy) * 0.35f)
    } else {
        max(MIN_FIX_DISTANCE_TO_UPDATE_M, minOf(previousAccuracy, candidateAccuracy) * 0.6f)
    }
    if (!isMoving && distanceM < movementThreshold) return false

    if (candidateAccuracy > previousAccuracy * 1.8f && distanceM < max(previousAccuracy, MIN_FIX_DISTANCE_TO_UPDATE_M)) {
        return false
    }

    return true
}

private fun geoPointAirDistanceMeters(from: GeoPoint, to: GeoPoint): Float {
    val out = FloatArray(1)
    android.location.Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, out)
    return out[0]
}

private fun isGpsCourseHeadingReliable(speedMps: Float, gpsBearing: Float?): Boolean =
    speedMps >= MIN_SPEED_FOR_GPS_HEADING_MPS && normalizeHeadingDegrees(gpsBearing) != null

private fun resolveNavigatorHeading(
    compassHeading: Float?,
    gpsBearing: Float?,
    speedMps: Float,
    calibrationOffsetDeg: Float = 0f,
): Float? {
    val gpsCourse = normalizeHeadingDegrees(gpsBearing)
    val useGps = speedMps >= MIN_SPEED_FOR_GPS_HEADING_MPS && gpsCourse != null
    val raw = if (useGps) gpsCourse else normalizeHeadingDegrees(compassHeading) ?: return null
    return normalizeHeadingDegrees(raw + calibrationOffsetDeg)
}

private fun Context.getStringForAppLanguage(langCode: String, resId: Int): String {
    val config = Configuration(resources.configuration).apply { setLocale(Locale(langCode)) }
    return createConfigurationContext(config).getString(resId)
}

private fun Context.getStringForAppLanguageFormatted(
    langCode: String,
    resId: Int,
    vararg formatArgs: Any
): String {
    val config = Configuration(resources.configuration).apply { setLocale(Locale(langCode)) }
    return createConfigurationContext(config).getString(resId, *formatArgs)
}

/** Freccia nord gialla (bussola). */
@Composable
private fun PruaLineArrow(
    headingDegrees: Float,
    modifier: Modifier = Modifier,
    arrowColor: Color = Color(0xFFFFEB3B)
) {
    val density = LocalDensity.current
    val strokePx = with(density) { 2.dp.toPx() }
    Canvas(modifier = modifier.size(36.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val d = size.minDimension
        rotate(degrees = headingDegrees, pivot = Offset(cx, cy)) {
            val tipY = cy - d * 0.36f
            val stemBottomY = cy + d * 0.30f
            val stemTopY = tipY + d * 0.14f
            drawLine(
                color = arrowColor,
                start = Offset(cx, stemBottomY),
                end = Offset(cx, stemTopY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
            val wing = d * 0.11f
            val barbY = tipY + d * 0.16f
            drawLine(
                color = arrowColor,
                start = Offset(cx, tipY),
                end = Offset(cx - wing * 1.35f, barbY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = arrowColor,
                start = Offset(cx, tipY),
                end = Offset(cx + wing * 1.35f, barbY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}

class MapActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocale(newBase))
    }

    private fun applyLocale(context: Context): Context {
        val prefs = context.getSharedPreferences("geohelp_prefs", MODE_PRIVATE)
        val lang = prefs.getString("lang", "it") ?: "it"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OsmdroidConfiguration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid_prefs", MODE_PRIVATE)
        )
        OsmdroidConfiguration.getInstance().userAgentValue = packageName

        val lat = intent.getStringExtra(EXTRA_LATITUDE).orEmpty()
        val lon = intent.getStringExtra(EXTRA_LONGITUDE).orEmpty()
        val latitude = lat.toDoubleOrNull()
        val longitude = lon.toDoubleOrNull()
        val trailStartLat = intent.getDoubleExtra(EXTRA_TRAIL_START_LAT, Double.NaN).takeIf { it.isFinite() }
        val trailStartLon = intent.getDoubleExtra(EXTRA_TRAIL_START_LON, Double.NaN).takeIf { it.isFinite() }
        val trailLabel = intent.getStringExtra(EXTRA_TRAIL_LABEL).orEmpty()
        val trkAsset = intent.getStringExtra(EXTRA_TRK_ASSET)?.takeIf { it.isNotBlank() }
        val lang = intent.getStringExtra(EXTRA_LANGUAGE)
            ?: getSharedPreferences("geohelp_prefs", MODE_PRIVATE).getString("lang", "it")
            ?: "it"
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()

        setContent {
            GeoHELPTheme {
                val title = getStringForAppLanguage(lang, R.string.open_on_map)
                MapScreen(
                    language = lang,
                    title = title,
                    initialLatitude = latitude,
                    initialLongitude = longitude,
                    userDisplayName = displayName,
                    trailStartLatitude = trailStartLat,
                    trailStartLongitude = trailStartLon,
                    trailLabel = trailLabel,
                    trkAsset = trkAsset,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_TRAIL_START_LAT = "extra_trail_start_lat"
        const val EXTRA_TRAIL_START_LON = "extra_trail_start_lon"
        const val EXTRA_TRAIL_LABEL = "extra_trail_label"
        const val EXTRA_TRK_ASSET = "extra_trk_asset"
    }
}

@Composable
private fun CompassHeadingPanel(
    language: String,
    compassHeading: Float?,
    gpsBearing: Float?,
    speedMps: Float,
    headingOffsetDeg: Float,
    modifier: Modifier = Modifier,
    panelContentDescription: String
) {
    val context = LocalContext.current
    val useGpsCourse = isGpsCourseHeadingReliable(speedMps, gpsBearing)
    val navigatorHeading = resolveNavigatorHeading(
        compassHeading = compassHeading,
        gpsBearing = gpsBearing,
        speedMps = speedMps,
        calibrationOffsetDeg = headingOffsetDeg,
    )
    val hInt = navigatorHeading?.roundToInt()?.let { ((it % 360) + 360) % 360 } ?: 0
    val pruaPadded = hInt.toString().padStart(3, '0')
    val compassNorm = normalizeHeadingDegrees(compassHeading) ?: 0f

    val nord = remember(language) { context.getStringForAppLanguage(language, R.string.map_panel_nord) }
    val magnetic = remember(language) { context.getStringForAppLanguage(language, R.string.map_panel_magnetic) }
    val sourceLabel = remember(language, useGpsCourse) {
        context.getStringForAppLanguage(
            language,
            if (useGpsCourse) R.string.map_heading_source_gps else R.string.map_heading_source_compass
        )
    }
    val pruaLine = if (navigatorHeading != null) {
        context.getStringForAppLanguageFormatted(language, R.string.map_prua_degrees, pruaPadded)
    } else {
        context.getStringForAppLanguage(language, R.string.map_heading_na)
    }

    Surface(
        modifier = modifier
            .width(88.dp)
            .semantics { contentDescription = panelContentDescription },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xA3000000),
        border = BorderStroke(1.dp, Color(0x3DFFFFFF)),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                nord,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                magnetic,
                color = Color(0x8CFFFFFF),
                fontSize = 8.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = Icons.Filled.North,
                contentDescription = null,
                tint = Color(0xFFFFEB3B),
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer { rotationZ = -compassNorm },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                pruaLine,
                color = Color(0xFFD5D9E5),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                sourceLabel,
                color = Color(0x8CFFFFFF),
                fontSize = 8.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MapLayersMenuButton(
    basemap: MapBasemap,
    onChoose: (MapBasemap) -> Unit,
    streetLabel: String,
    topoLabel: String,
    satelliteLabel: String,
    buttonContentDescription: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x85000000),
            shadowElevation = 4.dp,
            tonalElevation = 0.dp
        ) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = buttonContentDescription,
                    tint = Color.White
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(streetLabel) },
                onClick = {
                    onChoose(MapBasemap.STREETS)
                    expanded = false
                },
                trailingIcon = if (basemap == MapBasemap.STREETS) {
                    { Icon(Icons.Filled.Check, null, tint = Color(0xFF2E7D32)) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(topoLabel) },
                onClick = {
                    onChoose(MapBasemap.TOPOGRAPHIC)
                    expanded = false
                },
                trailingIcon = if (basemap == MapBasemap.TOPOGRAPHIC) {
                    { Icon(Icons.Filled.Check, null, tint = Color(0xFF2E7D32)) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(satelliteLabel) },
                onClick = {
                    onChoose(MapBasemap.SATELLITE)
                    expanded = false
                },
                trailingIcon = if (basemap == MapBasemap.SATELLITE) {
                    { Icon(Icons.Filled.Check, null, tint = Color(0xFF2E7D32)) }
                } else null
            )
        }
    }
}

@Composable
private fun MapTrailsToggleButton(
    enabled: Boolean,
    trailsVisible: Boolean,
    onToggle: () -> Unit,
    showTooltip: String,
    hideTooltip: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x85000000),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        IconButton(
            onClick = onToggle,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Hiking,
                contentDescription = if (trailsVisible) hideTooltip else showTooltip,
                tint = when {
                    !enabled -> Color(0x66FFFFFF)
                    trailsVisible -> Color(0xFFFFEB3B)
                    else -> Color(0x99FFFFFF)
                },
            )
        }
    }
}

@Composable
private fun OutdoorMapScaleOverlay(
    mapView: MapView?,
    mapRevision: Int,
    language: String,
    altitudeM: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ink = Color(0xFF4A4A4A)
    val maxBarWidthPx = with(density) { 120.dp.toPx() }
    val picked = remember(mapView, mapRevision) {
        mapView?.let { pickMapScaleBar(it, maxBarWidthPx) }
    }
    if (picked == null) return

    val distanceLabel = formatOutdoorScaleDistance(language, picked.first)
    val barWidthDp = with(density) { picked.second.toDp() }
    val elevationLabel = formatOutdoorElevation(context, language, altitudeM)
    val strokePx = with(density) { 1.2.dp.toPx() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = distanceLabel,
            color = ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(3.dp))
        Canvas(modifier = Modifier.size(width = barWidthDp, height = 10.dp)) {
            val y = 6f
            val tickTop = 2f
            val tickBottom = 10f
            drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = strokePx, cap = StrokeCap.Square)
            for (x in listOf(0f, size.width / 2f, size.width)) {
                drawLine(ink, Offset(x, tickTop), Offset(x, tickBottom), strokeWidth = strokePx, cap = StrokeCap.Square)
            }
        }
        if (elevationLabel != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = elevationLabel,
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun CompassCalibrationDialog(
    language: String,
    initialOffsetDeg: Float,
    onOffsetChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var offsetDeg by remember(initialOffsetDeg) { mutableFloatStateOf(initialOffsetDeg) }
    val scrollState = rememberScrollState()
    val title = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_title) }
    val intro = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_intro) }
    val verticalHint = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_vertical_hint) }
    val offsetTitle = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_offset_title) }
    val closeLabel = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_close) }
    val resetLabel = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_reset) }

    fun applyOffset(delta: Float) {
        offsetDeg = normalizeHeadingDegrees(offsetDeg + delta) ?: 0f
        saveCompassHeadingOffset(context, offsetDeg)
        onOffsetChanged(offsetDeg)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Explore, contentDescription = null, tint = Color(0xFF1565C0))
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                Text(intro, color = Color(0xFF616161), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text(verticalHint, color = Color(0xFF616161), fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF5F5F5),
                    border = BorderStroke(1.dp, Color(0x1F000000)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                        Text(offsetTitle, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            context.getStringForAppLanguageFormatted(
                                language,
                                R.string.map_calib_offset_current,
                                offsetDeg.roundToInt(),
                            ),
                            color = Color(0xFF616161),
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(-90, -15, 15, 90).forEach { delta ->
                                OutlinedButton(onClick = { applyOffset(delta.toFloat()) }) {
                                    Text("${if (delta > 0) "+" else ""}$delta°")
                                }
                            }
                            TextButton(onClick = {
                                offsetDeg = 0f
                                saveCompassHeadingOffset(context, 0f)
                                onOffsetChanged(0f)
                            }) {
                                Text(resetLabel)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                CALIBRATION_GUIDE_ROWS.forEach { row ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF5F5F5),
                        border = BorderStroke(1.dp, Color(0x1F000000)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                context.getStringForAppLanguage(language, row.titleRes),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                context.getStringForAppLanguage(language, row.bodyRes),
                                color = Color(0xFF616161),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(closeLabel, color = Color(0xFF1565C0))
            }
        },
    )
}

private fun createNavigatorMarkerDrawable(context: Context, label: String): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val widthPx = (140f * density).roundToInt().coerceAtLeast(1)
    val heightPx = (96f * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val centerX = widthPx / 2f
    val circleRadius = 21f * density
    val circleCenterY = 26f * density
    val labelTop = 54f * density
    val labelHorizontalPadding = 8f * density
    val labelCorner = 10f * density

    val circleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E8ECEF")
        style = Paint.Style.FILL
    }
    val circleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#7A8794")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#D91F2A")
        style = Paint.Style.FILL
    }
    val labelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((0.72f * 255).roundToInt(), 0, 0, 0)
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
    }

    canvas.drawCircle(centerX, circleCenterY, circleRadius, circleFill)
    canvas.drawCircle(centerX, circleCenterY, circleRadius, circleStroke)

    val arrowPath = Path().apply {
        moveTo(centerX, circleCenterY - 15f * density)
        lineTo(centerX + 11f * density, circleCenterY + 12f * density)
        lineTo(centerX, circleCenterY + 6f * density)
        lineTo(centerX - 11f * density, circleCenterY + 12f * density)
        close()
    }
    canvas.drawPath(arrowPath, arrowPaint)

    val display = navigatorMarkerLabel(label)
    val textWidth = textPaint.measureText(display)
    val labelRect = RectF(
        (centerX - textWidth / 2f - labelHorizontalPadding).coerceAtLeast(0f),
        labelTop,
        (centerX + textWidth / 2f + labelHorizontalPadding).coerceAtMost(widthPx.toFloat()),
        labelTop + 20f * density,
    )
    canvas.drawRoundRect(labelRect, labelCorner, labelCorner, labelFill)
    val textBaseline = labelRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(display, centerX, textBaseline, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun rememberNavigatorMarkerOverlay(
    mapView: MapView,
    existingMarker: Marker?,
    context: Context,
    label: String,
    point: GeoPoint?,
    visible: Boolean,
): Marker {
    val marker = existingMarker ?: Marker(mapView).apply {
        setAnchor(0.5f, 26f / 96f)
        setInfoWindow(null)
    }
    marker.position = point ?: marker.position ?: GeoPoint(0.0, 0.0)
    marker.icon = createNavigatorMarkerDrawable(context, label)
    marker.isEnabled = visible
    return marker
}

@Composable
private fun NavigatorCenterMarker(label: String) {
    val display = navigatorMarkerLabel(label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .border(1.2.dp, Color(0xD9FFFFFF), CircleShape)
                .background(Color(0x59000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Navigation,
                contentDescription = null,
                tint = Color(0xFFD50000),
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xB8000000),
        ) {
            Text(
                text = display,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.MapUserPositionMarker(
    label: String,
    followMode: Boolean,
) {
    if (followMode) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(10f)
        ) {
            NavigatorCenterMarker(label = label)
        }
    }
}

@Composable
private fun MapPositionInfoPanel(
    language: String,
    fix: DeviceFix?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val title = remember(language) {
        context.getStringForAppLanguage(language, R.string.map_position_current)
    }
    val acquiring = remember(language) {
        context.getStringForAppLanguage(language, R.string.map_gps_acquiring)
    }
    val na = remember(language) {
        context.getStringForAppLanguage(language, R.string.map_heading_na)
    }
    val dateFormat = remember(language) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale(language))
    }

    val coords = fix?.let {
        String.format(Locale.US, "%.5f, %.5f", it.point.latitude, it.point.longitude)
    } ?: acquiring
    val accText = fix?.accuracyM?.let { String.format(Locale.US, "%.0f m", it) } ?: na
    val speedKmh = fix?.speedMps?.let { max(0f, it) * 3.6f }
    val velText = speedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: na
    val fixText = fix?.fixAtMillis?.let { dateFormat.format(Date(it)) } ?: na

    Surface(
        modifier = modifier.width(182.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xA8000000),
        border = BorderStroke(1.dp, Color(0x3DFFFFFF)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                coords,
                color = Color(0xFFD5D9E5),
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Acc: $accText",
                color = Color(0xFFD5D9E5),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Vel: $velText",
                color = Color(0xFFD5D9E5),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Fix: $fixText",
                color = Color(0xFFD5D9E5),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun applyMapOverlays(
    mapView: MapView,
    basemap: MapBasemap,
    trailsOverlayEnabled: Boolean,
) {
    mapView.overlays.clear()

    if (basemap.supportsTrailsOverlay() && trailsOverlayEnabled) {
        val trailsProvider = MapTileProviderBasic(mapView.context.applicationContext, TILE_WAYMARKED_TRAILS_HIKING)
        mapView.overlays.add(TilesOverlay(trailsProvider, mapView.context))
    }
}

@Composable
private fun MapLifecycleEffect(mapView: MapView) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
}

@Composable
private fun DeviceLocationEffect(
    enabled: Boolean,
    onFix: (DeviceFix) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (!enabled || !LocationPermissions.hasFineLocation(context)) {
            onDispose { }
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER)
            var lastPublishedFix: DeviceFix? = null
            var lastGpsReceivedAtMs = 0L

            fun publish(location: Location?) {
                if (location == null) return
                if (location.provider != LocationManager.GPS_PROVIDER) {
                    if (lastGpsReceivedAtMs > 0L &&
                        SystemClock.uptimeMillis() - lastGpsReceivedAtMs < 15_000L
                    ) {
                        return
                    }
                } else {
                    lastGpsReceivedAtMs = SystemClock.uptimeMillis()
                }
                val fix = DeviceFix(
                    point = GeoPoint(location.latitude, location.longitude),
                    accuracyM = location.accuracy,
                    speedMps = if (location.hasSpeed()) location.speed else 0f,
                    gpsBearing = if (location.hasBearing()) location.bearing else null,
                    altitudeM = if (location.hasAltitude()) location.altitude else null,
                    fixAtMillis = location.time,
                    provider = location.provider,
                )
                if (!shouldAcceptFix(lastPublishedFix, fix)) return
                lastPublishedFix = fix
                onFix(fix)
            }

            val listener = LocationListener { location -> publish(location) }

            providers.mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }?.let { publish(it) }

            providers.forEach { provider ->
                runCatching {
                    locationManager.requestLocationUpdates(provider, 500L, 0f, listener)
                }
            }

            onDispose {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }
}

@Composable
private fun CompassEffect(onHeading: (Float) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rMat = FloatArray(9)
        val orient = FloatArray(3)
        var smoothedHeading: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rMat, event.values)
                SensorManager.getOrientation(rMat, orient)
                val rawHeading = Math.toDegrees(orient[0].toDouble()).toFloat()
                smoothedHeading = smoothHeadingDegrees(smoothedHeading, rawHeading, COMPASS_SMOOTHING_ALPHA)
                smoothedHeading?.let(onHeading)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    language: String,
    title: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    userDisplayName: String,
    trailStartLatitude: Double?,
    trailStartLongitude: Double?,
    trailLabel: String,
    trkAsset: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var basemap by remember { mutableStateOf(MapBasemap.STREETS) }
    var trailsOverlayEnabled by remember { mutableStateOf(false) }
    var followMode by remember { mutableStateOf(true) }
    var northDynamicEnabled by remember { mutableStateOf(true) }
    var deviceFix by remember { mutableStateOf<DeviceFix?>(null) }
    var compassHeading by remember { mutableFloatStateOf(0f) }
    var headingOffsetDeg by remember { mutableFloatStateOf(loadCompassHeadingOffset(context)) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var mapRevision by remember { mutableIntStateOf(0) }
    var lastAppliedMapOrientation by remember { mutableFloatStateOf(Float.NaN) }
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val navigatorOverlayState = remember { mutableStateOf<Marker?>(null) }
    val trailStartOverlayState = remember { mutableStateOf<Marker?>(null) }
    val routeOverlayState = remember { mutableStateOf<Polyline?>(null) }
    val trailTrackOverlayState = remember { mutableStateOf<Polyline?>(null) }
    val gestureTracker = remember { MapGestureTracker() }
    val mapOverlayKey = remember(basemap, trailsOverlayEnabled) {
        "${basemap.name}_$trailsOverlayEnabled"
    }
    var appliedMapOverlayKey by remember { mutableStateOf<String?>(null) }

    val streetLabel = remember(language) { context.getStringForAppLanguage(language, R.string.map_layer_street) }
    val topoLabel = remember(language) { context.getStringForAppLanguage(language, R.string.map_layer_topographic) }
    val satelliteLabel = remember(language) { context.getStringForAppLanguage(language, R.string.map_layer_satellite) }
    val northDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_north_content_desc) }
    val layersDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_layers_content_desc) }
    val trailsShowDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_trails_show) }
    val trailsHideDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_trails_hide) }
    val followActiveDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_follow_active) }
    val followCenterDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_follow_center) }
    val northLockOnDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_north_lock_on) }
    val northLockOffDesc = remember(language) { context.getStringForAppLanguage(language, R.string.map_north_lock_off) }
    val calibTooltip = remember(language) { context.getStringForAppLanguage(language, R.string.map_calib_tooltip) }

    val fallbackPoint = remember {
        if (initialLatitude != null && initialLongitude != null) {
            GeoPoint(initialLatitude, initialLongitude)
        } else {
            GeoPoint(45.00691, 7.82447)
        }
    }
    val trailStartPoint = remember(trailStartLatitude, trailStartLongitude) {
        if (trailStartLatitude != null && trailStartLongitude != null) {
            GeoPoint(trailStartLatitude, trailStartLongitude)
        } else {
            null
        }
    }
    val parsedTrk = remember(trkAsset) {
        trkAsset?.let { asset ->
            runCatching { TrekTrkParser.parseAsset(context, asset) }.getOrNull()
        }
    }

    val navigatorHeading = resolveNavigatorHeading(
        compassHeading = compassHeading,
        gpsBearing = deviceFix?.gpsBearing,
        speedMps = deviceFix?.speedMps ?: 0f,
        calibrationOffsetDeg = headingOffsetDeg,
    )

    gestureTracker.onUserMapGesture = {
        if (followMode) followMode = false
    }
    gestureTracker.onMapChanged = {
        mapRevision++
    }

    DeviceLocationEffect(enabled = true) { fix ->
        deviceFix = fix
    }

    CompassEffect { heading ->
        compassHeading = heading
    }

    LaunchedEffect(mapViewState.value, northDynamicEnabled, navigatorHeading) {
        val mapView = mapViewState.value ?: return@LaunchedEffect
        if (northDynamicEnabled) {
            val target = navigatorHeading?.let { -it } ?: 0f
            if (
                lastAppliedMapOrientation.isNaN() ||
                kotlin.math.abs(angleDeltaDegrees(lastAppliedMapOrientation, target)) >= MAP_ROTATION_UPDATE_THRESHOLD_DEG
            ) {
                mapView.setMapOrientation(target)
                lastAppliedMapOrientation = target
            }
        } else {
            if (
                lastAppliedMapOrientation.isNaN() ||
                kotlin.math.abs(angleDeltaDegrees(lastAppliedMapOrientation, 0f)) >= MAP_ROTATION_UPDATE_THRESHOLD_DEG
            ) {
                mapView.setMapOrientation(0f)
                lastAppliedMapOrientation = 0f
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(
        mapViewState.value,
        followMode,
        deviceFix?.point?.latitude,
        deviceFix?.point?.longitude,
    ) {
        if (!followMode) return@LaunchedEffect
        val mapView = mapViewState.value ?: return@LaunchedEffect
        val point = deviceFix?.point ?: return@LaunchedEffect
        mapView.centerOnProgrammatic(point, gestureTracker)
    }

    val attribution = remember(basemap, trailsOverlayEnabled) {
        val base = when (basemap) {
            MapBasemap.STREETS -> "© OpenStreetMap contributors"
            MapBasemap.TOPOGRAPHIC -> "© OSM, SRTM · OpenTopoMap (CC-BY-SA)"
            MapBasemap.SATELLITE -> "© Esri et al."
        }
        if (basemap.supportsTrailsOverlay() && trailsOverlayEnabled) {
            "$base · sentieri © waymarkedtrails.org (CC BY-SA)"
        } else {
            base
        }
    }

    if (showCalibrationDialog) {
        CompassCalibrationDialog(
            language = language,
            initialOffsetDeg = headingOffsetDeg,
            onOffsetChanged = { headingOffsetDeg = it },
            onDismiss = { showCalibrationDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCalibrationDialog = true },
                        modifier = Modifier.semantics { contentDescription = calibTooltip },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CompassCalibration,
                            contentDescription = calibTooltip,
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapViewState.value = this
                        setMultiTouchControls(true)
                        minZoomLevel = 4.0
                        maxZoomLevel = basemap.maxZoom
                        setTileSource(basemap.tileSource())
                        controller.setZoom(16.0)
                        controller.setCenter(fallbackPoint)
                        attachNavigatorGestures(gestureTracker)
                    }
                },
                update = { mapView ->
                    mapViewState.value = mapView
                    gestureTracker.onUserMapGesture = {
                        if (followMode) followMode = false
                    }
                    gestureTracker.onMapChanged = {
                        mapRevision++
                    }

                    val source = basemap.tileSource()
                    if (mapView.tileProvider.tileSource.name() != source.name()) {
                        mapView.setTileSource(source)
                    }
                    mapView.maxZoomLevel = basemap.maxZoom

                    if (appliedMapOverlayKey != mapOverlayKey) {
                        applyMapOverlays(
                            mapView = mapView,
                            basemap = basemap,
                            trailsOverlayEnabled = trailsOverlayEnabled,
                        )
                        appliedMapOverlayKey = mapOverlayKey
                    }

                    parsedTrk?.points?.takeIf { it.isNotEmpty() }?.let { trackPoints ->
                        val track = trailTrackOverlayState.value ?: Polyline(mapView).apply {
                            outlinePaint.strokeWidth = 6f
                            outlinePaint.color = android.graphics.Color.parseColor("#D50000")
                            outlinePaint.isAntiAlias = true
                        }
                        track.setPoints(trackPoints)
                        if (!mapView.overlays.contains(track)) {
                            mapView.overlays.add(track)
                        }
                        trailTrackOverlayState.value = track
                    }

                    // Route overlay: current user -> trail start
                    val userPoint = deviceFix?.point
                    val route = routeOverlayState.value ?: Polyline(mapView).apply {
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.color = android.graphics.Color.parseColor("#1976D2")
                        outlinePaint.isAntiAlias = true
                    }
                    val showAirLine = userPoint != null && trailStartPoint != null &&
                        geoPointAirDistanceMeters(userPoint, trailStartPoint) >= TRAIL_START_REACHED_DISTANCE_M
                    if (showAirLine) {
                        route.setPoints(listOf(userPoint!!, trailStartPoint!!))
                        if (!mapView.overlays.contains(route)) {
                            mapView.overlays.add(route)
                        }
                        routeOverlayState.value = route
                    } else {
                        routeOverlayState.value?.let { mapView.overlays.remove(it) }
                    }

                    // Trail start marker (only if a trail is selected)
                    if (trailStartPoint != null) {
                        val startMarker = trailStartOverlayState.value ?: Marker(mapView).apply {
                            setAnchor(0.5f, 1f)
                            setInfoWindow(null)
                        }
                        startMarker.position = trailStartPoint
                        startMarker.title = trailLabel
                        // Simple green dot marker
                        startMarker.icon = BitmapDrawable(
                            mapView.context.resources,
                            Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
                                val c = AndroidCanvas(this)
                                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#0B9F3A") }
                                c.drawCircle(12f, 12f, 10f, p)
                            },
                        )
                        if (!mapView.overlays.contains(startMarker)) {
                            mapView.overlays.add(startMarker)
                        }
                        trailStartOverlayState.value = startMarker
                    }

                    val navigatorOverlay = rememberNavigatorMarkerOverlay(
                        mapView = mapView,
                        existingMarker = navigatorOverlayState.value,
                        context = mapView.context,
                        label = userDisplayName,
                        point = deviceFix?.point,
                        visible = deviceFix != null && !followMode,
                    )
                    navigatorOverlay.position = deviceFix?.point ?: navigatorOverlay.position ?: fallbackPoint
                    navigatorOverlay.isEnabled = deviceFix != null && !followMode
                    if (!mapView.overlays.contains(navigatorOverlay)) {
                        mapView.overlays.add(navigatorOverlay)
                    }
                    navigatorOverlayState.value = navigatorOverlay
                    mapView.invalidate()
                }
            )

            mapViewState.value?.let { MapLifecycleEffect(it) }

            OutdoorMapScaleOverlay(
                mapView = mapViewState.value,
                mapRevision = mapRevision,
                language = language,
                altitudeM = deviceFix?.altitudeM,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapLayersMenuButton(
                    basemap = basemap,
                    onChoose = { basemap = it },
                    streetLabel = streetLabel,
                    topoLabel = topoLabel,
                    satelliteLabel = satelliteLabel,
                    buttonContentDescription = layersDesc,
                )
                if (basemap.supportsTrailsOverlay()) {
                    Spacer(Modifier.width(8.dp))
                    MapTrailsToggleButton(
                        enabled = true,
                        trailsVisible = trailsOverlayEnabled,
                        onToggle = { trailsOverlayEnabled = !trailsOverlayEnabled },
                        showTooltip = trailsShowDesc,
                        hideTooltip = trailsHideDesc,
                    )
                }
            }

            CompassHeadingPanel(
                language = language,
                compassHeading = compassHeading,
                gpsBearing = deviceFix?.gpsBearing,
                speedMps = deviceFix?.speedMps ?: 0f,
                headingOffsetDeg = headingOffsetDeg,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp)
                    .zIndex(5f),
                panelContentDescription = northDesc
            )

            deviceFix?.let { fix ->
                MapUserPositionMarker(
                    label = userDisplayName,
                    followMode = followMode,
                )
            }

            // Distance pill for trail
            if (trailStartPoint != null && deviceFix?.point != null) {
                val meters = geoPointAirDistanceMeters(deviceFix!!.point, trailStartPoint)
                if (meters >= TRAIL_START_REACHED_DISTANCE_M) {
                val distanceText = remember(language, meters) {
                    val formatted = if (meters >= 1000f) String.format(Locale.US, "%.1f km", meters / 1000f)
                    else String.format(Locale.US, "%.0f m", meters)
                    context.getStringForAppLanguage(language, R.string.map_trail_air_distance).format(formatted)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xB8000000),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 66.dp)
                        .zIndex(6f),
                ) {
                    Text(
                        text = distanceText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (deviceFix != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x9E000000),
                    ) {
                        IconButton(
                            onClick = {
                                followMode = true
                                deviceFix?.point?.let { point ->
                                    mapViewState.value?.centerOnProgrammatic(point, gestureTracker)
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (followMode) Icons.Filled.GpsFixed else Icons.Filled.MyLocation,
                                contentDescription = if (followMode) followActiveDesc else followCenterDesc,
                                tint = Color.White,
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (northDynamicEnabled) Color(0xFF0B9F3A) else Color(0xFFD91F2A),
                    modifier = Modifier
                        .size(30.dp)
                        .clickable {
                            northDynamicEnabled = !northDynamicEnabled
                        }
                        .semantics {
                            contentDescription = if (northDynamicEnabled) northLockOnDesc else northLockOffDesc
                        },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "X",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            MapPositionInfoPanel(
                language = language,
                fix = deviceFix,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 14.dp),
            )

            Text(
                text = attribution,
                color = Color(0x9E000000),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
            }
        }
    }
}
