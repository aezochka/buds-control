package dev.aezochka.budscontrol.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Заготовка под фоновое удержание соединения.
 * Сейчас соединение живёт в Application-scope репозитории,
 * сервис объявлен, чтобы не менять манифест при добавлении плитки/виджета.
 */
class BudsService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
