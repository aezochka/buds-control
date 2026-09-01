package dev.aezochka.budscontrol.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.aezochka.budscontrol.R

/**
 * Foreground-сервис, который держит эквалайзер живым.
 *
 * Почему так, а не просто `Equalizer(0, 0)` в ViewModel:
 * объект эффекта живёт, только пока жив владелец, и глобальная сессия 0
 * работает не на всех прошивках. Рабочий подход (как в OpenEQ) — держать
 * сервис и подключать эффект к КАЖДОЙ сессии плеера, о которой система
 * сообщает через ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.
 *
 * Плюс нужно разрешение MODIFY_AUDIO_SETTINGS — без него настройки
 * не применялись.
 */
class EqService : Service() {

    private val equalizers = mutableMapOf<Int, Equalizer>()
    private var levelsDb: List<Int> = emptyList()

    private val openReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val session = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: return
            attach(session)
        }
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val session = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: return
            equalizers.remove(session)?.let { runCatching { it.release() } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ContextCompat.registerReceiver(
            this, openReceiver,
            IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this, closeReceiver,
            IntentFilter(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getIntArrayExtra(EXTRA_LEVELS)?.let { levelsDb = it.toList() }

        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundSafely()
                // Глобальная сессия — плюс все уже играющие.
                attach(0)
                applyToAll()
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafely() {
        runCatching {
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_buds)
                .setContentTitle("Эквалайзер активен")
                .setContentText("Настройки применяются к воспроизведению")
                .setOngoing(true)
                .setSilent(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.e(TAG, "foreground: ${it.message}") }
    }

    private fun attach(session: Int) {
        if (equalizers.containsKey(session)) return
        runCatching {
            val eq = Equalizer(PRIORITY, session).apply { enabled = true }
            equalizers[session] = eq
            apply(eq)
            Log.i(TAG, "Эквалайзер подключён к сессии $session")
        }.onFailure { Log.w(TAG, "Сессия $session недоступна: ${it.message}") }
    }

    private fun applyToAll() {
        equalizers.values.forEach { apply(it) }
    }

    private fun apply(eq: Equalizer) {
        if (levelsDb.isEmpty()) return
        runCatching {
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(bands, levelsDb.size)) {
                eq.setBandLevel(i.toShort(), (levelsDb[i] * 100).toShort())
            }
        }
    }

    private fun stopEverything() {
        equalizers.values.forEach { runCatching { it.enabled = false; it.release() } }
        equalizers.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(openReceiver) }
        runCatching { unregisterReceiver(closeReceiver) }
        equalizers.values.forEach { runCatching { it.release() } }
        equalizers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Эквалайзер", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Держит настройки звука активными"
            }
        )
    }

    companion object {
        const val ACTION_APPLY = "dev.aezochka.budscontrol.EQ_APPLY"
        const val ACTION_STOP = "dev.aezochka.budscontrol.EQ_STOP"
        const val EXTRA_LEVELS = "levels"
        private const val CHANNEL = "equalizer"
        private const val NOTIFICATION_ID = 77
        private const val PRIORITY = 100
        private const val TAG = "EqService"

        fun apply(context: Context, levelsDb: List<Int>) {
            val intent = Intent(context, EqService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_LEVELS, levelsDb.toIntArray())
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, EqService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }
}
