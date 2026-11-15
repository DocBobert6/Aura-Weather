package com.example.ui.radar.engine

import java.security.MessageDigest

/**
 * Phase 2 — known-blank detection for MRMS `exportImage` images.
 *
 * Pure Kotlin (JDK `MessageDigest` only): no Android, no Compose, no
 * MapLibre, no OkHttp, no coroutines — compiled and unit-tested
 * locally with kotlinc + JUnit (see `MrmsBlankCheckTest`).
 *
 * # Why this exists
 *
 * MRMS never errors on a bad/stale time: it answers HTTP 200 with a
 * fully transparent PNG. Phase 0 proved it by hash — the
 * oldest-extent, end+60-min and start−3-h `exportImage` calls all
 * returned 16,390 B, byte-identical — and the rebuild research
 * (§3.3 adapter fix 3) requires image content to be validated so a
 * stale/out-of-window time can never silently blank the loop. This
 * file is that validation for the one case provable by hash: the
 * known-blank image.
 *
 * # Live probe evidence (this VM, 2026-11-15 22:35 UTC / 17:35 EST)
 *
 * Service info extent at probe time: `[1794774721000, 1794781497000]`
 * (epoch ms). Bbox `−87.5,36.6,−81.5,41.6` (the Phase 1 1.0× bbox),
 * `bboxSR=4326&imageSR=3857&format=png32&f=image`.
 *
 * - `time=1794763921000` (extent start − 3 h), size 2048×2048:
 *   HTTP 200, 16,390 B,
 *   SHA-256 `3f4fddeb9336441ae3dc31662c7f5ec7bb564b6f2476f7f60910ec8cbbf96651`
 *   (prefix `3f4fddeb9336441a` reproduces the Phase 0 hash exactly).
 * - `time=1794785097000` (extent end + 60 min), size 2048×2048:
 *   HTTP 200, 16,390 B, byte-identical SHA-256 to the above.
 * - Same two times at 1080×1000 (the Phase 1 engine image size):
 *   HTTP 200, 4,280 B, byte-identical SHA-256
 *   `37d3490d4ce5db2be2c450b39be7fc30ffde76047e134bc2e96e28343817c0a0`.
 * - Control: the NEWEST in-window time (`time=1794781497000`,
 *   1080×1000) also returned 4,280 B with the same blank hash —
 *   the sky over Cincinnati was genuinely clear at probe time, so
 *   there was no echo to render.
 *
 * Full probe record (URLs, times, bytes, hashes):
 * `~/workspace/aura-weather/phase2-probe/mrms_blank_probe.json`.
 *
 * # Two consequences the wiring agent must respect
 *
 * 1. **The blank hash is SIZE-DEPENDENT.** A fully transparent PNG
 *    compresses differently per dimension: 2048×2048 blanks hash
 *    to `3f4f…`, 1080×1000 blanks to `37d3…`. The known set below
 *    covers exactly the sizes the engine requests. A fetch at any
 *    OTHER size has no known hash: [isKnownBlankMrmsImage] returns
 *    false for it (it cannot prove blank by hash), and the wiring
 *    agent must then validate by decoding the image and checking
 *    for non-transparent pixels instead. Never extend the known set
 *    by guessing — only by a fresh live probe.
 * 2. **Blank means "no echo in this image", NOT by itself "stale".**
 *    The control above proves a valid in-window frame can be blank
 *    (clear sky). [RadarLoopStore] therefore combines this check
 *    with the metadata extent: blank + time outside the extent =
 *    failed/stale frame; blank + time inside the extent = a ready,
 *    empty (clear-sky) frame. Treating every blank as a failure
 *    would declare the loop unavailable on every clear day.
 */

/** One known-blank MRMS image: the size it was probed at, its byte count, its SHA-256. */
data class KnownBlankMrmsImage(
    val widthPx: Int,
    val heightPx: Int,
    val byteCount: Int,
    val sha256Hex: String
)

/**
 * Known blank at 2048×2048 (the Phase 0 probe size): 16,390 B.
 * Byte-identical across two out-of-window times (see file header).
 */
val KNOWN_BLANK_MRMS_2048: KnownBlankMrmsImage = KnownBlankMrmsImage(
    widthPx = 2048,
    heightPx = 2048,
    byteCount = 16390,
    sha256Hex = "3f4fddeb9336441ae3dc31662c7f5ec7bb564b6f2476f7f60910ec8cbbf96651"
)

/**
 * Known blank at 1080×1000 (the Phase 1 engine image size,
 * [RADAR_ENGINE_MRMS_IMAGE_WIDTH_PX] × [RADAR_ENGINE_MRMS_IMAGE_HEIGHT_PX]):
 * 4,280 B. Byte-identical across two out-of-window times, and equal
 * to the newest in-window frame on a clear sky (see file header).
 */
val KNOWN_BLANK_MRMS_1080X1000: KnownBlankMrmsImage = KnownBlankMrmsImage(
    widthPx = 1080,
    heightPx = 1000,
    byteCount = 4280,
    sha256Hex = "37d3490d4ce5db2be2c450b39be7fc30ffde76047e134bc2e96e28343817c0a0"
)

/** Every known-blank MRMS image (one entry per probed size). */
val KNOWN_BLANK_MRMS_IMAGES: List<KnownBlankMrmsImage> =
    listOf(KNOWN_BLANK_MRMS_2048, KNOWN_BLANK_MRMS_1080X1000)

/** Lower-case hex SHA-256 of [bytes]. */
fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        for (b in digest) {
            val v = b.toInt() and 0xFF
            append(hex[v ushr 4])
            append(hex[v and 0x0F])
        }
    }
}

/**
 * The known-blank SHA-256 for an MRMS image of [widthPx] ×
 * [heightPx], or null when no blank has been probed at that size.
 * Null means "unverifiable by hash", not "not blank" — the caller
 * must fall back to a decoded-pixel transparency check.
 */
fun knownBlankMrmsSha256ForSize(widthPx: Int, heightPx: Int): String? =
    KNOWN_BLANK_MRMS_IMAGES.firstOrNull {
        it.widthPx == widthPx && it.heightPx == heightPx
    }?.sha256Hex

/**
 * Is [bytes] one of the known-blank MRMS images? True only when
 * BOTH the byte count and the SHA-256 match a [KNOWN_BLANK_MRMS_IMAGES]
 * entry. Length is checked first so a real echo image of a different
 * size can never match on hash alone, and a truncated download of a
 * blank can never match on length alone.
 *
 * Fail-closed by design in the only direction a hash can be:
 * unknown bytes return FALSE (not proven blank). That is not a pass
 * for a blank — a blank at an unprobed size returns false here and
 * the wiring agent must catch it with the decoded-pixel check
 * (see the file header). Returning true for unknown bytes would be
 * worse: it would mark real echo as blank.
 */
fun isKnownBlankMrmsImage(bytes: ByteArray): Boolean {
    val entry = KNOWN_BLANK_MRMS_IMAGES.firstOrNull { it.byteCount == bytes.size }
        ?: return false
    return sha256Hex(bytes) == entry.sha256Hex
}
