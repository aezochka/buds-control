package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dev.aezochka.budscontrol.proto.AncMode
import dev.aezochka.budscontrol.proto.Cmd
import dev.aezochka.budscontrol.proto.EqPreset
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
    /** Когда кейс последний раз реально сообщил заряд. */
    val caseReportedAt: Long? = null,
    /** Значение восстановлено из памяти, а не получено сейчас. */
    val caseFromMemory: Boolean = false,
    val gameMode: Boolean = false,
    val ancMode: AncMode? = null,
    val eqPreset: EqPreset? = null,
    /** Гарнитура подтвердила команду поиска. */
    val findAcked: Boolean = false,
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

    /** Колбэк для сохранения заряда кейса на диск. */
    var onCaseReported: ((Int, Boolean) -> Unit)? = null

    /** Подставляет запомненный заряд кейса, пока гарнитура молчит. */
    fun seedCaseBattery(percent: Int, charging: Boolean, atMillis: Long) {
        _state.update {
            if (it.batteryCase != null) it
            else it.copy(batteryCase = percent, chargingCase = charging, caseReportedAt = atMillis, caseFromMemory = true)
        }
    }

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
            // Периодический опрос: заряд кейса приходит по событию, поэтому
            // без повторов плитка оставалась пустой всё время.
            launch {
                // Быстрый цикл: жесты на самих наушниках меняют режим/EQ, а
                // гарнитура не всегда присылает событие — поэтому переспрашиваем.
                while (_state.value.connected) {
                    delay(3_000)
                    if (!_state.value.connected) break
                    c.send(OppoProtocol.miscConfigReq(listOf(MiscType.GAME_MODE)))
                    c.send(OppoProtocol.equalizerReq())
                }
            }
            launch {
                while (_state.value.connected) {
                    delay(15_000)
                    if (!_state.value.connected) break
                    c.send(OppoProtocol.batteryReq())
                }
            }
            repeat(3) { attempt ->
                delay(1400L * (attempt + 1))
                val st = _state.value
                if (!st.connected) return@repeat
                if (st.batteryLeft == null && st.batteryRight == null) c.send(OppoProtocol.batteryReq())
                if (st.firmware == null) c.send(OppoProtocol.firmwareReq())
                if (st.touch.isEmpty()) c.send(OppoProtocol.touchConfigReq())
            }
        }
    }

    private suspend fun probe(c: SppConnection) {
        val supported = mutableSetOf<String>()
        c.send(OppoProtocol.firmwareReq()); supported += "firmware"
        c.send(OppoProtocol.batteryReq()); supported += "battery"
        c.send(OppoProtocol.miscConfigReq(listOf(MiscType.GAME_MODE, MiscType.MULTIPOINT, MiscType.LDAC)))
        c.send(OppoProtocol.touchConfigReq())
        c.send(OppoProtocol.ancConfigReq())
        c.send(OppoProtocol.equalizerReq())
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
                Cmd.MISC_CONFIG_RET -> applyMisc(frame.payload)
                Cmd.ANC_CONFIG_RET -> applyAnc(frame.payload)
                Cmd.TOUCH_CONFIG_RET -> applyTouch(frame.payload)
                Cmd.EQUALIZER_RET -> applyEq(frame.payload)
                Cmd.FIND_DEVICE_ACK -> _state.update {
                    it.copy(supported = it.supported + "find", findAcked = true)
                }
                Cmd.EQUALIZER_ACK -> _state.update { it.copy(supported = it.supported + "eq") }
                else -> Unit
            }
        }
    }

    private fun applyBattery(p: ByteArray, subscription: Boolean) {
        // Раскладка одинаковая для BATTERY_RET и SUBSCRIPTION_RET(BATTERY):
        // [0]=статус/тип, [1]=кол-во ячеек, далее пары (индекс, уровень) с i=2.
        if (p.size < 4) return
        var i = 2
        var l = _state.value.batteryLeft; var r = _state.value.batteryRight
        var cs = _state.value.batteryCase; var chg = _state.value.chargingCase
        var caseFresh = false
        while (i + 1 < p.size) {
            val idx = p[i].toInt() and 0xFF
            if (idx != 0xFF) {
                val level = p[i + 1].toInt() and 0x7F
                val charging = (p[i + 1].toInt() and 0x80) != 0
                when (idx - 1) {
                    0 -> { l = level; }
                    1 -> { r = level; }
                    2 -> { cs = level; chg = charging; caseFresh = true }
                }
            }
            i += 2
        }
        _state.update {
            it.copy(
                batteryLeft = l, batteryRight = r, batteryCase = cs, chargingCase = chg,
                caseReportedAt = if (caseFresh) System.currentTimeMillis() else it.caseReportedAt,
                caseFromMemory = if (caseFresh) false else it.caseFromMemory,
            )
        }
        if (caseFresh && cs != null) onCaseReported?.invoke(cs, chg)
    }

    private fun applyFirmware(p: ByteArray) {
        if (p.size < 3) return
        val raw = String(p, 2, p.size - 2).trim().trimEnd('\u0000')
        val version = raw.split(",").chunked(3).firstOrNull { it.size == 3 && it[1] == "2" }?.get(2) ?: raw
        _state.update { it.copy(firmware = version.ifBlank { null }) }
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

    private fun applyEq(p: ByteArray) {
        if (p.size < 2) return
        val code = p[p.size - 1].toInt() and 0xFF
        EqPreset.from(code)?.let { preset ->
            _state.update { it.copy(eqPreset = preset, supported = it.supported + "eq") }
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
    fun setEqualizer(preset: EqPreset) = send(OppoProtocol.equalizerSet(preset)) {
        _state.update { it.copy(eqPreset = preset) }
    }

    fun setAnc(mode: AncMode) = send(OppoProtocol.ancModeSet(mode)) { _state.update { it.copy(ancMode = mode) } }
    fun setTouch(side: TouchSide, type: TouchType, action: TouchAction) =
        send(OppoProtocol.touchConfigSet(side, type, action)) {
            _state.update { it.copy(touch = it.touch + ((side to type) to action)) }
        }
    /**
     * Поиск наушников. Если гарнитура не ответит ACK за 1.5 с — помечаем
     * функцию как неподдерживаемую, чтобы кнопка не врала.
     */
    fun findDevice(start: Boolean) {
        val c = conn ?: return
        scope.launch {
            _state.update { it.copy(findAcked = false) }
            c.send(OppoProtocol.findDevice(start))
            delay(1500)
            if (!_state.value.findAcked) {
                _state.update { it.copy(error = "Гарнитура не поддерживает поиск") }
            }
        }
    }
    fun refresh() {
        val c = conn ?: return
        scope.launch {
            // Кейс рапортует заряд не всегда с первого раза — просим дважды.
            c.send(OppoProtocol.batteryReq())
            delay(400)
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
