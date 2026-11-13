package com.example.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.example.R
import kotlin.random.Random

/**
 * Background music for the retro UI: the WeatherStar-inspired smooth-jazz
 * tracks bundled in res/raw (from the ws4kp project — AI-generated and
 * unencumbered by copyright), played at a low fixed volume, in random
 * order, ONLY while the app is in the foreground.
 *
 * Owned by MainActivity (never by a composable, so it survives
 * recomposition). No foreground service, no notification, no background
 * playback: the player pauses the moment the app leaves the foreground
 * (Activity.onStop) and resumes when it returns (onStart), and only ever
 * plays while the Home music toggle is on.
 *
 * Audio focus: the player requests focus (USAGE_MEDIA, GAIN) and pauses
 * on ANY focus loss — a phone call or another app's audio must never be
 * talked over by the weather music. On focus gain it resumes, but only
 * if the toggle is still on and the app is still in the foreground.
 * A permanent loss (AUDIOFOCUS_LOSS) sends no gain afterwards, so it is
 * recovered the next time the app returns to the foreground: onForeground()
 * re-requests focus. A transient loss self-heals on AUDIOFOCUS_GAIN and is
 * never re-requested, so returning mid-prompt cannot steal focus back.
 */
class BackgroundMusicPlayer(context: Context) {

    companion object {
        /** Fixed playback volume — the music sits far under the UI. */
        const val VOLUME = 0.15f

        /** The bundled tracks, in res/raw. */
        val TRACKS: List<Int> = listOf(
            R.raw.catch_the_sun,
            R.raw.crisp_day,
            R.raw.rolling_clouds,
            R.raw.strong_breeze
        )
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mediaPlayer: MediaPlayer? = null
    private var currentIndex: Int = -1
    private var enabled: Boolean = false
    private var inForeground: Boolean = false
    private var hasFocus: Boolean = false
    private var focusRequest: AudioFocusRequest? = null
    /** True only after a permanent AUDIOFOCUS_LOSS (no gain will follow). */
    private var permanentFocusLoss: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                permanentFocusLoss = false
                updatePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                permanentFocusLoss = true
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                pause()
            }
        }
    }

    /** The master switch (the Home music toggle). */
    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled) {
            requestFocus()
        } else {
            abandonFocus()
            stop()
        }
        updatePlayback()
    }

    /** Call from Activity.onStart — the app has come to the foreground. */
    fun onForeground() {
        inForeground = true
        if (enabled && permanentFocusLoss) {
            // A permanent loss sends no AUDIOFOCUS_GAIN, so without this
            // the music would stay silent until the toggle was cycled.
            // requestFocus() is a no-op while focus is still held, and a
            // transient loss never sets the flag, so this cannot steal
            // focus back from a transient holder or restart the track:
            // updatePlayback() below resumes the paused player in place.
            requestFocus()
        }
        updatePlayback()
    }

    /**
     * Call from Activity.onStop — the app has gone to the background.
     * Never plays here; the current track is paused in place so it
     * resumes where it left off when the app returns.
     */
    fun onBackground() {
        inForeground = false
        pause()
    }

    /** Release everything (Activity.onDestroy). */
    fun release() {
        stop()
        abandonFocus()
    }

    private fun updatePlayback() {
        if (enabled && inForeground && hasFocus) {
            start()
        } else {
            pause()
        }
    }

    private fun start() {
        val player = mediaPlayer
        if (player != null) {
            if (!player.isPlaying) player.start()
            return
        }
        playNext()
    }

    private fun playNext() {
        // Release whatever was playing (it ended, or it errored) before
        // starting the next track, so no player is ever orphaned.
        mediaPlayer?.release()
        mediaPlayer = null
        val next = nextTrackIndex(currentIndex, TRACKS.size, Random.Default)
        currentIndex = next
        val player = MediaPlayer.create(appContext, TRACKS[next]) ?: return
        player.setVolume(VOLUME, VOLUME)
        player.setOnCompletionListener {
            // Track ended: pick another at random (never the same twice).
            playNext()
        }
        player.setOnErrorListener { _, _, _ ->
            // A corrupt/unsupported track must not kill the feature —
            // skip to another track instead.
            playNext()
            true
        }
        mediaPlayer = player
        player.start()
    }

    private fun pause() {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            player.pause()
        }
    }

    private fun stop() {
        val player = mediaPlayer
        mediaPlayer = null
        currentIndex = -1
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (hasFocus) permanentFocusLoss = false
    }

    private fun abandonFocus() {
        if (!hasFocus && focusRequest == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
    }
}
