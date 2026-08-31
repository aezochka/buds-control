package dev.aezochka.budscontrol.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
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
 * Foreground tracker, only started by the user. It samples real GPS and the
 * phone's TYPE_STEP_COUNTER. It never fabricates route points or steps.
 */
class WalkService : Service(), LocationListener, SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: LocalStore
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var session: WalkSession? = null
    private var latestSteps: Long? = null

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
        if (session != null) return
        startForeground(NOTIFICATION_ID, notification("История прогулки включена"))
        scope.launch {
            val profile = store.profiles.first().firstOrNull { it.isSelected } ?: return@launch
            session = WalkSession(
                id = UUID.randomUUID().toString(),
                startedAtMillis = System.currentTimeMillis(),
                profileId = profile.id,
            )
        }
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            runCatching { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15_000L, 12f, this) }
            runCatching { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20_000L, 20f, this) }
        }
        if (has(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun stopTracking() {
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
        val finished = session?.copy(endedAtMillis = System.currentTimeMillis(), endSteps = latestSteps)
        session = null
        if (finished != null) scope.launch { store.saveSessions(store.sessions.first() + finished) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        val current = session ?: return
        if (location.accuracy > 60f) return
        val last = current.points.lastOrNull()
        if (last != null && location.distanceTo(Location("").apply { latitude = last.latitude; longitude = last.longitude }) < 8f) return
        val point = TrackPoint(location.latitude, location.longitude, System.currentTimeMillis(), null, latestSteps)
        session = current.copy(points = current.points + point)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) latestSteps = event.values[0].toLong()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null
    private fun has(permission: String) = ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(CHANNEL, "История прогулок", NotificationManager.IMPORTANCE_LOW)
        )
    }
    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_buds).setContentTitle("Buds Control").setContentText(text).setOngoing(true).build()

    companion object { const val ACTION_STOP = "dev.aezochka.budscontrol.STOP_TRACKING"; private const val CHANNEL = "walk"; private const val NOTIFICATION_ID = 71 }
}
