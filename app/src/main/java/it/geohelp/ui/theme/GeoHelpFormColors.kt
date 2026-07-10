package it.geohelp.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Testo form sempre scuro: l'UI usa card/superfici chiare anche se il telefono è in dark mode. */
val GeoHelpFormText = Color(0xFF1B1B1B)
val GeoHelpFormTextMuted = Color(0xFF616161)
val GeoHelpFormPlaceholder = Color(0xFF9E9E9E)
val GeoHelpFormBorder = Color(0xFFBDBDBD)
val GeoHelpFormBorderFocused = Color(0xFF1976D2)
val GeoHelpFormCursor = Color(0xFF1B5E20)

@Composable
fun geoHelpOutlinedFieldColors(
    containerColor: Color = Color.White,
    focusedBorderColor: Color = GeoHelpFormBorderFocused,
    unfocusedBorderColor: Color = GeoHelpFormBorder,
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GeoHelpFormText,
    unfocusedTextColor = GeoHelpFormText,
    disabledTextColor = GeoHelpFormPlaceholder,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor,
    cursorColor = GeoHelpFormCursor,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    disabledBorderColor = Color(0xFFE0E0E0),
    focusedLabelColor = focusedBorderColor,
    unfocusedLabelColor = GeoHelpFormTextMuted,
    disabledLabelColor = GeoHelpFormPlaceholder,
    focusedPlaceholderColor = GeoHelpFormPlaceholder,
    unfocusedPlaceholderColor = GeoHelpFormPlaceholder,
    focusedLeadingIconColor = GeoHelpFormTextMuted,
    unfocusedLeadingIconColor = GeoHelpFormTextMuted,
    focusedTrailingIconColor = GeoHelpFormTextMuted,
    unfocusedTrailingIconColor = GeoHelpFormTextMuted,
)

@Composable
fun geoHelpFilterChipColors(
    selectedContainerColor: Color = Color(0xFFB71C1C),
): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = selectedContainerColor,
    selectedLabelColor = Color.White,
    selectedLeadingIconColor = Color.White,
    labelColor = GeoHelpFormText,
    iconColor = GeoHelpFormTextMuted,
)
