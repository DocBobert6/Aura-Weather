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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
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
    zoneId: ZoneId = ZoneId.systemDefault(),
    tickerVisible: Boolean = false,
    // 3.1 notification deep link: the notified alert starts expanded and
    // the list scrolls to it. Null on a normal visit (first card opens).
    highlightAlertId: String? = null
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

    val listState = rememberLazyListState()
    LaunchedEffect(highlightAlertId, sortedAlerts) {
        val index = sortedAlerts.indexOfFirst { it.id == highlightAlertId }
        if (index >= 0) listState.scrollToItem(index)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("alerts_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Room for the floating phone bar, so the last row never
            // hides under it.
            bottom = AuraBars.contentBottomPadding(tickerVisible)
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
                        color = RetroTokens.Yellow
                    )
                    Text(
                        text = ("National Weather Service alerts" + (placeName?.let { " for $it" } ?: "")).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RetroTokens.TextSecondary
                    )
                    if (cacheAgeText != null) {
                        Text(
                            text = cacheAgeText.uppercase(),
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
                    initiallyExpanded = index == 0 || alert.id == highlightAlertId
                )
            }
        }
    }
}

@Composable
private fun EmptyAlertsCard() {
    RetroPanel(
        shape = RetroShapes.medium,
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
                tint = RetroTokens.Yellow,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ALL CLEAR IN THE REGION",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No active watches, warnings, or advisories currently issued for this place by the National Weather Service.",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroTokens.TextSecondary,
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
    // the area and instructions are visible without a tap. Keyed on
    // initiallyExpanded so a highlight arriving after first composition
    // (a second notification tap while already on Alerts) still expands
    // the highlighted card; a manual collapse is kept while the key is
    // unchanged, and items are keyed by alert id so the state follows
    // the alert, not the list position.
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }

    val (badgeBg, badgeText) = AuraColors.severityStyle(alert.severity)

    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.US).withZone(zoneId)
    }

    RetroPanel(
        shape = RetroShapes.medium,
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
                    tint = RetroTokens.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = alert.event,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Our own summary in a consistent time format ("9:42 AM"), not
            // the raw NWS headline ("9:42AM EST"). The headline is the
            // office's wording; the facts are the same.
            Text(
                text = buildString {
                    append("Issued ${timeFormatter.format(alert.effective)}")
                    append(" - Ends ${timeFormatter.format(alert.ends ?: alert.expires)}")
                    alert.senderName?.let { append(" - $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = RetroTokens.TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Ends pill: a solid severity-colour block with white text,
            // matching the badge above.
            Surface(
                color = AuraColors.severityColor(alert.severity),
                shape = AuraShapes.pill
            ) {
                Text(
                    text = run {
                        val end = alert.ends ?: alert.expires
                        val minutes = java.time.Duration.between(Instant.now(), end).toMinutes()
                        if (minutes in 0..120) "ENDS IN $minutes\u00A0MIN" else "ENDS ${timeFormatter.format(end).uppercase()}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Surface(
                        color = RetroTokens.Border,
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
                            color = RetroTokens.TextSecondary
                        )
                        Text(
                            text = alert.areaDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = RetroTokens.TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = "DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.TextSecondary
                    )
                    Text(
                        text = alert.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = RetroTokens.TextPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    if (!alert.instruction.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SAFETY INSTRUCTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.Yellow
                        )
                        Text(
                            text = alert.instruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = RetroTokens.TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onViewOnRadar,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RetroTokens.Yellow,
                            contentColor = RetroTokens.Background
                        ),
                        shape = RetroShapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Radar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "VIEW RADAR & WARNING AREA")
                    }
                }
            }
        }
    }
}
