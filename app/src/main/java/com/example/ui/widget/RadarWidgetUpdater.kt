package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.AuraWeatherApp
import com.example.ui.radar.engine.CoalescingFetcher
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The radar widget's render path (Item 4): one entry point for every
 * trigger — the receiver's onUpdate and the 15-minute
 * [com.example.data.work.RadarWidgetWorker] — so both stay in step.
 *
 * Data is fetched fresh here (unlike the main/strip widgets, which
 * re-render from the weather cache): a radar image cannot come from
 * the weather cache. No radar without network: a failed bake shows
 * the last baked image labelled stale ([RadarWidgetCache]), never
 * invented weather, never a blank. No selected place renders the
 * empty prompt, like the other widgets.
 *
 * A widget failure must never crash the caller, so every failure
 * inside here is contained and reported as an [Outcome], never
 * thrown — but it is LOGGED with its reason (Item 4 refinement):
 * the 23:41 placement bake failed with no trace at all, which is
 * what made it undiagnosable. A failed bake with no cached image
 * renders "WAITING FOR RADAR" the first time (a retry is pending —
 * see [RadarWidgetScheduler.enqueueRetry]) and "RADAR UNAVAILABLE"
 * once a bake has already failed and left nothing to show (Item 5);
 * the wording strings live in [RadarWidgetModel] so Worker B's
 * bitmap-text rendering can share them.
 */
object RadarWidgetUpdater {

    private const val TAG = "RadarWidget"

    /** SharedPreferences holding the failed-bake flag (see below). */
    private const val PREFS_NAME = "radar-widget"

    /**
     * True once a bake has failed with no image to show. Drives the
     * waiting/unavailable wording: the first such failure is still
     * "waiting" (a retry is scheduled), a repeat is "unavailable".
     * Cleared on any successful bake. Fail-soft — a preferences
     * failure just shows the waiting wording again.
     */
    private const val KEY_HAS_FAILED_BAKE = "has_failed_bake"

    enum class Outcome {
        /** Fresh bake rendered. */
        UPDATED,

        /** Bake failed; the last baked image rendered, labelled stale. */
        STALE_SHOWN,

        /** No selected place; the empty prompt rendered. */
        NO_PLACE,

        /** Nothing to show (no bake, no cache); the unavailable label rendered. */
        UNAVAILABLE,

        /** No radar widget placed; nothing done (and nothing fetched). */
        NO_WIDGETS
    }

    /** Re-render every placed radar widget. */
    suspend fun updateRadar(context: Context): Outcome {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, RadarWidgetReceiver::class.java)
        )
        if (ids.isEmpty()) return Outcome.NO_WIDGETS
        return runCatching {
            withContext(Dispatchers.IO) { updateRadarInternal(context, manager, ids) }
        }.getOrElse {
            // An unexpected failure (e.g. the repository throwing)
            // still shows the stale image rather than crashing the
            // caller — or the unavailable label when there is none.
            // Logged: the old code swallowed this with no trace.
            Log.w(TAG, "radar widget update threw; showing stale or unavailable", it)
            renderStaleOrUnavailable(context, manager, ids)
        }
    }

    private suspend fun updateRadarInternal(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ): Outcome {
        val container = (context.applicationContext as AuraWeatherApp).container
        val place = container.weatherRepository.getSelectedPlace()
            ?: run {
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        WidgetRemoteViews.buildRadar(context, null, "", showEmptyPrompt = true)
                    )
                }
                return Outcome.NO_PLACE
            }
        val now = Instant.now()
        val fetcher = CoalescingFetcher()
        try {
            return when (val baked = RadarWidgetCompositor.bake(place, now, fetcher, now.toEpochMilli())) {
                is RadarWidgetBakeResult.Success -> {
                    RadarWidgetCache.save(context, baked.bitmap, baked.label, baked.frameTimeMs)
                    setHasFailedBake(context, failed = false)
                    Log.d(TAG, "radar widget updated: ${baked.label}")
                    ids.forEach { id ->
                        manager.updateAppWidget(
                            id,
                            WidgetRemoteViews.buildRadar(context, baked.bitmap, baked.label, showEmptyPrompt = false)
                        )
                    }
                    Outcome.UPDATED
                }
                is RadarWidgetBakeResult.Failed -> {
                    Log.d(TAG, "radar widget bake failed: ${baked.reason}; showing stale or unavailable")
                    renderStaleOrUnavailable(context, manager, ids)
                }
            }
        } finally {
            fetcher.close()
        }
    }

    /**
     * The stale path: the last baked image with " · STALE" on its
     * label, or the empty-state wording when there has never been a
     * bake. The cached label already states its frame
     * ("LIVE NOW · HH:MM"); appending " · STALE" is the honest
     * re-show — the pixels are old and the label says so. The
     * no-cache wording distinguishes waiting from unavailable
     * (Item 5): "WAITING FOR RADAR" on the first failure (a retry
     * is pending), "RADAR UNAVAILABLE" once a bake has already
     * failed and left nothing to show. Wording-only: the centred
     * empty view's text is fixed in the layout (Worker B), so the
     * label bar carries the state until the layout can carry it
     * centred.
     */
    private fun renderStaleOrUnavailable(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ): Outcome {
        val cached = RadarWidgetCache.load(context)
        return when (radarWidgetFailedBakeOutcome(cached != null)) {
            RadarWidgetFailedBakeOutcome.STALE_SHOWN -> {
                val staleLabel = "${cached!!.label} · STALE"
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        WidgetRemoteViews.buildRadar(context, cached.bitmap, staleLabel, showEmptyPrompt = false)
                    )
                }
                Outcome.STALE_SHOWN
            }
            RadarWidgetFailedBakeOutcome.UNAVAILABLE -> {
                val hasFailedBefore = hasFailedBakeBefore(context)
                val label = radarWidgetEmptyStateLabel(hasFailedBefore)
                setHasFailedBake(context, failed = true)
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        WidgetRemoteViews.buildRadar(context, null, label, showEmptyPrompt = false)
                    )
                }
                Outcome.UNAVAILABLE
            }
        }
    }

    private fun hasFailedBakeBefore(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_FAILED_BAKE, false)
    }.getOrDefault(false)

    private fun setHasFailedBake(context: Context, failed: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_HAS_FAILED_BAKE, failed).apply()
        }
    }
}
