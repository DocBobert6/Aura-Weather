package com.example

import com.example.update.ReleaseInfo
import com.example.update.isNewerVersion
import com.example.update.isStableTag
import com.example.update.parseAssetDigest
import com.example.update.parseVersion
import com.example.update.selectUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the in-app updater's version comparison and release filtering. */
class AppUpdateTest {

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        apkUrl: String? = "https://github.com/DocBobert6/Aura-Weather/releases/download/$tag/aura-weather.apk",
    ) = ReleaseInfo(
        tagName = tag,
        draft = draft,
        prerelease = prerelease,
        apkUrl = apkUrl,
        digest = "sha256:" + "a".repeat(64),
        htmlUrl = "https://github.com/DocBobert6/Aura-Weather/releases/tag/$tag",
        body = "Notes for $tag",
    )

    // --- version comparison: full numeric dotted compare ---

    @Test
    fun newerMajorMinor() {
        // Aura Weather versions are "4.0" style (major.minor).
        assertTrue(isNewerVersion("4.1", "4.0"))
        assertTrue(isNewerVersion("5.0", "4.9"))
        assertTrue(isNewerVersion("4.0", "3.9"))
    }

    @Test
    fun newerMajorMinorPatch() {
        assertTrue(isNewerVersion("1.0.0", "0.1.1"))
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun trailingComponentCounts() {
        // The AutoSpace bug class: a trailing component must never
        // be ignored, or a patch update is never offered.
        assertTrue(isNewerVersion("4.0.1", "4.0"))
        assertTrue(isNewerVersion("4.0.1", "4.0.0"))
        assertFalse(isNewerVersion("4.0", "4.0.1"))
        assertFalse(isNewerVersion("4.0.0", "4.0.1"))
    }

    @Test
    fun numericNotLexicographic() {
        // The classic trap: "10" < "9" as text, but 10 > 9 as a number.
        assertTrue(isNewerVersion("1.0.10", "1.0.9"))
        assertFalse(isNewerVersion("1.0.9", "1.0.10"))
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertTrue(isNewerVersion("4.10", "4.9"))
    }

    @Test
    fun equalVersionIsNotNewer() {
        assertFalse(isNewerVersion("4.0", "4.0"))
        assertFalse(isNewerVersion("v4.0", "4.0"))
        assertFalse(isNewerVersion("4.0", "v4.0"))
        assertFalse(isNewerVersion("4.0.0", "4.0"))
        assertFalse(isNewerVersion("4.0", "4.0.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(isNewerVersion("3.9", "4.0"))
        assertFalse(isNewerVersion("4.0", "4.1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun leadingVIsIgnored() {
        assertTrue(isNewerVersion("v4.1", "v4.0"))
        assertTrue(isNewerVersion("v5.0", "4.9"))
    }

    @Test
    fun unparseableVersionIsNeverNewer() {
        assertFalse(isNewerVersion("banana", "4.0"))
        assertFalse(isNewerVersion("4.0", "banana"))
        assertFalse(isNewerVersion("", "4.0"))
        assertFalse(isNewerVersion("4.0", ""))
        assertFalse(isNewerVersion("4.0.x", "4.0"))
    }

    // --- stable tags ---

    @Test
    fun stableTagAcceptsOneToThreeNumericComponents() {
        assertTrue(isStableTag("v4"))
        assertTrue(isStableTag("v4.0"))
        assertTrue(isStableTag("v4.0.1"))
        assertTrue(isStableTag("4.1"))
    }

    @Test
    fun stableTagRejectsSuffixesAndExtras() {
        assertFalse(isStableTag("v4.1-beta.1"))
        assertFalse(isStableTag("v4.0.x"))
        assertFalse(isStableTag("v1.0.0.1"))
        assertFalse(isStableTag("v4."))
        assertFalse(isStableTag(""))
    }

    @Test
    fun parseVersionHandlesOneToThreeComponents() {
        assertEquals(listOf(4), parseVersion("v4"))
        assertEquals(listOf(4, 0), parseVersion("v4.0"))
        assertEquals(listOf(4, 0, 1), parseVersion("v4.0.1"))
        assertNull(parseVersion("v4.0.0.1"))
        assertNull(parseVersion("banana"))
    }

    // --- release selection ---

    @Test
    fun selectUpdatePicksNewestStableNewerRelease() {
        val found = selectUpdate(
            listOf(release("v4.1"), release("v4.0")),
            "4.0"
        )
        assertEquals("v4.1", found?.tagName)
    }

    @Test
    fun selectUpdateSkipsDraftsPrereleasesAndOldReleases() {
        val found = selectUpdate(
            listOf(
                release("v5.0", draft = true),
                release("v4.2", prerelease = true),
                release("v4.1-beta.1"),
                release("v4.0"),
                release("v3.9"),
            ),
            "4.0"
        )
        assertNull(found)
    }

    @Test
    fun selectUpdateReturnsNullWhenUpToDate() {
        assertNull(selectUpdate(listOf(release("v4.0")), "4.0"))
        assertNull(selectUpdate(listOf(release("v3.9")), "4.0"))
        assertNull(selectUpdate(emptyList(), "4.0"))
    }

    @Test
    fun selectUpdateReturnsReleaseWithNoApk() {
        // A newer release with no APK is still returned — the UI
        // says so instead of offering a download.
        val found = selectUpdate(listOf(release("v4.1", apkUrl = null)), "4.0")
        assertEquals("v4.1", found?.tagName)
        assertNull(found?.apkUrl)
    }

    // --- asset digest ---

    @Test
    fun parseAssetDigestAcceptsSha256Hex() {
        val hex = "a".repeat(64)
        assertEquals(hex, parseAssetDigest("sha256:$hex"))
        assertEquals(hex, parseAssetDigest("  sha256:$hex  "))
        assertEquals(hex, parseAssetDigest(hex))
    }

    @Test
    fun parseAssetDigestRejectsMalformed() {
        assertNull(parseAssetDigest(null))
        assertNull(parseAssetDigest(""))
        assertNull(parseAssetDigest("sha256:xyz"))
        assertNull(parseAssetDigest("sha256:" + "a".repeat(63)))
        assertNull(parseAssetDigest("md5:" + "a".repeat(32)))
    }
}
