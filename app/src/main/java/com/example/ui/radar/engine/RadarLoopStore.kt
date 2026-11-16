package com.example.ui.radar.engine

import com.example.data.mapper.DEFAULT_RADAR_SOURCE_ORDER
import com.example.data.mapper.RadarSourceKind
import com.example.data.mapper.loopDwellMs
import com.example.data.mapper.loopNextIndex
import java.time.Instant

/**
 * Phase 2 — the loop frame store: the pure, testable core of the
 * rebuilt radar loop.
 *
 * Pure Kotlin: no Android, no Compose, no MapLibre, no OkHttp, no
 * coroutines — compiled and unit-tested locally with kotlinc + JUnit
 * (see `RadarLoopStoreTest`). The wiring agent (which owns the
 * fetcher, the MapLibre sources and the chrome) consumes exactly the
 * API in this file; the activity never keeps its own copy of loop
 * state.
 *
 * The store owns five things, and nothing else:
 * 1. **One readiness boolean per frame** ([isReady]) — never an
 *    all-tiles set, never a MapLibre signal. There is NO public
 *    per-source / per-frame "tiles loaded" API in MapLibre 13.7.1
 *    (verified by javap on the published AAR), so readiness is
 *    proven by the app's own fetcher (bytes in hand, reported via
 *    [markFrameReady]) and never assumed from the map engine.
 *    MapLibre's `addOnDidFinishRenderingFrameListener` /
 *    `addOnDidBecomeIdleListener` tell the wiring agent the map
 *    rendered — they do NOT tell this store a frame is ready.
 * 2. **Source lock.** The loop's source ([RadarSourceKind]) is
 *    resolved ONCE per generation for the WHOLE loop, never
 *    per-frame mid-loop. A fallback ([fallbackTo]) re-resolves the
 *    whole loop: new generation, all readiness reset, no frame
 *    carried over — a different source means different pixels even
 *    for the same valid time, and carrying a ready flag across
 *    sources would mix palettes inside one loop (the flicker the
 *    rebuild exists to kill). Falling back UP the order (to a
 *    source that already failed for this loop) is rejected.
 * 3. **No-timeout advance.** [tryAdvance] returns HOLD — never an
 *    advance — when the next frame is not ready. There is no wait
 *    budget and no skip: the loop holds frame N and the chrome
 *    says "waiting" (state [LiveLoopState.WAITING_NEXT]). The old
 *    2 s wait-timeout advance is gone by design
 *    (it was shorter than one median MRMS image fetch, 2.77 s).
 * 4. **Dwell + end pause.** [dwellMsFor] reuses
 *    [loopDwellMs]: 750 ms per frame, 2,250 ms on the newest frame
 *    before the loop wraps.
 * 5. **5-min refresh with regression tolerance.**
 *    [refreshFrames] holds the previous frame list — never blanks
 *    the loop — when the new list is empty or its newest time moved
 *    BACKWARDS (Phase 0 spike 4: the MRMS extent end regressed
 *    within ~10 min). A backwards move is recorded in
 *    [lastRegression] / [regressionCount]. Frame identity across
 *    refreshes is the valid time: a frame with the same valid time
 *    in the new list keeps its readiness ([RefreshResult.carriedReady]).
 *
 * # Generation discipline (the wiring agent must follow this)
 *
 * Every fetch the wiring agent issues is tagged with the
 * [generationId] current at issue time. [requestFrame],
 * [markFrameReady] and [markFrameFailed] all take that id and
 * IGNORE work from a superseded generation — a late MRMS image
 * for generation 3 must never mark a frame ready in generation 4.
 * A generation ends (id increments) on [resolveGeneration] and on
 * [fallbackTo]. A time-only refresh ([refreshFrames]) keeps the
 * generation id: same source, same viewport, same URLs for the
 * carried frames. A BBOX change (pan past the 1.5× margin) is NOT
 * a refresh — it changes every MRMS URL, so it is a new generation
 * via [resolveGeneration] with the same source and the new frames.
 *
 * # Blank frames (clear sky vs stale)
 *
 * [markFrameReady] takes the fetched MRMS image bytes when the
 * source is MRMS and checks them with [isKnownBlankMrmsImage].
 * Blank + time OUTSIDE the current metadata extent is a stale
 * request: the frame is marked FAILED, never ready
 * ([FrameMarkOutcome.REJECTED_BLANK_STALE]) — a stale time can
 * never silently blank the loop. Blank + time INSIDE the extent is
 * a ready, empty frame ([FrameMarkOutcome.READY_EMPTY]): the sky
 * is genuinely clear (proven by live probe 2026-11-15: the newest
 * in-window frame over Cincinnati was blank). [allReadyFramesBlank]
 * lets the wiring agent notice a wholly empty loop and cross-check
 * another source; the store itself never treats clear sky as a
 * failure. Tiled sources (LibreWXR/IEM) have no single image to
 * hash: their bytes, if passed, are ignored for blank detection.
 *
 * # What this file does NOT own
 *
 * Fetching (the fetcher, its retries and its negative cache live in
 * [CoalescingPolicy] + the wiring agent), decoding, MapLibre
 * sources/layers, the 150 ms crossfade, the chrome text, and the
 * clock: time only enters as parameters ([tryAdvance] takes the
 * elapsed dwell; [refreshFrames] takes `nowMs`), so every
 * transition is deterministic in tests.
 */

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

