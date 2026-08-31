package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dev.aezochka.budscontrol.proto.AncMode
import dev.aezochka.budscontrol.proto.Cmd
import dev.aezochka.budscontrol.proto.MiscType
import dev.aezochka.budscontrol.proto.OppoProtocol
import dev.aezochka.budscontrol.proto.SubType
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Живое состояние гарнитуры — только то, что реально пришло по SPP. */
data class LiveState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String = "",
    val address: String = "",
    val firmware: String? = null,
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val chargingCase: Boolean = false,
    val inEarLeft: Boolean? = null,
    val inEarRight: Boolean? = null,
    val gameMode: Boolean = false,
    val ancMode: AncMode? = null,
    val touch: Map<Pair<TouchSide, TouchType>, TouchAction> = emptyMap(),
    val supported: Set<String> = emptySet(),
    val probed: Boolean = false,
    val error: String? = null,
)

/**
 * Обёртка над SppConnection: подключение, опрос возможностей, живые события.
 * Ничего не выдумывает — если гарнитура молчит, поле остаётся null.
 */
class BudsSession(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var conn: SppConnection? = null

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String) {
        val s = _state.value
        // Защита от вечного реконнекта: если уже работаем с этим адресом — выходим.
        if (s.address == address && (s.connected || s.connecting)) return
        disconnect()
        _state.value = LiveState(connecting = true, deviceName = name, address = address)
        scope.launch {
            val adapter: BluetoothAdapter? =
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            if (device == null) {
                _state.update { it.copy(connecting = false, error = "Не нашёл устройство $address") }
                return@launch
            }
            val c = SppConnection(device, scope)
            conn = c
            observe(c)
            observeClose(c)
            c.connect().onFailure { e ->
                _state.update {
                    it.copy(
                        connecting = false,
                        error = when (e) {
                            is SecurityException -> "Нужно разрешение Bluetooth"
                            else -> "Наушники заняты другим приложением или лежат в кейсе"
                        },
                    )
                }
                return@launch
            }
            _state.update { it.copy(connecting = false, connected = true) }
            c.send(OppoProtocol.subscribe(setOf(SubType.BATTERY, SubType.STATUS, SubType.ANC_SELECTOR, SubType.GAME_MODE)))
            probe(c)
            // Часть моделей игнорирует первый запрос — повторяем, как в Gadgetbridge.
            repeat(3) { attempt ->
                delay(1400L * (attempt + 1))
                val st = _state.value
                if (!st.connected) return@repeat
                if (st.batteryLeft == null && st.batteryRight == null) c.send(OppoProtocol.batteryReq())
                if (st.firmware == null) c.send(OppoProtocol.firmwareReq())
                if (st.touch.isEmpty()) c.send(OppoProtocol.touchConfigReq())
                if (st.inEarLeft == null) c.send(OppoProtocol.statusReq())
            }
        }
    }

    private suspend fun probe(c: SppConnection) {
        val supported = mutableSetOf<String>()
        c.send(OppoProtocol.firmwareReq()); supported += "firmware"
        c.send(OppoProtocol.batteryReq()); supported += "battery"
        c.send(OppoProtocol.statusReq()); supported += "status"
        c.send(OppoProtocol.miscConfigReq(listOf(MiscType.GAME_MODE, MiscType.MULTIPOINT, MiscType.LDAC)))
        c.send(OppoProtocol.touchConfigReq())
        c.send(OppoProtocol.ancConfigReq())
        _state.update { it.copy(supported = supported, probed = true) }
    }

    private fun observeClose(c: SppConnection) = scope.launch {
        c.closed.collect {
            _state.update {
                if (it.connected) it.copy(connected = false, error = "Соединение разорвано") else it
            }
        }
    }

    private fun observe(c: SppConnection) = scope.launch {
        c.frames.collect { frame ->
            when (frame.cmd) {
                Cmd.BATTERY_RET, Cmd.SUBSCRIPTION_RET -> applyBattery(frame.payload, frame.cmd == Cmd.SUBSCRIPTION_RET)
                Cmd.FIRMWARE_RET -> applyFirmware(frame.payload)
                Cmd.STATUS_RET -> applyStatus(frame.payload)
                Cmd.MISC_CONFIG_RET -> applyMisc(frame.payload)
                Cmd.ANC_CONFIG_RET -> applyAnc(frame.payload)
                Cmd.TOUCH_CONFIG_RET -> applyTouch(frame.payload)
                else -> Unit
            }
        }
    }

    private fun applyBattery(p: ByteArray, subscription: Boolean) {
        val start = if (subscription) 1 else 0
        if (p.size < start + 2) return
        var i = start + 2
        var l = _state.value.batteryLeft; var r = _state.value.batteryRight
        var cs = _state.value.batteryCase; var chg = _state.value.chargingCase
        while (i + 1 < p.size) {
            val idx = p[i].toInt() and 0xFF
            if (idx != 0xFF) {
                val level = p[i + 1].toInt() and 0x7F
                val charging = (p[i + 1].toInt() and 0x80) != 0
                when (idx - 1) { 0 -> l = level; 1 -> r = level; 2 -> if (level > 0) { cs = level; chg = charging } }
            }
            i += 2
        }
        _state.update { it.copy(batteryLeft = l, batteryRight = r, batteryCase = cs, chargingCase = chg) }
    }

    private fun applyFirmware(p: ByteArray) {
        if (p.size < 3) return
        val raw = String(p, 2, p.size - 2).trim().trimEnd('\u0000')
        val version = raw.split(",").chunked(3).firstOrNull { it.size == 3 && it[1] == "2" }?.get(2) ?: raw
        _state.update { it.copy(firmware = version.ifBlank { null }) }
    }

    private fun applyStatus(p: ByteArray) {
        if (p.size < 2) return
        var i = 2
        var left = _state.value.inEarLeft; var right = _state.value.inEarRight
        while (i + 1 < p.size) {
            val side = p[i].toInt() and 0xFF
            val value = p[i + 1].toInt() and 0xFF
            val inEar = value == 0x03
            if (side == 0x01) left = inEar
            if (side == 0x02) right = inEar
            i += 2
        }
        _state.update { it.copy(inEarLeft = left, inEarRight = right) }
    }

    private fun applyMisc(p: ByteArray) {
        if (p.size < 3 || p[0].toInt() != 0) return
        var i = 2
        while (i + 1 < p.size) {
            val on = (p[i + 1].toInt() and 0xFF) == 1
            when (MiscType.from(p[i].toInt() and 0xFF)) {
                MiscType.GAME_MODE -> _state.update { it.copy(gameMode = on, supported = it.supported + "game") }
                MiscType.MULTIPOINT -> _state.update { it.copy(supported = it.supported + "multipoint") }
                MiscType.LDAC -> _state.update { it.copy(supported = it.supported + "ldac") }
                else -> Unit
            }
            i += 2
        }
    }

    private fun applyAnc(p: ByteArray) {
        if (p.size < 4 || p[0].toInt() != 0) return
        AncMode.from(p[3].toInt() and 0xFF)?.let { m ->
            _state.update { it.copy(ancMode = m, supported = it.supported + "anc") }
        }
    }

    private fun applyTouch(p: ByteArray) {
        if (p.isEmpty() || p[0].toInt() != 0) return
        val map = _state.value.touch.toMutableMap()
        var i = 2
        while (i + 3 < p.size) {
            val side = TouchSide.from(p[i].toInt() and 0xFF)
            val typeCode = (p[i + 1].toInt() and 0xFF) or ((p[i + 2].toInt() and 0xFF) shl 8)
            val type = TouchType.from(typeCode)
            val action = TouchAction.from(p[i + 3].toInt() and 0xFF)
            if (side != null && type != null && action != null) map[side to type] = action
            i += 4
        }
        _state.update { it.copy(touch = map, supported = it.supported + "touch") }
    }

    fun setGameMode(on: Boolean) = send(OppoProtocol.miscConfigSet(MiscType.GAME_MODE, on)) {
        _state.update { it.copy(gameMode = on) }
    }
    fun setAnc(mode: AncMode) = send(OppoProtocol.ancModeSet(mode)) { _state.update { it.copy(ancMode = mode) } }
    fun setTouch(side: TouchSide, type: TouchType, action: TouchAction) =
        send(OppoProtocol.touchConfigSet(side, type, action)) {
            _state.update { it.copy(touch = it.touch + ((side to type) to action)) }
        }
    fun findDevice(start: Boolean) = send(OppoProtocol.findDevice(start)) {}
    fun refresh() {
        val c = conn ?: return
        scope.launch {
            c.send(OppoProtocol.batteryReq())
            c.send(OppoProtocol.statusReq())
            c.send(OppoProtocol.miscConfigReq(listOf(MiscType.GAME_MODE)))
        }
    }

    private fun send(data: ByteArray, onOk: () -> Unit) {
        val c = conn ?: return
        scope.launch { if (c.send(data)) onOk() }
    }

    fun disconnect() {
        conn?.close(); conn = null
        _state.value = LiveState()
    }
}
