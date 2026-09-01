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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.aezochka.budscontrol.R
import dev.aezochka.budscontrol.data.LocalStore
import dev.aezochka.budscontrol.data.TrackPoint
import dev.aezochka.budscontrol.data.WalkSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Запись прогулки на реальных данных.
 *
 * Точки берём через FusedLocationProvider: чистый LocationManager на многих
 * телефонах молчит в фоне (агрессивный энергосейв вендора), из-за чего маршрут
 * оставался пустым. Fused сам выбирает GPS/сеть и работает при потушенном экране.
 */
class WalkService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: LocalStore
    private lateinit var sensorManager: SensorManager
    private lateinit var fused: FusedLocationProviderClient

    @Volatile private var session: WalkSession? = null
    private var heartbeat: kotlinx.coroutines.Job? = null
    private var latestSteps: Long? = null
    private var baselineSteps: Long? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { handleLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fused = LocationServices.getFusedLocationProviderClient(this)
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
        val hasLocation = has(Manifest.permission.ACCESS_FINE_LOCATION) ||
            has(Manifest.permission.ACCESS_COARSE_LOCATION)
        val type = if (hasLocation) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(statusText(hasLocation, 0)), type)
            } else {
                startForeground(NOTIFICATION_ID, notification(statusText(hasLocation, 0)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось поднять foreground: ${e.message}")
            stopSelf()
            return
        }

        if (session == null) {
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
        }

        if (hasLocation) {
            // 5 секунд / 5 метров: достаточно частo, чтобы маршрут был живым.
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(3_000L)
                .setMinUpdateDistanceMeters(5f)
                .setWaitForAccurateLocation(false)
                .build()
            runCatching { fused.requestLocationUpdates(request, locationCallback, mainLooper) }
                .onFailure { Log.e(TAG, "Fused отказал: ${it.message}") }
            // Последняя известная точка — чтобы маршрут начался сразу.
            runCatching {
                fused.lastLocation.addOnSuccessListener { it?.let(::handleLocation) }
            }
        } else {
            Log.w(TAG, "Нет разрешения на геолокацию — маршрут писаться не будет")
        }

        if (has(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        startHeartbeat()
    }

    /**
     * Пульс раз в 10 секунд. Раньше сессия писалась только при смещении >= 4 м,
     * поэтому стоя на месте время и шаги на экране не двигались вообще —
     * данные оживали лишь после перезапуска приложения.
     */
    private fun startHeartbeat() {
        if (heartbeat != null) return
        heartbeat = scope.launch {
            while (isActive) {
                delay(10_000)
                val current = session ?: continue
                val alive = current.copy(
                    endSteps = latestSteps,
                    lastSeenMillis = System.currentTimeMillis(),
                )
                session = alive
                store.upsertSession(alive)
            }
        }
    }

    private fun stopTracking() {
        heartbeat?.cancel(); heartbeat = null
        runCatching { fused.removeLocationUpdates(locationCallback) }
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

    private fun handleLocation(location: Location) {
        val current = session ?: return
        if (location.accuracy > 100f) return
        val last = current.points.lastOrNull()
        if (last != null) {
            val prev = Location("").apply { latitude = last.latitude; longitude = last.longitude }
            if (location.distanceTo(prev) < 4f) {
                // Стоим на месте: точку не добавляем, но отмечаем, что живы.
                val touched = current.copy(lastSeenMillis = System.currentTimeMillis(), endSteps = latestSteps)
                session = touched
                scope.launch { store.upsertSession(touched) }
                return
            }
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
        scope.launch { store.upsertSession(updated) }
        updateNotification(updated.points.size)
    }

    private fun updateNotification(points: Int) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(statusText(true, points)))
        }
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

    private fun relativeSteps(): Long? {
        val total = latestSteps ?: return null
        val base = session?.startSteps ?: baselineSteps ?: return null
        return (total - base).coerceAtLeast(0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null
    private fun has(permission: String) =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun statusText(hasLocation: Boolean, points: Int): String = when {
        !hasLocation -> "Нет доступа к геолокации — маршрут не пишется"
        points == 0 -> "Ищу спутники…"
        else -> "Записано точек: $points"
    }

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