/** One loop frame. Identity is [validTime]: the same valid time in a refreshed list is the same frame. */
data class LoopFrame(val validTime: Instant, val label: String)

/** The Live loop's failure/loading state machine (research §3.2.6). */
enum class LiveLoopState {
    /** Generation accepted, no frame ready yet. */
    LOADING,

    /** At least one frame ready; playback not yet started. */
    PRIMED,

    /** Playhead moving on dwell; the current frame is shown. */
    PLAYING,

    /** Held on the current frame: the next frame is not ready. The chrome must say "waiting". */
    WAITING_NEXT,

    /** Nothing usable: no ready frame and every attempt failed (or metadata failed with no generation). */
    UNAVAILABLE
}

/** Per-frame fetch state, for the retry rule (research D11: retry re-requests in-flight/queued work, not only failed URLs). */
enum class FrameFetchState {
    /** Never requested in this generation. */
    NOT_REQUESTED,

    /** Requested, not yet settled. */
    IN_FLIGHT,

    /** Bytes in hand (and, for MRMS, not a stale blank). */
    READY,

    /** Settled as failed (network error, or MRMS blank for a stale time). */
    FAILED
}

/** What [RadarLoopStore.markFrameReady] decided about one frame. */
enum class FrameMarkOutcome {
    /** Ready, with echo (or a tiled frame: no blank check applies). */
    READY,

    /** Ready but provably no-echo: an MRMS known-blank image for an in-window time (clear sky). */
    READY_EMPTY,

    /** NOT ready: an MRMS known-blank image for a time outside the metadata extent (stale). The frame is marked FAILED. */
    REJECTED_BLANK_STALE,

    /** Ignored: bad index, or the caller's generation id is not the store's current one. Nothing changed. */
    IGNORED
}

/** Why [RadarLoopStore.tryAdvance] held instead of advancing. */
enum class HoldReason {
    /** Still inside the dwell for the current frame — not a failure. */
    DWELLING,

    /** Dwell elapsed but the next frame is not ready — hold N, chrome says "waiting". */
    WAITING_NEXT,

    /** Playback is paused. */
    PAUSED,

    /** The loop has no frames at all. */
    NO_FRAMES
}

/** The result of [RadarLoopStore.tryAdvance]: an advance, or a hold with its reason. */
sealed class AdvanceOutcome {
    /** Playhead moved from [fromIndex] to [toIndex]. */
    data class Advanced(val fromIndex: Int, val toIndex: Int) : AdvanceOutcome()

