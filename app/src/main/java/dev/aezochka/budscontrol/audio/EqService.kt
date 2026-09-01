package dev.aezochka.budscontrol.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
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
        intent?.getIntArrayExtra(EXTRA_LEVELS)?.let {
            levelsDb = it.toList()
            LevelStore.save(this, levelsDb)
        }
        if (levelsDb.isEmpty()) levelsDb = LevelStore.load(this)

        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_ATTACH_SESSION -> {
                startForegroundSafely()
                val session = intent.getIntExtra(EXTRA_SESSION, -1)
                if (session > 0) {
                    attach(session)
                    applyToAll()
                }
            }
            ACTION_DETACH_SESSION -> {
                val session = intent.getIntExtra(EXTRA_SESSION, -1)
                equalizers.remove(session)?.let { runCatching { it.release() } }
                EqLog.log("Сессия $session отключена, осталось ${equalizers.size}")
            }
            else -> {
                startForegroundSafely()
                EqLog.log("Сервис запущен, уровни=$levelsDb, активных сессий=${equalizers.size}")
                attach(0)
                attachActivePlayers()
                applyToAll()
            }
        }
        return START_STICKY
    }

    /**
     * Догоняет уже играющие плееры. Broadcast приходит только в момент
     * открытия сессии, поэтому при запуске сервиса поверх играющей музыки
     * без этого прохода мы остались бы только на сессии 0.
     */
    private fun attachActivePlayers() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val am = getSystemService(AudioManager::class.java) ?: return
            val ids = am.activePlaybackConfigurations
                .mapNotNull { config ->
                    runCatching {
                        AudioPlaybackConfiguration::class.java
                            .getMethod("getSessionId")
                            .invoke(config) as? Int
                    }.getOrNull()
                }
                .filter { it > 0 }
                .distinct()
            EqLog.log("Активные сессии плееров: ${ids.ifEmpty { listOf("нет") }}")
            ids.forEach { attach(it) }
        }.onFailure { EqLog.log("Не удалось прочитать активные сессии: ${it.message}") }
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
            EqLog.log("Сервис: подключён к сессии $session, полос=${eq.numberOfBands}")
        }.onFailure {
            EqLog.log("Сервис: сессия $session недоступна — ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun applyToAll() {
        equalizers.values.forEach { apply(it) }
    }

    private fun apply(eq: Equalizer) {
        if (levelsDb.isEmpty()) {
            EqLog.log("Сервис: уровней нет, применять нечего")
            return
        }
        runCatching {
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(bands, levelsDb.size)) {
                eq.setBandLevel(i.toShort(), (levelsDb[i] * 100).toShort())
            }
            // Читаем обратно: подтверждение, что железо приняло значения.
            val readBack = (0 until bands).map { eq.getBandLevel(it.toShort()) / 100 }
            EqLog.log("Сервис: применено $levelsDb, прочитано обратно $readBack")
        }.onFailure { EqLog.log("Сервис: применение упало — ${it.message}") }
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
        const val ACTION_ATTACH_SESSION = "dev.aezochka.budscontrol.EQ_ATTACH_SESSION"
        const val ACTION_DETACH_SESSION = "dev.aezochka.budscontrol.EQ_DETACH_SESSION"
        const val EXTRA_LEVELS = "levels"
        const val EXTRA_SESSION = "session"
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

        /** Подключить эффект к конкретной сессии плеера. */
        fun attachSession(context: Context, session: Int) {
            val intent = Intent(context, EqService::class.java).apply {
                action = ACTION_ATTACH_SESSION
                putExtra(EXTRA_SESSION, session)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun detachSession(context: Context, session: Int) {
            val intent = Intent(context, EqService::class.java).apply {
                action = ACTION_DETACH_SESSION
                putExtra(EXTRA_SESSION, session)
            }
            runCatching { context.startService(intent) }
        }
    }
}
