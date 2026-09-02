package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Предупреждение о низком заряде наушников.
 *
 * Музыку не обрываем: плавно приглушаем, играем сигнал, через пару секунд
 * возвращаем громкость. Резкое выключение звука воспринимается как сбой
 * плеера, а плавное — как системное уведомление.
 *
 * Приглушаем через AudioManager.STREAM_MUSIC, а не через MediaSession: так
 * работает с любым плеером, включая те, что не отдают управление.
 */
object LowBatteryAlert {

    private const val DUCK_STEPS = 8
    private const val STEP_MS = 45L

    /**
     * Приглушает музыку, играет сигнал, возвращает громкость.
     *
     * @param holdMs сколько держать приглушение после сигнала
     */
    suspend fun play(context: Context, holdMs: Long = 2_000L) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val original = manager?.getStreamVolume(AudioManager.STREAM_MUSIC)
        val ducked = original?.let { (it * 0.25f).toInt().coerceAtLeast(0) }

        // Плавное затухание: пошагово вниз, а не одним прыжком.
        if (manager != null && original != null && ducked != null && original > ducked) {
            fade(manager, from = original, to = ducked)
        }

        runCatching { tone() }

        delay(holdMs)

        // Возврат — так же плавно.
        if (manager != null && original != null && ducked != null && original > ducked) {
            fade(manager, from = ducked, to = original)
        }
    }

    private suspend fun fade(manager: AudioManager, from: Int, to: Int) {
        val diff = (to - from).toFloat()
        for (step in 1..DUCK_STEPS) {
            val value = (from + diff * step / DUCK_STEPS).toInt()
            runCatching {
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            }
            delay(STEP_MS)
        }
    }

    /** Двойной нисходящий сигнал — «садится». */
    private fun tone() {
        val sampleRate = 44100
        val seconds = 0.9
        val frames = (sampleRate * seconds).toInt()
        val buffer = ShortArray(frames)

        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / frames
            // Два коротких гудка вниз по тону: узнаваемо и не раздражает.
            val beep = if (progress < 0.45) progress / 0.45 else (progress - 0.55) / 0.45
            val inGap = progress in 0.45..0.55
            val freq = 900.0 - 260.0 * beep.coerceIn(0.0, 1.0)
            val envelope = if (inGap) 0.0 else {
                val local = if (progress < 0.45) progress / 0.45 else (progress - 0.55) / 0.45
                exp(-2.2 * local) * minOf(1.0, local * 25)
            }
            buffer[i] = (sin(2 * PI * freq * t) * envelope * 0.9 * Short.MAX_VALUE).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.setNotificationMarkerPosition(frames - 1)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    runCatching { t?.release() }
                }
                override fun onPeriodicNotification(t: AudioTrack?) = Unit
            }
        )
        track.play()
    }
}