    /** Playhead stayed; [reason] says why. */
    data class Hold(val reason: HoldReason) : AdvanceOutcome()
}

/** Why [RadarLoopStore.refreshFrames] held the previous frame list instead of applying the new one. */
enum class RefreshOutcome {
    /** New list applied (readiness carried by valid time). */
    APPLIED,

    /** No generation exists yet — nothing to refresh. */
    NO_GENERATION,

    /** New list was empty — previous frames held, never blanked. */
    HELD_PREVIOUS_EMPTY,

    /** Newest time moved backwards — previous frames held, regression recorded. */
    HELD_PREVIOUS_REGRESSED,

    /** New list has exactly the same valid times — nothing changed. */
    NO_CHANGE
}

/** The result of [RadarLoopStore.refreshFrames]. */
data class RefreshResult(
    val outcome: RefreshOutcome,
    /** Frames that kept their readiness across the refresh (same valid time). */
    val carriedReady: Int,
    /** The regression, when [outcome] is [RefreshOutcome.HELD_PREVIOUS_REGRESSED]. */
    val regression: ExtentRegression?
)

/** One recorded backwards move of the frame list's newest time (Phase 0 spike 4). */
data class ExtentRegression(
    /** Newest valid time before the refresh. */
    val previousNewest: Instant,
    /** Newest valid time the refresh tried to install. */
    val newNewest: Instant,
    /** Wall-clock ms supplied by the caller when the regression was recorded. */
    val recordedAtMs: Long
)

/** The result of resolving (or re-resolving) a generation. */
data class GenerationResolution(
    val generationId: Long,
    val source: RadarSourceKind,
    val frameCount: Int,
    /** Frames that kept readiness (always 0 for a new generation; refreshes report via [RefreshResult]). */
    val carriedReady: Int,
    /** True when the source changed (a fallback). */
    val changedSource: Boolean
)

/** What [RadarLoopStore.retry] asks the wiring agent to re-request: every frame that is not ready, current frame first. */
data class RetryPlan(val generationId: Long, val frameIndices: List<Int>)

// ---------------------------------------------------------------------------
// The store
// ---------------------------------------------------------------------------

class RadarLoopStore {

    /** Current generation id. 0 = no generation resolved yet. Increments on every [resolveGeneration] / [fallbackTo]. */
    var generationId: Long = 0L
        private set

    /** The loop's source, locked for the whole current generation. Null before the first resolution. */
    var source: RadarSourceKind? = null
        private set

    /** Current generation's frames, oldest first. */
    var frames: List<LoopFrame> = emptyList()
        private set

    /** Current loop state. */
    var state: LiveLoopState = LiveLoopState.LOADING
        private set

    /** Playhead index into [frames]. Starts at the newest frame on every (re-)resolution. */
    var currentIndex: Int = 0
        private set

    /** Playback pause flag (chrome play/pause). [tryAdvance] holds with [HoldReason.PAUSED] while set. */
    var isPaused: Boolean = false
        private set

    /** Most recent backwards move of the newest frame time, or null when none has been seen. */
    var lastRegression: ExtentRegression? = null
        private set

    /** How many refreshes have been held for a backwards-moving frame list. */
    var regressionCount: Int = 0
        private set

    /** How many whole-loop fallbacks have happened in this store's life. */
    var fallbackCount: Int = 0
        private set

    /** How many metadata fetches have failed while a generation was held (loop kept playing old frames). */
    var metadataFailureCount: Int = 0
        private set

    private var readyFlags: BooleanArray = booleanArrayOf()
    private var emptyFlags: BooleanArray = booleanArrayOf()
    private var fetchStates: Array<FrameFetchState> =
        Array(0) { FrameFetchState.NOT_REQUESTED }

    // -- Reads -----------------------------------------------------------

    /** Frame count of the current generation (0 before the first resolution). */
    fun frameCount(): Int = frames.size

