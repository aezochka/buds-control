package dev.aezochka.budscontrol.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.aezochka.budscontrol.R

/** Уведомление о срабатывании таймера сна — раньше он завершался молча. */
object SleepNotifier {
    private const val CHANNEL = "sleep_timer"
    private const val ID = 91

    fun notifyFinished(context: Context) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Таймер сна", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Сообщает, когда таймер сна остановил музыку"
                        enableVibration(true)
                    }
                )
            }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_buds)
                .setContentTitle("Таймер сна сработал")
                .setContentText("Музыка поставлена на паузу. Спокойной ночи.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Музыка поставлена на паузу. Наушники можно снять — они сами уйдут в сон в кейсе."
                    )
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()
            manager.notify(ID, notification)
        }
    }
}
