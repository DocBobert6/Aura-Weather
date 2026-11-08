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
import com.example.domain.model.WeatherCondition
import kotlin.random.Random

/**
 * Animated dynamic atmospheric sky background.
 * Renders gradients, sun/moon glow, drifting clouds, rain particles, snow, or stars.
 */
@Composable
fun WeatherSky(
    condition: WeatherCondition,
    isDaytime: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sky_transition")

    val driftOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    val rainOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain"
    )

    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stars"
    )

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

    // Sky gradient colors based on conditions and time of day
    val gradientColors = remember(condition, isDaytime) {
        when {
            condition == WeatherCondition.Thunderstorm -> listOf(
                Color(0xFF0F0B1E),
                Color(0xFF1B1438),
                Color(0xFF2E1A47)
            )
            condition == WeatherCondition.Rain || condition == WeatherCondition.HeavyRain || condition == WeatherCondition.Drizzle -> listOf(
                Color(0xFF121B24),
                Color(0xFF1E2D3D),
                Color(0xFF2C3E50)
            )
            condition == WeatherCondition.Snow -> listOf(
                Color(0xFF162534),
                Color(0xFF233950),
                Color(0xFF355270)
            )
            isDaytime && (condition == WeatherCondition.ClearDay || condition == WeatherCondition.PartlyCloudyDay) -> listOf(
                Color(0xFF1565C0),
                Color(0xFF1E88E5),
                Color(0xFF42A5F5)
            )
            isDaytime -> listOf(
                Color(0xFF263238),
                Color(0xFF37474F),
                Color(0xFF546E7A)
            )
            else -> listOf( // Clear / Partly Cloudy Night
                Color(0xFF070B19),
                Color(0xFF0D172E),
                Color(0xFF132247)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw atmospheric background gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = gradientColors,
                startY = 0f,
                endY = height
            )
        )

        // 2. Draw stars at night
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

        // 3. Draw subtle atmospheric light glow (Sun / Moon)
        val celestialCenter = if (isDaytime) Offset(width * 0.75f, height * 0.16f) else Offset(width * 0.8f, height * 0.18f)
        val celestialColor = if (isDaytime) Color(0xFFFFD54F) else Color(0xFFE0E0E0)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    celestialColor.copy(alpha = if (isDaytime) 0.35f else 0.25f),
                    celestialColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = celestialCenter,
                radius = width * 0.45f
            ),
            radius = width * 0.45f,
            center = celestialCenter
        )

        // 4. Draw animated rain streaks
        if (condition == WeatherCondition.Rain || condition == WeatherCondition.HeavyRain || condition == WeatherCondition.Thunderstorm || condition == WeatherCondition.Drizzle) {
            val strokeWidth = if (condition == WeatherCondition.HeavyRain) 2.5f else 1.5f
            val streakLength = if (condition == WeatherCondition.HeavyRain) 36f else 22f
            val rainColor = Color(0xFFA5D6A7).copy(alpha = 0.45f)

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

        // 5. Draw animated snow flakes
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