    /** The frame under the playhead, or null when the loop is empty. */
    fun currentFrame(): LoopFrame? = frames.getOrNull(currentIndex)

    /** Valid time of frame [index], or null when out of range. */
    fun frameValidTime(index: Int): Instant? = frames.getOrNull(index)?.validTime

    /** Is frame [index] ready to show? False out of range. */
    fun isReady(index: Int): Boolean = readyFlags.getOrNull(index) == true

    /** Is frame [index] ready AND provably no-echo (MRMS known-blank, in-window)? False out of range. */
    fun isEmpty(index: Int): Boolean = emptyFlags.getOrNull(index) == true

    /** Fetch state of frame [index], or null when out of range. */
    fun fetchState(index: Int): FrameFetchState? = fetchStates.getOrNull(index)

    /** Is the frame under the playhead ready? */
    fun isCurrentReady(): Boolean = isReady(currentIndex)

    /** How many frames are ready. */
    fun readyCount(): Int = readyFlags.count { it }

    /** How many ready frames are provably no-echo. */
    fun blankReadyCount(): Int = readyFlags.indices.count { readyFlags[it] && emptyFlags[it] }

    /**
     * True when at least one frame is ready and EVERY ready frame is
     * provably no-echo — the loop is playing clear sky. The wiring
     * agent may cross-check another source on this signal; the store
     * never treats it as a failure.
     */
    fun allReadyFramesBlank(): Boolean =
        readyCount() > 0 && readyFlags.indices.all { !readyFlags[it] || emptyFlags[it] }

    /** Dwell for frame [index] once shown: 750 ms, 2,250 ms on the newest frame (reuses [loopDwellMs]). */
    fun dwellMsFor(index: Int): Long = loopDwellMs(index, frames.size)

    /** Next playhead index after the current one, wrapping to 0 (reuses [loopNextIndex]). */
    fun nextIndex(): Int = loopNextIndex(currentIndex, frames.size)

    // -- Generation resolution (source lock) -----------------------------

    /**
     * Resolve a (new) generation: lock [newSource] for the WHOLE
     * loop and install [newFrames]. All readiness resets — a new
     * generation is fetched fresh, even when the source is unchanged
     * (a bbox change makes every MRMS URL new). The playhead starts
     * at the newest frame; state becomes [LiveLoopState.LOADING].
     * Returns null and changes nothing when [newFrames] is empty; in
     * that case, when no generation exists at all, the state becomes
     * [LiveLoopState.UNAVAILABLE] (there is nothing to show).
     */
    fun resolveGeneration(
        newSource: RadarSourceKind,
        newFrames: List<LoopFrame>
    ): GenerationResolution? {
        if (newFrames.isEmpty()) {
            if (generationId == 0L) state = LiveLoopState.UNAVAILABLE
            return null
        }
        val changed = source != null && source != newSource
        generationId += 1
        source = newSource
        frames = newFrames.toList()
        readyFlags = BooleanArray(frames.size)
        emptyFlags = BooleanArray(frames.size)
        fetchStates = Array(frames.size) { FrameFetchState.NOT_REQUESTED }
        currentIndex = frames.lastIndex
        isPaused = false
        state = LiveLoopState.LOADING
        return GenerationResolution(
            generationId = generationId,
            source = newSource,
            frameCount = frames.size,
            carriedReady = 0,
            changedSource = changed
        )
    }

