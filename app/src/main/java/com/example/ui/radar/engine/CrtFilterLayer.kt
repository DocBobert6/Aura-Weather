package com.example.ui.radar.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 90s CRT filter layer — the mask part of a period CRT (scanlines,
 * aperture grille, warm phosphor tint, vignette). No glow, no bloom,
 * no halation, no curvature.
 *
 * Placement: between the MapView and the chrome in [RadarEngineScreen],
 * so the whole radar map gets the treatment and the legend / transport
 * / pills / header stay crisp above it. The layer draws the full size
 * of the screen's Box, so it covers the whole map including on pan
 * and zoom — it is a screen-space overlay, not a map layer, so the
 * map moving underneath it never leaves an uncovered edge.
 *
 * Touch: this layer intercepts NOTHING. It is a plain Box with a
 * draw modifier only — no clickable, no pointerInput, no semantics —
 * so hit-testing skips it entirely and pan / zoom / pinch reach the
 * MapView below, and the chrome controls above it work exactly as
 * before.
 *
 * Cost: the layer reads no state that changes per frame (the toggle
 * is read once by the caller, which either composes this layer or
 * nothing at all). [drawWithCache] builds the vignette brush once per
 * size and replays the cached line draws only when the layer is
 * invalidated — a size change or a toggle flip. The 750ms loop tick,
 * the ticker rotation and the map's own redraws never touch it, and
 * it holds no bitmap and reads no pixels. When the toggle is off the
 * caller composes nothing here, so the cost is zero.
 */
@Composable
fun CrtFilterLayer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val density = this.density
                val scanSpacing = CrtFilter.scanlineSpacingPx(density)
                val scanThickness = CrtFilter.scanlineThicknessPx(density)
                val grilleSpacing = CrtFilter.grilleSpacingPx(density)
                val grilleThickness = CrtFilter.grilleThicknessPx(density)
                val scanColor = Color.Black.copy(alpha = CrtFilter.SCANLINE_ALPHA)
                val grilleColor = Color.Black.copy(alpha = CrtFilter.GRILLE_ALPHA)
                val tintColor = Color(CrtFilter.PHOSPHOR_TINT_ARGB)
                    .copy(alpha = CrtFilter.PHOSPHOR_TINT_ALPHA)
                // CacheDrawScope has size but no center — compute it
                // from the size (center is a DrawScope property, only
                // available inside onDrawBehind).
                val layerCenter = Offset(size.width / 2f, size.height / 2f)
                val vignetteBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = CrtFilter.VIGNETTE_EDGE_ALPHA)
                    ),
                    center = layerCenter,
                    radius = CrtFilter.vignetteRadiusPx(size.width, size.height)
                )
                onDrawBehind {
                    // Warm phosphor glass over the whole map.
                    drawRect(tintColor)
                    // Horizontal scanlines, top to bottom.
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = scanColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = scanThickness
                        )
                        y += scanSpacing
                    }
                    // Vertical aperture-grille stripes, left to right.
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = grilleColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = grilleThickness
                        )
                        x += grilleSpacing
                    }
                    // Vignette: the glass darkens toward the corners.
                    drawRect(brush = vignetteBrush)
                }
            }
    )
}
