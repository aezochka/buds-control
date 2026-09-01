package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Реальные уровни звука для анимации полос на плитке эквалайзера.
 *
 * Использует системный Visualizer с сессией 0 (микс всего вывода).
 * Требует RECORD_AUDIO — без разрешения молча отдаёт null, и UI
 * показывает обычную «дышащую» анимацию.
 */
class MusicPulse(private val context: Context) {

    /** Пять полос 0..1, обновляются ~20 раз в секунду. Null — данных нет. */
    fun levels(): Flow<List<Float>?> = callbackFlow {
        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) = Unit

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                            if (fft == null) return
                            trySend(bandsFromFft(fft))
                        }
                    },
                    Visualizer.getMaxCaptureRate().coerceAtMost(20_000),
                    false, true,
                )
                enabled = true
            }
        } catch (e: Exception) {
            // Нет RECORD_AUDIO или устройство не даёт микс — честно отдаём null.
            Log.i(TAG, "Visualizer недоступен: ${e.message}")
            trySend(null)
        }
        awaitClose {
            runCatching { visualizer?.enabled = false }
            runCatching { visualizer?.release() }
        }
    }

    private fun bandsFromFft(fft: ByteArray): List<Float> {
        // fft: [real0, imag0, real1, imag1, ...] — считаем магнитуды и
        // раскладываем по пяти логарифмическим полосам.
        val magnitudes = FloatArray(fft.size / 2)
        for (i in magnitudes.indices) {
            val re = fft[i * 2].toFloat()
            val im = fft[i * 2 + 1].toFloat()
            magnitudes[i] = sqrt(re * re + im * im)
        }
        val edges = intArrayOf(1, 4, 12, 32, 80, magnitudes.size)
        return (0 until 5).map { band ->
            val from = edges[band]
            val to = min(edges[band + 1], magnitudes.size)
            if (from >= to) return@map 0.12f
            var sum = 0f
            for (i in from until to) sum += abs(magnitudes[i])
            val avg = sum / (to - from)
            // Логарифмическое сжатие: тихие сигналы тоже видны.
            val norm = (avg / 40f).coerceIn(0f, 1f).pow(0.55f)
            norm.coerceIn(0.12f, 1f)
        }
    }

    companion object {
        private const val TAG = "MusicPulse"
    }
}