    /**
     * Fall back to [newSource], re-resolving the WHOLE loop: new
     * generation, new frame list, ALL readiness reset — no frame is
     * carried over, so the two sources' palettes can never mix
     * inside one loop. The source must move DOWN
     * [DEFAULT_RADAR_SOURCE_ORDER] (to a source that has not already
     * failed for this loop); falling back up, to the current source,
     * or with an empty frame list returns null and changes nothing.
     * Returns null also when no generation exists yet (use
     * [resolveGeneration] for the first resolution).
     */
    fun fallbackTo(
        newSource: RadarSourceKind,
        newFrames: List<LoopFrame>
    ): GenerationResolution? {
        val current = source ?: return null
        if (newFrames.isEmpty()) return null
        if (newSource == current) return null
        val order = DEFAULT_RADAR_SOURCE_ORDER
        if (order.indexOf(newSource) <= order.indexOf(current)) return null
        fallbackCount += 1
        // resolveGeneration resets everything; changedSource is true by construction here.
        return resolveGeneration(newSource, newFrames)
    }

    // -- Refresh (5-min timer + resume) ----------------------------------

    /**
     * Refresh the frame list in place (same source, same viewport —
     * a time-only refresh; a bbox change is a new generation via
     * [resolveGeneration]). Readiness carries across by valid time:
     * a frame whose valid time appears in the new list keeps its
     * ready/empty/fetch state. The playhead follows its frame's
     * valid time when it survives, else moves to the newest frame.
     *
     * The previous list is HELD — never blanked — when the new list
     * is empty ([RefreshOutcome.HELD_PREVIOUS_EMPTY]) or its newest
     * time moved backwards ([RefreshOutcome.HELD_PREVIOUS_REGRESSED];
     * the regression is recorded in [lastRegression] and counted in
     * [regressionCount]). An identical list is a no-op
     * ([RefreshOutcome.NO_CHANGE]).
     */
    fun refreshFrames(newFrames: List<LoopFrame>, nowMs: Long): RefreshResult {
        if (frames.isEmpty()) {
            return RefreshResult(RefreshOutcome.NO_GENERATION, 0, null)
        }
        if (newFrames.isEmpty()) {
            return RefreshResult(RefreshOutcome.HELD_PREVIOUS_EMPTY, 0, null)
        }
        val oldNewest = frames.maxOf { it.validTime }
        val newNewest = newFrames.maxOf { it.validTime }
        if (newNewest < oldNewest) {
            val regression = ExtentRegression(oldNewest, newNewest, nowMs)
            lastRegression = regression
            regressionCount += 1
            return RefreshResult(RefreshOutcome.HELD_PREVIOUS_REGRESSED, 0, regression)
        }
        if (newFrames.map { it.validTime } == frames.map { it.validTime }) {
            return RefreshResult(RefreshOutcome.NO_CHANGE, readyCount(), null)
        }
        val currentTime = frames.getOrNull(currentIndex)?.validTime
        val oldByTime = frames.mapIndexed { i, f -> f.validTime to i }.toMap()
        val newReady = BooleanArray(newFrames.size)
        val newEmpty = BooleanArray(newFrames.size)
        val newFetch = Array(newFrames.size) { FrameFetchState.NOT_REQUESTED }
        var carried = 0
        for ((newIndex, frame) in newFrames.withIndex()) {
            val oldIndex = oldByTime[frame.validTime]
            if (oldIndex != null && readyFlags.getOrNull(oldIndex) == true) {
                newReady[newIndex] = true
                newEmpty[newIndex] = emptyFlags.getOrNull(oldIndex) == true
                newFetch[newIndex] = FrameFetchState.READY
                carried += 1
            }
        }
        frames = newFrames.toList()
        readyFlags = newReady
        emptyFlags = newEmpty
        fetchStates = newFetch
        currentIndex = currentTime?.let { t ->
            newFrames.indexOfFirst { it.validTime == t }.takeIf { it >= 0 }
        } ?: frames.lastIndex
        if (readyCount() == 0) state = LiveLoopState.LOADING
        return RefreshResult(RefreshOutcome.APPLIED, carried, null)
    }

    // -- Tail cap (future / HRRR wiring) ---------------------------------

