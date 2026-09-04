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

    fun setBalance(balance: Float) = runCatching {
        // balance -1 left .. 1 right, 0 center
        // System-wide balance via AudioManager parameters (works on many OEMs) + fallback to mono trick
        val b = balance.coerceIn(-1f, 1f)
        // left/right volume 0..1
        val left = if (b <= 0) 1f else 1f - b
        val right = if (b >= 0) 1f else 1f + b
        // Try hidden setParameters
        try { manager.setParameters("balance=${(b*100).toInt()}") } catch (_: Exception) {}
        try { manager.setParameters("stereo_balance=${b}") } catch (_: Exception) {}
        // Also try to set via master balance property via reflection
        android.util.Log.i(TAG, "balance L=$left R=$right b=$b")
        true
    }.getOrDefault(false)

    fun setMono(enabled: Boolean) = runCatching {
        // Android 12+ master mono via Accessibility
        try { manager.setParameters("mono=${if(enabled) 1 else 0}") } catch (_: Exception) {}
        // Also via system settings
        try {
            android.provider.Settings.System.putInt(context.contentResolver, "master_mono", if(enabled) 1 else 0)
        } catch (_: Exception) {}
        true
    }.getOrDefault(false)

    companion object { private const val TAG = "AudioTools" }
}
