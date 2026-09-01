package dev.aezochka.budscontrol.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dev.aezochka.budscontrol.R
import dev.aezochka.budscontrol.data.LocalStore
import dev.aezochka.budscontrol.data.TrackPoint
import dev.aezochka.budscontrol.data.WalkSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Фоновая запись прогулки. Стартует только по включению пользователем.
 *
 * Ключевое отличие от прошлой версии: каждая новая точка сразу пишется
 * в DataStore через upsertSession. Раньше сессия жила в памяти и терялась,
 * если Android убивал сервис — поэтому история всегда была пустой.
 */
class WalkService : Service(), LocationListener, SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: LocalStore
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager

    @Volatile private var session: WalkSession? = null
    private var latestSteps: Long? = null
    private var baselineSteps: Long? = null

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTracking(); return START_NOT_STICKY }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        // Тип сервиса подбираем по фактически выданным разрешениям: с типом
        // location без ACCESS_FINE_LOCATION Android бросает исключение и сервис
        // умирает молча — именно поэтому история не писалась вообще.
        val hasLocation = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val type = if (hasLocation) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(statusText(hasLocation)), type)
            } else {
                startForeground(NOTIFICATION_ID, notification(statusText(hasLocation)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось поднять foreground: ${e.message}")
            stopSelf()
            return
        }
        if (session != null) return

        scope.launch {
            val profile = store.profiles.first().firstOrNull { it.isSelected }
            val fresh = WalkSession(
                id = UUID.randomUUID().toString(),
                startedAtMillis = System.currentTimeMillis(),
                profileId = profile?.id ?: "unknown",
                startSteps = latestSteps,
            )
            session = fresh
            store.upsertSession(fresh)
            Log.i(TAG, "Начата сессия ${fresh.id}")
        }

        if (hasLocation) {
            runCatching {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 8f, this)
            }.onFailure { Log.w(TAG, "GPS недоступен: ${it.message}") }
            runCatching {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15_000L, 15f, this)
            }
            // Последняя известная точка — чтобы маршрут не начинался с пустоты.
            runCatching {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }.getOrNull()?.let { onLocationChanged(it) }
        } else {
            Log.w(TAG, "Нет разрешения на геолокацию — маршрут писаться не будет")
        }

        if (has(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            } ?: Log.w(TAG, "Шагомера нет в этом телефоне")
        }
    }

    private fun stopTracking() {
        runCatching { locationManager.removeUpdates(this) }
        runCatching { sensorManager.unregisterListener(this) }
        val finished = session?.copy(
            endedAtMillis = System.currentTimeMillis(),
            endSteps = latestSteps,
        )
        session = null
        if (finished != null) {
            scope.launch {
                store.upsertSession(finished)
                Log.i(TAG, "Сессия закрыта: ${finished.points.size} точек")
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        val current = session ?: return
        if (location.accuracy > 70f) return
        val last = current.points.lastOrNull()
        if (last != null) {
            val prev = Location("").apply { latitude = last.latitude; longitude = last.longitude }
            if (location.distanceTo(prev) < 6f) return
        }
        val point = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timeMillis = System.currentTimeMillis(),
            batteryPercent = null,
            steps = relativeSteps(),
        )
        val updated = current.copy(points = current.points + point, endSteps = latestSteps)
        session = updated
        // Пишем сразу: если сервис умрёт, точки останутся.
        scope.launch { store.upsertSession(updated) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull()?.toLong() ?: return
        latestSteps = total
        if (baselineSteps == null) baselineSteps = total
        val current = session ?: return
        if (current.startSteps == null) {
            val updated = current.copy(startSteps = total)
            session = updated
            scope.launch { store.upsertSession(updated) }
        }
    }

    /** Шаги от начала сессии — датчик считает от загрузки телефона. */
    private fun relativeSteps(): Long? {
        val total = latestSteps ?: return null
        val base = session?.startSteps ?: baselineSteps ?: return null
        return (total - base).coerceAtLeast(0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null
    private fun has(permission: String) =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun statusText(hasLocation: Boolean): String =
        if (hasLocation) "Записываю маршрут прогулки" else "Нет доступа к геолокации — маршрут не пишется"

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "История прогулок", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_buds)
            .setContentTitle("Buds Control")
            .setContentText(text)
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_START = "dev.aezochka.budscontrol.START_TRACKING"
        const val ACTION_STOP = "dev.aezochka.budscontrol.STOP_TRACKING"
        private const val CHANNEL = "walk"
        private const val NOTIFICATION_ID = 71
        private const val TAG = "WalkService"
    }
}
