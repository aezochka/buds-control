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

    private data class Note(val startAt: Double, val durationSec: Double, val freq: Double)

    /**
     * Приглушает музыку, играет сигнал, возвращает громкость.
     *
     * @param holdMs сколько держать приглушение после сигнала
     */
    suspend fun play(context: Context, holdMs: Long = 1_600L) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val original = manager?.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Минимум 30% и минимум 1 деление: раньше при тихой музыке получался
        // ноль, и вместо приглушения была полная тишина.
        val ducked = original?.let { (it * 0.3f).toInt().coerceAtLeast(1).coerceAtMost(it) }

        if (manager != null && original != null && ducked != null && original > ducked) {
            fade(manager, from = original, to = ducked)
        }

        // Сигнал играем на канале будильника: он НЕ приглушается вместе с
        // музыкой, поэтому его слышно. На STREAM_MUSIC он глушился сам собой.
        runCatching { tone(manager) }

        delay(holdMs)

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

    /**
     * Мягкий сигнал из двух нот с обертонами.
     *
     * Чистая синусоида звучит дёшево и резко, поэтому добавляем октаву и
     * квинту тише основного тона — получается тёплый колокольчик, а не писк.
     * Ноты E5 → A5: нисходящая интонация читается как «внимание», но без
     * тревожности будильника.
     */
    private fun tone(manager: AudioManager?) {
        val sampleRate = 44100
        val seconds = 1.0
        val frames = (sampleRate * seconds).toInt()
        val buffer = ShortArray(frames)

        // Две ноты подряд с небольшим наложением, чтобы не было щелчка.
        val notes = listOf(
            Note(startAt = 0.0, durationSec = 0.55, freq = 659.25),  // E5
            Note(startAt = 0.34, durationSec = 0.62, freq = 880.00), // A5
        )

        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for (note in notes) {
                val local = t - note.startAt
                if (local < 0 || local > note.durationSec) continue
                val phase = local / note.durationSec
                // Мягкая атака и длинный спад: так звучит удар по металлу,
                // а не включение зуммера.
                val envelope = (1 - exp(-14.0 * phase)) * exp(-3.4 * phase)
                sample += sin(2 * PI * note.freq * local) * envelope
                sample += sin(2 * PI * note.freq * 2 * local) * envelope * 0.22
                sample += sin(2 * PI * note.freq * 3 * local) * envelope * 0.09
            }
            val value = (sample * 0.32).coerceIn(-1.0, 1.0)
            buffer[i] = (value * Short.MAX_VALUE).toInt().toShort()
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
