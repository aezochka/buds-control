package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.aezochka.budscontrol.proto.ApplePods
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Слушает BLE-рекламу AirPods.
 *
 * AirPods не отвечают по SPP, поэтому заряд берём из широковещательных
 * пакетов Apple. Сопряжение и подключение не нужны — только сканирование,
 * и это работает даже когда наушники подключены к другому телефону.
 */
class ApplePodsScanner(private val context: Context) {

    data class Found(
        val address: String,
        val rssi: Int,
        val status: ApplePods.Status,
    )

    @SuppressLint("MissingPermission")
    fun scan(): Flow<Found> = callbackFlow {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val payload = result.scanRecord
                    ?.getManufacturerSpecificData(ApplePods.APPLE_MANUFACTURER_ID)
                    ?: return
                val status = ApplePods.parse(payload) ?: return
                trySend(Found(result.device.address, result.rssi, status))
            }
        }

        // Фильтр по Apple: иначе в поток летит вся BLE-эфирная нагрузка вокруг.
        val filter = ScanFilter.Builder()
            .setManufacturerData(ApplePods.APPLE_MANUFACTURER_ID, byteArrayOf(), byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        val started = runCatching { scanner.startScan(listOf(filter), settings, callback) }.isSuccess
        awaitClose {
            if (started) runCatching { scanner.stopScan(callback) }
        }
    }
}
