package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
 * thrown.
 */
object RadarWidgetUpdater {

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
                    ids.forEach { id ->
                        manager.updateAppWidget(
                            id,
                            WidgetRemoteViews.buildRadar(context, baked.bitmap, baked.label, showEmptyPrompt = false)
                        )
                    }
                    Outcome.UPDATED
                }
                is RadarWidgetBakeResult.Failed -> renderStaleOrUnavailable(context, manager, ids)
            }
        } finally {
            fetcher.close()
        }
    }

    /**
     * The stale path: the last baked image with " · STALE" on its
     * label, or the unavailable label when there has never been a
     * bake. The cached label already states its frame
     * ("LIVE NOW · HH:MM"); appending " · STALE" is the honest
     * re-show — the pixels are old and the label says so.
     */
    private fun renderStaleOrUnavailable(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ): Outcome {
        val cached = RadarWidgetCache.load(context)
        if (cached != null) {
            val staleLabel = "${cached.label} · STALE"
            ids.forEach { id ->
                manager.updateAppWidget(
                    id,
                    WidgetRemoteViews.buildRadar(context, cached.bitmap, staleLabel, showEmptyPrompt = false)
                )
            }
            return Outcome.STALE_SHOWN
        }
        ids.forEach { id ->
            manager.updateAppWidget(
                id,
                WidgetRemoteViews.buildRadar(context, null, "RADAR UNAVAILABLE", showEmptyPrompt = false)
            )
        }
        return Outcome.UNAVAILABLE
    }
}
