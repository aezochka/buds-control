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
class SystemAudioFx(private val context: android.content.Context) {
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

    /**
     * Пробует взять реальные параметры эквалайзера с устройства.
     * Если не удалось — работаем на стандартных 5 полосах и всё равно
     * отправляем уровни в сервис: раньше при неудаче полосы вообще
     * не рисовались и эквалайзер выглядел мёртвым.
     */
    fun attach(): Boolean {
        if (equalizer != null) return true
        val ok = tryAttach()
        if (!ok) {
            bandFrequencies = FALLBACK_BANDS
            minGainMb = -1500
            maxGainMb = 1500
        }
        return true
    }

    private fun tryAttach(): Boolean {
        return runCatching {
            val eq = Equalizer(0, 0).apply { enabled = true }
            val bands = eq.numberOfBands.toInt()
            bandFrequencies = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            val range = eq.bandLevelRange
            minGainMb = range[0]
            maxGainMb = range[1]
            equalizer = eq

            loudness = runCatching { LoudnessEnhancer(0) }.getOrNull()
            true
        }.getOrElse {
            false
        }
    }

    /**
     * Единственный источник истины после первого движения — ручной массив.
     *
     * На телефоне локальный Equalizer создаётся, но setBandLevel() падает.
     * Раньше currentGainsDb() всё равно читал его первым: пользователь двигал
     * полосу, а сервис каждый раз получал старые [3,0,0,0,3].
     */
    fun currentGainsDb(): List<Int> {
        if (manualGains.isNotEmpty()) return manualGains
        val eq = equalizer
        return if (eq == null) List(bandCount) { 0 } else runCatching {
            (0 until bandCount).map { eq.getBandLevel(it.toShort()) / 100 }
        }.getOrElse { List(bandCount) { 0 } }
    }

    /** Уровни, выставленные вручную — именно их отправляем в EqService. */
    private var manualGains: List<Int> = emptyList()

    /**
     * Ставит усиление полосы. Помимо локального объекта, отправляем уровни
     * в foreground-сервис: только он реально влияет на воспроизведение,
     * потому что подключён ко всем активным сессиям плееров.
     */
    fun setBandDb(band: Int, db: Int) {
        // Держим значения у себя. Локальный Equalizer на многих устройствах падает
        // с "invalid parameter operation" — поэтому всё идёт только через сервис.
        val current = currentGainsDb().toMutableList()
        while (current.size <= band) current.add(0)
        current[band] = db.coerceIn(minGainMb / 100, maxGainMb / 100)
        manualGains = current
        pushToService()
    }

    /**
     * Отдаёт текущие уровни сервису, который применяет их к плеерам.
     *
     * Если кривая ровная, сервис останавливаем: держать foreground-сервис
     * (а значит и его уведомление) ради нулевого эквалайзера незачем.
     */
    private fun pushToService() {
        val levels = currentGainsDb()
        if (levels.isEmpty()) return
        if (levels.all { it == 0 }) {
            EqService.stop(context)
            return
        }
        EqService.apply(context, levels)
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
        // Подгоняем под реальное число полос: если гарнитура сообщает 5, а
        // пресет прислал 5 — 1:1. Если вдруг число полос другое, обрезаем/дополняем нулями.
        val count = bandCount.coerceAtLeast(5)
        val aligned = when {
            gains.size == count -> gains
            gains.size < count -> gains + List(count - gains.size) { 0 }
            else -> gains.take(count)
        }
        manualGains = aligned.map { it.coerceIn(minGainMb / 100, maxGainMb / 100) }
        pushToService()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null; loudness = null
    }

    companion object {
        private const val TAG = "SystemAudioFx"
        /** Стандартные полосы, если устройство не сообщило свои. */
        private val FALLBACK_BANDS = listOf(60, 230, 910, 3600, 14000)
    }
}
