package com.example

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Header-logo regression guard (0.3.3 launch crash, 2026-11-17).
 *
 * 0.3.3 never opened: every launch crashed with
 * `IllegalArgumentException: Only VectorDrawables and rasterized asset
 * types are supported ex. PNG, JPG, WEBP`, thrown during the first
 * measure/layout pass. The only call site was the header logo in
 * WeatherNavDisplay.kt, which loaded `painterResource(R.mipmap
 * .ic_launcher)`. On API 26+ that mipmap resolves to
 * `mipmap-anydpi-v26/ic_launcher.xml`, an `<adaptive-icon>` — and
 * Compose's painterResource cannot draw adaptive icons (nor the
 * layer-list foreground inside one). The legacy mipmap PNGs exist in
 * the APK but are never selected on API 26+.
 *
 * The fix loads the sun mark from a rasterized PNG asset,
 * `R.drawable.ic_aura_weather_sun_orbit`, which is exactly what
 * painterResource supports. These tests pin that:
 *
 * - [logoDrawable_isRasterizedAsset] — the logo asset is a bitmap
 *   drawable, not an adaptive icon or anything else painterResource
 *   would reject.
 * - [logoPainter_loadsInComposition] — the exact call the app makes
 *   (an Image fed by painterResource on the logo asset) composes
 *   without throwing.
 * - [adaptiveIcon_throwsForPainterResource] — the old call still
 *   throws the same exception, so this file proves it is sensitive to
 *   the regression and not just a test that passes either way.
 *
 * Runs in CI only (Robolectric + Compose test rule — the kotlinc
 * pure-test script has no Android runtime on its classpath).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeaderLogoPainterTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun appResources() =
        ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun logoDrawable_isRasterizedAsset() {
        val drawable = appResources().getDrawable(
            R.drawable.ic_aura_weather_sun_orbit, null
        )
        assertNotNull(drawable)
        assertTrue(
            "header logo must be a rasterized asset painterResource can draw, was ${drawable!!.javaClass.simpleName}",
            drawable is BitmapDrawable
        )
    }

    @Test
    fun launcherMipmap_isNotDrawableByPainterResource() {
        val drawable = appResources().getDrawable(R.mipmap.ic_launcher, null)
        assertNotNull(drawable)
        assertFalse(
            "R.mipmap.ic_launcher must not be a type painterResource accepts (it is an adaptive icon on API 26+)",
            drawable is BitmapDrawable || drawable is VectorDrawable
        )
    }

    @Test
    fun logoPainter_loadsInComposition() {
        composeRule.setContent {
            Image(
                painter = painterResource(R.drawable.ic_aura_weather_sun_orbit),
                contentDescription = "Aura Weather"
            )
        }
        composeRule.waitForIdle()
        composeRule.onRoot().assertExists()
    }

    @Test(expected = IllegalArgumentException::class)
    fun adaptiveIcon_throwsForPainterResource() {
        composeRule.setContent {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "Aura Weather"
            )
        }
        composeRule.waitForIdle()
    }
}
