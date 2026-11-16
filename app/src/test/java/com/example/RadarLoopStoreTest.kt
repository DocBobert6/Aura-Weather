package com.example

import com.example.data.mapper.RadarSourceKind
import com.example.ui.radar.engine.AdvanceOutcome
import com.example.ui.radar.engine.FrameFetchState
import com.example.ui.radar.engine.FrameMarkOutcome
import com.example.ui.radar.engine.HoldReason
import com.example.ui.radar.engine.LiveLoopState
import com.example.ui.radar.engine.LoopFrame
import com.example.ui.radar.engine.RadarLoopStore
import com.example.ui.radar.engine.RefreshOutcome
import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for RadarLoopStore — the Phase 2 loop core. */
class RadarLoopStoreTest {

    private fun frames(count: Int = 10, startEpochSec: Long = 1_794_780_000L): List<LoopFrame> =
        (0 until count).map { i ->
            LoopFrame(
                validTime = Instant.ofEpochSecond(startEpochSec + i * 300L),
                label = if (i == count - 1) "LIVE NOW" else "${(count - 1 - i) * 5}M AGO"
            )
        }

    private fun blank1080(): ByteArray =
        Base64.getDecoder().decode(MrmsBlankCheckTest.BLANK_1080X1000_B64)

    private fun readyStore(
        source: RadarSourceKind = RadarSourceKind.MRMS,
        ready: Set<Int> = (0..9).toSet()
    ): RadarLoopStore {
        val store = RadarLoopStore()
        store.resolveGeneration(source, frames())
        val gen = store.generationId
        for (i in ready) {
            store.requestFrame(i, gen)
            store.markFrameReady(i, gen)
        }
        store.startPlayback()
        return store
    }

    // -- Source lock ------------------------------------------------------

    @Test fun `resolveGeneration locks one source for the whole loop`() {
        val store = RadarLoopStore()
        val res = store.resolveGeneration(RadarSourceKind.MRMS, frames())
        assertEquals(RadarSourceKind.MRMS, store.source)
        assertEquals(10, res?.frameCount)
        assertEquals(LiveLoopState.LOADING, store.state)
        assertEquals(9, store.currentIndex) // playhead starts at the newest frame
        assertEquals(0, store.readyCount())
    }

    @Test fun `resolveGeneration rejects an empty frame list`() {
        val store = RadarLoopStore()
        assertNull(store.resolveGeneration(RadarSourceKind.MRMS, emptyList()))
        assertEquals(LiveLoopState.UNAVAILABLE, store.state)
        assertEquals(0L, store.generationId)
    }

    @Test fun `fallback re-resolves the whole loop and resets readiness`() {
        val store = readyStore()
        val genBefore = store.generationId
        assertEquals(LiveLoopState.PLAYING, store.state)
        val res = store.fallbackTo(RadarSourceKind.LIBRE_WXR, frames())
        assertEquals(RadarSourceKind.LIBRE_WXR, store.source)
        assertNotEquals(genBefore, store.generationId)
        assertEquals(0, store.readyCount())
        assertEquals(LiveLoopState.LOADING, store.state)
        assertTrue(res?.changedSource == true)
        assertEquals(1, store.fallbackCount)
        // No frame carried over: the new source's pixels differ even
        // for the same valid time, so no palette mixing is possible.
        for (i in 0..9) assertFalse(store.isReady(i))
    }

    @Test fun `fallback cannot move up the order or stay on the same source`() {
        val store = readyStore()
        assertNull(store.fallbackTo(RadarSourceKind.MRMS, frames()))
        val lw = store.fallbackTo(RadarSourceKind.LIBRE_WXR, frames())
        assertTrue(lw != null)
        // MRMS already failed for this loop: falling back UP is rejected.
        assertNull(store.fallbackTo(RadarSourceKind.MRMS, frames()))
        // Same-source fallback is rejected.
        assertNull(store.fallbackTo(RadarSourceKind.LIBRE_WXR, frames()))
        // Skipping a step DOWN is allowed (LibreWXR known unavailable).
        val store2 = readyStore()
        val iem = store2.fallbackTo(RadarSourceKind.IEM, frames())
        assertTrue(iem != null)
        assertEquals(RadarSourceKind.IEM, store2.source)
    }

    @Test fun `fallback with no generation is rejected`() {
        val store = RadarLoopStore()
        assertNull(store.fallbackTo(RadarSourceKind.IEM, frames()))
    }

    // -- No-timeout advance ----------------------------------------------

