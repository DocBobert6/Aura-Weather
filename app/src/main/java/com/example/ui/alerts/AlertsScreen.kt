package com.example.ui.alerts

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherAlert
import com.example.ui.designsystem.AuraBars
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.AuraShapes
import com.example.ui.designsystem.LocalSkyHeaderColor
import com.example.ui.designsystem.GlassLevel
import com.example.ui.designsystem.GlassSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlertsScreen(
    alerts: List<WeatherAlert>,
    onViewOnRadar: () -> Unit,
    modifier: Modifier = Modifier,
    fetchedAt: Instant? = null,
    isStale: Boolean = false,
    placeName: String? = null,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    // Cached alerts show their age: a stale alert list is not live data.
    val cacheAgeText = if (isStale && fetchedAt != null) {
        val mins = (Instant.now().epochSecond - fetchedAt.epochSecond) / 60
        if (mins < 60) "Cached • ${mins}m ago" else "Cached • ${mins / 60}h ago"
    } else null
    val sortedAlerts = remember(alerts) {
        alerts.sortedBy { alert ->
            when (alert.severity) {
                AlertSeverity.Extreme -> 0
                AlertSeverity.Severe -> 1
                AlertSeverity.Moderate -> 2
                AlertSeverity.Minor -> 3
                AlertSeverity.Unknown -> 4
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("alerts_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Room for the floating phone bar, so the last row never
            // hides under it.
            bottom = AuraBars.contentBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WEATHER ALERTS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalSkyHeaderColor.current
                    )
                    Text(
                        text = "National Weather Service alerts" + (placeName?.let { " for $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalSkyHeaderColor.current.copy(alpha = 0.65f)
                    )
                    if (cacheAgeText != null) {
                        Text(
                            text = cacheAgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.Warning
                        )
                    }
                }

                if (sortedAlerts.isNotEmpty()) {
                    Surface(
                        color = AuraColors.severityColor(AlertSeverity.Extreme),
                        shape = AuraShapes.pill
                    ) {
                        Text(
                            text = "${sortedAlerts.size} ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (sortedAlerts.isEmpty()) {
            item {
                EmptyAlertsCard()
            }
        } else {
            itemsIndexed(sortedAlerts, key = { _, alert -> alert.id }) { index, alert ->
                AlertExpandableCard(
                    alert = alert,
                    onViewOnRadar = onViewOnRadar,
                    zoneId = zoneId,
                    initiallyExpanded = index == 0
                )
            }
        }
    }
}

@Composable
private fun EmptyAlertsCard() {
    GlassSurface(
        level = GlassLevel.Card,
        shape = AuraShapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "No alerts",
                tint = AuraColors.Accent,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "All Clear in the Region",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No active watches, warnings, or advisories currently issued for this place by the National Weather Service.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun AlertExpandableCard(
    alert: WeatherAlert,
    onViewOnRadar: () -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault(),
    initiallyExpanded: Boolean = false
) {
    // The first card (the most severe — the list is sorted) starts open so
    // the area and instructions are visible without a tap.
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    val (badgeBg, badgeText) = AuraColors.severityStyle(alert.severity)

    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.US).withZone(zoneId)
    }

    GlassSurface(
        level = GlassLevel.Card,
        shape = AuraShapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = AuraShapes.pill
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = alert.event,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Our own summary in a consistent time format ("9:42 AM"), not
            // the raw NWS headline ("9:42AM EST"). The headline is the
            // office's wording; the facts are the same.
            Text(
                text = buildString {
                    append("Issued ${timeFormatter.format(alert.effective)}")
                    append(" · Ends ${timeFormatter.format(alert.ends ?: alert.expires)}")
                    alert.senderName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Freshness pill: a Warning-tinted background with onSurface
            // text. Pale amber (Warning) text on the light pill was washed
            // out; onSurface reads in both modes and the tint keeps the
            // warning meaning.
            Surface(
                color = AuraColors.Warning.copy(alpha = 0.18f),
                shape = AuraShapes.pill
            ) {
                Text(
                    text = run {
                        val end = alert.ends ?: alert.expires
                        val minutes = java.time.Duration.between(Instant.now(), end).toMinutes()
                        if (minutes in 0..120) "Ends in $minutes min" else "Ends ${timeFormatter.format(end)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                    ) {}

                    Spacer(modifier = Modifier.height(12.dp))

                    if (alert.areaDesc.isNotBlank()) {
                        Text(
                            text = "AFFECTED AREAS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = alert.areaDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = "DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = alert.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    if (!alert.instruction.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SAFETY INSTRUCTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AuraColors.Accent
                        )
                        Text(
                            text = alert.instruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onViewOnRadar,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Radar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "View Radar & Warning Area")
                    }
                }
            }
        }
    }
}