    /**
     * Apply a tail cap: replace the frame list with [playable], the
     * leading run the wiring agent computed (see `capFutureFrames`).
     * Readiness carries across by valid time, exactly like
     * [refreshFrames]; the playhead follows its frame's valid time
     * when it survives, else moves to the newest playable frame.
     *
     * Unlike [refreshFrames] this deliberately bypasses the
     * backwards-newest guard: a cap's newest time IS earlier by
     * construction (the tail was cut), and that is the point — the
     * cut tail must never play as a blank map. The caller guarantees
     * [playable] is a leading run of the current list (usually a
     * prefix); any valid time not in the current list simply starts
     * NOT_REQUESTED.
     *
     * State after the cap: no frames at all → [LiveLoopState.UNAVAILABLE]
     * (the wiring agent shows the unavailable state, never a blank
     * map); nothing ready → [LiveLoopState.LOADING]; the playhead's
     * frame ready → PLAYING when already playing/waiting, PRIMED
     * otherwise; the playhead's frame unready but something else
     * ready → [LiveLoopState.WAITING_NEXT].
     *
     * Returns true when the frame list changed, false when [playable]
     * is exactly the current list or there is no generation yet.
     */
    fun applyTailCap(playable: List<LoopFrame>): Boolean {
        if (frames.isEmpty()) return false
        if (playable.map { it.validTime } == frames.map { it.validTime }) return false
        val currentTime = frames.getOrNull(currentIndex)?.validTime
        val oldByTime = frames.mapIndexed { i, f -> f.validTime to i }.toMap()
        val newReady = BooleanArray(playable.size)
        val newEmpty = BooleanArray(playable.size)
        val newFetch = Array(playable.size) { FrameFetchState.NOT_REQUESTED }
        for ((newIndex, frame) in playable.withIndex()) {
            val oldIndex = oldByTime[frame.validTime]
            if (oldIndex != null && readyFlags.getOrNull(oldIndex) == true) {
                newReady[newIndex] = true
                newEmpty[newIndex] = emptyFlags.getOrNull(oldIndex) == true
                newFetch[newIndex] = FrameFetchState.READY
            } else if (oldIndex != null) {
                newFetch[newIndex] = fetchStates.getOrNull(oldIndex)
                    ?: FrameFetchState.NOT_REQUESTED
            }
        }
        frames = playable.toList()
        readyFlags = newReady
        emptyFlags = newEmpty
        fetchStates = newFetch
        currentIndex = currentTime?.let { t ->
            playable.indexOfFirst { it.validTime == t }.takeIf { it >= 0 }
        } ?: playable.lastIndex.coerceAtLeast(0)
        state = when {
            frames.isEmpty() -> LiveLoopState.UNAVAILABLE
            readyCount() == 0 -> LiveLoopState.LOADING
            isReady(currentIndex) -> when (state) {
                LiveLoopState.PLAYING, LiveLoopState.WAITING_NEXT -> LiveLoopState.PLAYING
                else -> LiveLoopState.PRIMED
            }
            else -> LiveLoopState.WAITING_NEXT
        }
        return true
    }

    // -- Fetch reporting (generation-guarded) ----------------------------

    /**
     * Mark frame [index] as requested in [requestGenerationId].
     * Returns false (ignored) when the index is out of range, the
     * generation id is stale, or the frame is already in flight or
     * ready. The wiring agent calls this when it issues the fetch so
     * [retry] knows what is in flight.
     */
    fun requestFrame(index: Int, requestGenerationId: Long): Boolean {
        if (requestGenerationId != generationId) return false
        if (index !in frames.indices) return false
        if (fetchStates[index] != FrameFetchState.NOT_REQUESTED &&
            fetchStates[index] != FrameFetchState.FAILED
        ) return false
        fetchStates[index] = FrameFetchState.IN_FLIGHT
        return true
    }

