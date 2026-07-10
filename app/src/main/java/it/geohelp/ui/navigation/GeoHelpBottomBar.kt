package it.geohelp.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import it.geohelp.R

enum class MainDestination {
    HELP,
    CALL_112,
    POSITION,
    HOME,
    SETTINGS,
    INFO,
}

private val GreenDark = Color(0xFF0D4D10)
private val SosRed = Color(0xFFB71C1C)
private val NavWhite = Color.White
private val NavWhiteDim = Color(0xCCFFFFFF)
private val SosCircleSize = 88.dp
private val BarContentHeight = 58.dp
private val BarBottomPadding = 14.dp
private val NavIconSize = 25.dp
private val NavTrackingIconSize = 29.dp
private val NavLabelSize = 11.5.sp
private val NavLabelLineHeight = 12.5.sp

@Composable
fun GeoHelpBottomBar(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    onCall112: () -> Unit,
    tabSosLabel: String,
    tabCall112Label: String,
    tabTrackingLabel: String,
    tabSettingsLabel: String,
    tabInfoLabel: String,
    contentDescCall112: String,
    contentDescTracking: String,
    contentDescSettings: String,
    contentDescInfo: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = BarBottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(BarContentHeight)
                .background(GreenDark)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavSlot(
                selected = false,
                onClick = onCall112,
                label = tabCall112Label,
                icon = {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = contentDescCall112,
                        modifier = Modifier.size(NavIconSize),
                        tint = NavWhite,
                    )
                },
            )
            BottomNavSlot(
                selected = selected == MainDestination.POSITION,
                onClick = { onSelect(MainDestination.POSITION) },
                label = tabTrackingLabel,
                icon = {
                    Image(
                        painter = painterResource(R.drawable.ic_tracking),
                        contentDescription = contentDescTracking,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(NavTrackingIconSize)
                            .clip(CircleShape),
                    )
                },
            )
            Spacer(modifier = Modifier.width(64.dp))
            BottomNavSlot(
                selected = selected == MainDestination.SETTINGS,
                onClick = { onSelect(MainDestination.SETTINGS) },
                label = tabSettingsLabel,
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = contentDescSettings,
                        modifier = Modifier.size(NavIconSize),
                        tint = NavWhite,
                    )
                },
            )
            BottomNavSlot(
                selected = selected == MainDestination.INFO,
                onClick = { onSelect(MainDestination.INFO) },
                label = tabInfoLabel,
                icon = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = contentDescInfo,
                        modifier = Modifier.size(NavIconSize),
                        tint = NavWhite,
                    )
                },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f)
                .offset(y = -(BarContentHeight - SosCircleSize / 2))
                .size(SosCircleSize)
                .clip(CircleShape)
                .background(SosRed)
                .clickable { onSelect(MainDestination.HELP) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tabSosLabel,
                color = NavWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavSlot(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Text(
            text = label,
            fontSize = NavLabelSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) NavWhite else NavWhiteDim,
            maxLines = 2,
            lineHeight = NavLabelLineHeight,
            textAlign = TextAlign.Center,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .width(26.dp)
                    .height(2.dp)
                    .background(NavWhite),
            )
        }
    }
}
