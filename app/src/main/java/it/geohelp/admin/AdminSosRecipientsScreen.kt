package it.geohelp.admin

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import it.geohelp.R
import it.geohelp.data.supabase.SosRecipient
import it.geohelp.data.supabase.SosRecipientsRepository
import it.geohelp.ui.theme.geoHelpOutlinedFieldColors
import kotlinx.coroutines.launch

private fun getStringForLocale(context: Context, locale: String, resId: Int): String {
    val config = Configuration(context.resources.configuration).apply {
        setLocale(java.util.Locale(locale))
    }
    return context.createConfigurationContext(config).resources.getString(resId)
}

@Composable
private fun stringResourceForLocale(locale: String, resId: Int): String {
    val context = LocalContext.current
    return remember(locale, resId) { getStringForLocale(context, locale, resId) }
}

@Composable
fun SosRecipientsAdminSection(
    currentLanguage: String,
    reloadKey: Int = 0,
    onRecipientsChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repo = remember { SosRecipientsRepository() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<SosRecipient>>(emptyList()) }
    val savingIds = remember { mutableStateMapOf<Long, Boolean>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SosRecipient?>(null) }

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
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.admin_sos_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF1B1B1B),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.admin_sos_hint),
                fontSize = 13.sp,
                color = Color(0xFF424242),
            )
            Spacer(Modifier.height(10.dp))
            when {
                loading -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFFB71C1C))
                    }
                }
                error != null -> {
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.admin_sos_error),
                        color = Color(0xFFB71C1C),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
                else -> {
                    rows.forEach { row ->
                        val id = row.id ?: return@forEach
                        SosRecipientAdminRow(
                            currentLanguage = currentLanguage,
                            row = row,
                            saving = savingIds[id] == true,
                            onActiveChange = { active ->
                                if (savingIds[id] == true) return@SosRecipientAdminRow
                                savingIds[id] = true
                                scope.launch {
                                    runCatching { repo.setActive(id, active) }
                                        .onSuccess {
                                            rows = rows.map {
                                                if (it.id == id) it.copy(active = active) else it
                                            }
                                            onRecipientsChanged()
                                        }
                                        .onFailure { error = it.message }
                                    savingIds[id] = false
                                }
                            },
                            onSave = { label, phone ->
                                if (savingIds[id] == true) return@SosRecipientAdminRow
                                savingIds[id] = true
                                scope.launch {
                                    runCatching {
                                        repo.update(
                                            id = id,
                                            label = label,
                                            phone = phone,
                                            active = row.active,
                                            sortOrder = row.sortOrder ?: 0,
                                        )
                                    }
                                        .onSuccess {
                                            rows = rows.map {
                                                if (it.id == id) {
                                                    it.copy(label = label, phone = phone)
                                                } else {
                                                    it
                                                }
                                            }
                                            onRecipientsChanged()
                                        }
                                        .onFailure { error = it.message }
                                    savingIds[id] = false
                                }
                            },
                            onDelete = { deleteTarget = row },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (rows.size < SosRecipientsRepository.MAX_RECIPIENTS) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_add))
                        }
                    } else {
                        Text(
                            text = stringResourceForLocale(currentLanguage, R.string.admin_sos_max_reached),
                            fontSize = 12.sp,
                            color = Color(0xFF757575),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SosRecipientEditDialog(
            currentLanguage = currentLanguage,
            title = stringResourceForLocale(currentLanguage, R.string.admin_sos_add),
            initialLabel = "",
            initialPhone = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { label, phone ->
                scope.launch {
                    runCatching {
                        repo.insert(
                            label = label,
                            phone = phone,
                            sortOrder = (rows.maxOfOrNull { it.sortOrder ?: 0 } ?: 0) + 10,
                        )
                    }
                        .onSuccess {
                            showAddDialog = false
                            reload()
                            onRecipientsChanged()
                        }
                        .onFailure { error = it.message }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        val id = target.id
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_delete_title))
            },
            text = {
                Text(
                    stringResourceForLocale(currentLanguage, R.string.admin_sos_delete_msg),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (id == null) {
                            deleteTarget = null
                            return@TextButton
                        }
                        scope.launch {
                            runCatching { repo.delete(id) }
                                .onSuccess {
                                    deleteTarget = null
                                    reload()
                                    onRecipientsChanged()
                                }
                                .onFailure { error = it.message }
                        }
                    },
                ) {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.admin_sos_delete_confirm),
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
private fun SosRecipientAdminRow(
    currentLanguage: String,
    row: SosRecipient,
    saving: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onSave: (String?, String) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember(row.id, row.label) { mutableStateOf(row.label.orEmpty()) }
    var phone by remember(row.id, row.phone) { mutableStateOf(row.phone) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = geoHelpOutlinedFieldColors(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                colors = geoHelpOutlinedFieldColors(),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = row.active,
                    onCheckedChange = onActiveChange,
                    enabled = !saving,
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFB71C1C)),
                )
                Text(
                    text = stringResourceForLocale(currentLanguage, R.string.admin_sos_active),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSave(label, phone) },
                    enabled = !saving && phone.any(Char::isDigit),
                ) {
                    Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_save))
                }
                IconButton(onClick = onDelete, enabled = !saving) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResourceForLocale(
                            currentLanguage,
                            R.string.admin_sos_delete_confirm,
                        ),
                        tint = Color(0xFFB71C1C),
                    )
                }
            }
        }
    }
}

@Composable
private fun SosRecipientEditDialog(
    currentLanguage: String,
    title: String,
    initialLabel: String,
    initialPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (String?, String) -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var phone by remember { mutableStateOf(initialPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = geoHelpOutlinedFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = geoHelpOutlinedFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, phone) },
                enabled = phone.any(Char::isDigit),
            ) {
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_cancel))
            }
        },
    )
}