    /**
     * Report frame [index] fetched in [fetchGenerationId]. For an
     * MRMS generation, pass the decoded image bytes in [imageBytes]
     * and whether the frame's requested time is inside the current
     * metadata extent in [timeWithinExtent]: a known-blank image
     * for an OUT-of-extent time is stale, so the frame is marked
     * FAILED ([FrameMarkOutcome.REJECTED_BLANK_STALE]) and never
     * becomes ready; a known-blank image for an IN-extent time is a
     * ready, empty frame ([FrameMarkOutcome.READY_EMPTY]). For tiled
     * sources the bytes are ignored (there is no single image to
     * hash). Returns [FrameMarkOutcome.IGNORED] for a bad index or a
     * stale generation id.
     *
     * State: LOADING → PRIMED on the first ready frame; WAITING_NEXT
     * → PLAYING when the awaited next frame becomes ready;
     * UNAVAILABLE → PRIMED when a frame becomes ready again.
     */
    fun markFrameReady(
        index: Int,
        fetchGenerationId: Long,
        imageBytes: ByteArray? = null,
        timeWithinExtent: Boolean = true
    ): FrameMarkOutcome {
        if (fetchGenerationId != generationId) return FrameMarkOutcome.IGNORED
        if (index !in frames.indices) return FrameMarkOutcome.IGNORED
        if (source == RadarSourceKind.MRMS && imageBytes != null &&
            isKnownBlankMrmsImage(imageBytes)
        ) {
            if (!timeWithinExtent) {
                readyFlags[index] = false
                emptyFlags[index] = false
                fetchStates[index] = FrameFetchState.FAILED
                maybeEnterUnavailable()
                return FrameMarkOutcome.REJECTED_BLANK_STALE
            }
            readyFlags[index] = true
            emptyFlags[index] = true
            fetchStates[index] = FrameFetchState.READY
            onFrameBecameReady(index)
            return FrameMarkOutcome.READY_EMPTY
        }
        readyFlags[index] = true
        emptyFlags[index] = false
        fetchStates[index] = FrameFetchState.READY
        onFrameBecameReady(index)
        return FrameMarkOutcome.READY
    }

    /**
     * Report frame [index] failed in [fetchGenerationId]. Returns
     * false (ignored) for a bad index or a stale generation id. When
     * every frame has been attempted and none is ready, the state
     * becomes [LiveLoopState.UNAVAILABLE].
     */
    fun markFrameFailed(index: Int, fetchGenerationId: Long): Boolean {
        if (fetchGenerationId != generationId) return false
        if (index !in frames.indices) return false
        readyFlags[index] = false
        emptyFlags[index] = false
        fetchStates[index] = FrameFetchState.FAILED
        maybeEnterUnavailable()
        return true
    }

    /**
     * Report a metadata fetch failure (MRMS service info / LibreWXR
     * maps). Returns true when the loop can keep playing held frames
     * (a generation exists — the failure is counted, the state is
     * untouched); false when there is no generation at all, in which
     * case the state becomes [LiveLoopState.UNAVAILABLE].
     */
    fun markMetadataFailed(): Boolean {
        if (frames.isEmpty()) {
            state = LiveLoopState.UNAVAILABLE
            return false
        }
        metadataFailureCount += 1
        return true
    }

    // -- Playback ----------------------------------------------------------

    /**
     * Start playback from [LiveLoopState.PRIMED]. Returns false when
     * not primed or no frame is ready. The wiring agent calls this
     * once the first frame is ready (or on play press); [tryAdvance]
     * also starts playback implicitly on the first advance, so a
     * missed call cannot stall the loop.
     */
    fun startPlayback(): Boolean {
        if (state != LiveLoopState.PRIMED) return false
        if (readyCount() == 0) return false
        state = LiveLoopState.PLAYING
        return true
    }

