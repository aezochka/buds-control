package dev.aezochka.budscontrol.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Определяет, лежит ли телефон экраном вниз.
 *
 * Берём акселерометр, а не гироскоп: гироскоп даёт скорость вращения, а нам
 * нужна сама ориентация относительно земли. Ось Z около -9.8 означает, что
 * экран смотрит в стол.
 *
 * Порог с запасом и в обе стороны (гистерезис), иначе на границе состояние
 * дёргается туда-обратно при малейшем шевелении.
 */
class FaceDownSensor(context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val available: Boolean get() = sensor != null

    val faceDown: Flow<Boolean> = callbackFlow {
        val mgr = manager
        val acc = sensor
        if (mgr == null || acc == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        var down = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values.getOrNull(2) ?: return
                // Вниз — только уверенно; обратно — раньше, чтобы название
                // возвращалось сразу, как телефон подняли.
                if (!down && z < -7.5f) {
                    down = true
                    trySend(true)
                } else if (down && z > -5.5f) {
                    down = false
                    trySend(false)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // Нам нужен только факт переворота, поэтому берём самый редкий поток
        // событий: SENSOR_DELAY_UI дёргал UI десятки раз в секунду и давал
        // микрофризы.
        mgr.registerListener(listener, acc, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { mgr.unregisterListener(listener) }
    }.distinctUntilChanged()
}
