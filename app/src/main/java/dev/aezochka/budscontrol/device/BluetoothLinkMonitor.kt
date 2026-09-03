package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
 * Какие из наших гарнитур РЕАЛЬНО подключены к телефону.
 *
 * История ошибок здесь важна:
 *  - `BluetoothManager.getConnectedDevices(A2DP)` бросает исключение: этот
 *    метод только для GATT. Он ронял приложение на старте.
 *  - `BluetoothDevice.isConnected` через рефлексию оказался ненадёжным: он
 *    отдавал true для просто сопряжённых устройств, поэтому зелёный индикатор
 *    горел для выключенных наушников и даже при выключенном Bluetooth.
 *
 * Правильный путь — прокси профилей A2DP и HEADSET через
 * `BluetoothAdapter.getProfileProxy`: они дают именно подключённые устройства.
 * Если адаптер выключен, ответ всегда пустой.
 */
class BluetoothLinkMonitor(private val context: Context) {

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Набор подключённых адресов из переданного списка профилей. */
    @SuppressLint("MissingPermission")
    fun observeAny(addresses: Set<String>): Flow<Set<String>> = callbackFlow {
        if (addresses.isEmpty()) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }

        var a2dp: BluetoothA2dp? = null
        var headset: BluetoothHeadset? = null

        fun connectedNow(): Set<String> {
            val bt = adapter()
            // Bluetooth выключен — никаких подключений быть не может.
            if (bt == null || !bt.isEnabled) return emptySet()
            val devices = buildList {
                // connectedDevices отдаёт только реально подключённые по профилю,
                // а не просто сопряжённые. Именно это раньше и не проверялось.
                addAll(runCatching { a2dp?.connectedDevices.orEmpty() }.getOrDefault(emptyList()))
                addAll(runCatching { headset?.connectedDevices.orEmpty() }.getOrDefault(emptyList()))
            }
            return devices
                .map { it.address.uppercase() }
                .filter { it in addresses }
                .toSet()
        }

        fun publish() { trySend(connectedNow()) }

        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                when (profile) {
                    BluetoothProfile.A2DP -> a2dp = proxy as? BluetoothA2dp
                    BluetoothProfile.HEADSET -> headset = proxy as? BluetoothHeadset
                }
                publish()
            }

            override fun onServiceDisconnected(profile: Int) {
                when (profile) {
                    BluetoothProfile.A2DP -> a2dp = null
                    BluetoothProfile.HEADSET -> headset = null
                }
                publish()
            }
        }

        val bt = adapter()
        runCatching { bt?.getProfileProxy(context, serviceListener, BluetoothProfile.A2DP) }
        runCatching { bt?.getProfileProxy(context, serviceListener, BluetoothProfile.HEADSET) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { publish() }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        publish()

        // Часть прошивок не рассылает события для TWS — перепроверяем сами.
        val poller = launch {
            while (isActive) {
                delay(3_000)
                publish()
            }
        }

        awaitClose {
            poller.cancel()
            runCatching { context.unregisterReceiver(receiver) }
            val current = adapter()
            runCatching { a2dp?.let { current?.closeProfileProxy(BluetoothProfile.A2DP, it) } }
            runCatching { headset?.let { current?.closeProfileProxy(BluetoothProfile.HEADSET, it) } }
        }
    }.distinctUntilChanged()

    /** Одно устройство: удобная обёртка над [observeAny]. */
    fun observe(address: String): Flow<Boolean> = callbackFlow {
        if (address.isBlank()) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val job = launch {
            observeAny(setOf(address.uppercase())).collect { trySend(it.isNotEmpty()) }
        }
        awaitClose { job.cancel() }
    }.distinctUntilChanged()
}
