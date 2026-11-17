package com.example

import com.example.data.mapper.RadarSourceKind
import com.example.ui.radar.engine.RadarDisplayFamily
import com.example.ui.radar.engine.RadarDisplayRange
import com.example.ui.radar.engine.displayFamiliesFor
import com.example.ui.radar.engine.isFutureVisibleIndexValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.3.5 radar range-switch guard: [displayFamiliesFor] is the single
 * authority for which display family owns the map, and
 * [RadarEngineActivity.refreshDisplay] hides every family it does not
 * name before showing the active frame.
 *
 * The property under test is the 0.3.4 bug, stated as a rule: for
 * EVERY Live (range, source) pair the future family must NOT be
 * visible — the old Live branches hid the other two live families
 * but never the future one, so IN 6H → Live left the sixth-hour
 * frame on top while the live loop advanced underneath. These tests
 * fail on that old rule (proven by temporarily returning FUTURE
 * alongside the live family — see the sensitivity note in the
 * commit message) and pass on the fixed rule.
 */
class RadarDisplayFamiliesTest {

    private val liveSources =
        listOf(RadarSourceKind.MRMS, RadarSourceKind.IEM, RadarSourceKind.LIBRE_WXR)
    private val futureRanges = listOf(
        RadarDisplayRange.FUTURE_6H,
        RadarDisplayRange.FUTURE_24H,
        RadarDisplayRange.FUTURE_48H
    )
    private val allSources: List<RadarSourceKind?> =
        liveSources + listOf<RadarSourceKind?>(null)

    @Test
    fun `live MRMS shows only MRMS, never the future family`() {
        assertEquals(
            setOf(RadarDisplayFamily.MRMS),
            displayFamiliesFor(RadarDisplayRange.LIVE, RadarSourceKind.MRMS)
        )
    }

    @Test
    fun `live IEM shows only live IEM, never the future family`() {
        assertEquals(
            setOf(RadarDisplayFamily.LIVE_IEM),
            displayFamiliesFor(RadarDisplayRange.LIVE, RadarSourceKind.IEM)
        )
    }

    @Test
    fun `live LibreWXR shows only LibreWXR, never the future family`() {
        assertEquals(
            setOf(RadarDisplayFamily.LIBRE_WXR),
            displayFamiliesFor(RadarDisplayRange.LIVE, RadarSourceKind.LIBRE_WXR)
        )
    }

    @Test
    fun `live with no source yet shows nothing`() {
        assertEquals(
            emptySet<RadarDisplayFamily>(),
            displayFamiliesFor(RadarDisplayRange.LIVE, null)
        )
    }

    @Test
    fun `every future range shows only the future family whatever the live source`() {
        for (range in futureRanges) {
            for (source in allSources) {
                assertEquals(
                    "range=$range source=$source",
                    setOf(RadarDisplayFamily.FUTURE),
                    displayFamiliesFor(range, source)
                )
            }
        }
    }

    @Test
    fun `satellite shows only the satellite family whatever the live source`() {
        for (source in allSources) {
            assertEquals(
                "source=$source",
                setOf(RadarDisplayFamily.SATELLITE),
                displayFamiliesFor(RadarDisplayRange.SATELLITE, source)
            )
        }
    }

    @Test
    fun `exhaustive - no two different selections share a family except by identity`() {
        // Every (range, source) pair names at most one family, and two
        // pairs that name the same family are the same display: the
        // future ranges share FUTURE (one family, three ranges), the
        // satellite selection is alone in SATELLITE, and each live
        // source has its own family. Nothing else overlaps.
        val pairs = mutableListOf<Pair<RadarDisplayRange, RadarSourceKind?>>()
        for (source in allSources) pairs += RadarDisplayRange.LIVE to source
        for (range in futureRanges) {
            for (source in allSources) pairs += range to source
        }
        for (source in allSources) pairs += RadarDisplayRange.SATELLITE to source
        for ((range, source) in pairs) {
            val families = displayFamiliesFor(range, source)
            assertTrue("range=$range source=$source names >1 family", families.size <= 1)
        }
        // The three live families are pairwise distinct, and none of
        // them is the future family.
        val liveFamilies = liveSources.map {
            displayFamiliesFor(RadarDisplayRange.LIVE, it).single()
        }
        assertEquals(3, liveFamilies.toSet().size)
        assertFalse(liveFamilies.contains(RadarDisplayFamily.FUTURE))
    }

    @Test
    fun `future hold index is valid only inside the active frame list`() {
        // No frame showing (-1) is never a stale hold.
        assertFalse(isFutureVisibleIndexValid(-1, 13))
        assertFalse(isFutureVisibleIndexValid(-1, 0))
        // Every index of the active list is a valid hold (prefix-aligned).
        for (i in 0 until 13) assertTrue(isFutureVisibleIndexValid(i, 13))
        // The first index past the list — the 24 h -> 6 h case, where
        // the old visible frame sits beyond the new list — is invalid.
        assertFalse(isFutureVisibleIndexValid(13, 13))
        assertFalse(isFutureVisibleIndexValid(40, 13))
        assertFalse(isFutureVisibleIndexValid(0, 0))
    }
}
