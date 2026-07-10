package it.geohelp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import it.geohelp.ui.theme.geoHelpFilterChipColors
import it.geohelp.ui.theme.geoHelpOutlinedFieldColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import it.geohelp.ui.components.SecretTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.geohelp.data.medical.MedicalConditionCatalog
import it.geohelp.data.medical.MedicalData
import it.geohelp.data.medical.MedicalDataRepository
import io.github.jan.supabase.auth.auth
import it.geohelp.data.medical.PinManager
import it.geohelp.data.supabase.Supabase
import it.geohelp.ui.theme.GeoHelpBackground
import kotlinx.coroutines.launch

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

private enum class MedicalStage { LOADING, PIN_SETUP, PIN_PROMPT, FORM, PIN_CHANGE }

/**
 * Schermata dei dati medici. Fasi:
 *  1. PIN_SETUP  — prima volta per questo account, crea un PIN (lock locale per utente).
 *  2. PIN_PROMPT — il PIN esiste; bisogna inserirlo per sbloccare il form.
 *  3. FORM       — form modificabile; i dati sono letti/salvati su Supabase.
 */
@Composable
fun MedicalScreen(
    currentLanguage: String,
    onBack: () -> Unit,
    onMedicalDataChanged: (MedicalData?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUser = Supabase.client.auth.currentUserOrNull()
    val userId = authUser?.id.orEmpty()
    val userEmail = authUser?.email.orEmpty()
    val pinManager = remember(userId, userEmail) {
        PinManager(context, userId, userEmail)
    }
    val medRepo = remember { MedicalDataRepository() }

    var stage by remember { mutableStateOf(MedicalStage.LOADING) }
    var data by remember { mutableStateOf(MedicalData.EMPTY) }

    /** Carica i dati medici dal server (esistono come colonne in chiaro). */
    suspend fun loadFromServer() {
        data = runCatching { medRepo.load() }
            .onFailure { android.util.Log.e("geoHELP/Medical", "load() fallita", it) }
            .getOrDefault(MedicalData.EMPTY)
        onMedicalDataChanged(if (data == MedicalData.EMPTY) null else data)
    }

    LaunchedEffect(userId, userEmail) {
        stage = if (pinManager.hasPin()) MedicalStage.PIN_PROMPT else MedicalStage.PIN_SETUP
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        GeoHelpBackground(imageAlpha = 0.42f, overlayAlpha = 0.46f) {
            when (stage) {
                MedicalStage.LOADING -> CenteredSpinner()

                MedicalStage.PIN_SETUP -> PinSetupContent(
                    currentLanguage = currentLanguage,
                    onCancel = onBack,
                    onPinCreated = { pin ->
                        pinManager.setPin(pin)
                        // Anche al primo setup carichiamo i dati: se l'utente reimposta il
                        // PIN dopo averlo dimenticato, sul server i dati ci sono ancora.
                        scope.launch {
                            loadFromServer()
                            stage = MedicalStage.FORM
                        }
                    }
                )

                MedicalStage.PIN_PROMPT -> PinPromptContent(
                    currentLanguage = currentLanguage,
                    onCancel = onBack,
                    verifyPin = { pin -> pinManager.verify(pin) },
                    onUnlocked = {
                        scope.launch {
                            loadFromServer()
                            stage = MedicalStage.FORM
                        }
                    },
                    onResetPin = {
                        // Reset PIN locale: i dati su Supabase restano intatti.
                        // L'utente li ritroverà dopo aver creato un nuovo PIN.
                        pinManager.clear()
                        stage = MedicalStage.PIN_SETUP
                    }
                )

                MedicalStage.FORM -> MedicalFormContent(
                    currentLanguage = currentLanguage,
                    initial = data,
                    onBack = onBack,
                    onChangePin = { stage = MedicalStage.PIN_CHANGE },
                    onSave = { updated, done ->
                        scope.launch {
                            try {
                                medRepo.save(updated)
                                data = updated
                                onMedicalDataChanged(updated)
                                onBack()
                            } catch (_: Throwable) {
                                // errore rete / server: restiamo sul form
                            } finally {
                                done()
                            }
                        }
                    }
                )

                MedicalStage.PIN_CHANGE -> PinChangeContent(
                    currentLanguage = currentLanguage,
                    pinManager = pinManager,
                    onCancel = { stage = MedicalStage.FORM },
                    onSuccess = { stage = MedicalStage.FORM },
                )
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFFB71C1C))
    }
}

@Composable
private fun PinSetupContent(
    currentLanguage: String,
    onCancel: () -> Unit,
    onPinCreated: (CharArray) -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    CardCentered {
        Text(
            text = stringResourceForLocale(currentLanguage, R.string.pin_set_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1B1B1B),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResourceForLocale(currentLanguage, R.string.pin_set_subtitle),
            fontSize = 13.sp,
            color = Color(0xFF424242),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 14.dp)
        )

        PinField(
            currentLanguage = currentLanguage,
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = stringResourceForLocale(currentLanguage, R.string.pin_set_field),
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PinField(
            currentLanguage = currentLanguage,
            value = pinConfirm,
            onValueChange = { pinConfirm = it.filter(Char::isDigit).take(8) },
            label = stringResourceForLocale(currentLanguage, R.string.pin_set_field_confirm),
            imeAction = ImeAction.Done,
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error!!, color = Color(0xFFB00020), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                Text(stringResourceForLocale(currentLanguage, R.string.medical_back))
            }
            Button(
                onClick = {
                    error = null
                    if (pin.length < 4) {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_set_error_short)
                        return@Button
                    }
                    if (pin != pinConfirm) {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_set_error_mismatch)
                        return@Button
                    }
                    onPinCreated(pin.toCharArray())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    stringResourceForLocale(currentLanguage, R.string.pin_set_save_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PinPromptContent(
    currentLanguage: String,
    onCancel: () -> Unit,
    verifyPin: (CharArray) -> Boolean,
    onUnlocked: () -> Unit,
    onResetPin: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResourceForLocale(currentLanguage, R.string.pin_reset_dialog_title)) },
            text = { Text(stringResourceForLocale(currentLanguage, R.string.pin_reset_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetPin()
                }) {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.pin_reset_dialog_confirm),
                        color = Color(0xFFB71C1C),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResourceForLocale(currentLanguage, R.string.pin_reset_dialog_cancel))
                }
            }
        )
    }

    CardCentered {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .background(
                    color = MedicalTitlePillBackground,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.pin_prompt_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResourceForLocale(currentLanguage, R.string.pin_prompt_subtitle),
            fontSize = 13.sp,
            color = Color(0xFF424242),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 14.dp)
        )

        PinField(
            currentLanguage = currentLanguage,
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = stringResourceForLocale(currentLanguage, R.string.pin_prompt_field),
            imeAction = ImeAction.Done,
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error!!, color = Color(0xFFB00020), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                Text(stringResourceForLocale(currentLanguage, R.string.medical_back))
            }
            Button(
                onClick = {
                    error = null
                    val pinChars = pin.toCharArray()
                    if (verifyPin(pinChars)) {
                        onUnlocked()
                    } else {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_prompt_error_wrong)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    stringResourceForLocale(currentLanguage, R.string.pin_prompt_unlock_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.pin_prompt_forgot),
                color = Color(0xFF1565C0),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MedicalFormContent(
    currentLanguage: String,
    initial: MedicalData,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onSave: (MedicalData, () -> Unit) -> Unit,
) {
    var selectedConditions by remember {
        mutableStateOf(MedicalConditionCatalog.decode(initial.conditions))
    }
    var pacemaker by remember { mutableStateOf(initial.pacemaker) }
    var bloodGroup by remember { mutableStateOf(initial.bloodGroup) }
    var allergies by remember { mutableStateOf(initial.allergies) }
    var therapies by remember { mutableStateOf(initial.therapies) }
    var notes by remember { mutableStateOf(initial.notes) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        selectedConditions = MedicalConditionCatalog.decode(initial.conditions)
        pacemaker = initial.pacemaker
        bloodGroup = initial.bloodGroup
        allergies = initial.allergies
        therapies = initial.therapies
        notes = initial.notes
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = MedicalContentTopInset, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MedicalTitlePillBackground,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResourceForLocale(currentLanguage, R.string.medical_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.medical_subtitle),
                fontSize = 13.sp,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .background(Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                MedicalConditionsSelector(
                    currentLanguage = currentLanguage,
                    selected = selectedConditions,
                    onSelectionChange = { selectedConditions = it },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFF2F4F7),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResourceForLocale(currentLanguage, R.string.medical_form_pacemaker),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = pacemaker,
                        onCheckedChange = { pacemaker = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFB71C1C)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                BloodGroupSelector(
                    currentLanguage = currentLanguage,
                    selected = bloodGroup,
                    onSelected = { bloodGroup = it },
                )
                Spacer(modifier = Modifier.height(10.dp))
                FormField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = stringResourceForLocale(currentLanguage, R.string.medical_form_allergies),
                    placeholder = stringResourceForLocale(currentLanguage, R.string.medical_form_allergies_hint),
                )
                Spacer(modifier = Modifier.height(10.dp))
                FormField(
                    value = therapies,
                    onValueChange = { therapies = it },
                    label = stringResourceForLocale(currentLanguage, R.string.medical_form_therapies),
                    placeholder = stringResourceForLocale(currentLanguage, R.string.medical_form_therapies_hint),
                )
                Spacer(modifier = Modifier.height(10.dp))
                FormField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = stringResourceForLocale(currentLanguage, R.string.medical_form_notes),
                    placeholder = stringResourceForLocale(currentLanguage, R.string.medical_form_notes_hint),
                    singleLine = false,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack, modifier = Modifier.height(50.dp)) {
                    Text(stringResourceForLocale(currentLanguage, R.string.medical_back))
                }
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        onSave(
                            MedicalData(
                                conditions = MedicalConditionCatalog.encode(selectedConditions),
                                pacemaker = pacemaker,
                                bloodGroup = bloodGroup,
                                allergies = allergies.trim(),
                                therapies = therapies.trim(),
                                notes = notes.trim(),
                            ),
                            { saving = false }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                    } else {
                        Text(
                            stringResourceForLocale(currentLanguage, R.string.medical_save_btn),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            TextButton(
                onClick = onChangePin,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
            ) {
                Text(
                    text = stringResourceForLocale(currentLanguage, R.string.pin_change_title),
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PinChangeContent(
    currentLanguage: String,
    pinManager: PinManager,
    onCancel: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newPinConfirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    CardCentered {
        Text(
            text = stringResourceForLocale(currentLanguage, R.string.pin_change_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1B1B1B),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResourceForLocale(currentLanguage, R.string.pin_change_subtitle),
            fontSize = 13.sp,
            color = Color(0xFF424242),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 14.dp)
        )
        PinField(
            currentLanguage = currentLanguage,
            value = oldPin,
            onValueChange = { oldPin = it.filter(Char::isDigit).take(8) },
            label = stringResourceForLocale(currentLanguage, R.string.pin_change_old),
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PinField(
            currentLanguage = currentLanguage,
            value = newPin,
            onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
            label = stringResourceForLocale(currentLanguage, R.string.pin_change_new),
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PinField(
            currentLanguage = currentLanguage,
            value = newPinConfirm,
            onValueChange = { newPinConfirm = it.filter(Char::isDigit).take(8) },
            label = stringResourceForLocale(currentLanguage, R.string.pin_change_confirm),
            imeAction = ImeAction.Done,
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error!!, color = Color(0xFFB00020), fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                Text(stringResourceForLocale(currentLanguage, R.string.medical_back))
            }
            Button(
                enabled = !loading,
                onClick = {
                    error = null
                    val oldChars = oldPin.toCharArray()
                    val newChars = newPin.toCharArray()
                    if (!pinManager.verify(oldChars)) {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_change_error_old)
                        return@Button
                    }
                    if (newPin.length < 4) {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_set_error_short)
                        return@Button
                    }
                    if (newPin != newPinConfirm) {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_change_error_match)
                        return@Button
                    }
                    loading = true
                    val ok = pinManager.changePinVerified(oldChars, newChars)
                    loading = false
                    if (!ok) {
                        error = getStringForLocale(context, currentLanguage, R.string.pin_change_error_old)
                    } else {
                        onSuccess()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(22.dp)
                    )
                } else {
                    Text(
                        stringResourceForLocale(currentLanguage, R.string.pin_change_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private val MedicalContentTopInset = 64.dp
/** Sfondo della "pillola" dei titoli "Dati medici" / "Inserisci il PIN": giallo oro. */
private val MedicalTitlePillBackground = Color(0xFFFFC107)

@Composable
private fun CardCentered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = MedicalContentTopInset, start = 24.dp, end = 24.dp, bottom = 24.dp)
                .widthIn(max = 460.dp)
                .background(Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(16.dp))
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun PinField(
    currentLanguage: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
) {
    val context = LocalContext.current
    val showSecret = getStringForLocale(context, currentLanguage, R.string.content_desc_show_secret)
    val hideSecret = getStringForLocale(context, currentLanguage, R.string.content_desc_hide_secret)
    SecretTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardType = KeyboardType.NumberPassword,
        imeAction = imeAction,
        modifier = Modifier.fillMaxWidth(),
        visibilityToggleDescription = showSecret,
        showDescription = showSecret,
        hideDescription = hideSecret,
    )
}

@Composable
private fun MedicalConditionsSelector(
    currentLanguage: String,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    Text(
        text = stringResourceForLocale(currentLanguage, R.string.medical_form_conditions),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1B1B1B),
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text = stringResourceForLocale(currentLanguage, R.string.medical_form_conditions_hint),
        fontSize = 12.sp,
        color = Color(0xFF616161),
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (entry in MedicalConditionCatalog.ORDERED) {
            val code = entry.code
            val checked = selected.contains(code)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (checked) Color(0xFFFFEBEE) else Color(0xFFF2F4F7),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { on ->
                        onSelectionChange(
                            if (on) selected + code else selected - code
                        )
                    },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFB71C1C))
                )
                Text(
                    text = stringResourceForLocale(currentLanguage, entry.labelResId),
                    fontSize = 14.sp,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(0xFF1B1B1B),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = code,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Color(0xFF9E9E9E)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
        colors = geoHelpOutlinedFieldColors()
    )
}

/**
 * Selettore del gruppo sanguigno (FilterChip su due righe 4+4).
 * Tocca di nuovo lo stesso chip per deselezionare; "Non indicato" azzera la scelta.
 */
@Composable
private fun BloodGroupSelector(
    currentLanguage: String,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResourceForLocale(currentLanguage, R.string.medical_form_blood_group),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1B1B1B),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val groups = MedicalData.BLOOD_GROUPS
        BloodGroupChipRow(groups = groups.subList(0, 4), selected = selected, onSelected = onSelected)
        Spacer(modifier = Modifier.height(8.dp))
        BloodGroupChipRow(groups = groups.subList(4, 8), selected = selected, onSelected = onSelected)
        TextButton(
            onClick = { onSelected(null) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = stringResourceForLocale(currentLanguage, R.string.medical_form_blood_group_clear)
            )
        }
    }
}

@Composable
private fun BloodGroupChipRow(
    groups: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            FilterChip(
                selected = selected == group,
                onClick = {
                    onSelected(if (selected == group) null else group)
                },
                label = {
                    Text(
                        text = group,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = geoHelpFilterChipColors(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
