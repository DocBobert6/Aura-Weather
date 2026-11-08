package com.example.ui.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * Backdrop blur for the floating glass (2.4b). The sky — and only the sky —
 * is recorded into a [GraphicsLayer] in [WeatherNavDisplay]; a glass
 * surface then draws a blurred copy of that layer, translated so the
 * glass's position lines up with the sky. That is a real blur of what's
 * behind the bar, not just a translucent tint.
 *
 * Two deliberate limits, both documented in the plan:
 * - Only the sky is recorded. Radar's glass opts out (its backdrop is the
 *   map, not the sky) via `LocalBackdropBlur provides null`, and falls
 *   back to the stronger tint-only scrim.
 * - Blur is off on low-RAM devices and in battery saver ([enabled]);
 *   glass then uses the stronger scrim, legibility first.
 */

/** Shared backdrop-blur state, provided by [WeatherNavDisplay]. */
class BackdropBlurState(
    /** The recorded sky layer. */
    val skyLayer: GraphicsLayer,
    /** Whether blur may run on this device right now. */
    val enabled: Boolean
) {
    /**
     * Incremented every time the sky re-records. Glass canvases read it
     * inside their draw block, which subscribes the draw to sky
     * re-records — so the blurred copy stays live as the sky drifts.
     */
    internal var recordVersion by mutableStateOf(0)
        private set

    /** The recorded sky's origin in window coordinates (usually (0, 0)). */
    internal var skyOriginInWindow by mutableStateOf(Offset.Zero)

    /**
     * Record [block] (the sky) into the layer and bump the version so
     * glass redraws. Call from `drawWithContent` on the recording box.
     */
    internal fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        size: IntSize,
        block: DrawScope.() -> Unit
    ) {
        skyLayer.record(density, layoutDirection, size, block)
        recordVersion++
    }
}

/** Null means "no backdrop blur here" (Radar, or a preview). */
val LocalBackdropBlur = staticCompositionLocalOf<BackdropBlurState?> { null }

/**
 * Whether backdrop blur may run: off on low-RAM devices and in battery
 * saver (2.4b guardrail). Read once; a restart picks up a change.
 */
@Composable
fun rememberBackdropBlurEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Do not start a line with `!` right after an `as` cast: kotlinc
        // 2.2.10 misparses it (the `!` line is read as a continuation of
        // the cast). Bind the result to a val and return that instead.
        val enabled = !activityManager.isLowRamDevice && !powerManager.isPowerSaveMode
        enabled
    }
}

/** Which edge of a progressively-blurred bar the blur is weakest at. */
enum class ProgressiveEdge { Top, Bottom }

/**
 * The blurred sky behind a glass surface: the recorded sky layer, drawn
 * translated to this surface's position and blurred by the level's radius.
 * When [progressive] is true (top bar, bottom nav bar only — 2.4c) a
 * second, weaker-blurred copy is masked in at the content edge, so the
 * blur ramps from sharp where text sits to full frost at the far edge.
 */
@Composable
internal fun BoxScope.BackdropBlurCanvas(
    level: GlassLevel,
    shape: Shape,
    progressive: Boolean,
    progressiveEdge: ProgressiveEdge
) {
    val state = LocalBackdropBlur.current ?: return
    val layer = state.skyLayer
    val density = LocalDensity.current
    val blurPx = with(density) { AuraGlass.blurRadius(level).toPx() }
    var offsetInSky by remember { mutableStateOf(Offset.Zero) }
    val positionModifier = Modifier.onGloballyPositioned { coords ->
        offsetInSky = coords.positionInWindow() - state.skyOriginInWindow
    }

    if (progressive) {
        // Strong blur across the whole bar…
        BackdropLayerCanvas(
            layer = layer,
            state = state,
            offsetInSky = offsetInSky,
            blurPx = blurPx,
            mask = null,
            shape = shape,
            positionModifier = positionModifier
        )
        // …plus a weaker-blurred copy masked in at the content edge.
        val mask = if (progressiveEdge == ProgressiveEdge.Top) {
            Brush.verticalGradient(listOf(Color.White, Color.Transparent))
        } else {
            Brush.verticalGradient(listOf(Color.Transparent, Color.White))
        }
        BackdropLayerCanvas(
            layer = layer,
            state = state,
            offsetInSky = offsetInSky,
            blurPx = blurPx / 3f,
            mask = mask,
            shape = shape,
            positionModifier = positionModifier
        )
    } else {
        BackdropLayerCanvas(
            layer = layer,
            state = state,
            offsetInSky = offsetInSky,
            blurPx = blurPx,
            mask = null,
            shape = shape,
            positionModifier = positionModifier
        )
    }
}

/** One blurred copy of the recorded sky, clipped to the glass shape. */
@Composable
private fun BoxScope.BackdropLayerCanvas(
    layer: GraphicsLayer,
    state: BackdropBlurState,
    offsetInSky: Offset,
    blurPx: Float,
    mask: Brush?,
    shape: Shape,
    positionModifier: Modifier
) {
    // The clip lives on a PARENT Box, not on the Canvas's own modifier
    // chain. A clip on the same chain as a graphicsLayer with a
    // renderEffect does not reliably contain the effect's output — the
    // blur's offscreen buffer is a rectangle, and the effect fills its
    // corners, so the lighter square-cornered panel showed inside every
    // rounded card (Batch C moved the clip after the graphicsLayer; the
    // panel persisted on device). A parent-level clip is a separate
    // boundary that the effect cannot bypass: it clips the whole blurred
    // output to the shape.
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .then(positionModifier)
                .graphicsLayer { renderEffect = BlurEffect(blurPx, blurPx) }
        ) {
            // Reading the version subscribes this draw to sky re-records; the
            // value itself is unused.
            @Suppress("UNUSED_VARIABLE")
            val ignored = state.recordVersion
            withTransform({ translate(-offsetInSky.x, -offsetInSky.y) }) {
                drawLayer(layer)
            }
            if (mask != null) {
                // DstIn keeps the blurred copy where the gradient is opaque
                // (the content edge) and lets the stronger blur show through
                // at the far edge.
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
        }
    }
}
