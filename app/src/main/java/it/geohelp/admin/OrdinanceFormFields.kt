package it.geohelp.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.geohelp.ui.theme.geoHelpOutlinedFieldColors
import java.util.Locale

import it.geohelp.data.supabase.finalizeOrdinanceStoragePath
import it.geohelp.data.supabase.normalizeOrdinanceStoragePath
fun normalizeOrdinanceTitle(input: String): String =
    input.uppercase(Locale.ROOT)

/** Comune in catalogo: MAIUSCOLO (coerente con intestazione lista). */
fun normalizeOrdinanceComune(input: String): String =
    input.uppercase(Locale.ROOT)

fun parseIsoDateParts(iso: String): Triple<String, String, String> {
    val parts = iso.filter { it.isDigit() || it == '-' }.take(10).split('-')
    return Triple(
        parts.getOrElse(0) { "" }.filter(Char::isDigit).take(4),
        parts.getOrElse(1) { "" }.filter(Char::isDigit).take(2),
        parts.getOrElse(2) { "" }.filter(Char::isDigit).take(2),
    )
}

fun composeIsoDate(year: String, month: String, day: String): String =
    if (year.length == 4 && month.length == 2 && day.length == 2) {
        "$year-$month-$day"
    } else {
        ""
    }

fun isCompleteOrdinanceIsoDate(formatted: String): Boolean =
    formatted.matches(Regex("""\d{4}-\d{2}-\d{2}"""))

@Composable
fun OrdinanceDateFields(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (initYear, initMonth, initDay) = parseIsoDateParts(value)
    var year by remember(value) { mutableStateOf(initYear) }
    var month by remember(value) { mutableStateOf(initMonth) }
    var day by remember(value) { mutableStateOf(initDay) }
    val monthFocus = remember { FocusRequester() }
    val dayFocus = remember { FocusRequester() }

    fun emit() {
        onValueChange(composeIsoDate(year, month, day))
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            label()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = year,
                onValueChange = { raw ->
                    val next = raw.filter(Char::isDigit).take(4)
                    val wasFull = year.length == 4
                    year = next
                    emit()
                    if (!wasFull && next.length == 4) {
                        monthFocus.requestFocus()
                    }
                },
                modifier = Modifier.weight(1.1f),
                singleLine = true,
                placeholder = { Text("AAAA") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = geoHelpOutlinedFieldColors(),
            )
            Text("–", modifier = Modifier.width(16.dp))
            OutlinedTextField(
                value = month,
                onValueChange = { raw ->
                    val next = raw.filter(Char::isDigit).take(2)
                    val wasFull = month.length == 2
                    month = next
                    emit()
                    if (!wasFull && next.length == 2) {
                        dayFocus.requestFocus()
                    }
                },
                modifier = Modifier
                    .weight(0.75f)
                    .focusRequester(monthFocus),
                singleLine = true,
                placeholder = { Text("MM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = geoHelpOutlinedFieldColors(),
            )
            Text("–", modifier = Modifier.width(16.dp))
            OutlinedTextField(
                value = day,
                onValueChange = { raw ->
                    day = raw.filter(Char::isDigit).take(2)
                    emit()
                },
                modifier = Modifier
                    .weight(0.75f)
                    .focusRequester(dayFocus),
                singleLine = true,
                placeholder = { Text("GG") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = geoHelpOutlinedFieldColors(),
            )
        }
    }
}

@Composable
fun OrdinanceStoragePathField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(normalizeOrdinanceStoragePath(it)) },
        label = label,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = placeholder?.let { { Text(it) } },
        colors = geoHelpOutlinedFieldColors(),
    )
}

@Composable
fun OrdinanceTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(normalizeOrdinanceTitle(it)) },
        label = label,
        modifier = modifier.fillMaxWidth(),
        colors = geoHelpOutlinedFieldColors(),
    )
}

@Composable
fun OrdinanceComuneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(normalizeOrdinanceComune(it)) },
        label = label,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        colors = geoHelpOutlinedFieldColors(),
    )
}
