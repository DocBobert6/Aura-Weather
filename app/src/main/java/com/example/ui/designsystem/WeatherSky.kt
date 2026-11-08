package com.example.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.domain.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated mesh sky background (2.4a). A 3×3 grid of colour points from
 * the condition palette ([AuraSky.gradientColors] — the single source, so
 * sky and chrome can never disagree), blended as overlapping radial
 * gradients over the condition's vertical gradient, with a slow drift and
 * a small scroll parallax (the mesh moves slower than the content).
 * Stars, the celestial glow, rain, and snow sit on top, unchanged.
 * No touch reactivity anywhere (decision D3).
 *
 * The sky is static when reduced motion is on (no infinite transition is
 * created at all), and on battery saver only the drift + glow animate —
 * rain and stars hold still.
 */
@Composable
fun WeatherSky(
    condition: WeatherCondition,
    isDaytime: Boolean,
    parallaxPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val reduceMotion = AuraMotion.rememberReducedMotion()
    val powerSave = rememberPowerSaveMode()
    val animateSky = !reduceMotion
    val animateParticles = animateSky && !powerSave
    val (driftOffset, rainOffset, starAlpha) =
        skyAnimationValues(animateSky, animateParticles)

    // Precomputed particle coordinates for zero per-frame allocation
    val starCoords = remember {
        List(40) {
            Pair(Random.nextFloat(), Random.nextFloat())
        }
    }

    val rainDrops = remember {
        List(45) {
            Pair(Random.nextFloat(), Random.nextFloat())
        }
    }

    // Single source for the sky colours (2.2 token). This replaces the
    // local `when` WeatherSky used to carry; Sleet / FreezingRain now take
    // the snow gradient, as the token says.
    val gradientColors = remember(condition, isDaytime) {
        AuraSky.gradientColors(condition, isDaytime)
    }
    // Mesh points: the 3-stop gradient evaluated down the screen, centre
    // column lightened, corners darkened, for an organic colour field.
    val meshPoints = remember(gradientColors) { meshColors(gradientColors) }
    // Parallax: the mesh shifts up to 64dp at 256px of scroll, back to
    // rest at the top of the list.
    val parallaxShiftPx = -(parallaxPx * 0.25f).coerceIn(0f, 64f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Base vertical gradient (the condition's own stops).
        drawRect(
            brush = Brush.verticalGradient(
                colors = gradientColors,
                startY = 0f,
                endY = height
            )
        )

        // 2. Mesh: 9 overlapping radial blobs, drifting slowly and
        // shifting with the scroll parallax.
        meshPoints.forEachIndexed { index, point ->
            val phase = index * 0.7f
            val driftAngle = driftOffset * 2f * PI.toFloat() + phase
            val center = Offset(
                x = point.xFrac * width + sin(driftAngle) * (width * 0.03f),
                y = point.yFrac * height + cos(driftAngle) * (height * 0.015f) + parallaxShiftPx
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        point.color.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = width * 0.55f
                ),
                radius = width * 0.55f,
                center = center
            )
        }

        // 3. Draw stars at night
        if (!isDaytime && condition != WeatherCondition.Cloudy && condition != WeatherCondition.Rain && condition != WeatherCondition.HeavyRain) {
            starCoords.forEach { (xFrac, yFrac) ->
                val x = xFrac * width
                val y = yFrac * (height * 0.55f)
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha * (0.4f + (xFrac * 0.5f))),
                    radius = 1.5f + (xFrac * 1.5f),
                    center = Offset(x, y)
                )
            }
        }

        // 4. Draw subtle atmospheric light glow (Sun / Moon)
        val celestialCenter = if (isDaytime) Offset(width * 0.75f, height * 0.16f) else Offset(width * 0.8f, height * 0.18f)
        val celestialColor = AuraSky.celestialColor(isDaytime)
        // 0.35 alpha at 0.45 width read as a muddy olive smudge on a dark
        // sky. A subtler glow: it should read as light, not a stain.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    celestialColor.copy(alpha = if (isDaytime) 0.18f else 0.25f),
                    celestialColor.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = celestialCenter,
                radius = width * 0.35f
            ),
            radius = width * 0.35f,
            center = celestialCenter
        )

        // 5. Draw animated rain streaks
        if (condition == WeatherCondition.Rain || condition == WeatherCondition.HeavyRain || condition == WeatherCondition.Thunderstorm || condition == WeatherCondition.Drizzle) {
            val strokeWidth = if (condition == WeatherCondition.HeavyRain) 2.5f else 1.5f
            val streakLength = if (condition == WeatherCondition.HeavyRain) 36f else 22f
            val rainColor = AuraColors.Rain.copy(alpha = 0.45f)

            rainDrops.forEach { (xFrac, yFrac) ->
                val startX = (xFrac * width + (driftOffset * 80f)) % width
                val startY = ((yFrac + rainOffset) % 1f) * height
                drawLine(
                    color = rainColor,
                    start = Offset(startX, startY),
                    end = Offset(startX - 6f, startY + streakLength),
                    strokeWidth = strokeWidth
                )
            }
        }

        // 6. Draw animated snow flakes
        if (condition == WeatherCondition.Snow || condition == WeatherCondition.Sleet || condition == WeatherCondition.FreezingRain) {
            rainDrops.forEach { (xFrac, yFrac) ->
                val startX = (xFrac * width + (driftOffset * 120f)) % width
                val startY = ((yFrac + (rainOffset * 0.35f)) % 1f) * height
                drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = 2.0f + (xFrac * 2.0f),
                    center = Offset(startX, startY)
                )
            }
        }
    }
}

/**
 * The sky's three animation phases (drift / rain / star twinkle). When
 * [animate] is false (reduced motion) no infinite transition is created
 * at all, so nothing re-draws on a timer; when [animateParticles] is
 * false (battery saver) the rain and stars hold still and only the drift
 * + glow move.
 */
@Composable
private fun skyAnimationValues(
    animate: Boolean,
    animateParticles: Boolean
): Triple<Float, Float, Float> {
    if (!animate) return Triple(0f, 0f, 1f)
    val transition = rememberInfiniteTransition(label = "sky_transition")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val rain by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain"
    )
    val stars by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stars"
    )
    return Triple(drift, if (animateParticles) rain else 0f, if (animateParticles) stars else 1f)
}

/** One mesh point: grid position + colour. */
private data class MeshPoint(val xFrac: Float, val yFrac: Float, val color: Color)

/**
 * The 3×3 mesh from a 3-stop gradient: each point's colour is the
 * gradient evaluated at the point's height, the centre column catching a
 * little light and the corners falling away.
 */
private fun meshColors(gradient: List<Color>): List<MeshPoint> {
    val xs = listOf(0.15f, 0.5f, 0.85f)
    val ys = listOf(0.10f, 0.45f, 0.88f)
    return ys.flatMapIndexed { row, y ->
        xs.mapIndexed { col, x ->
            var color = gradientAt(gradient, y)
            if (col == 1) color = lerp(color, Color.White, 0.10f)
            if ((row == 0 || row == 2) && col != 1) color = lerp(color, Color.Black, 0.08f)
            MeshPoint(x, y, color)
        }
    }
}

/** A 3-stop gradient evaluated at [fraction] (0 = top, 1 = bottom). */
private fun gradientAt(gradient: List<Color>, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f < 0.5f) {
        lerp(gradient[0], gradient[1], f * 2f)
    } else {
        lerp(gradient[1], gradient[2], (f - 0.5f) * 2f)
    }
}
