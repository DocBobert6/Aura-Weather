package com.example.data.mapper

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Future radar tail (Phase 4, 2026-11-15/16): the honest end of the HRRR
// forecast. Pure Kotlin — covered by FutureRadarTailTest, no Compose, no
// Android.
// ---------------------------------------------------------------------------

/**
 * Per-frame availability of a future (HRRR) frame, as observed by the
 * wiring agent (the fetcher), NOT guessed here:
 * - SERVED: the frame's tiles answered HTTP 200 (the centre tile is
 *   enough for a coverage signal — a 503 there means the lead is not
 *   published; see the probe notes below).
 * - FAILED: the frame's tiles answered 503/404 or errored.
 * - UNKNOWN: not probed yet.
 *
 * The wiring agent probes each frame and re-runs [capFutureFrames] as
 * signals arrive; this file only turns a signal list into a playable
 * list + an honest tail note. It never fetches anything itself.
 */

/** Per-frame served/failed signal for one future frame. */
enum class FutureFrameAvailability {
    SERVED,
    FAILED,
    UNKNOWN
}

/**
 * The playable future list after the tail rule has been applied.
 * - [playableFrames]: the frames the loop may play, oldest first.
 * - [capped]: true when the full list was cut short — i.e. a FAILED
 *   frame exists at or before the end of the list. False means the
 *   whole range serves (or nothing has failed yet).
 * - [endNote]: the honest tail note, or null when the range is not
 *   capped. When capped with at least one playable frame it reads
 *   "FORECAST ENDS ABOUT 11:00 UTC" (the last playable frame's valid
 *   time, UTC); when NOTHING serves it reads "NO FORECAST FRAMES
 *   AVAILABLE" — the loop is empty, and the wiring agent must show
 *   the unavailable state instead of a blank map.
 * - [lastServedValidTime]: the last playable frame's valid time, or
 *   null when nothing is playable.
 * - [droppedCount]: how many frames of the full list were cut.
 * - [firstFailedIndex]: the index of the first FAILED frame, or null
 *   when no frame has failed.
 */
data class FutureTailResult(
    val playableFrames: List<RadarFrame>,
    val capped: Boolean,
    val endNote: String?,
    val lastServedValidTime: Instant?,
    val droppedCount: Int,
    val firstFailedIndex: Int?
)

/** UTC HH:MM for the tail note ("11:00"). Colons are already used in
 * displayed times across the app ("9:42 AM"), so this is safe. */
private val forecastEndTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

/**
 * The honest tail note for a forecast that ends at [endTime] (UTC):
 * "FORECAST ENDS ABOUT 11:00 UTC". Star4000-safe: capitals, digits,
 * spaces and a colon (colons already appear in displayed times).
 */
fun formatForecastEndNote(endTime: Instant): String =
    "FORECAST ENDS ABOUT ${forecastEndTimeFormat.format(endTime)} UTC"

/**
 * THE TAIL RULE — cap at the last reliably-served frame.
 *
 * The playable list is the longest leading run of frames before the
 * first FAILED frame; everything from the first failure on is
 * dropped, even if a later frame later reports SERVED.
 *
 * Why a cap, not a per-frame unavailable state: the loop is a
 * scrubber — a dead frame inside the loop would play as a blank
 * with a message attached, stuttering through the dead tail. A cap
 * keeps the loop contiguous and playable, and [FutureTailResult.endNote]
 * tells the user exactly where the forecast ends, so a dead tail
 * never renders as a blank map with no message.
 *
 * Why the first failure ends the list (monotonic cutoff): IEM's
 * 503s are monotonic in forecast lead — the archive publishes
 * leads in order and the unserved tail is a contiguous block.
 * Probed live 2026-11-16 ~00:30 UTC (run 23:00 UTC, non-synoptic):
 * centre zoom-8 tile served F0090–F2160 (85/97 leads), first 503 at
 * F2220, 503 for every lead after (F2220–F2880, 12 leads); full
 * 77-tile grids confirm it (F2160 77x200, F2880 77x503). The
 * 2026-11-11 probe found the same shape (coverage ending ~F1980–
 * F2160). A lone failure is therefore treated as the end of the
 * forecast, not a hole to skip over — skipping would show a later
 * frame as if the forecast continued past a gap, which is dishonest.
 *
 * UNKNOWN frames before any failure are kept provisionally: the
 * wiring agent probes ahead of the playhead (current frame +
 * look-ahead), so an unprobed frame is "not yet known", not "dead".
 * The wiring agent MUST re-run this function as probe results
 * arrive — a provisional frame that then fails is cut by the next
 * run, and the end note updates with it.
 *
 * [availability] is parallel to [frames]; a shorter list (or a
 * missing entry) reads as UNKNOWN. Extra entries are ignored.
 * An empty frame list yields an empty, uncapped result.
 */
