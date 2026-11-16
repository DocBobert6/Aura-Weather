package com.example.ui.radar.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Phase 2 — the new radar screen's foundation: MapLibre map + retro
 * chrome overlays. The map engine lives in [RadarEngineController]
 * (MapView, style, sources, layers); this file is chrome only.
 *
 * Chrome is the old screen's look, unchanged: the same
 * [PillSegmentedControl] / [GlassIconButton] / [RetroPanel] /
 * [RetroTokens] pieces the old `RadarScreen` uses, in the same
 * arrangement (top pill cluster, zoom buttons at centre-end, legend +
 * transport at the bottom). Two deliberate differences from the old
 * screen, both structural, neither visual:
 * - No bottom nav bar or ticker here: both are owned by
 *   `WeatherNavDisplay`, not by the radar screen. The bottom controls
 *   therefore pad just 16dp above the bottom of this screen's Box —
 *   NOT `AuraBars.contentBottomPadding(tickerVisible)`. That helper
 *   is scroll clearance (bar height + ticker + navigationBars inset,
 *   PR #18) for lists whose content scrolls under the bar; this
 *   screen's Box already ends above the ticker and the bar (the
 *   shell lays them out in flow below it), so applying it here
 *   double-counted ~121dp and floated the whole bottom stack
 *   mid-screen (transport pill ~121dp above the ticker on the
 *   2026-11-15 device shot). 16dp matches the standalone engine
 *   entry, which had the chrome bottom-aligned.
 * - No "Local Site" layer pill: the place drives the centre instead
 *   (Phase 5). The pill set is Radar / Sat Visible / Sat IR.
 *
 * Phase 2 loop chrome (the controller computes, this file displays):
 * - The play button works now: Pause icon while the loop is playing,
 *   PlayArrow otherwise; disabled unless the loop is PRIMED, PLAYING
 *   or WAITING_NEXT. The slider pauses the loop while the user drags
 *   it (`onScrubStart` on the first drag move, `onScrubEnd` when the
 *   drag finishes) and resumes from the scrubbed frame on release.
 * - A status pill shows the loop's waiting states in plain words
 *   ("LOADING LOOP…", "WAITING FOR NEXT FRAME…") instead of a silent
 *   freeze: when the dwell has elapsed but the next frame is not
 *   ready, the loop holds the current frame and says so.
 * - When no source can provide a loop, an unavailable panel says so
 *   ("LIVE RADAR UNAVAILABLE" / "FORECAST RADAR UNAVAILABLE") with a
 *   RETRY pill that re-resolves the loop.
 * - A source + cost panel names the loop's locked source for the
 *   shown frame (MRMS / LIBREWXR / IEM / HRRR) and the data cost of
 *   the loop so far, measured from actual fetched bytes — never a
 *   fixed estimate. The panel appears only once bytes exist. For a
 *   future range the cost is the range's actual bytes with no cache
 *   claim ("FIRST LOAD 27.7 KB" — nothing caches a future visit),
 *   and a capped tail shows its end note ("FORECAST ENDS ABOUT
 *   11:00 UTC") below the legend; when the cap leaves nothing
 *   playable the unavailable panel carries the state instead.
 *
 * Phase 3 satellite chrome (same panels, lifted out of the
 * radar-only gate — the controller computes, this file displays):
 * - The status pill shows "LOADING SATELLITE…" while the satellite
 *   image resolves, and the unavailable panel shows "SATELLITE
 *   UNAVAILABLE" with the same RETRY pill when there is no image to
 *   hold. The source + cost panel names "GIBS WMS" or "GIBS TILES"
 *   with the switch's actual bytes. The dBZ legend, transport pill
 *   and slider stay radar-only (satellite is a single image).
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
    isPlaying: Boolean,
    playEnabled: Boolean,
    statusText: String?,
    unavailableText: String?,
    sourceLabel: String?,
    costText: String?,
    endNote: String?,
    onSelectLayer: (EngineLayer) -> Unit,
    onSelectRange: (EngineRange) -> Unit,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubFrame: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    // Retained for the shell contract (WeatherNavDisplay passes it);
    // the chrome no longer pads for it — the content Box already
    // ends above the ticker, so the ticker needs no clearance here.
    tickerVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isSatellite = selectedLayer != EngineLayer.Radar
    val isForecastRange = !isSatellite && selectedRange != EngineRange.Live
    val frameCount = frames.size
    val safeFrameIndex = currentFrameIndex.coerceIn(0, (frameCount - 1).coerceAtLeast(0))
    val currentFrame = frames.getOrNull(safeFrameIndex)
    // No frames yet (still loading): the pill must not claim a frame
    // label it does not have. Live shows its newest label; a future
    // range shows its range — "LIVE NOW" on a loading forecast
    // would be the wrong tense entirely.
    val emptyFrameLabel = when (selectedRange) {
        EngineRange.Live -> "LIVE NOW"
        EngineRange.Future24h -> "IN 24H"
        EngineRange.Future48h -> "IN 48H"
    }
    // The slider fires onValueChange on every drag move; the controller
    // needs exactly one start/end pair per drag, so the drag state
    // lives here and the start callback fires on the first move only.
    var scrubDragging by remember { mutableStateOf(false) }

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
                            text = (currentFrame?.label ?: emptyFrameLabel).uppercase(),
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
        // dBZ legend and no loop to play. Satellite gets its own
        // bottom column with just the three shared panels — status,
        // unavailable + RETRY, source + cost — so a satellite visit
        // says what it is doing and what it cost. Bottom padding is
        // just 16dp: the shell's content Box already ends above the
        // ticker and the docked bar, so the chrome sits 16dp above
        // the ticker (see the file header — contentBottomPadding
        // here double-counted the clearance and floated the stack
        // mid-screen).
        if (isSatellite) {
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
                if (unavailableText != null) {
                    EngineUnavailablePanel(unavailableText, onRetry)
                }
                if (statusText != null) {
                    EngineStatusPill(statusText)
                }
                if (sourceLabel != null) {
                    EngineSourceCostPanel(sourceLabel, costText)
                }
            }
        }
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
                // Loop failure state: honest wording plus a RETRY pill.
                // The RETRY pill is a clickable yellow RetroPanel, not
                // a GlassIconButton: that button is a fixed 48dp
                // circle and the word RETRY would clip inside it.
                // Shared with the satellite bottom column (same panel,
                // "SATELLITE UNAVAILABLE" text from the controller).
                if (unavailableText != null) {
                    EngineUnavailablePanel(unavailableText, onRetry)
                }

                // Loop waiting state: the loop holds the current frame
                // while the next one is not ready yet, and says so —
                // never a silent freeze. Shared with satellite
                // ("LOADING SATELLITE…" while resolving).
                if (statusText != null) {
                    EngineStatusPill(statusText)
                }

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

                // Forecast ranges are model output: the top banner
                // says FORECAST, so this column does not repeat it.
                // What it does say, while the tail is capped, is
                // where the forecast ends ("FORECAST ENDS ABOUT
                // 11:00 UTC") — the one forecast fact the top
                // banner cannot carry. The live clear-air caption
                // stays a plain line.
                if (isForecastRange) {
                    if (endNote != null) {
                        RetroPanel(shape = RetroShapes.medium) {
                            Text(
                                text = endNote.uppercase(),
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

                // Loop source + data cost, from actual fetched bytes:
                // the locked source on the left, the cost on the right
                // once any bytes exist. Hidden entirely (not a dash)
                // while the loop has fetched nothing yet. Shared with
                // the satellite bottom column ("GIBS WMS" / "GIBS
                // TILES" + the switch's actual bytes).
                if (sourceLabel != null) {
                    EngineSourceCostPanel(sourceLabel, costText)
                }

                // Classic transport pill: play/pause + frame scrubber
                // inside the pill. The frame label lives in the top
                // pill now. Play works in Phase 2: the loop ticks at
                // 750ms per frame (2250ms on the newest) and the
                // button pauses and resumes it. Dragging the slider
                // pauses the loop until the drag ends.
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
                            onClick = onPlayPause,
                            enabled = playEnabled,
                            containerColor = RetroTokens.Yellow,
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = RetroTokens.Background
                            )
                        }

                        // A 28dp thumb: the default thumb is a tiny dot
                        // that is hard to grab precisely. The track
                        // keeps the default 48dp touch height.
                        // The range's extent, flanking the scrubber so
                        // it costs no extra row: first frame label to
                        // last frame label. No frames yet means no
                        // frame labels — a dash, never an invented
                        // time ("-45M" was a stale Live guess that
                        // also showed on loading forecasts).
                        Text(
                            text = (frames.firstOrNull()?.label ?: "-").uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = RetroTokens.Yellow
                        )

                        Slider(
                            value = safeFrameIndex.toFloat(),
                            onValueChange = {
                                if (!scrubDragging) {
                                    scrubDragging = true
                                    onScrubStart()
                                }
                                onScrubFrame(it.toInt())
                            },
                            onValueChangeFinished = {
                                if (scrubDragging) {
                                    scrubDragging = false
                                    onScrubEnd()
                                }
                            },
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
                            text = (frames.lastOrNull()?.label ?: "-").uppercase(),
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
private fun EngineUnavailablePanel(unavailableText: String, onRetry: () -> Unit) {
    RetroPanel(shape = RetroShapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = unavailableText.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.HeaderOrange,
                modifier = Modifier.weight(1f)
            )
            RetroPanel(
                shape = RetroShapes.medium,
                color = RetroTokens.Yellow,
                modifier = Modifier.clickable { onRetry() }
            ) {
                Text(
                    text = "RETRY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.Background,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun EngineStatusPill(statusText: String) {
    RetroPanel(shape = RetroShapes.medium) {
        Text(
            text = statusText.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RetroTokens.Yellow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EngineSourceCostPanel(sourceLabel: String, costText: String?) {
    RetroPanel(shape = RetroShapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SOURCE: $sourceLabel",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = RetroTokens.Yellow
            )
            if (costText != null) {
                Text(
                    text = costText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = RetroTokens.TextSecondary
                )
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
