package com.example.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Place
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens

@Composable
fun SettingsScreen(
    useMetric: Boolean,
    pirateWeatherApiKey: String,
    pirateWeatherWorking: Boolean = false,
    pirateWeatherAttempted: Boolean = false,
    savedPlaces: List<Place>,
    selectedPlaceId: String?,
    onSetUseMetric: (Boolean) -> Unit,
    onSavePirateWeatherApiKey: (String) -> Unit,

    onSelectPlace: (String) -> Unit,
    onAddPlace: (String, String, Double, Double) -> Unit,
    tickerVisible: Boolean = false,
    alertsEnabled: Boolean = false,
    onSetAlertsEnabled: (Boolean) -> Unit = {},
    rainAlertsEnabled: Boolean = false,
    onSetRainAlertsEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddPlaceDialog by remember { mutableStateOf(false) }

    // 3.1: the notification permission is requested only here — when the
    // user turns alerts on — never on first launch. Below Android 13
    // there is no runtime permission; it is granted at install.
    val context = LocalContext.current
    var showAlertRationale by remember { mutableStateOf(false) }
    // Build C: which toggle asked for the permission ("alerts" or "rain"),
    // so the grant turns on the right one.
    var permissionFor by remember { mutableStateOf<String?>(null) }
    val alertPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (permissionFor) {
                "alerts" -> onSetAlertsEnabled(true)
                "rain" -> onSetRainAlertsEnabled(true)
            }
        }
        // Denied: the toggle stays off — notifications cannot be delivered
        // without the permission, so enabling the work would be pointless.
        permissionFor = null
    }

    // Local function, declared before use (Kotlin local functions must be
    // declared before the call site in the same composable).
    fun onAlertsToggleChecked(checked: Boolean) {
        if (!checked) {
            onSetAlertsEnabled(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onSetAlertsEnabled(true)
        } else {
            permissionFor = "alerts"
            showAlertRationale = true
        }
    }

    // Build C: same permission flow as the alerts toggle, for rain.
    fun onRainToggleChecked(checked: Boolean) {
        if (!checked) {
            onSetRainAlertsEnabled(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onSetRainAlertsEnabled(true)
        } else {
            permissionFor = "rain"
            showAlertRationale = true
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Room for the floating phone bar, so the last row never
            // hides under it.
            bottom = AuraBars.contentBottomPadding(tickerVisible)
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "SETTINGS & REGION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.Yellow
                )
                Text(
                    text = "UNIT PREFERENCES, LOCATIONS AND DATA SOURCES",
                    style = MaterialTheme.typography.labelSmall,
                    color = RetroTokens.TextSecondary
                )
            }
        }

        // Units Section
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = "Units",
                            tint = RetroTokens.Yellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "USE METRIC UNITS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                            Text(
                                text = if (useMetric) "SI Metric (°C, km/h, hPa, mm)" else "US Customary (°F, mph, inHg, in)",
                                style = MaterialTheme.typography.bodySmall,
                                color = RetroTokens.TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = useMetric,
                        onCheckedChange = onSetUseMetric,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RetroTokens.Yellow,
                            checkedTrackColor = RetroTokens.PanelLight
                        )
                    )
                }
            }
        }

        // Alert Notifications (3.1): a periodic check for the selected
        // place, every 15 minutes. Turning it on asks for the
        // notification permission (with a rationale) the first time.
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert notifications",
                            tint = RetroTokens.Yellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "ALERT NOTIFICATIONS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                            Text(
                                text = if (alertsEnabled) "Checked every 15 minutes for your selected place" else "Get notified when the NWS issues an alert for your selected place",
                                style = MaterialTheme.typography.bodySmall,
                                color = RetroTokens.TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = { onAlertsToggleChecked(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RetroTokens.Yellow,
                            checkedTrackColor = RetroTokens.PanelLight
                        )
                    )
                }
            }
        }

        // Build C: rain-starting alerts. Off by default (opt-in); the
        // same 15-minute worker pass as NWS alerts, the same channel.
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = "Rain-starting alerts",
                            tint = RetroTokens.Border,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "RAIN-STARTING ALERTS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                            Text(
                                text = if (rainAlertsEnabled) "Checked every 15 minutes for your selected place" else "Get notified when rain is about to start for your selected place",
                                style = MaterialTheme.typography.bodySmall,
                                color = RetroTokens.TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = rainAlertsEnabled,
                        onCheckedChange = { onRainToggleChecked(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RetroTokens.Yellow,
                            checkedTrackColor = RetroTokens.PanelLight
                        )
                    )
                }
            }
        }

        // Saved Places (no presets — the user adds their own places)
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Places",
                                tint = RetroTokens.Yellow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "SAVED PLACES",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                        }

                        Button(
                            onClick = { showAddPlaceDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RetroTokens.Yellow,
                                contentColor = RetroTokens.Background
                            ),
                            shape = RetroShapes.small
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Place", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "ADD", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    savedPlaces.forEach { place ->
                        val isSelected = place.id == selectedPlaceId
                        Surface(
                            color = if (isSelected) RetroTokens.PanelLight else RetroTokens.Panel,
                            shape = AuraShapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectPlace(place.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = (if (place.stateCode.isNotBlank()) "${place.name}, ${place.stateCode}" else place.name).uppercase(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = RetroTokens.TextPrimary
                                    )
                                    Text(
                                        text = "${"%.2f".format(Locale.US, place.point.lat)}, ${"%.2f".format(Locale.US, place.point.lon)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = RetroTokens.TextSecondary
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = RetroTokens.Yellow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pirate Weather API Key & 1-Minute Rain
        item {
            var inputKey by remember(pirateWeatherApiKey) { mutableStateOf(pirateWeatherApiKey) }
            var isKeyVisible by remember { mutableStateOf(false) }
            var isSavedMessageVisible by remember { mutableStateOf(false) }

            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Pirate Weather Key",
                                tint = AuraColors.Warning,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "PIRATE WEATHER API KEY",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RetroTokens.TextPrimary
                            )
                        }

                        if (pirateWeatherApiKey.isNotBlank()) {
                            KeyStatusPill(pirateWeatherWorking, pirateWeatherAttempted)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "A Pirate Weather key turns on true 1-minute precipitation for the minute-cast - minute-by-minute rain for the next 60 minutes. Without a key, the minute-cast uses the 15-minute Open-Meteo interpolation. The \"KEY ACTIVE\" pill appears only once the app has actually fetched with your key. Free tier: 10,000 calls a month, no card required. Pirate Weather is a small community service with no SLA, and a new key can take about 20 minutes to go live. Core weather works keyless.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RetroTokens.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = {
                            inputKey = it
                            isSavedMessageVisible = false
                        },
                        label = { Text("Pirate Weather API Key") },
                        placeholder = { Text("Paste your API key here...") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide key" else "Show key",
                                    tint = RetroTokens.TextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = RetroTokens.TextPrimary,
                            unfocusedTextColor = RetroTokens.TextPrimary,
                            focusedBorderColor = RetroTokens.Yellow,
                            unfocusedBorderColor = RetroTokens.Border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (inputKey.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    inputKey = ""
                                    onSavePirateWeatherApiKey("")
                                }
                            ) {
                                Text("Clear", color = RetroTokens.HeaderRed)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = {
                                onSavePirateWeatherApiKey(inputKey)
                                isSavedMessageVisible = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RetroTokens.Yellow,
                                contentColor = RetroTokens.Background
                            ),
                            shape = RetroShapes.medium
                        ) {
                            Text(if (isSavedMessageVisible) "SAVED!" else "SAVE KEY")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Need a key? Free API keys with 10,000 calls/month, no card required, are available at pirate-weather.apiable.io",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = RetroTokens.TextSecondary
                    )
                }
            }
        }

        // Data Sources & Attributions
        item {
            RetroPanel(shape = RetroShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sources",
                            tint = RetroTokens.Yellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "DATA SOURCES & ATTRIBUTION",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AttributionRow("Weather & Alerts", "National Weather Service (NOAA / api.weather.gov)")
                    AttributionRow("Minute-Cast & AQI", "Open-Meteo.com (CC BY 4.0)")
                    AttributionRow("Pollen", "Unavailable — no free US pollen source")
                    AttributionRow("1-Minute Rain (with key)", "Pirate Weather (pirateweather.net)")
                    AttributionRow("Radar Reflectivity", "Iowa Environmental Mesonet (IEM) / NEXRAD")
                    AttributionRow("Radar Basemap", "Esri World Dark Gray Base")
                    AttributionRow("Satellite Imagery", "NASA GIBS (EOSDIS) GOES-East ABI")
                    AttributionRow("Display Font", "Star4000 Large/Small by Nick Smith (twcclassics.com), via the MIT-licensed ws4kp project")
                    AttributionRow("Background Music", "WeatherStar-inspired tracks from the ws4kp project (netbymatt/ws4kp), AI-generated and unencumbered by copyright")
                }
            }
        }

        // Emergency Warning Disclaimer
        item {
            RetroPanel(
                shape = RetroShapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Disclaimer",
                        tint = AuraColors.Warning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Not an official NWS product. In an active tornado warning or severe weather emergency, always prioritize official National Weather Service broadcasts, NOAA Weather Radio, and local emergency management sirens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RetroTokens.TextSecondary
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddPlaceDialog) {
        AddPlaceDialog(
            onDismiss = { showAddPlaceDialog = false },
            onConfirm = { name, state, lat, lon ->
                showAddPlaceDialog = false
                onAddPlace(name, state, lat, lon)
            }
        )
    }

    // 3.1 rationale: shown before the permission request, only when the
    // user turns alerts on. "Not now" leaves the toggle off.
    if (showAlertRationale) {
        AlertDialog(
            onDismissRequest = { showAlertRationale = false },
            title = { Text("ALLOW NOTIFICATIONS?", color = RetroTokens.TextPrimary) },
            text = {
                Text(
                    text = if (permissionFor == "rain") "Aura Weather checks the minute-cast every 15 minutes and notifies you when rain is about to start for your selected place. To receive those warnings, allow notifications." else "Aura Weather checks for National Weather Service alerts every 15 minutes and notifies you when a new one is issued for your selected place. To receive those warnings, allow notifications.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RetroTokens.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAlertRationale = false
                        alertPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RetroTokens.Yellow,
                        contentColor = RetroTokens.Background
                    ),
                    shape = RetroShapes.medium
                ) {
                    Text("ALLOW")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlertRationale = false }) {
                    Text("NOT NOW", color = RetroTokens.TextSecondary)
                }
            },
            containerColor = RetroTokens.PanelLight
        )
    }
}

