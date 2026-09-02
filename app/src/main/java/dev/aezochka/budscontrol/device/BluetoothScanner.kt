package dev.aezochka.budscontrol.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Настоящее обнаружение гарнитур. Два источника:
 *  1. bondedDevices — то, что уже сопряжено в системе;
 *  2. startDiscovery — живой поиск рядом, чтобы профиль добавлялся
 *     «через Bluetooth», а не вводился руками.
 */
class BluetoothScanner(private val context: Context) {

    data class Found(
        val name: String?,
        val address: String,
        val bonded: Boolean,
        val rssi: Int? = null,
    )

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun hasPermission(): Boolean {
        val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.BLUETOOTH
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun bonded(): List<Found> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty()
            .filter { looksLikeAudio(it) }
            .map { Found(it.name, it.address, bonded = true) }
            .sortedBy { it.name ?: it.address }
    }

    /** Живой поиск. Эмитит найденные устройства по мере обнаружения. */
    @SuppressLint("MissingPermission")
    fun discover(): Flow<Found> = callbackFlow {
        val bt = adapter
        if (bt == null || !hasPermission() || !bt.isEnabled) { close(); return@callbackFlow }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                if (!looksLikeAudio(device)) return
                trySend(Found(device.name, device.address, bonded = device.bondState == BluetoothDevice.BOND_BONDED, rssi = rssi))
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND),
            ContextCompat.RECEIVER_EXPORTED,
        )
        bonded().forEach { trySend(it) }
        bt.startDiscovery()

        awaitClose {
            runCatching { bt.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /**
     * Только наушники и гарнитуры.
     *
     * Раньше проходил любой AUDIO_VIDEO: в списке оказывались колонки,
     * магнитолы и телевизоры. Теперь смотрим точный подкласс устройства,
     * а имя — лишь подстраховка для TWS, которые рапортуют класс неверно.
     */
    @SuppressLint("MissingPermission")
    private fun looksLikeAudio(device: BluetoothDevice): Boolean {
        val deviceClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
        val headphoneClasses = setOf(
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
        )
        if (deviceClass in headphoneClasses) return true

        // Колонки и прочую акустику отсекаем явно, даже если имя похожее.
        val speakerClasses = setOf(
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
        )
        if (deviceClass in speakerClasses) return false

        val n = runCatching { device.name }.getOrNull()?.lowercase().orEmpty()
        if (n.isEmpty()) return false
        // Явно не наушники — отбрасываем по имени.
        if (listOf("speaker", "колонка", "tv", "soundbar", "car", "audio system").any { it in n }) return false
        return listOf("buds", "enco", "tws", "earbud", "pods", "headset", "headphone", "наушник")
            .any { it in n }
    }
}
