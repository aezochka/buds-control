package dev.aezochka.budscontrol.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * РАБОЧИЙ эквалайзер — системный, через android.media.audiofx.
 *
 * Почему так: в открытом протоколе OPPO/realme команды эквалайзера нет вообще
 * (в Gadgetbridge есть только LDAC, игровой режим, multipoint, поиск телефона).
 * Коды EQ, которые я использовала раньше, были из непроверенного форка —
 * поэтому гарнитура на них не отвечала и звук не менялся.
 *
 * Системный эффект применяется к выводу телефона, то есть реально слышен
 * в наушниках. Сессия 0 — глобальный микс.
 */
class SystemAudioFx {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null

    /** Частоты полос в Гц, как их сообщает само устройство. */
    var bandFrequencies: List<Int> = emptyList()
        private set

    /** Границы усиления в миллибеллах. */
    var minGainMb: Short = -1500
        private set
    var maxGainMb: Short = 1500
        private set

    val bandCount: Int get() = bandFrequencies.size
    val available: Boolean get() = equalizer != null

    fun attach(): Boolean {
        if (equalizer != null) return true
        return runCatching {
            val eq = Equalizer(0, 0).apply { enabled = true }
            val bands = eq.numberOfBands.toInt()
            bandFrequencies = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            val range = eq.bandLevelRange
            minGainMb = range[0]
            maxGainMb = range[1]
            equalizer = eq

            loudness = runCatching { LoudnessEnhancer(0) }.getOrNull()
            Log.i(TAG, "Эквалайзер подключён: $bands полос, ${bandFrequencies}")
            true
        }.getOrElse {
            Log.w(TAG, "Системный эквалайзер недоступен: ${it.message}")
            false
        }
    }

    /** Текущие уровни полос в дБ. */
    fun currentGainsDb(): List<Int> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until bandCount).map { eq.getBandLevel(it.toShort()) / 100 }
        }.getOrElse { emptyList() }
    }

    /** Ставит усиление одной полосы в дБ. */
    fun setBandDb(band: Int, db: Int) {
        val eq = equalizer ?: return
        runCatching {
            val mb = (db * 100).coerceIn(minGainMb.toInt(), maxGainMb.toInt())
            eq.setBandLevel(band.toShort(), mb.toShort())
        }
    }

    fun setEnabled(on: Boolean) {
        runCatching { equalizer?.enabled = on }
    }



    /** Дополнительная громкость в мБ — компенсация тихих записей. */
    fun setLoudness(gainMb: Int) {
        runCatching {
            loudness?.apply {
                setTargetGain(gainMb.coerceIn(0, 1500))
                enabled = gainMb > 0
            }
        }
    }

    fun applyPreset(gains: List<Int>) {
        gains.forEachIndexed { index, db -> setBandDb(index, db) }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null; loudness = null
    }

    companion object { private const val TAG = "SystemAudioFx" }
}