fun capFutureFrames(
    frames: List<RadarFrame>,
    availability: List<FutureFrameAvailability>
): FutureTailResult {
    if (frames.isEmpty()) {
        return FutureTailResult(
            playableFrames = emptyList(),
            capped = false,
            endNote = null,
            lastServedValidTime = null,
            droppedCount = 0,
            firstFailedIndex = null
        )
    }
    val firstFailedIndex = frames.indices.firstOrNull { i ->
        availability.getOrNull(i) == FutureFrameAvailability.FAILED
    }
    val playable = if (firstFailedIndex != null) frames.take(firstFailedIndex) else frames.toList()
    val capped = firstFailedIndex != null
    val lastServed = playable.lastOrNull()?.validTime
    val endNote = when {
        !capped -> null
        lastServed != null -> formatForecastEndNote(lastServed)
        else -> "NO FORECAST FRAMES AVAILABLE"
    }
    return FutureTailResult(
        playableFrames = playable,
        capped = capped,
        endNote = endNote,
        lastServedValidTime = lastServed,
        droppedCount = frames.size - playable.size,
        firstFailedIndex = firstFailedIndex
    )
}

// ---------------------------------------------------------------------------
// Honest future cost text from ACTUAL fetched bytes (Phase 4).
// ---------------------------------------------------------------------------

/**
 * Format a byte count the way the radar cost texts do: "334 B",
 * "28 KB", "3.5 MB" (one decimal, ".0" stripped). Star4000-safe:
 * capitals, digits, spaces and periods only. Negative input is
 * treated as 0 — a byte count is never negative.
 */
fun formatDataBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    if (b < 1024L) return "$b B"
    val kb = b / 1024.0
    if (b < 1024L * 1024L) return "${trimOneDecimal(kb)} KB"
    val mb = b / (1024.0 * 1024.0)
    return "${trimOneDecimal(mb)} MB"
}

/** One decimal, with a ".0" stripped ("3.5", "28"). */
private fun trimOneDecimal(value: Double): String {
    val oneDecimal = (value * 10).toLong() / 10.0
    val text = oneDecimal.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}

/**
 * Honest first-load cost text for a future range, from ACTUAL
 * fetched bytes — the replacement for the stale estimates in
 * [loopPreloadCostText] ("FIRST LOAD ABOUT 3.5 MB THEN CACHED" for
 * 24H, "ABOUT 5 MB" for 48H). Those numbers were never measured:
 * the 2026-11-16 live probe fetched a full 77-tile frame for
 * ~26–29 KB (clear sky — tiles are mostly transparent PNGs), so a
 * whole 24H range can cost far less than 3.5 MB, and a stormy one
 * far more. Fixed numbers are wrong in both directions.
 *
 * The wiring agent calls this with the CUMULATIVE bytes actually
 * fetched for the range so far (sum over the frames fetched), and
 * re-calls it as more frames load — the text then tracks reality
 * instead of asserting an estimate. 0 bytes (nothing fetched yet)
 * reads "FIRST LOAD 0 B".
 *
 * NOTE (Phase 4, Agent B): there is deliberately NO "THEN CACHED"
 * here, although the old estimates carried it. Nothing caches a
 * future visit's bytes: the proving fetcher has no disk cache at
 * all, the future MapLibre sources are volatile (their tiles never
 * persist to the ambient cache), and IEM tiles carry
 * `max-age=300` (5 min) — so even a server-side cache claim would
 * hold only for minutes, not across sessions. A revisit re-fetches.
 * The byte figure itself is exact; the text claims nothing beyond
 * this visit's first load. (The Live loop's `loopLoadCostText`
 * keeps its "THEN CACHED": its MRMS bytes are held in memory
 * across the 5-min refresh within a generation, and its tiled
 * fallbacks replay from MapLibre's in-memory tile pyramid — a
 * within-session claim, not a cross-session one.)
 */
fun futureCostTextFromBytes(bytesFetched: Long): String =
    "FIRST LOAD ${formatDataBytes(bytesFetched)}"
