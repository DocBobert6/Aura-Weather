package com.example.ui.radar.engine

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.mapper.RadarFrame
import com.example.ui.designsystem.AuraColors
import com.example.ui.designsystem.GlassIconButton
import com.example.ui.designsystem.PillSegmentedControl
import com.example.ui.designsystem.RetroPanel
import com.example.ui.designsystem.RetroShapes
import com.example.ui.designsystem.RetroTokens
import org.maplibre.android.maps.MapView

/**
 * Phase 1 — the new radar screen's foundation: MapLibre map + retro
 * chrome overlays. The map engine lives in [RadarEngineActivity]
 * (MapView, style, sources, layers); this file is chrome only.
 *
 * Chrome is the old screen's look, unchanged: the same
 * [PillSegmentedControl] / [GlassIconButton] / [RetroPanel] /
 * [RetroTokens] pieces the old `RadarScreen` uses, in the same
 * arrangement (top pill cluster, zoom buttons at centre-end, legend +
 * transport at the bottom). Two deliberate differences from the old
 * screen, both structural, neither visual:
 * - No bottom nav bar or ticker here: both are owned by
 *   `WeatherNavDisplay`, not by the radar screen, and this screen
 *   runs standalone (debug launcher entry) until the Phase 5 cutover
 *   wires it into the nav. The bottom controls therefore pad 16dp,
 *   not `AuraBars.contentBottomPadding`; at cutover that padding
 *   becomes the nav clearance again.
 * - No "Local Site" layer pill: Phase 1 has no place/station state
 *   (standalone activity, Cincinnati default centre). The pill set is
 *   Radar / Sat Visible / Sat IR.
 * - The play button is DISABLED: there is no loop in Phase 1 (the
 *   loop is Phase 2). The slider scrubs frames manually. A disabled
 *   play button says "not yet" honestly; a play button that stepped
 *   one frame would lie about what it does.
 *
 * The dBZ legend pill is a faithful copy of the old screen's private
 * `LegendColorPill` (it is private there; making it public would be
 * an invasive change to the old screen for one 20-line pill).
 */

/** Layers the engine screen offers (no Local Site — see file header). */
enum class EngineLayer {
    Radar,
    SatelliteVis,
    SatelliteIr
}

/** Ranges the engine screen offers. */
enum class EngineRange {
    Live,
    Future24h,
    Future48h
}

