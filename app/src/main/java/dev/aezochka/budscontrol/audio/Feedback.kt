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
        // hasVibrator() обязателен: на части устройств сервис есть, а мотора
        // нет, и вызовы молча уходят в никуда.
        if (!v.hasVibrator()) return
        runCatching {
            // Амплитуда была 50..120 из 255 — на большинстве телефонов это
            // не чувствуется вообще. Плюс не все поддерживают амплитуду,
            // тогда нужен простой одноразовый импульс.
            val supportsAmplitude = v.hasAmplitudeControl()
            val effect = when (kind) {
                Kind.Tap -> oneShot(18, 170, supportsAmplitude)
                Kind.On -> oneShot(28, 230, supportsAmplitude)
                Kind.Off -> oneShot(22, 190, supportsAmplitude)
                Kind.Switch -> oneShot(14, 150, supportsAmplitude)
                Kind.Alarm -> VibrationEffect.createWaveform(
                    longArrayOf(0, 140, 90, 140, 90, 220),
                    intArrayOf(0, 220, 0, 220, 0, 255),
                    -1,
                )
            }
            v.vandalizeSafe(effect)
        }
    }

    private fun oneShot(ms: Long, amplitude: Int, supportsAmplitude: Boolean): VibrationEffect =
        if (supportsAmplitude) {
            VibrationEffect.createOneShot(ms, amplitude)
        } else {
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
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
            // Ноты вместо произвольных частот: A5, E5→A5, A5→E5.
            // Музыкальные интервалы на слух приятнее случайных значений.
            Kind.Tap -> Spec(0.075, 880.0, 880.0, 0.42)
            Kind.On -> Spec(0.16, 659.25, 987.77, 0.50)
            Kind.Off -> Spec(0.16, 987.77, 659.25, 0.46)
            Kind.Switch -> Spec(0.09, 783.99, 783.99, 0.38)
            Kind.Alarm -> Spec(0.8, 659.25, 880.0, 0.62)
        }
        val frames = (sampleRate * spec.seconds).toInt().coerceAtLeast(64)
        val buffer = ShortArray(frames)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / frames
            val freq = spec.startHz + (spec.endHz - spec.startHz) * progress
            // Мягкая атака вместо мгновенной: резкий фронт давал «щёлк»,
            // который и звучал неприятно.
            val attack = 1 - exp(-18.0 * progress)
            val envelope = attack * exp(-5.0 * progress)
            // Обертоны тише основного тона: чистая синусоида звучит дёшево.
            val wave = sin(2 * PI * freq * t) +
                sin(2 * PI * freq * 2 * t) * 0.18 +
                sin(2 * PI * freq * 3 * t) * 0.07
            val value = (wave * envelope * spec.volume).coerceIn(-1.0, 1.0)
            buffer[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_MEDIA, а не SONIFICATION: системный канал
                        // уведомлений часто приглушён или замьючен, и щелчков
                        // не было слышно вообще.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
