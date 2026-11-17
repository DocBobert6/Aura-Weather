package com.example.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The radar widget's last baked image (Item 4): persisted so a
 * refresh with no network still has something honest to show — the
 * last baked image, labelled stale — instead of a blank widget.
 *
 * Files under the app's internal files dir (never world-readable):
 * the baked PNG, its label line, and its frame time. All I/O is
 * fail-soft (null / false on any failure): a missing or corrupt
 * cache is "no image", never a crash. Callers run this on Dispatchers.IO.
 */
object RadarWidgetCache {

    private const val DIR_NAME = "radar-widget"
    private const val IMAGE_FILE = "latest.png"
    private const val LABEL_FILE = "latest-label.txt"
    private const val TIME_FILE = "latest-time.txt"

    /** One cached bake: the image, its label, and its frame time (epoch ms). */
    data class Cached(
        val bitmap: Bitmap,
        val label: String,
        val frameTimeMs: Long
    )

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Persist a bake (overwrites the previous one). False on any I/O failure. */
    fun save(context: Context, bitmap: Bitmap, label: String, frameTimeMs: Long): Boolean {
        return runCatching {
            val d = dir(context).apply { mkdirs() }
            File(d, LABEL_FILE).writeText(label)
            File(d, TIME_FILE).writeText(frameTimeMs.toString())
            File(d, IMAGE_FILE).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        }.getOrDefault(false)
    }

    /** Load the last bake, or null when there is none (or it is unreadable). */
    fun load(context: Context): Cached? {
        return runCatching {
            val d = dir(context)
            val label = File(d, LABEL_FILE).readText()
            val frameTimeMs = File(d, TIME_FILE).readText().toLong()
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(File(d, IMAGE_FILE).absolutePath, options)
                ?: return null
            Cached(bitmap, label, frameTimeMs)
        }.getOrNull()
    }
}