// The scrubber's custom thumb is an experimental Material3 API.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarEngineScreen(
    mapView: MapView,
    selectedLayer: EngineLayer,
    selectedRange: EngineRange,
    frames: List<RadarFrame>,
    currentFrameIndex: Int,
    onSelectLayer: (EngineLayer) -> Unit,
    onSelectRange: (EngineRange) -> Unit,
    onScrubFrame: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSatellite = selectedLayer != EngineLayer.Radar
    val isForecastRange = !isSatellite && selectedRange != EngineRange.Live
    val frameCount = frames.size
    val safeFrameIndex = currentFrameIndex.coerceIn(0, (frameCount - 1).coerceAtLeast(0))
    val currentFrame = frames.getOrNull(safeFrameIndex)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Top floating pill cluster: station pill + frame label pill,
        // then the layer chooser as a pill segmented control.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroPanel(
                    shape = RetroShapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = AuraColors.Accent,
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (when (selectedLayer) {
                                EngineLayer.Radar -> "Composite Radar"
                                EngineLayer.SatelliteVis -> "Visible Satellite"
                                EngineLayer.SatelliteIr -> "Infrared Satellite"
                            }).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.Yellow,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                RetroPanel(
                    shape = RetroShapes.medium,
                    modifier = Modifier.height(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = (currentFrame?.label ?: "LIVE NOW").uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentFrame?.label == "LIVE NOW" || currentFrame?.label == "NOW") {
                                RetroTokens.Yellow
                            } else {
                                RetroTokens.TextPrimary
                            },
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Layer chooser as a pill segmented control.
            PillSegmentedControl(
                options = listOf("Radar", "Sat Visible", "Sat IR"),
                selectedIndex = selectedLayer.ordinal,
                onSelect = { onSelectLayer(EngineLayer.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )

            // Time-range chooser: only for radar layers, never
            // satellite (a single image).
            if (!isSatellite) {
                Spacer(modifier = Modifier.height(10.dp))
                PillSegmentedControl(
                    options = listOf("LIVE", "IN 24H", "IN 48H"),
                    selectedIndex = selectedRange.ordinal,
                    onSelect = { onSelectRange(EngineRange.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Future ranges are model output: say so plainly and in a
            // distinct colour, so a forecast is never mistaken for
            // observed radar.
            if (isForecastRange) {
                Spacer(modifier = Modifier.height(10.dp))
                RetroPanel(shape = RetroShapes.medium) {
                    Text(
                        text = "FORECAST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RetroTokens.HeaderOrange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Zoom +/- In-Map Controls (48dp solid navy buttons)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassIconButton(
                onClick = onZoomIn,
                containerColor = RetroTokens.Panel,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = RetroTokens.TextPrimary
                )
            }

            GlassIconButton(
                onClick = onZoomOut,
                containerColor = RetroTokens.Panel,
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = RetroTokens.TextPrimary
                )
            }

            GlassIconButton(
                onClick = onRecenter,
                containerColor = RetroTokens.Panel,
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Recenter",
                    tint = RetroTokens.TextPrimary
                )
            }
        }

        // Bottom Controls: Legend, Playback & Scrub Slider. Radar
        // layers only: satellite is a single image, so there is no
        // dBZ legend and no loop to play. Bottom padding is 16dp —
        // there is no nav bar to clear in the standalone activity
        // (see the file header); at cutover this becomes the nav
        // clearance again.
        if (!isSatellite) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Radar Legend Bar
                RetroPanel(shape = RetroShapes.medium) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DBZ:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.Yellow
                        )
                        for ((label, color) in AuraColors.RadarLegend) {
                            EngineLegendColorPill(label, color)
                        }
                    }
                }

                // Forecast ranges are model output: a short one-line
                // caption on a solid panel, so it never wraps with an
                // orphaned word or collides with map labels. The live
                // clear-air caption stays a plain line.
                if (isForecastRange) {
                    RetroPanel(shape = RetroShapes.medium) {
                        Text(
                            text = "FORECAST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.HeaderOrange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Clear-air speckle: the lightest echoes can be
                    // insects or ground clutter, not rain.
                    RetroPanel(shape = RetroShapes.medium) {
                        Text(
                            text = "LIGHTEST ECHOES CAN BE CLEAR-AIR RETURNS, NOT RAIN.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RetroTokens.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Classic transport pill: play/pause + frame scrubber
                // inside the pill. The frame label lives in the top
                // pill now. Play is disabled in Phase 1 — there is no
                // loop yet (Phase 2); the slider scrubs manually.
                RetroPanel(
                    shape = RetroShapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(
                            onClick = {},
                            enabled = false,
                            containerColor = RetroTokens.Yellow,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play (loop arrives in Phase 2)",
                                tint = RetroTokens.Background
                            )
                        }

                        // A 28dp thumb: the default thumb is a tiny dot
                        // that is hard to grab precisely. The track
                        // keeps the default 48dp touch height.
                        // The range's extent, flanking the scrubber so
                        // it costs no extra row: first frame label to
                        // last frame label.
                        Text(
                            text = (frames.firstOrNull()?.label ?: "-45M").uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = RetroTokens.Yellow
                        )

                        Slider(
                            value = safeFrameIndex.toFloat(),
                            onValueChange = { onScrubFrame(it.toInt()) },
                            valueRange = 0f..(frameCount - 1).coerceAtLeast(1).toFloat(),
                            steps = (frameCount - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = RetroTokens.Yellow,
                                activeTrackColor = RetroTokens.Yellow,
                                inactiveTrackColor = RetroTokens.Border
                            ),
                            thumb = { _ ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            color = RetroTokens.Yellow,
                                            shape = CircleShape
                                        )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = (frames.lastOrNull()?.label ?: "NOW").uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = RetroTokens.Yellow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineLegendColorPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            shape = CircleShape,
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = RetroTokens.TextPrimary
        )
    }
}
