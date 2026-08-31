package dev.aezochka.budscontrol.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import dev.aezochka.budscontrol.proto.AncMode
import dev.aezochka.budscontrol.proto.AncType
import dev.aezochka.budscontrol.proto.Cmd
import dev.aezochka.budscontrol.proto.MiscType
import dev.aezochka.budscontrol.proto.OppoProtocol
import dev.aezochka.budscontrol.proto.SubType
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана: одно место правды для UI. */
data class BudsState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val vendor: String = "",
    val firmware: String? = null,
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val chargingLeft: Boolean = false,
    val chargingRight: Boolean = false,
    val chargingCase: Boolean = false,
    val ancMode: AncMode = AncMode.OFF,
    val ancCycleModes: Set<AncMode> = emptySet(),
    val gameMode: Boolean = false,
    val multipoint: Boolean = false,
    val ldac: Boolean = false,
    val touchConfig: Map<Pair<TouchSide, TouchType>, TouchAction> = emptyMap(),
    val caps: Capabilities = Capabilities(),
    val error: String? = null,
) {
    val batteryMin: Int?
        get() = listOfNotNull(batteryLeft, batteryRight).minOrNull()
}

enum class ConnectionState { DISCONNECTED, CONNECTING, PROBING, CONNECTED, ERROR }

/**
 * Держит соединение, разбирает входящие кадры, отдаёт состояние в UI.
 */
class BudsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob())
    private var conn: SppConnection? = null

    private val _state = MutableStateFlow(BudsState())
    val state: StateFlow<BudsState> = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    fun pairedCandidates(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return try {
            adapter.bondedDevices
                .filter { it.bluetoothClass?.majorDeviceClass == 1024 || looksLikeBuds(it.name) }
                .sortedBy { it.name ?: it.address }
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет доступа к списку устройств: ${e.message}")
            emptyList()
        }
    }

    private fun looksLikeBuds(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return listOf("buds", "enco", "airdots", "earbud", "tws", "pods", "realme", "oneplus", "oppo")
            .any { it in n }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        val profile = DeviceDatabase.lookup(device.name)
        _state.value = BudsState(
            connection = ConnectionState.CONNECTING,
            deviceName = profile.displayName,
            deviceAddress = device.address,
            vendor = profile.vendor,
            caps = DeviceDatabase.initialCapabilities(profile),
        )

        scope.launch {
            val c = SppConnection(device, scope)
            conn = c
            observeFrames(c)

            c.connect().onFailure { e ->
                _state.update {
                    it.copy(
                        connection = ConnectionState.ERROR,
                        error = when (e) {
                            is SecurityException -> "Нужно разрешение на Bluetooth"
                            else -> "Не удалось подключиться. Наушники в чехле или заняты другим приложением?"
                        },
                    )
                }
                return@launch
            }

            _state.update { it.copy(connection = ConnectionState.PROBING) }

            // Подписка на самостоятельные уведомления от гарнитуры
            c.send(OppoProtocol.subscribe(setOf(SubType.BATTERY, SubType.STATUS, SubType.ANC_SELECTOR, SubType.GAME_MODE)))

            val caps = CapabilityProbe(c).run(_state.value.caps)
            _state.update { it.copy(caps = caps, connection = ConnectionState.CONNECTED) }
        }
    }

    private fun observeFrames(c: SppConnection) {
        scope.launch {
            c.frames.collect { frame ->
                when (frame.cmd) {
                    Cmd.BATTERY_RET, Cmd.SUBSCRIPTION_RET -> handleBatteryOrSubscription(frame)
                    Cmd.FIRMWARE_RET -> handleFirmware(frame.payload)
                    Cmd.ANC_CONFIG_RET -> handleAnc(frame.payload)
                    Cmd.MISC_CONFIG_RET -> handleMisc(frame.payload)
                    Cmd.TOUCH_CONFIG_RET -> handleTouch(frame.payload)
                    else -> Unit
                }
            }
        }
    }

    private fun handleBatteryOrSubscription(frame: OppoProtocol.Frame) {
        val p = frame.payload
        if (frame.cmd == Cmd.SUBSCRIPTION_RET) {
            when (SubType.from(p.firstOrNull()?.toInt()?.and(0xFF) ?: -1)) {
                SubType.BATTERY -> applyBattery(p)
                SubType.ANC_SELECTOR -> {
                    if (p.size > 2) AncMode.from(p[2].toInt() and 0xFF)?.let { m ->
                        _state.update { it.copy(ancMode = m) }
                    }
                }
                SubType.GAME_MODE -> {
                    if (p.size > 1) {
                        val on = (p[1].toInt() and 0xFF) == 1
                        _state.update { it.copy(gameMode = on) }
                    }
                }
                else -> Unit
            }
        } else {
            if (p.isNotEmpty() && p[0].toInt() == 0) applyBattery(p)
        }
    }

    private fun applyBattery(payload: ByteArray) {
        if (payload.size < 2) return
        var i = 2
        var l = _state.value.batteryLeft
        var r = _state.value.batteryRight
        var cs = _state.value.batteryCase
        var cl = false; var cr = false; var cc = false
        while (i + 1 < payload.size) {
            val idxRaw = payload[i].toInt() and 0xFF
            if (idxRaw == 0xFF) { i += 2; continue }
            val level = payload[i + 1].toInt() and 0x7F
            val charging = (payload[i + 1].toInt() and 0x80) != 0
            when (idxRaw - 1) {
                0 -> { l = level; cl = charging }
                1 -> { r = level; cr = charging }
                2 -> if (level > 0) { cs = level; cc = charging }
            }
            i += 2
        }
        _state.update {
            it.copy(
                batteryLeft = l, batteryRight = r, batteryCase = cs,
                chargingLeft = cl, chargingRight = cr, chargingCase = cc,
            )
        }
    }

    private fun handleFirmware(payload: ByteArray) {
        if (payload.size < 3) return
        val raw = String(payload, 2, payload.size - 2).trim().trimEnd('\u0000')
        val parts = raw.split(",")
        val version = if (parts.size >= 3) {
            parts.chunked(3).firstOrNull { it.size == 3 && it[1] == "2" }?.get(2) ?: raw
        } else raw
        _state.update { it.copy(firmware = version.ifBlank { null }) }
    }

    private fun handleAnc(payload: ByteArray) {
        if (payload.size < 4 || payload[0].toInt() != 0) return
        val type = AncType.entries.firstOrNull { it.code == (payload[1].toInt() and 0xFF) } ?: return
        val value = payload[3].toInt() and 0xFF
        when (type) {
            AncType.MODE -> AncMode.from(value)?.let { m -> _state.update { it.copy(ancMode = m) } }
            AncType.TOUCH_CYCLE_MODES -> {
                val modes = AncMode.fromMask(value)
                _state.update { s ->
                    s.copy(
                        ancCycleModes = modes,
                        caps = s.caps.copy(ancModes = if (modes.isNotEmpty()) modes else s.caps.ancModes),
                    )
                }
            }
        }
    }

    private fun handleMisc(payload: ByteArray) {
        if (payload.size < 3 || payload[0].toInt() != 0) return
        var i = 2
        while (i + 1 < payload.size) {
            val type = MiscType.from(payload[i].toInt() and 0xFF)
            val on = (payload[i + 1].toInt() and 0xFF) == 1
            when (type) {
                MiscType.GAME_MODE -> _state.update { it.copy(gameMode = on) }
                MiscType.MULTIPOINT -> _state.update { it.copy(multipoint = on) }
                MiscType.LDAC -> _state.update { it.copy(ldac = on) }
                else -> Unit
            }
            i += 2
        }
    }

    private fun handleTouch(payload: ByteArray) {
        if (payload.isEmpty() || payload[0].toInt() != 0) return
        val map = _state.value.touchConfig.toMutableMap()
        var i = 2
        while (i + 3 < payload.size) {
            val side = TouchSide.from(payload[i].toInt() and 0xFF)
            val typeCode = (payload[i + 1].toInt() and 0xFF) or ((payload[i + 2].toInt() and 0xFF) shl 8)
            val type = TouchType.from(typeCode)
            val action = TouchAction.from(payload[i + 3].toInt() and 0xFF)
            if (side != null && type != null && action != null) map[side to type] = action
            i += 4
        }
        _state.update { it.copy(touchConfig = map) }
    }

    // ---- Действия из UI ----

    fun setAncMode(mode: AncMode) = send(OppoProtocol.ancModeSet(mode)) {
        _state.update { it.copy(ancMode = mode) }
    }

    fun setAncCycle(modes: Set<AncMode>) {
        if (modes.size < 2) return
        send(OppoProtocol.ancCycleModesSet(modes)) { _state.update { it.copy(ancCycleModes = modes) } }
    }

    fun setGameMode(on: Boolean) = send(OppoProtocol.miscConfigSet(MiscType.GAME_MODE, on)) {
        _state.update { it.copy(gameMode = on) }
    }

    fun setMultipoint(on: Boolean) = send(OppoProtocol.miscConfigSet(MiscType.MULTIPOINT, on)) {
        _state.update { it.copy(multipoint = on) }
    }

    fun setLdac(on: Boolean) = send(OppoProtocol.miscConfigSet(MiscType.LDAC, on)) {
        _state.update { it.copy(ldac = on) }
    }

    fun setTouch(side: TouchSide, type: TouchType, action: TouchAction) =
        send(OppoProtocol.touchConfigSet(side, type, action)) {
            _state.update { it.copy(touchConfig = it.touchConfig + ((side to type) to action)) }
        }

    fun findDevice(start: Boolean) = send(OppoProtocol.findDevice(start)) {}

    fun refresh() {
        val c = conn ?: return
        scope.launch {
            c.send(OppoProtocol.batteryReq())
            if (_state.value.caps.anc.yes) c.send(OppoProtocol.ancConfigReq(AncType.MODE))
            val misc = _state.value.caps.supportedMisc.toList()
            if (misc.isNotEmpty()) c.send(OppoProtocol.miscConfigReq(misc))
            if (_state.value.caps.touch.yes) c.send(OppoProtocol.touchConfigReq())
        }
    }

    private fun send(data: ByteArray, onOk: () -> Unit) {
        val c = conn ?: return
        scope.launch { if (c.send(data)) onOk() }
    }

    fun disconnect() {
        conn?.close()
        conn = null
        _state.update { BudsState() }
    }

    private companion object { const val TAG = "BudsRepository" }
}
