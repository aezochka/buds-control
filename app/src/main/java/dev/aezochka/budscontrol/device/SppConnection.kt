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
 * RFCOMM-канал к гарнитуре.
 *
 * Важное отличие от первой версии: UUID берётся из того, что реально
 * объявило устройство (как в Gadgetbridge), а не хардкодом. Если SPP нет
 * в списке — пробуем vendor-UUID 079a, потом небезопасный сокет по каналу 1.
 * Именно жёсткий хардкод SPP приводил к вечному «подключаюсь → отвал».
 */
class SppConnection(
    private val device: BluetoothDevice,
    private val scope: CoroutineScope,
) {
    private var socket: BluetoothSocket? = null
    private val writeLock = Mutex()

    private val _frames = MutableSharedFlow<OppoProtocol.Frame>(
        replay = 0, extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<OppoProtocol.Frame> = _frames.asSharedFlow()

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    @Volatile var isConnected: Boolean = false; private set

    @SuppressLint("MissingPermission")
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        for (uuid in candidateUuids()) {
            try {
                Log.i(TAG, "Пробую UUID $uuid")
                val s = device.createRfcommSocketToServiceRecord(uuid)
                s.connect()
                socket = s
                isConnected = true
                startReadLoop(s)
                Log.i(TAG, "Подключено по $uuid")
                return@withContext Result.success(Unit)
            } catch (e: IOException) {
                Log.w(TAG, "UUID $uuid не подошёл: ${e.message}")
                runCatching { socket?.close() }
                socket = null
            } catch (e: SecurityException) {
                return@withContext Result.failure(e)
            }
        }
        // Последняя попытка: небезопасный сокет на первом канале.
        try {
            val s = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(OppoProtocol.SPP_UUID))
            s.connect()
            socket = s; isConnected = true; startReadLoop(s)
            Result.success(Unit)
        } catch (e: Exception) {
            isConnected = false
            Result.failure(e)
        }
    }

    /** Порядок кандидатов: то что объявило устройство, затем известные UUID. */
    @SuppressLint("MissingPermission")
    private fun candidateUuids(): List<UUID> {
        val advertised = runCatching { device.uuids?.map { it.uuid } }.getOrNull().orEmpty()
        val spp = UUID.fromString(OppoProtocol.SPP_UUID)
        val vendor = UUID.fromString("0000079a-d102-11e1-9b23-00025b00a5a5")
        return buildList {
            advertised.firstOrNull { it == spp }?.let { add(it) }
            advertised.firstOrNull { it == vendor }?.let { add(it) }
            addAll(advertised.filterNot { it == spp || it == vendor })
            if (none { it == spp }) add(spp)
            if (none { it == vendor }) add(vendor)
        }.distinct()
    }

    private fun startReadLoop(s: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            val chunk = ByteArray(1024)
            val acc = ArrayList<Byte>(2048)
            try {
                while (isActive && s.isConnected) {
                    val read = s.inputStream.read(chunk)
                    if (read <= 0) break
                    for (i in 0 until read) acc.add(chunk[i])
                    // Кадры могут склеиваться и рваться на границе чтения.
                    // Разбираем только целые, а недополученный хвост оставляем
                    // в накопителе — он дополнится следующим read().
                    val bytes = acc.toByteArray()
                    val result = OppoProtocol.decode(bytes)
                    if (result.consumed > 0) {
                        acc.clear()
                        for (i in result.consumed until bytes.size) acc.add(bytes[i])
                        result.frames.forEach { _frames.emit(it) }
                    }
                    // Защита от мусорного потока без преамбулы.
                    if (acc.size > 8192) acc.clear()
                }
            } catch (e: IOException) {
                Log.d(TAG, "Чтение прервано: ${e.message}")
            } finally {
                isConnected = false
                _closed.emit(Unit)
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

    private companion object { const val TAG = "SppConnection" }
}
