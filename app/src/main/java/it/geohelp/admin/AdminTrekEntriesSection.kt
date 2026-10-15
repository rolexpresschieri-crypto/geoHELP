package it.geohelp.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.R
import it.geohelp.data.supabase.TrekEntriesRepository
import it.geohelp.data.supabase.TrekEntry
import it.geohelp.data.supabase.displayComune
import it.geohelp.data.supabase.normalizeStoragePath
import it.geohelp.ui.theme.geoHelpOutlinedFieldColors
import kotlinx.coroutines.launch

@Composable
fun TrekEntriesAdminSection(
    currentLanguage: String,
    reloadKey: Int = 0,
    onChanged: () -> Unit = {},
    stringResourceForLocale: (String, Int) -> String,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TrekEntriesRepository() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<TrekEntry>>(emptyList()) }
    val savingIds = remember { mutableStateMapOf<Long, Boolean>() }
    var addKind by remember { mutableStateOf<String?>(null) }
    var addSaving by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<TrekEntry?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { repo.listAllForAdmin() }
                .onSuccess { rows = it }
                .onFailure { e ->
                    error = e.message ?: "error"
                    rows = emptyList()
                }
            loading = false
        }
    }

    LaunchedEffect(reloadKey) { reload() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.admin_trails_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.admin_trails_hint),
                fontSize = 12.sp,
                color = Color(0xFF616161),
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error != null -> Text(error!!, color = Color(0xFFB00020), fontSize = 13.sp)
                else -> {
                    rows.forEach { row ->
                        TrekEntryAdminRow(
                            currentLanguage = currentLanguage,
                            row = row,
                            saving = savingIds[row.id] == true,
                            stringResourceForLocale = stringResourceForLocale,
                            onSave = { updated ->
                                savingIds[row.id] = true
                                scope.launch {
                                    runCatching { repo.update(updated) }
                                        .onSuccess {
                                            reload()
                                            onChanged()
                                        }
                                        .onFailure { e -> error = e.message }
                                    savingIds.remove(row.id)
                                }
                            },
                            onDelete = { deleteTarget = row },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            addError = null
                            addKind = "trail"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResourceForLocale(currentLanguage, R.string.admin_trails_add_trail))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            addError = null
                            addKind = "waypoint"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResourceForLocale(currentLanguage, R.string.admin_trails_add_poi))
                    }
                }
            }
        }
    }

    if (addKind != null) {
        TrekEntryEditDialog(
            currentLanguage = currentLanguage,
            kind = addKind!!,
            stringResourceForLocale = stringResourceForLocale,
            saving = addSaving,
            saveError = addError,
            onDismiss = {
                if (!addSaving) {
                    addKind = null
                    addError = null
                }
            },
            onConfirmTrail = { comune, name, number, description, lat, lon, pdf, trk, active ->
                addSaving = true
                addError = null
                scope.launch {
                    runCatching {
                        repo.insertTrail(
                            comune = comune,
                            name = name,
                            number = number,
                            description = description,
                            startLat = lat,
                            startLon = lon,
                            pdfStoragePath = pdf,
                            trkStoragePath = trk,
                            isActive = active,
                        )
                    }.onSuccess {
                        addKind = null
                        reload()
                        onChanged()
                    }.onFailure { e ->
                        addError = e.message ?: stringResourceForLocale(
                            currentLanguage,
                            R.string.admin_trails_error_save,
                        )
                    }
                    addSaving = false
                }
            },
            onConfirmWaypoint = { comune, name, description, lat, lon, icon, active ->
                addSaving = true
                addError = null
                scope.launch {
                    runCatching {
                        repo.insertWaypoint(
                            comune = comune,
                            name = name,
                            description = description,
                            startLat = lat,
                            startLon = lon,
                            iconStoragePath = icon,
                            isActive = active,
                        )
                    }.onSuccess {
                        addKind = null
                        reload()
                        onChanged()
                    }.onFailure { e ->
                        addError = e.message ?: stringResourceForLocale(
                            currentLanguage,
                            R.string.admin_trails_error_save,
                        )
                    }
                    addSaving = false
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResourceForLocale(currentLanguage, R.string.admin_trails_delete_title)) },
            text = { Text(stringResourceForLocale(currentLanguage, R.string.admin_trails_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { repo.delete(target.id) }
                            .onSuccess {
                                deleteTarget = null
                                reload()
                                onChanged()
                            }
                            .onFailure { e -> error = e.message }
                    }
                }) {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.admin_trails_delete_confirm),
                        color = Color(0xFFB71C1C),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_cancel))
                }
            },
        )
    }
}