    @Test fun `advance holds while the next frame is unready, then advances`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        // Only the newest frame (index 9, the playhead) is ready.
        store.requestFrame(9, gen)
        store.markFrameReady(9, gen)
        assertEquals(LiveLoopState.PRIMED, store.state)
        store.startPlayback()
        // Dwell not elapsed yet: hold for dwelling.
        assertEquals(
            AdvanceOutcome.Hold(HoldReason.DWELLING),
            store.tryAdvance(100)
        )
        // Dwell elapsed (2,250 ms end pause on the newest frame) but
        // frame 0 is not ready: HOLD, never advance past it.
        val held = store.tryAdvance(2250)
        assertEquals(AdvanceOutcome.Hold(HoldReason.WAITING_NEXT), held)
        assertEquals(9, store.currentIndex)
        assertEquals(LiveLoopState.WAITING_NEXT, store.state)
        // Still held on a second call: no timeout advance, ever.
        assertEquals(
            AdvanceOutcome.Hold(HoldReason.WAITING_NEXT),
            store.tryAdvance(10_000)
        )
        assertEquals(9, store.currentIndex)
        // Frame 0 becomes ready: the loop advances immediately (the
        // dwell was already spent waiting).
        store.requestFrame(0, gen)
        store.markFrameReady(0, gen)
        assertEquals(LiveLoopState.PLAYING, store.state)
        val advanced = store.tryAdvance(2250)
        assertEquals(AdvanceOutcome.Advanced(9, 0), advanced)
        assertEquals(0, store.currentIndex)
    }

    @Test fun `dwell is 750ms normally and 2250ms on the newest frame`() {
        val store = readyStore()
        assertEquals(750L, store.dwellMsFor(0))
        assertEquals(750L, store.dwellMsFor(8))
        assertEquals(2250L, store.dwellMsFor(9))
    }

    @Test fun `advance wraps from the newest frame to frame 0`() {
        val store = readyStore()
        store.startPlayback()
        val out = store.tryAdvance(2250)
        assertEquals(AdvanceOutcome.Advanced(9, 0), out)
    }

    @Test fun `paused loop never advances`() {
        val store = readyStore()
        store.setPaused(true)
        assertEquals(
            AdvanceOutcome.Hold(HoldReason.PAUSED),
            store.tryAdvance(10_000)
        )
        assertEquals(9, store.currentIndex)
        store.setPaused(false)
        assertEquals(AdvanceOutcome.Advanced(9, 0), store.tryAdvance(2250))
    }

    @Test fun `stale-generation marks are ignored`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val oldGen = store.generationId
        store.fallbackTo(RadarSourceKind.LIBRE_WXR, frames())
        assertNotEquals(oldGen, store.generationId)
        // A late completion from the old generation changes nothing.
        assertEquals(FrameMarkOutcome.IGNORED, store.markFrameReady(0, oldGen))
        assertFalse(store.isReady(0))
        assertFalse(store.markFrameFailed(1, oldGen))
        assertFalse(store.requestFrame(2, oldGen))
    }

    // -- Refresh + regression --------------------------------------------

    @Test fun `refresh carries readiness by valid time`() {
        val store = readyStore(ready = setOf(7, 8, 9))
        // New list shifted 5 min forward: frames 1..9 survive, 0 drops, 10 arrives.
        val shifted = frames(count = 10, startEpochSec = 1_794_780_300L)
        val result = store.refreshFrames(shifted, nowMs = 1_000L)
        assertEquals(RefreshOutcome.APPLIED, result.outcome)
        assertEquals(3, result.carriedReady)
        assertEquals(3, store.readyCount())
        // Same valid times kept their readiness.
        assertTrue(store.isReady(6)) // was 7
        assertTrue(store.isReady(7)) // was 8
        assertTrue(store.isReady(8)) // was 9
        assertFalse(store.isReady(9)) // brand-new frame
        // Generation id is unchanged: same source, same viewport.
        assertEquals(1L, store.generationId)
        // Playhead followed its frame's valid time (was index 9).
        assertEquals(8, store.currentIndex)
    }

    @Test fun `refresh with a backwards newest time holds the previous list`() {
        val store = readyStore()
        val before = store.frames
        val regressed = frames(count = 10, startEpochSec = 1_794_780_000L - 600L)
        val result = store.refreshFrames(regressed, nowMs = 5_000L)
        assertEquals(RefreshOutcome.HELD_PREVIOUS_REGRESSED, result.outcome)
        assertEquals(before, store.frames)
        assertEquals(10, store.readyCount())
        assertEquals(1, store.regressionCount)
        assertEquals(before.maxOf { it.validTime }, store.lastRegression?.previousNewest)
        assertEquals(regressed.maxOf { it.validTime }, store.lastRegression?.newNewest)
        assertEquals(5_000L, store.lastRegression?.recordedAtMs)
        // The loop never blanked: it is still playing.
        assertEquals(LiveLoopState.PLAYING, store.state)
    }

    @Test fun `refresh with an empty list holds the previous frames`() {
        val store = readyStore()
        val result = store.refreshFrames(emptyList(), nowMs = 1_000L)
        assertEquals(RefreshOutcome.HELD_PREVIOUS_EMPTY, result.outcome)
        assertEquals(10, store.readyCount())
        assertEquals(LiveLoopState.PLAYING, store.state)
    }

    @Test fun `refresh with identical times is a no-op`() {
        val store = readyStore(ready = setOf(9))
        val result = store.refreshFrames(frames(), nowMs = 1_000L)
        assertEquals(RefreshOutcome.NO_CHANGE, result.outcome)
        assertEquals(1, store.readyCount())
    }

    @Test fun `refresh that leaves nothing ready returns to LOADING`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        store.requestFrame(9, gen)
        store.markFrameReady(9, gen)
        assertEquals(LiveLoopState.PRIMED, store.state)
        // Entirely new times: no readiness carries.
        val fresh = frames(count = 10, startEpochSec = 1_794_790_000L)
        val result = store.refreshFrames(fresh, nowMs = 1_000L)
        assertEquals(RefreshOutcome.APPLIED, result.outcome)
        assertEquals(0, result.carriedReady)
        assertEquals(LiveLoopState.LOADING, store.state)
    }

    // -- Failure, retry, recovery ----------------------------------------

    @Test fun `failure of every frame enters UNAVAILABLE, retry recovers`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.IEM, frames())
        val gen = store.generationId
        for (i in 0..9) {
            store.requestFrame(i, gen)
            store.markFrameFailed(i, gen)
        }
        assertEquals(LiveLoopState.UNAVAILABLE, store.state)
        // Retry re-requests ALL frames — including the in-flight and
        // queued ones, not only the failed ones (D11 fix). Here all
        // ten are failed, so all ten come back, current frame first.
        val plan = store.retry()
        assertEquals(gen, plan.generationId)
        assertEquals((0..9).toList(), plan.frameIndices.sorted())
        assertEquals(9, plan.frameIndices.first())
        assertEquals(LiveLoopState.LOADING, store.state)
        for (i in 0..9) assertEquals(FrameFetchState.IN_FLIGHT, store.fetchState(i))
        // Recovery: frames become ready again.
        store.markFrameReady(9, gen)
        assertEquals(LiveLoopState.PRIMED, store.state)
        store.startPlayback()
        assertEquals(LiveLoopState.PLAYING, store.state)
    }

    @Test fun `retry re-requests in-flight frames, not only failed ones`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        store.requestFrame(9, gen)
        store.markFrameReady(9, gen) // current frame ready
        store.requestFrame(0, gen) // in flight, stalled
        store.requestFrame(1, gen)
        store.markFrameFailed(1, gen) // failed
        // Frames 2..8 never requested. Retry must return 0, 1 and 2..8.
        val plan = store.retry()
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), plan.frameIndices)
        assertEquals(FrameFetchState.IN_FLIGHT, store.fetchState(0))
        assertEquals(FrameFetchState.IN_FLIGHT, store.fetchState(1))
    }

    @Test fun `metadata failure with a generation holds the old frames`() {
        val store = readyStore()
        assertTrue(store.markMetadataFailed())
        assertEquals(1, store.metadataFailureCount)
        assertEquals(LiveLoopState.PLAYING, store.state)
        assertEquals(10, store.readyCount())
    }

    @Test fun `metadata failure with no generation is UNAVAILABLE`() {
        val store = RadarLoopStore()
        assertFalse(store.markMetadataFailed())
        assertEquals(LiveLoopState.UNAVAILABLE, store.state)
    }

    // -- Blank handling ---------------------------------------------------

    @Test fun `MRMS blank for an in-window time is a ready empty frame`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        val out = store.markFrameReady(9, gen, imageBytes = blank1080(), timeWithinExtent = true)
        assertEquals(FrameMarkOutcome.READY_EMPTY, out)
        assertTrue(store.isReady(9))
        assertTrue(store.isEmpty(9))
        assertEquals(LiveLoopState.PRIMED, store.state)
    }

    @Test fun `MRMS blank for an out-of-window time is a stale failure`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        val out = store.markFrameReady(9, gen, imageBytes = blank1080(), timeWithinExtent = false)
        assertEquals(FrameMarkOutcome.REJECTED_BLANK_STALE, out)
        assertFalse(store.isReady(9))
        assertEquals(FrameFetchState.FAILED, store.fetchState(9))
        // The loop is not UNAVAILABLE yet: only one frame failed.
        assertEquals(LiveLoopState.LOADING, store.state)
    }

    @Test fun `all frames blank-stale enters UNAVAILABLE`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        for (i in 0..9) {
            store.markFrameReady(i, gen, imageBytes = blank1080(), timeWithinExtent = false)
        }
        assertEquals(LiveLoopState.UNAVAILABLE, store.state)
        assertEquals(0, store.readyCount())
    }

    @Test fun `all ready frames blank is observable but not a failure`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.MRMS, frames())
        val gen = store.generationId
        for (i in 0..9) {
            store.markFrameReady(i, gen, imageBytes = blank1080(), timeWithinExtent = true)
        }
        assertTrue(store.allReadyFramesBlank())
        assertEquals(10, store.blankReadyCount())
        assertEquals(LiveLoopState.PRIMED, store.state)
    }

    @Test fun `blank bytes are ignored for tiled sources`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.LIBRE_WXR, frames())
        val gen = store.generationId
        // The same bytes would be blank for MRMS; for a tiled source
        // there is no single image to hash, so the frame is ready.
        val out = store.markFrameReady(9, gen, imageBytes = blank1080(), timeWithinExtent = false)
        assertEquals(FrameMarkOutcome.READY, out)
        assertTrue(store.isReady(9))
        assertFalse(store.isEmpty(9))
    }

    // -- Scrub ------------------------------------------------------------

    @Test fun `scrub to a ready frame shows it, scrub to an unready frame waits`() {
        val store = readyStore(ready = setOf(3, 9))
        store.startPlayback()
        assertTrue(store.scrubTo(3))
        assertEquals(3, store.currentIndex)
        assertEquals(LiveLoopState.PLAYING, store.state)
        assertTrue(store.scrubTo(5))
        assertEquals(5, store.currentIndex)
        assertEquals(LiveLoopState.WAITING_NEXT, store.state)
        assertFalse(store.scrubTo(10))
        assertFalse(store.scrubTo(-1))
        assertEquals(5, store.currentIndex)
    }

    // -- Tail cap (Phase 4: future / HRRR wiring) -------------------------

    @Test fun `tail cap cuts the list and carries readiness by valid time`() {
        val store = readyStore(ready = setOf(0, 1, 2, 3))
        // Playhead on frame 2; the cap cuts after frame 2.
        store.scrubTo(2)
        val all = frames()
        assertTrue(store.applyTailCap(all.take(3)))
        assertEquals(3, store.frameCount())
        assertEquals(2, store.currentIndex)
        assertTrue(store.isReady(0))
        assertTrue(store.isReady(2))
        assertEquals(LiveLoopState.PLAYING, store.state)
    }

    @Test fun `tail cap moves the playhead when it sat past the cut`() {
        val store = readyStore(ready = setOf(0, 1, 2, 3, 4, 5))
        store.scrubTo(5)
        assertEquals(5, store.currentIndex)
        assertTrue(store.applyTailCap(frames().take(3)))
        assertEquals(3, store.frameCount())
        // Frame 5 is gone: the playhead moves to the newest playable.
        assertEquals(2, store.currentIndex)
        assertTrue(store.isReady(2))
        assertEquals(LiveLoopState.PLAYING, store.state)
    }

    @Test fun `tail cap to an empty list is UNAVAILABLE, never a blank map`() {
        val store = RadarLoopStore()
        store.resolveGeneration(RadarSourceKind.IEM, frames())
        assertTrue(store.applyTailCap(emptyList()))
        assertEquals(0, store.frameCount())
        assertEquals(LiveLoopState.UNAVAILABLE, store.state)
    }

    @Test fun `tail cap with no ready frames left returns to LOADING`() {
        val store = readyStore(ready = setOf(8, 9))
        assertTrue(store.applyTailCap(frames().take(3)))
        assertEquals(3, store.frameCount())
        assertEquals(0, store.readyCount())
        assertEquals(LiveLoopState.LOADING, store.state)
    }

    @Test fun `tail cap with an identical list is a no-op`() {
        val store = readyStore(ready = setOf(0, 1))
        assertFalse(store.applyTailCap(frames()))
        assertEquals(10, store.frameCount())
    }

    @Test fun `tail cap with no generation is a no-op`() {
        val store = RadarLoopStore()
        assertFalse(store.applyTailCap(frames().take(3)))
        assertEquals(0, store.frameCount())
    }

    @Test fun `tail cap keeps a held playhead waiting when its frame is unready`() {
        val store = readyStore(ready = setOf(0, 1))
        store.scrubTo(4)
        assertEquals(4, store.currentIndex)
        assertEquals(LiveLoopState.WAITING_NEXT, store.state)
        // Frame 4 survives the cap but is still unready; frames 0-1
        // are ready, so the loop keeps waiting on frame 4, not blank.
        assertTrue(store.applyTailCap(frames().take(5)))
        assertEquals(4, store.currentIndex)
        assertEquals(LiveLoopState.WAITING_NEXT, store.state)
    }
}
