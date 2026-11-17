package com.example.ui.widget

import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure bitmap-text helpers for the classic RemoteViews widgets (Item 3,
 * 0.3.2). The @font/ XML route failed on Robert's Pixel launcher — all
 * three widgets rendered system sans despite
 * `android:fontFamily="@font/star4000_*"` — so widget text is now drawn
 * with the Star4000 Typeface on a Canvas into a bitmap and set via
 * `RemoteViews.setImageViewBitmap` (see [WidgetTextBitmapRenderer]).
 * This file holds the part of that pipeline that needs no Android:
 * sanitising, ellipsising, bitmap sizing, and the Binder budget check.
 * It is pure Kotlin, so it is unit-testable here and in CI.
 */

/** Which Star4000 face a widget text run uses. */
enum class WidgetTextFace {
    /** Display face: big temperatures. */
    Large,

    /** Label/body face: everything else. */
    Small
}

/**
 * Star4000 has no en dash (U+2013), em dash (U+2014), or ellipsis
 * (U+2026) glyphs — they render blank. The same is true of the micro
 * sign (U+00B5), the superscripts 1/2/3 (U+00B9/U+00B2/U+00B3), the
 * subscript digits (U+2080-U+2089, which render as the missing-glyph
 * box, not blank), the middot (U+00B7), the multiply sign (U+00D7),
 * and the minus sign (U+2212) — all verified with a PIL ink probe of
 * both star4000_large.ttf and star4000_small.ttf (0 ink pixels each;
 * the degree sign U+00B0, the bullet U+2022, and the curly quotes do
 * have ink and are left alone). Strings rendered in a Star4000 face
 * pass through here so a stray character never vanishes or turns
 * into a box. Same rule as HomeScreen's star4000Safe (L-8);
 * duplicated here because this file must stay free of Compose/Android
 * imports for the pure tests.
 */
fun star4000Safe(text: String): String =
    text.replace("\u2014", "-").replace("\u2013", "-").replace("\u2026", "...")
        .replace("\u00B5", "U")
        .replace("\u00B9", "1").replace("\u00B2", "2").replace("\u00B3", "3")
        .replace("\u2080", "0").replace("\u2081", "1").replace("\u2082", "2")
        .replace("\u2083", "3").replace("\u2084", "4").replace("\u2085", "5")
        .replace("\u2086", "6").replace("\u2087", "7").replace("\u2088", "8")
        .replace("\u2089", "9")
        .replace("\u00B7", "-").replace("\u00D7", "x").replace("\u2212", "-")

/** Bytes per pixel of Bitmap.Config.ARGB_8888 — the renderer's config. */
const val WIDGET_BITMAP_BYTES_PER_PIXEL = 4

/**
 * Binder budget for one RemoteViews update: a RemoteViews parcel travels
 * in a single Binder transaction, whose buffer is ~1 MB in total. Every
 * bitmap in one update shares that budget, so the renderer refuses any
 * single run that would eat it alone; the layout caps (see
 * WidgetRemoteViews) keep the real total far below it.
 */
const val WIDGET_BITMAP_BINDER_BUDGET_BYTES = 1_048_576L

/** ARGB_8888 byte size of a [widthPx] x [heightPx] bitmap. */
fun bitmapByteSize(widthPx: Int, heightPx: Int): Long =
    max(0, widthPx).toLong() * max(0, heightPx) * WIDGET_BITMAP_BYTES_PER_PIXEL

/** True when one bitmap of this size fits the Binder budget on its own. */
fun bitmapFitsBinderBudget(
    widthPx: Int,
    heightPx: Int,
    budgetBytes: Long = WIDGET_BITMAP_BINDER_BUDGET_BYTES
): Boolean = bitmapByteSize(widthPx, heightPx) <= budgetBytes

/** Pixel size of one rendered text run. */
data class BitmapTextSize(val widthPx: Int, val heightPx: Int)

/**
 * Bitmap size for one text run, from Paint measurements: [textWidthPx]
 * is `Paint.measureText`, [ascentPx]/[descentPx] are `Paint.ascent()` /
 * `Paint.descent()` (ascent is negative). [paddingPx] is added on all
 * four sides so no glyph edge is clipped. At least 1x1, never 0.
 */
fun computeBitmapSize(
    textWidthPx: Float,
    ascentPx: Float,
    descentPx: Float,
    paddingPx: Int
): BitmapTextSize {
    val width = ceil(textWidthPx).toInt() + paddingPx * 2
    val height = ceil(descentPx - ascentPx).toInt() + paddingPx * 2
    return BitmapTextSize(max(1, width), max(1, height))
}

/**
 * Ellipsize [text] to fit [maxWidthPx] as measured by [measure], keeping
 * as many leading characters as will fit and appending "..." (three
 * periods — Star4000 has no U+2026 ellipsis glyph). Text that already
 * fits is returned unchanged; text that cannot fit even "..." returns
 * "" so the caller can hide the view instead of drawing a stub.
 */
fun ellipsizeToWidth(
    text: String,
    maxWidthPx: Float,
    measure: (String) -> Float
): String {
    if (text.isEmpty() || measure(text) <= maxWidthPx) return text
    val ellipsis = "..."
    if (measure(ellipsis) > maxWidthPx) return ""
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (measure(text.substring(0, mid) + ellipsis) <= maxWidthPx) low = mid
        else high = mid - 1
    }
    return text.substring(0, low) + ellipsis
}
