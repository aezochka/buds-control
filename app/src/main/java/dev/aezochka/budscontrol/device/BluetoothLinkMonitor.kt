package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Какие из наших гарнитур реально подключены к телефону по Bluetooth.
 *
 * Это отдельный слой от SPP: Android может уже держать наушники как медиа-
 * устройство, пока наш служебный RFCOMM-канал ещё не открыт. UI показывает
 * именно это состояние, поэтому свайпы по вкладкам и бару больше не могут
 * выглядеть как «отключилось».
 *
 * Важно: BluetoothManager.getConnectedDevices() работает ТОЛЬКО с GATT и на
 * профиль A2DP/HEADSET бросает IllegalArgumentException — именно этот вызов
 * ронял приложение на старте. Состояние берём через сам BluetoothDevice
 * (скрытый isConnected) с честным запасным вариантом.
 */
class BluetoothLinkMonitor(private val context: Context) {

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * Реально подключённое устройство определяем через BluetoothDevice.isConnected.
     * Метод скрыт из публичного SDK, но стабилен и используется всеми
     * подобными приложениями; при отсутствии — считаем не подключённым, а не
     * падаем.
     */
    @SuppressLint("MissingPermission")
    private fun isDeviceConnected(device: BluetoothDevice): Boolean = runCatching {
        val m = BluetoothDevice::class.java.getMethod("isConnected")
        m.invoke(device) as? Boolean ?: false
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun connectedFrom(addresses: Set<String>): Set<String> = runCatching {
        val bonded = adapter()?.bondedDevices.orEmpty()
        bonded.filter { it.address.uppercase() in addresses && isDeviceConnected(it) }
            .map { it.address.uppercase() }
            .toSet()
    }.getOrDefault(emptySet())

    /** Набор подключённых адресов из переданного списка профилей. */
    fun observeAny(addresses: Set<String>): Flow<Set<String>> = callbackFlow {
        if (addresses.isEmpty()) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }

        fun publish() { trySend(connectedFrom(addresses)) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { publish() }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        publish()

        // Подстраховка: часть прошивок не рассылает ACL-события для TWS,
        // поэтому раз в несколько секунд перепроверяем состояние сами.
        val poller = launch {
            while (isActive) {
                delay(4_000)
                publish()
            }
        }

        awaitClose {
            poller.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    /** Одно устройство: удобная обёртка над [observeAny]. */
    fun observe(address: String): Flow<Boolean> = callbackFlow {
        if (address.isBlank()) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val target = setOf(address.uppercase())
        val job = launch {
            observeAny(target).collect { trySend(it.isNotEmpty()) }
        }
        awaitClose { job.cancel() }
    }.distinctUntilChanged()
}
