package dev.aezochka.budscontrol.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Ловит системные broadcast'ы об открытии и закрытии аудиосессий плееров.
 *
 * Зачем отдельный manifest-receiver, а не только регистрация внутри сервиса:
 * система рассылает эти интенты как явные вызовы приложений-эквалайзеров,
 * и до нас они доходят только если приёмник объявлен в манифесте. Пока его
 * не было, EqService видел лишь глобальную сессию 0 — уровни принимались и
 * читались обратно (это видно в логе), но на Bluetooth-выводе многие ROM
 * глобальный эффект не применяют, поэтому звук не менялся.
 */
class EqSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val action = intent?.action ?: return
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (session <= 0) {
            EqLog.log("Broadcast $action без валидной сессии ($session) — пропускаю")
            return
        }
        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                EqLog.log("Плеер открыл сессию $session — подключаю эквалайзер")
                EqService.attachSession(ctx, session)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                EqLog.log("Плеер закрыл сессию $session")
                EqService.detachSession(ctx, session)
            }
        }
    }
}
