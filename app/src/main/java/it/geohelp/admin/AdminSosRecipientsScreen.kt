package it.geohelp.admin

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.R
import it.geohelp.ui.components.SecretTextField
import it.geohelp.data.supabase.SosRecipient
import it.geohelp.data.supabase.SosRecipientsRepository
import it.geohelp.ui.theme.GeoHelpBackground
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSosRecipientsScreen(
    currentLanguage: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { SosRecipientsRepository() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<SosRecipient>>(emptyList()) }
    val savingIds = remember { mutableStateMapOf<Long, Boolean>() }

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

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResourceForLocale(
                                currentLanguage,
                                R.string.admin_sos_back,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFB71C1C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        GeoHelpBackground(imageAlpha = 0.38f, overlayAlpha = 0.45f) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.admin_sos_hint),
                        fontSize = 13.sp,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
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
                                text = stringResourceForLocale(
                                    currentLanguage,
                                    R.string.admin_sos_error,
                                ),
                                color = Color(0xFFB71C1C),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rows.forEach { row ->
                                    val id = row.id ?: return@forEach
                                    AdminSosRow(
                                        currentLanguage = currentLanguage,
                                        row = row,
                                        saving = savingIds[id] == true,
                                        onActiveChange = { active ->
                                            if (savingIds[id] == true) return@AdminSosRow
                                            savingIds[id] = true
                                            scope.launch {
                                                runCatching { repo.setActive(id, active) }
                                                    .onSuccess {
                                                        rows = rows.map {
                                                            if (it.id == id) it.copy(active = active) else it
                                                        }
                                                    }
                                                    .onFailure {
                                                        error = it.message
                                                    }
                                                savingIds[id] = false
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSosRow(
    currentLanguage: String,
    row: SosRecipient,
    saving: Boolean,
    onActiveChange: (Boolean) -> Unit,
) {
    val roleLabel = when {
        row.isPrimary -> stringResourceForLocale(currentLanguage, R.string.admin_sos_role_primary)
        row.isBackup -> stringResourceForLocale(currentLanguage, R.string.admin_sos_role_backup)
        else -> row.role
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.92f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.label?.trim().orEmpty().ifBlank { roleLabel },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(text = row.phone, fontSize = 14.sp, color = Color(0xFF424242))
                Text(
                    text = roleLabel,
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                )
            }
            Checkbox(
                checked = row.active,
                onCheckedChange = { onActiveChange(it) },
                enabled = !saving,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFFB71C1C),
                ),
            )
        }
    }
}

@Composable
fun AdminSosPinDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val wrongPinMsg = remember(currentLanguage) {
        getStringForLocale(context, currentLanguage, R.string.admin_sos_pin_wrong)
    }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_title))
        },
        text = {
            Column {
                if (!AdminSosPin.isConfigured()) {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_not_configured),
                        color = Color(0xFFB71C1C),
                    )
                } else {
                    Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_hint))
                    SecretTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            error = null
                        },
                        label = stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_title),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        visibilityToggleDescription = getStringForLocale(
                            context,
                            currentLanguage,
                            R.string.content_desc_show_secret,
                        ),
                        showDescription = getStringForLocale(
                            context,
                            currentLanguage,
                            R.string.content_desc_show_secret,
                        ),
                        hideDescription = getStringForLocale(
                            context,
                            currentLanguage,
                            R.string.content_desc_hide_secret,
                        ),
                    )
                    error?.let {
                        Text(
                            it,
                            color = Color(0xFFB71C1C),
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (!AdminSosPin.isConfigured()) {
                        onDismiss()
                        return@TextButton
                    }
                    if (AdminSosPin.verify(pin)) {
                        onSuccess()
                    } else {
                        error = wrongPinMsg
                    }
                },
                enabled = AdminSosPin.isConfigured(),
            ) {
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_ok))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResourceForLocale(currentLanguage, R.string.admin_sos_pin_cancel))
            }
        },
    )
}
