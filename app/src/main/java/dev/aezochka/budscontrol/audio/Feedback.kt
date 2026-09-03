package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Отклик на действия: вибрация и звук.
 *
 * Важное про прошлые попытки. Свой синтез тонов через AudioTrack звучал
 * дёшево, а системные `playSoundEffect` и «предопределённые» тактильные
 * эффекты вообще не срабатывали: и то и другое ГАСИТСЯ системными
 * переключателями «звук нажатий» и «вибрация при касании». Если пользователь
 * их выключил (а это частая настройка), приложение молчало — при том, что
 * свои тумблеры в настройках включены.
 *
 * Поэтому здесь:
 *  - звук через ToneGenerator на канале STREAM_SYSTEM — короткие системные
 *    тоны, которые не зависят от «звука нажатий»;
 *  - вибрация с VibrationAttributes.USAGE_NOTIFICATION вместо касания, чтобы
 *    её не подавляла настройка тактильного отклика при нажатии.
 */
class Feedback(private val context: Context) {

    enum class Kind { Tap, On, Off, Switch, Alarm }

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /**
     * ToneGenerator создаётся один раз и переиспользуется: создание на каждый
     * тап занимает десятки миллисекунд и давало микрофризы.
     */
    private val tones: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_SYSTEM, 70)
    }.getOrNull()

    private val clicks = ClickSounds(context)

    /**
     * @param soundPack ключ набора звуков из настроек. Пустой или "system"
     *   означает системные тоны, "off" — без звука.
     */
    fun play(kind: Kind, soundOn: Boolean, hapticOn: Boolean, soundPack: String = "click_a") {
        if (hapticOn) vibrate(kind)
        if (!soundOn) return
        // Старые сохранённые значения system/off тоже сводим к первому
        // пользовательскому клику: этих пунктов больше нет в настройках.
        val selected = when (soundPack) {
            "click_b", "click_c" -> soundPack
            else -> "click_a"
        }
        // Тревожный сигнал оставляем системным: сэмпл клика для него
        // слишком короткий и не читается как предупреждение.
        if (kind == Kind.Alarm) sound(kind) else clicks.play(selected)
    }

    private fun sound(kind: Kind) {
        val gen = tones ?: return
        val (tone, ms) = when (kind) {
            Kind.Tap -> ToneGenerator.TONE_PROP_BEEP to 55
            Kind.Switch -> ToneGenerator.TONE_PROP_BEEP to 45
            Kind.On -> ToneGenerator.TONE_PROP_ACK to 110
            Kind.Off -> ToneGenerator.TONE_PROP_NACK to 110
            Kind.Alarm -> ToneGenerator.TONE_PROP_BEEP2 to 260
        }
        runCatching { gen.startTone(tone, ms) }
    }

    private fun vibrate(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val id = when (kind) {
                    Kind.Tap, Kind.Switch -> VibrationEffect.EFFECT_TICK
                    Kind.On, Kind.Off -> VibrationEffect.EFFECT_CLICK
                    Kind.Alarm -> VibrationEffect.EFFECT_DOUBLE_CLICK
                }
                VibrationEffect.createPredefined(id)
            } else {
                val ms = if (kind == Kind.Alarm) 60L else 22L
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // USAGE_NOTIFICATION, а не TOUCH: с TOUCH система подавляет
                // вибрацию, когда выключен тактильный отклик при нажатии.
                v.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect)
            }
        }
    }
}
