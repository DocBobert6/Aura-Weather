package com.example.ui.radar.engine

/**
 * The era-accurate radar blip (0.4.0) — the pure half.
 *
 * The classic Weather Channel radar marked your town with a blinking
 * white dot. The engine half ([RadarEngineController]) draws it as a
 * MapLibre [SymbolLayer] geo-anchored to the selected place, so it
 * sits under the CRT filter, survives pan/zoom, and ages like the
 * radar itself; the widget half bakes a still version into the radar
 * widget picture (a widget cannot blink).
 *
 * Pure Kotlin: no Android, no MapLibre, no Compose — compiled and
 * unit-tested locally with kotlinc + JUnit (see `RadarBlipTest`).
 * The icon bitmap itself is drawn with `android.graphics` in the
 * controller (CI-only); only its pixel spec lives here, so the
 * widget's still dot and the engine's icon agree on the shape.
 *
 * # Blink rule
 *
 * The dot flips state on every frame advance — one flip per loop
 * beat. The beat is the loop's frame step: 750 ms per frame
 * ([RADAR_LOOP_DWELL_MS]), 2,250 ms on the newest frame
 * ([RADAR_LOOP_END_PAUSE_MS]) — so on the newest frame the dot
 * holds its state for 2,250 ms, exactly like the loop itself holds
 * that frame. The phase is the playhead's parity: even index =
 * visible, odd index = hidden. Tying the phase to the playhead (not
 * a second timer) means the blink can never drift from the loop:
 * when the loop holds (waiting for the next frame) or pauses, the
 * beat stops and the dot holds with it.
 *
 * When the loop is not playing (paused, loading, primed) the dot
 * holds steady VISIBLE: the marker is the point of the radar, and
 * only the beat blinks it. The blip shows on the satellite layers
 * too (Robert, 2026-11-17): it is the place marker, not just a
 * radar marker, and the same rule applies there — steady VISIBLE
 * while the loop is not playing, blinking with the playhead
 * parity while it plays.
 */

// ---------------------------------------------------------------------------
// MapLibre ids
// ---------------------------------------------------------------------------

/** GeoJson point source holding the selected place. */
const val RADAR_BLIP_SOURCE_ID: String = "radar-blip"

/** Symbol layer drawing the blip icon. */
const val RADAR_BLIP_LAYER_ID: String = "radar-blip-layer"

/** Runtime image id for the dot-plus-ring bitmap. */
const val RADAR_BLIP_IMAGE_ID: String = "radar-blip-icon"

// ---------------------------------------------------------------------------
// Icon spec (px, in the bitmap's own coordinate space)
// ---------------------------------------------------------------------------

/** Icon bitmap: square, this many px per side. */
const val RADAR_BLIP_ICON_SIZE_PX: Int = 96

/** Filled white dot radius. */
const val RADAR_BLIP_DOT_RADIUS_PX: Float = 10f

/** Thin white ring radius, around the dot. */
const val RADAR_BLIP_RING_RADIUS_PX: Float = 32f

/** Thin white ring stroke width. */
const val RADAR_BLIP_RING_STROKE_PX: Float = 4f

// ---------------------------------------------------------------------------
// Blink phase (pure)
// ---------------------------------------------------------------------------

/**
 * Is the blip visible on playhead frame [frameIndex]? Even = visible,
 * odd = hidden — one flip per frame advance, i.e. one flip per
 * 750 ms dwell (2,250 ms on the newest frame).
 */
fun radarBlipVisibleForFrameIndex(frameIndex: Int): Boolean =
    frameIndex % 2 == 0

/** What the blip layer should show right now. */
enum class RadarBlipDisplay {
    /** Dot drawn (blinking on-phase, or steady while not playing). */
    VISIBLE,

    /** Dot hidden (blinking off-phase while the loop plays). */
    HIDDEN
}

/**
 * The blip's display state: steady VISIBLE while the loop is not
 * playing; blinking with the playhead parity while it plays. The
 * rule is layer-independent — radar and satellite alike (Robert,
 * 2026-11-17: the blip shows on satellite too).
 */
fun radarBlipDisplay(
    isLoopPlaying: Boolean,
    currentFrameIndex: Int
): RadarBlipDisplay {
    if (!isLoopPlaying) return RadarBlipDisplay.VISIBLE
    return if (radarBlipVisibleForFrameIndex(currentFrameIndex)) {
        RadarBlipDisplay.VISIBLE
    } else {
        RadarBlipDisplay.HIDDEN
    }
}
