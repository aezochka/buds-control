package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Состояние системного Bluetooth-подключения конкретной гарнитуры.
 *
 * Это отдельный слой от SPP. Android может уже держать T110 как медиа-устройство
 * (A2DP/HEADSET), в то время как наш служебный RFCOMM-канал ещё не открыт.
 * UI показывает именно это реальное состояние Bluetooth, а SPP открывается
 * только после системного подключения. Поэтому свайпы по UI больше не могут
 * быть причиной «отвала» наушников.
 */
class BluetoothLinkMonitor(private val context: Context) {

    /**
     * Набор реально подключённых из списка профилей.
     *
     * Это важно: верхний бар меняет ВЫБОР ДЛЯ UI, но не должен менять
     * физически подключённую гарнитуру. Контроллер получает весь список
     * профилей и выбирает только тот MAC, который Android подтверждает как
     * A2DP/HEADSET.
     */
    @SuppressLint("MissingPermission")
    fun observeAny(addresses: Set<String>): Flow<Set<String>> = callbackFlow {
        if (addresses.isEmpty()) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }

        fun connectedAddresses(): Set<String> {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val devices = buildList {
                addAll(manager?.getConnectedDevices(BluetoothProfile.A2DP).orEmpty())
                addAll(manager?.getConnectedDevices(BluetoothProfile.HEADSET).orEmpty())
            }
            return devices.map { it.address.uppercase() }.filter { it in addresses }.toSet()
        }

        fun publish() { trySend(connectedAddresses()) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { publish() }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        publish()
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    @SuppressLint("MissingPermission")
    fun observe(address: String): Flow<Boolean> = callbackFlow {
        if (address.isBlank()) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        fun isConnected(): Boolean {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val connected = buildList {
                // A2DP — медиа, HEADSET — звонки. TWS обычно объявляет оба.
                addAll(manager?.getConnectedDevices(BluetoothProfile.A2DP).orEmpty())
                addAll(manager?.getConnectedDevices(BluetoothProfile.HEADSET).orEmpty())
            }
            return connected.any { it.address.equals(address, ignoreCase = true) }
        }

        fun publish() { trySend(isConnected()) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?: return
                if (!device.address.equals(address, ignoreCase = true)) return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothAdapter.ACTION_STATE_CHANGED -> publish()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        publish()
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()
}
