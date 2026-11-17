package com.example.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.example.R
import kotlin.math.max

/**
 * Bitmap-rendered Star4000 text for the classic RemoteViews widgets
 * (Item 3, 0.3.2).
 *
 * Why bitmaps: the @font/ XML route (`android:fontFamily="@font/..."` in
 * the RemoteViews layouts) FAILED on Robert's Pixel launcher — all three
 * widgets rendered system sans (proved 2026-11-16 by rendering the
 * actual TTFs and comparing letterforms against his screenshot). The
 * launcher's RemoteViews inflation does not resolve the bundled font,
 * so the only route that cannot be undone by the host is drawing the
 * text ourselves: load the Star4000 Typeface from the font resources,
 * draw each text run with it on a Canvas into a small bitmap, and set
 * it with `RemoteViews.setImageViewBitmap`. A bitmap carries its own
 * pixels; no host font resolution is involved.
 *
 * Trade-off (for the PR body): bitmap text is not selectable or
 * readable as text by accessibility services — TalkBack gets the run's
 * string as the ImageView's content description instead, and the app
 * itself remains the accessible surface.
 *
 * Safety: this renderer NEVER throws out of [renderText]. A failed font
 * load falls back to the system typeface (still Star4000-shaped layout,
 * still legible); any other failure returns null and the caller hides
 * that run, so a widget update can never crash on text. Bitmaps are
 * text runs only, never full-widget bitmaps, and each run is checked
 * against the Binder budget before it is created.
 */
object WidgetTextBitmapRenderer {

    /** Padding around the glyphs inside each bitmap, in dp. */
    private const val PADDING_DP = 1f

    @Volatile
    private var largeTypeface: Typeface? = null

    @Volatile
    private var smallTypeface: Typeface? = null

    @Volatile
    private var largeLoaded = false

    @Volatile
    private var smallLoaded = false

    /**
     * The Star4000 Typeface for [face], loaded once and cached (a load
     * failure is cached too, as the system fallback, so a broken font
     * can never make every update re-attempt it). `ResourcesCompat`
     * covers API 24+; minSdk is 24.
     */
    private fun typeface(context: Context, face: WidgetTextFace): Typeface {
        return when (face) {
            WidgetTextFace.Large -> synchronized(this) {
                if (!largeLoaded) {
                    largeTypeface = runCatching {
                        ResourcesCompat.getFont(context, R.font.star4000_large)
                    }.getOrNull() ?: Typeface.DEFAULT_BOLD
                    largeLoaded = true
                }
                largeTypeface ?: Typeface.DEFAULT_BOLD
            }
            WidgetTextFace.Small -> synchronized(this) {
                if (!smallLoaded) {
                    smallTypeface = runCatching {
                        ResourcesCompat.getFont(context, R.font.star4000_small)
                    }.getOrNull() ?: Typeface.DEFAULT
                    smallLoaded = true
                }
                smallTypeface ?: Typeface.DEFAULT
            }
        }
    }

    /**
     * Render [text] as a bitmap in the Star4000 [face] at [textSizeSp],
     * in [color], ellipsised to [maxWidthDp] when it would overflow.
     * Returns null for blank text, text that cannot fit even "...", a
     * run over the Binder budget, or any rendering failure — never throws.
     */
    fun renderText(
        context: Context,
        text: String,
        face: WidgetTextFace,
        textSizeSp: Float,
        color: Int,
        maxWidthDp: Float
    ): Bitmap? {
        return try {
            val safe = star4000Safe(text)
            if (safe.isBlank()) return null
            val metrics = context.resources.displayMetrics
            val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, textSizeSp, metrics
            )
            val maxWidthPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, maxWidthDp, metrics
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = typeface(context, face)
                this.textSize = textSizePx
                this.color = color
            }
            val fitted = ellipsizeToWidth(safe, maxWidthPx) { paint.measureText(it) }
            if (fitted.isEmpty()) return null
            val paddingPx = max(
                1,
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, PADDING_DP, metrics
                ).toInt()
            )
            val size = computeBitmapSize(
                paint.measureText(fitted), paint.ascent(), paint.descent(), paddingPx
            )
            if (!bitmapFitsBinderBudget(size.widthPx, size.heightPx)) return null
            val bitmap = Bitmap.createBitmap(
                size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.drawText(
                fitted,
                paddingPx.toFloat(),
                paddingPx - paint.ascent(),
                paint
            )
            bitmap
        } catch (_: Throwable) {
            null
        }
    }
}