@Composable
private fun TrekEntryAdminRow(
    currentLanguage: String,
    row: TrekEntry,
    saving: Boolean,
    stringResourceForLocale: (String, Int) -> String,
    onSave: (TrekEntry) -> Unit,
    onDelete: () -> Unit,
) {
    var comune by remember(row.id) { mutableStateOf(displayComune(row.comune)) }
    var name by remember(row.id) { mutableStateOf(row.name) }
    var number by remember(row.id) { mutableStateOf(row.number) }
    var description by remember(row.id) { mutableStateOf(row.description) }
    var lat by remember(row.id) { mutableStateOf(row.startLat.toString()) }
    var lon by remember(row.id) { mutableStateOf(row.startLon.toString()) }
    var pdfPath by remember(row.id) { mutableStateOf(row.pdfStoragePath.orEmpty()) }
    var trkPath by remember(row.id) { mutableStateOf(row.trkStoragePath.orEmpty()) }
    var iconPath by remember(row.id) { mutableStateOf(row.iconStoragePath.orEmpty()) }
    var active by remember(row.id) { mutableStateOf(row.isActive) }

    Surface(
        color = Color(0xFFF8F9FB),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = if (row.isWaypoint) {
                    stringResourceForLocale(currentLanguage, R.string.admin_trails_kind_poi)
                } else {
                    stringResourceForLocale(currentLanguage, R.string.admin_trails_kind_trail)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D47A1),
            )
            TrekField(comune, { comune = it }, stringResourceForLocale(currentLanguage, R.string.admin_ordinances_comune))
            TrekField(name, { name = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_name))
            if (!row.isWaypoint) {
                TrekField(number, { number = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_number))
            }
            TrekField(description, { description = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_description))
            TrekField(
                lat,
                { lat = it },
                stringResourceForLocale(currentLanguage, R.string.admin_trails_lat),
                KeyboardType.Decimal,
            )
            TrekField(
                lon,
                { lon = it },
                stringResourceForLocale(currentLanguage, R.string.admin_trails_lon),
                KeyboardType.Decimal,
            )
            if (!row.isWaypoint) {
                TrekField(pdfPath, { pdfPath = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_pdf_path))
                TrekField(trkPath, { trkPath = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_trk_path))
            } else {
                TrekField(iconPath, { iconPath = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_icon_path))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = active,
                    onCheckedChange = { active = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0D47A1)),
                )
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_active), fontSize = 13.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val parsedLat = lat.replace(',', '.').toDoubleOrNull()
                val parsedLon = lon.replace(',', '.').toDoubleOrNull()
                Button(
                    onClick = {
                        onSave(
                            row.copy(
                                comune = comune,
                                name = name,
                                number = number,
                                description = description,
                                startLat = parsedLat ?: row.startLat,
                                startLon = parsedLon ?: row.startLon,
                                pdfStoragePath = pdfPath.ifBlank { null }?.let(::normalizeStoragePath),
                                trkStoragePath = trkPath.ifBlank { null }?.let(::normalizeStoragePath),
                                iconStoragePath = iconPath.ifBlank { null }?.let(::normalizeStoragePath),
                                isActive = active,
                            ),
                        )
                    },
                    enabled = !saving &&
                        comune.isNotBlank() &&
                        name.isNotBlank() &&
                        (row.isWaypoint || trkPath.isNotBlank()) &&
                        parsedLat != null &&
                        parsedLon != null,
                ) {
                    Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_save))
                }
                IconButton(onClick = onDelete, enabled = !saving) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFB71C1C))
                }
            }
        }
    }
}

@Composable
private fun TrekEntryEditDialog(
    currentLanguage: String,
    kind: String,
    stringResourceForLocale: (String, Int) -> String,
    saving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onConfirmTrail: (String, String, String, String, Double?, Double?, String?, String, Boolean) -> Unit,
    onConfirmWaypoint: (String, String, String, Double, Double, String?, Boolean) -> Unit,
) {
    val isWaypoint = kind == "waypoint"
    var comune by remember { mutableStateOf("Cesana") }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var pdfPath by remember { mutableStateOf("") }
    var trkPath by remember { mutableStateOf("") }
    var iconPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isWaypoint) {
                    stringResourceForLocale(currentLanguage, R.string.admin_trails_add_poi)
                } else {
                    stringResourceForLocale(currentLanguage, R.string.admin_trails_add_trail)
                },
            )
        },
        text = {
            Column {
                TrekField(comune, { comune = it }, stringResourceForLocale(currentLanguage, R.string.admin_ordinances_comune))
                TrekField(name, { name = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_name))
                if (!isWaypoint) {
                    TrekField(number, { number = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_number))
                }
                TrekField(description, { description = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_description))
                TrekField(
                    lat,
                    { lat = it },
                    stringResourceForLocale(currentLanguage, R.string.admin_trails_lat_optional),
                    KeyboardType.Decimal,
                )
                TrekField(
                    lon,
                    { lon = it },
                    stringResourceForLocale(currentLanguage, R.string.admin_trails_lon_optional),
                    KeyboardType.Decimal,
                )
                if (!isWaypoint) {
                    TrekField(pdfPath, { pdfPath = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_pdf_path))
                    TrekField(trkPath, { trkPath = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_trk_path))
                } else {
                    TrekField(iconPath, { iconPath = it }, stringResourceForLocale(currentLanguage, R.string.admin_trails_icon_path))
                }
                if (saveError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(saveError, color = Color(0xFFB00020), fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        },
        confirmButton = {
            val parsedLat = lat.replace(',', '.').toDoubleOrNull()
            val parsedLon = lon.replace(',', '.').toDoubleOrNull()
            TextButton(
                onClick = {
                    if (isWaypoint) {
                        if (parsedLat != null && parsedLon != null) {
                            onConfirmWaypoint(
                                comune,
                                name,
                                description,
                                parsedLat,
                                parsedLon,
                                iconPath.ifBlank { null },
                                true,
                            )
                        }
                    } else {
                        onConfirmTrail(
                            comune,
                            name,
                            number,
                            description,
                            parsedLat,
                            parsedLon,
                            pdfPath.ifBlank { null },
                            trkPath,
                            true,
                        )
                    }
                },
                enabled = !saving &&
                    comune.isNotBlank() &&
                    name.isNotBlank() &&
                    if (isWaypoint) parsedLat != null && parsedLon != null else trkPath.isNotBlank(),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_cancel))
            }
        },
    )
}

@Composable
private fun TrekField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = geoHelpOutlinedFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
