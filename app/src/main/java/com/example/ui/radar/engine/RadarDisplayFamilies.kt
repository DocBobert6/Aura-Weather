package com.example.ui.radar.engine

import com.example.data.mapper.RadarSourceKind

/**
 * 0.3.5 radar range-switch guard: which display family owns the map for
 * one (range, source) selection.
 *
 * The engine keeps SEPARATE display families — MRMS two-slot
 * ImageSources (live), live-IEM tiled layers, LibreWXR tiled layers,
 * future (HRRR) tiled layers, and the satellite pair/tiles — and the
 * families sit at different z-order heights (future layers are
 * appended AFTER the MRMS slots, the satellite WMS pair after those),
 * so a family left visible renders ON TOP of the active one. The
 * invariant is: after a range/layer switch, exactly ONE display family
 * is ever visible — the active (range, source) pair's family. No frame
 * from any other range/source may remain visible.
 *
 * 0.3.4 violated it: the Live branches of `refreshDisplay()` hid the
 * other two live families but never the future family, so going from
 * IN 6H back to Live left the sixth-hour frame on top while the live
 * loop advanced underneath (the chrome said LIVE; the map did not).
 * [RadarEngineActivity.refreshDisplay] follows this rule: it hides
 * every family this function does NOT name before showing the active
 * frame. The active family is never hidden by that step, so the
 * within-family crossfade and the within-family hold (an unready next
 * frame keeps the current frame of the SAME family) are untouched —
 * only a hold across families is forbidden.
 *
 * Pure Kotlin — no Android, no Compose, no MapLibre — so the
 * pure-test script can compile it ([RadarDisplayFamiliesTest] covers
 * every (range, source) pair exhaustively).
 */

/** One family of map layers; only one is ever visible at a time. */
enum class RadarDisplayFamily {
    /** MRMS two-slot ImageSources (live radar, primary source). */
    MRMS,

    /** Live IEM tiled layers (live radar, fallback source). */
    LIVE_IEM,

    /** LibreWXR tiled layers (live radar, paired source). */
    LIBRE_WXR,

    /** Future (HRRR) tiled layers (IN 6H / IN 24H / IN 48H). */
    FUTURE,

    /** Satellite: the WMS pair or the tile layers, whichever owns it. */
    SATELLITE
}

/** A (range, layer) selection, in pure terms (no Compose enum). */
enum class RadarDisplayRange {
    LIVE,
    FUTURE_6H,
    FUTURE_24H,
    FUTURE_48H,
    SATELLITE
}

/**
 * The display families visible for ([range], [source]): exactly one,
 * the active pair's family — except Live with no source yet, where
 * nothing is visible (basemap only). A future range always shows the
 * FUTURE family whatever the live source is; satellite always shows
 * SATELLITE.
 */
fun displayFamiliesFor(
    range: RadarDisplayRange,
    source: RadarSourceKind?
): Set<RadarDisplayFamily> = when (range) {
    RadarDisplayRange.SATELLITE -> setOf(RadarDisplayFamily.SATELLITE)
    RadarDisplayRange.FUTURE_6H,
    RadarDisplayRange.FUTURE_24H,
    RadarDisplayRange.FUTURE_48H -> setOf(RadarDisplayFamily.FUTURE)
    RadarDisplayRange.LIVE -> when (source) {
        RadarSourceKind.MRMS -> setOf(RadarDisplayFamily.MRMS)
        RadarSourceKind.IEM -> setOf(RadarDisplayFamily.LIVE_IEM)
        RadarSourceKind.LIBRE_WXR -> setOf(RadarDisplayFamily.LIBRE_WXR)
        null -> emptySet()
    }
}

/**
 * A held future frame is only a valid hold while its layer index is a
 * frame of the ACTIVE future list. Future lists are prefix-aligned
 * (a shorter range is a prefix of the built superset, a tail cap is a
 * leading run), so an index below the active frame count IS the same
 * frame — but after a future→future switch (24 h → 6 h) or a tail cap
 * the old visible index can sit past the end of the new list, and
 * holding it would show a frame the new range never contains. That
 * index must be dropped (basemap briefly), never held.
 */
fun isFutureVisibleIndexValid(visibleIndex: Int, activeFrameCount: Int): Boolean =
    visibleIndex in 0 until activeFrameCount