/**
 * Key status pill for the Settings key sections. Shows yellow
 * "KEY ACTIVE" once the key has actually delivered data, red "KEY
 * FAILED" once a fetch was attempted with the key and it never
 * delivered, and nothing when the key was never tried — so a
 * stored-but-failing key is visible instead of blank.
 */
@Composable
private fun KeyStatusPill(working: Boolean, attempted: Boolean) {
    if (working) {
        Surface(
            color = RetroTokens.Yellow,
            shape = AuraShapes.pill
        ) {
            Text(
                text = "KEY ACTIVE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Background,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    } else if (attempted) {
        Surface(
            color = RetroTokens.HeaderRed,
            shape = AuraShapes.pill
        ) {
            Text(
                text = "KEY FAILED",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = RetroTokens.TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun AttributionRow(title: String, source: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = RetroTokens.TextPrimary
        )
        Text(
            text = source,
            style = MaterialTheme.typography.bodySmall,
            color = RetroTokens.TextSecondary
        )
    }
}

@Composable
private fun AddPlaceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var stateCode by remember { mutableStateOf("KY") }
    var latStr by remember { mutableStateOf("") }
    var lonStr by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADD CUSTOM LOCATION", color = RetroTokens.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("City or Place Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RetroTokens.TextPrimary,
                        unfocusedTextColor = RetroTokens.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = stateCode,
                    onValueChange = { stateCode = it },
                    label = { Text("State Code (e.g. KY, OH)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RetroTokens.TextPrimary,
                        unfocusedTextColor = RetroTokens.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = latStr,
                    onValueChange = { latStr = it },
                    label = { Text("Latitude (e.g. 39.08)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RetroTokens.TextPrimary,
                        unfocusedTextColor = RetroTokens.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lonStr,
                    onValueChange = { lonStr = it },
                    label = { Text("Longitude (e.g. -84.51)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RetroTokens.TextPrimary,
                        unfocusedTextColor = RetroTokens.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = RetroTokens.HeaderRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latStr.toDoubleOrNull()
                    val lon = lonStr.toDoubleOrNull()
                    if (name.isBlank() || lat == null || lon == null) {
                        errorMsg = "Please enter a valid name and coordinate numbers."
                    } else {
                        onConfirm(name, stateCode, lat, lon)
                    }
                }
            ) {
                Text("ADD LOCATION")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RetroTokens.TextSecondary)
            }
        },
        containerColor = RetroTokens.PanelLight
    )
}
