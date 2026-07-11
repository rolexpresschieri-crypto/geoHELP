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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.R
import it.geohelp.data.supabase.TouristOrdinance
import it.geohelp.data.supabase.TouristOrdinancesRepository
import it.geohelp.data.supabase.finalizeOrdinanceStoragePath
import it.geohelp.ui.theme.geoHelpOutlinedFieldColors
import kotlinx.coroutines.launch

@Composable
fun OrdinancesAdminSection(
    currentLanguage: String,
    reloadKey: Int = 0,
    onOrdinancesChanged: () -> Unit = {},
    stringResourceForLocale: (String, Int) -> String,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TouristOrdinancesRepository() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<TouristOrdinance>>(emptyList()) }
    val savingIds = remember { mutableStateMapOf<Long, Boolean>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var addSaving by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<TouristOrdinance?>(null) }

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
                text = stringResourceForLocale(currentLanguage, R.string.admin_ordinances_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.admin_ordinances_hint),
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
                        OrdinanceAdminRow(
                            currentLanguage = currentLanguage,
                            row = row,
                            saving = savingIds[row.id] == true,
                            stringResourceForLocale = stringResourceForLocale,
                            onSave = { comune, title, date, path, active ->
                                savingIds[row.id] = true
                                scope.launch {
                                    runCatching {
                                        repo.update(row.id, comune, title, date, path, active)
                                    }.onSuccess {
                                        reload()
                                        onOrdinancesChanged()
                                    }.onFailure { e -> error = e.message }
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
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_add))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        OrdinanceEditDialog(
            currentLanguage = currentLanguage,
            title = stringResourceForLocale(currentLanguage, R.string.admin_ordinances_add),
            stringResourceForLocale = stringResourceForLocale,
            saving = addSaving,
            saveError = addError,
            onDismiss = {
                if (!addSaving) {
                    showAddDialog = false
                    addError = null
                }
            },
            onConfirm = { comune, title, date, path, active ->
                addSaving = true
                addError = null
                scope.launch {
                    runCatching {
                        repo.insert(comune, title, date, path, active)
                    }.onSuccess {
                        showAddDialog = false
                        addError = null
                        reload()
                        onOrdinancesChanged()
                    }.onFailure { e ->
                        addError = e.message ?: stringResourceForLocale(
                            currentLanguage,
                            R.string.admin_ordinances_error_save,
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
            title = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_delete_title)) },
            text = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { repo.delete(target.id) }
                            .onSuccess {
                                deleteTarget = null
                                reload()
                                onOrdinancesChanged()
                            }
                            .onFailure { e -> error = e.message }
                    }
                }) {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.admin_ordinances_delete_confirm),
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
private fun OrdinanceAdminRow(
    currentLanguage: String,
    row: TouristOrdinance,
    saving: Boolean,
    stringResourceForLocale: (String, Int) -> String,
    onSave: (String, String, String, String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var comune by remember(row.id) { mutableStateOf(normalizeOrdinanceComune(row.comune)) }
    var title by remember(row.id) { mutableStateOf(normalizeOrdinanceTitle(row.title)) }
    var issuedAt by remember(row.id) { mutableStateOf(row.issuedAt.take(10)) }
    var pdfPath by remember(row.id) { mutableStateOf(finalizeOrdinanceStoragePath(row.pdfStoragePath)) }
    var active by remember(row.id) { mutableStateOf(row.isActive) }

    Surface(
        color = Color(0xFFF8F9FB),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            OrdinanceComuneField(
                value = comune,
                onValueChange = { comune = it },
                label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_comune)) },
            )
            Spacer(Modifier.height(6.dp))
            OrdinanceTitleField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_title_field)) },
            )
            Spacer(Modifier.height(6.dp))
            OrdinanceDateFields(
                value = issuedAt,
                onValueChange = { issuedAt = it },
                label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_date)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OrdinanceStoragePathField(
                value = pdfPath,
                onValueChange = { pdfPath = it },
                label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_pdf_path)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = active,
                    onCheckedChange = { active = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0D47A1)),
                )
                Text(
                    stringResourceForLocale(currentLanguage, R.string.admin_sos_active),
                    fontSize = 13.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onSave(comune, title, issuedAt, pdfPath, active) },
                    enabled = !saving &&
                        comune.isNotBlank() &&
                        title.isNotBlank() &&
                        isCompleteOrdinanceIsoDate(issuedAt) &&
                        pdfPath.isNotBlank(),
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
private fun OrdinanceEditDialog(
    currentLanguage: String,
    title: String,
    stringResourceForLocale: (String, Int) -> String,
    saving: Boolean = false,
    saveError: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit,
) {
    var comune by remember { mutableStateOf("CESANA") }
    var ordinanceTitle by remember { mutableStateOf("") }
    var issuedAt by remember { mutableStateOf("") }
    var pdfPath by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OrdinanceComuneField(
                    value = comune,
                    onValueChange = { comune = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_comune)) },
                )
                Spacer(Modifier.height(8.dp))
                OrdinanceTitleField(
                    value = ordinanceTitle,
                    onValueChange = { ordinanceTitle = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_title_field)) },
                )
                Spacer(Modifier.height(8.dp))
                OrdinanceDateFields(
                    value = issuedAt,
                    onValueChange = { issuedAt = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OrdinanceStoragePathField(
                    value = pdfPath,
                    onValueChange = { pdfPath = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_ordinances_pdf_path)) },
                    placeholder = "SESTRIERE/260706",
                )
                if (saveError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = saveError,
                        color = Color(0xFFB00020),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(comune, ordinanceTitle, issuedAt, pdfPath, active) },
                enabled = !saving &&
                    comune.isNotBlank() &&
                    ordinanceTitle.isNotBlank() &&
                    isCompleteOrdinanceIsoDate(issuedAt) &&
                    pdfPath.isNotBlank(),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp,
                    )
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
