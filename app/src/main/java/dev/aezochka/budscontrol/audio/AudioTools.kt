package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Лимит громкости и таймер сна — на системном AudioManager.
 * Раньше «Лимит» только менял цвет плитки и ничего не делал.
 */
class AudioTools(private val context: Context) {
    private val manager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val maxVolume: Int get() = runCatching {
        manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }.getOrDefault(15)

    val currentVolume: Int get() = runCatching {
        manager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }.getOrDefault(0)

    /**
     * Ограничивает громкость: если текущая выше порога — сразу опускает.
     * percent 0..100, 0 = без лимита.
     */
    fun enforceLimit(percent: Int): Boolean = runCatching {
        if (percent <= 0) return true
        val cap = (maxVolume * percent / 100).coerceAtLeast(1)
        if (currentVolume > cap) {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0)
            Log.i(TAG, "Громкость снижена до $cap из $maxVolume")
        }
        true
    }.getOrElse {
        Log.w(TAG, "Не удалось поставить лимит: ${it.message}")
        false
    }

    /** Пауза воспроизведения — для таймера сна. */
    fun pausePlayback() = runCatching {
        @Suppress("DEPRECATION")
        manager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        )
        @Suppress("DEPRECATION")
        manager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        )
    }.isSuccess

    companion object { private const val TAG = "AudioTools" }
}
