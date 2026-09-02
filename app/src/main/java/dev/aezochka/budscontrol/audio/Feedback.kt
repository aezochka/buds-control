package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Отклик на действия: вибрация и звук.
 *
 * Свой синтез тонов убран. Две попытки сделать «приятный» звук вручную
 * провалились: синусоида с обертонами всё равно звучит дёшево и чуждо
 * системе. Здесь используются штатные звуки Android (те же, что у клавиатуры
 * и системных переключателей) и предопределённые тактильные эффекты — они
 * настроены производителем под конкретный телефон и совпадают с остальной
 * системой.
 */
class Feedback(private val context: Context) {

    enum class Kind { Tap, On, Off, Switch, Alarm }

    private val audio: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun play(kind: Kind, soundOn: Boolean, hapticOn: Boolean) {
        if (hapticOn) vibrate(kind)
        if (soundOn) sound(kind)
    }

    /**
     * Системный звук интерфейса.
     *
     * playSoundEffect работает мгновенно и не создаёт AudioTrack, поэтому
     * не нужен фоновый поток и не бывает микрофризов.
     */
    private fun sound(kind: Kind) {
        val effect = when (kind) {
            Kind.Tap -> AudioManager.FX_KEY_CLICK
            Kind.On -> AudioManager.FX_FOCUS_NAVIGATION_UP
            Kind.Off -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
            Kind.Switch -> AudioManager.FX_KEYPRESS_STANDARD
            Kind.Alarm -> AudioManager.FX_FOCUS_NAVIGATION_UP
        }
        runCatching { audio?.playSoundEffect(effect, 1f) }
    }

    /**
     * Тактильный отклик.
     *
     * Предопределённые эффекты (TICK/CLICK/DOUBLE_CLICK) вместо ручных
     * длительностей и амплитуд: производитель уже подобрал их под свой
     * вибромотор, поэтому они ощущаются как системные, а не как жужжание.
     */
    private fun vibrate(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val id = when (kind) {
                    Kind.Tap -> VibrationEffect.EFFECT_TICK
                    Kind.Switch -> VibrationEffect.EFFECT_TICK
                    Kind.On -> VibrationEffect.EFFECT_CLICK
                    Kind.Off -> VibrationEffect.EFFECT_CLICK
                    Kind.Alarm -> VibrationEffect.EFFECT_DOUBLE_CLICK
                }
                VibrationEffect.createPredefined(id)
            } else {
                // На старых версиях предопределённых эффектов нет.
                val ms = if (kind == Kind.Alarm) 60L else 20L
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }
}
