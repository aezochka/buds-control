package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import kotlinx.coroutines.delay

/**
 * Предупреждение о низком заряде наушников.
 *
 * Музыку не обрываем: запрашиваем audio focus с DUCK, плеер сам плавно
 * приглушается, играет сигнал, затем фокус отпускается и громкость
 * возвращается. Через setStreamVolume это делать нельзя — на тихой музыке
 * значение уезжало в ноль и вместо приглушения наступала тишина.
 *
 * Звук берём системный (уведомление), а не синтезируем: собственные тоны
 * звучали дёшево и не совпадали с остальной системой.
 */
object LowBatteryAlert {

    suspend fun play(context: Context, holdMs: Long = 1_500L) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
        } else {
            null
        }

        if (request != null) runCatching { manager?.requestAudioFocus(request) }

        // Даём плееру время приглушиться, иначе сигнал попадёт в полную громкость.
        delay(250)

        playSystemSound(context)

        delay(holdMs)

        // Плеер вернёт громкость сам, плавно.
        if (request != null) runCatching { manager?.abandonAudioFocusRequest(request) }
    }

    /**
     * Проигрывает системный звук уведомления.
     *
     * Ringtone, а не AudioTrack: звук совпадает с системным, не требует
     * генерации буфера и не грузит главный поток.
     */
    private fun playSystemSound(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }
    }
}
