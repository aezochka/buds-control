package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Звук и вибро на действия: короткие синтезированные щелчки.
 * Ничего не качается — тон генерируется на месте, поэтому APK не растёт.
 */
class Feedback(private val context: Context) {
    /** Отдельная область: звук переживает пересоздание экрана. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


    enum class Kind {
        /** Обычный тап по плитке. */
        Tap,
        /** Включили функцию. */
        On,
        /** Выключили функцию. */
        Off,
        /** Переключение вкладки. */
        Switch,
        /** Таймер сна дозвонил. */
        Alarm,
    }

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** Проигрывает щелчок. soundOn/hapticOn берутся из настроек. */
    fun play(kind: Kind, soundOn: Boolean, hapticOn: Boolean) {
        if (hapticOn) vibrate(kind)
        // Синтез тона — это цикл на тысячи сэмплов плюс создание AudioTrack.
        // В главном потоке он давал микрофризы на каждом нажатии, поэтому
        // уводим звук в фон: UI не должен ждать генерацию буфера.
        if (soundOn) scope.launch { tone(kind) }
    }

    private fun vibrate(kind: Kind) {
        val v = vibrator ?: return
        runCatching {
            val effect = when (kind) {
                Kind.Tap -> VibrationEffect.createOneShot(12, 60)
                Kind.On -> VibrationEffect.createOneShot(22, 120)
                Kind.Off -> VibrationEffect.createOneShot(16, 80)
                Kind.Switch -> VibrationEffect.createOneShot(10, 50)
                Kind.Alarm -> VibrationEffect.createWaveform(
                    longArrayOf(0, 140, 90, 140, 90, 220),
                    intArrayOf(0, 180, 0, 180, 0, 255),
                    -1,
                )
            }
            v.vandalizeSafe(effect)
        }
    }

    private fun Vibrator.vandalizeSafe(effect: VibrationEffect) {
        runCatching { vibrate(effect) }
    }

    /**
     * Короткий тон. Для «включили» частота идёт вверх, для «выключили» вниз —
     * так на слух понятно, что произошло.
     */
    private fun tone(kind: Kind) {
        val sampleRate = 44100
        val spec = when (kind) {
            Kind.Tap -> Spec(0.045, 880.0, 880.0, 0.22)
            Kind.On -> Spec(0.12, 660.0, 1180.0, 0.30)
            Kind.Off -> Spec(0.12, 1180.0, 620.0, 0.26)
            Kind.Switch -> Spec(0.05, 1040.0, 1040.0, 0.18)
            Kind.Alarm -> Spec(0.8, 520.0, 900.0, 0.36)
        }
        val frames = (sampleRate * spec.seconds).toInt().coerceAtLeast(64)
        val buffer = ShortArray(frames)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / frames
            val freq = spec.startHz + (spec.endHz - spec.startHz) * progress
            // Экспоненциальное затухание: щелчок без хвоста и без клика на конце.
            val envelope = exp(-4.0 * progress) * minOf(1.0, progress * 40)
            val value = sin(2 * PI * freq * t) * envelope * spec.volume
            buffer[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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

    private data class Spec(val seconds: Double, val startHz: Double, val endHz: Double, val volume: Double)
}
