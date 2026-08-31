package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import dev.aezochka.budscontrol.proto.OppoProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Один RFCOMM-сокет к гарнитуре + цикл чтения кадров.
 *
 * Устройство отвечает только на то, что понимает, поэтому «нет ответа» —
 * это валидный результат, а не ошибка: на нём строится определение функций.
 */
class SppConnection(
    private val device: BluetoothDevice,
    private val scope: CoroutineScope,
) {
    private var socket: BluetoothSocket? = null
    private val writeLock = Mutex()

    private val _frames = MutableSharedFlow<OppoProtocol.Frame>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<OppoProtocol.Frame> = _frames.asSharedFlow()

    @Volatile
    var isConnected: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uuid = UUID.fromString(OppoProtocol.SPP_UUID)
            val s = device.createRfcommSocketToServiceRecord(uuid)
            s.connect()
            socket = s
            isConnected = true
            startReadLoop(s)
            Result.success(Unit)
        } catch (e: IOException) {
            Log.w(TAG, "Не удалось открыть SPP: ${e.message}")
            isConnected = false
            runCatching { socket?.close() }
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения BLUETOOTH_CONNECT: ${e.message}")
            isConnected = false
            Result.failure(e)
        }
    }

    private fun startReadLoop(s: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val pending = ArrayDeque<Byte>()
            try {
                while (isActive && s.isConnected) {
                    val read = s.inputStream.read(buffer)
                    if (read <= 0) break
                    for (i in 0 until read) pending.addLast(buffer[i])
                    // Отдаём кодеку весь накопленный буфер: кадры могут склеиваться
                    // и рваться на границе чтения.
                    val bytes = pending.toByteArray()
                    val decoded = OppoProtocol.decode(bytes)
                    if (decoded.isNotEmpty()) {
                        pending.clear()
                        decoded.forEach { _frames.emit(it) }
                    } else if (pending.size > 4096) {
                        pending.clear() // защита от мусора
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Чтение прервано: ${e.message}")
            } finally {
                isConnected = false
            }
        }
    }

    suspend fun send(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val s = socket ?: return@withContext false
        writeLock.withLock {
            try {
                s.outputStream.write(data)
                s.outputStream.flush()
                true
            } catch (e: IOException) {
                Log.w(TAG, "Запись не прошла: ${e.message}")
                isConnected = false
                false
            }
        }
    }

    fun close() {
        isConnected = false
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        const val TAG = "SppConnection"
    }
}