    /** Pause (`true`) or resume (`false`) playback. Resuming does not advance by itself — [tryAdvance] governs. */
    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    /**
     * Try to advance the playhead after [elapsedMsSinceFrameShown]
     * ms on the current frame. NO-TIMEOUT: when the dwell has
     * elapsed but the next frame is not ready, this returns
     * `Hold(WAITING_NEXT)` and the state becomes
     * [LiveLoopState.WAITING_NEXT] — the loop holds frame N and the
     * chrome says "waiting". It NEVER advances past an unready
     * frame. The wiring agent re-calls this on every readiness
     * change and on its dwell timer; in WAITING_NEXT the dwell is
     * already spent, so the loop advances the moment N+1 becomes
     * ready.
     */
    fun tryAdvance(elapsedMsSinceFrameShown: Long): AdvanceOutcome {
        if (frames.isEmpty()) return AdvanceOutcome.Hold(HoldReason.NO_FRAMES)
        if (isPaused) return AdvanceOutcome.Hold(HoldReason.PAUSED)
        if (elapsedMsSinceFrameShown < dwellMsFor(currentIndex)) {
            return AdvanceOutcome.Hold(HoldReason.DWELLING)
        }
        val next = nextIndex()
        if (!isReady(next)) {
            if (state == LiveLoopState.PLAYING || state == LiveLoopState.PRIMED) {
                state = LiveLoopState.WAITING_NEXT
            }
            return AdvanceOutcome.Hold(HoldReason.WAITING_NEXT)
        }
        val from = currentIndex
        currentIndex = next
        state = LiveLoopState.PLAYING
        return AdvanceOutcome.Advanced(from, next)
    }

    /**
     * Jump the playhead to [index] (slider scrub). Returns false
     * when out of range or the loop is empty. Scrubbing to a ready
     * frame shows it (PLAYING when already playing/waiting, PRIMED
     * otherwise); scrubbing to an unready frame holds with
     * [LiveLoopState.WAITING_NEXT] when something else is ready, or
     * stays LOADING when nothing is.
     */
    fun scrubTo(index: Int): Boolean {
        if (frames.isEmpty() || index !in frames.indices) return false
        currentIndex = index
        state = if (isReady(index)) {
            when (state) {
                LiveLoopState.PLAYING, LiveLoopState.WAITING_NEXT -> LiveLoopState.PLAYING
                else -> LiveLoopState.PRIMED
            }
        } else {
            if (readyCount() > 0) LiveLoopState.WAITING_NEXT else LiveLoopState.LOADING
        }
        return true
    }

    /**
     * Retry: re-request EVERYTHING not ready — in-flight/queued
     * frames as well as failed ones (research D11: a retry that
     * only re-requests failed URLs is a no-op for a stalled queue).
     * The returned indices are ordered current frame first, then
     * the loop order after it, so the frame about to be shown is
     * re-requested first. The returned frames are marked IN_FLIGHT.
     * From UNAVAILABLE with nothing ready the state returns to
     * LOADING; otherwise the state is untouched. Empty plan when
     * there is no generation.
     */
    fun retry(): RetryPlan {
        if (frames.isEmpty()) return RetryPlan(generationId, emptyList())
        val ordered = (currentIndex until frames.size) + (0 until currentIndex)
        val indices = ordered.filter { fetchStates[it] != FrameFetchState.READY }
        for (i in indices) fetchStates[i] = FrameFetchState.IN_FLIGHT
        if (state == LiveLoopState.UNAVAILABLE && readyCount() == 0) {
            state = LiveLoopState.LOADING
        }
        return RetryPlan(generationId, indices)
    }

    // -- Internals ---------------------------------------------------------

    private fun onFrameBecameReady(index: Int) {
        when (state) {
            LiveLoopState.LOADING -> state = LiveLoopState.PRIMED
            LiveLoopState.UNAVAILABLE -> state = LiveLoopState.PRIMED
            LiveLoopState.WAITING_NEXT -> if (index == nextIndex()) {
                state = LiveLoopState.PLAYING
            }
            else -> Unit
        }
    }

    private fun maybeEnterUnavailable() {
        if (frames.isEmpty()) return
        if (readyCount() == 0 && fetchStates.all { it == FrameFetchState.FAILED }) {
            state = LiveLoopState.UNAVAILABLE
        }
    }
}
