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
    /**
     * Android уже держит эту гарнитуру как медиа-устройство (A2DP/HEADSET).
     * Это не SPP: наушники могут играть музыку, а служебный канал ещё не
     * открыт. UI показывает зелёный кружок именно по этому флагу.
     */
    val bluetoothConnected: Boolean = false,
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
    val spatialAudio: Boolean = false,
    val multipoint: Boolean = false,
    /** Наушник не отвечает по заряду — обычно лежит в кейсе. */
    val budInCaseLeft: Boolean = false,
    val budInCaseRight: Boolean = false,
    val ancMode: AncMode? = null,
    /** Гарнитура подтвердила команду поиска. */
    val findAcked: Boolean = false,
    /** Гарнитура подтвердила кадр эквалайзера (0x0418). */
    val eqAcked: Boolean = false,
    val touch: Map<Pair<TouchSide, TouchType>, TouchAction> = emptyMap(),
    val supported: Set<String> = emptySet(),
    /** Коды команд, которые гарнитура объявила поддерживаемыми (ответ 0x8100). */
    val capabilities: Set<Int> = emptySet(),
    /**
     * Опрос возможностей завершён: на запросы либо ответили, либо истёк
     * таймаут. До этого момента ничего не скрываем — иначе рабочие функции
     * пропадают, пока идёт handshake.
     */
    val probeComplete: Boolean = false,
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
    /**
     * Подключение. [force] игнорирует защиту от повторов: нужно при возврате
     * в приложение, иначе состояние «подключаюсь» залипало навсегда —
     * сокет уже умер, пока приложение было в фоне, а флаг остался.
     */
    /** Сокет реально жив — переподключаться не нужно. */
    fun hasLiveSocket(): Boolean = conn?.isConnected == true

    fun connect(address: String, name: String, force: Boolean = false) {
        val s = _state.value
        val alive = conn?.isConnected == true
        // Сокет к тому же адресу жив — выходим сразу, не глядя на флаги
        // состояния. Раньше при alive=true, но connected=false шли дальше и
        // рвали рабочее соединение: отсюда постоянный реконект.
        if (s.address == address && alive) {
            if (!s.connected && !s.connecting) {
                _state.update { it.copy(connected = true, connecting = false) }
            }
            return
        }

        // Дальше идём только если сокета нет или это другое устройство.
        conn?.close()
        conn = null
        // При переподключении к ТОМУ ЖЕ устройству сохраняем уже известные
        // значения: полный сброс гасил игровой режим и другие тумблеры, и они
        // мигали, пока гарнитура не ответит заново.
        val keep = if (s.address == address) s else null
        _state.value = LiveState(
            connecting = true,
            bluetoothConnected = true,
            deviceName = name,
            address = address,
            gameMode = keep?.gameMode ?: false,
            multipoint = keep?.multipoint ?: false,
            spatialAudio = keep?.spatialAudio ?: false,
            ancMode = keep?.ancMode,
            firmware = keep?.firmware,
            touch = keep?.touch ?: emptyMap(),
            supported = keep?.supported ?: emptySet(),
            capabilities = keep?.capabilities ?: emptySet(),
            batteryLeft = keep?.batteryLeft,
            batteryRight = keep?.batteryRight,
            batteryCase = keep?.batteryCase,
        )
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
            // Таймаут: без него состояние «подключаюсь» висело бесконечно,
            // и серый кружок крутился до перезапуска приложения.
            val timeout = launch {
                delay(12_000)
                if (_state.value.connecting) {
                    _state.update {
                        it.copy(connecting = false, connected = false, error = "Наушники не отвечают")
                    }
                    c.close()
                }
            }
            c.connect().onFailure { e ->
                timeout.cancel()
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
            timeout.cancel()
            _state.update { it.copy(connecting = false, connected = true, bluetoothConnected = true) }
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
        // Только отправляем запросы. supported наполняется в обработчиках
        // ответов — раньше функции помечались поддерживаемыми заранее,
        // из-за чего эквалайзер выглядел рабочим, хотя ответа не было.
        // Сначала спрашиваем таблицу возможностей: у разных моделей набор
        // функций отличается, и вендорское приложение узнаёт его именно так.
        c.send(OppoProtocol.capabilityReq())
        c.send(OppoProtocol.firmwareReq())
        c.send(OppoProtocol.batteryReq())
        c.send(OppoProtocol.miscConfigReq(listOf(MiscType.GAME_MODE, MiscType.MULTIPOINT, MiscType.LDAC, MiscType.SPATIAL_AUDIO)))
        c.send(OppoProtocol.touchConfigReq())
        c.send(OppoProtocol.ancConfigReq())
        _state.update { it.copy(probed = true) }

        // Даём гарнитуре время ответить, и только потом разрешаем скрывать
        // функции. Молчание в ответ = функции нет (устройства этого протокола
        // просто игнорируют незнакомые команды).
        scope.launch {
            delay(2500)
            if (_state.value.connected) _state.update { it.copy(probeComplete = true) }
        }
    }

    private fun observeClose(c: SppConnection) = scope.launch {
        c.closed.collect {
            _state.update {
                if (!it.connected && !it.connecting) it
                else it.copy(
                    connected = false,
                    connecting = false,
                    // Пока связи нет — не знаем, что умеет гарнитура,
                    // поэтому скрывать ничего нельзя.
                    probeComplete = false,
                    batteryLeft = null,
                    batteryRight = null,
                    error = "Наушники отключились",
                )
            }
        }
    }

    private fun observe(c: SppConnection) = scope.launch {
        c.frames.collect { frame ->
            when (frame.cmd) {
                Cmd.BATTERY_RET, Cmd.SUBSCRIPTION_RET -> {
                    _state.update { it.copy(supported = it.supported + "battery") }
                    applyBattery(frame.payload, frame.cmd == Cmd.SUBSCRIPTION_RET)
                }
                Cmd.FIRMWARE_RET -> {
                    _state.update { it.copy(supported = it.supported + "firmware") }
                    applyFirmware(frame.payload)
                }
                Cmd.CAPABILITY_RET -> applyCapabilities(frame.payload)
                Cmd.MISC_CONFIG_RET -> applyMisc(frame.payload)
                Cmd.ANC_CONFIG_RET -> applyAnc(frame.payload)
                Cmd.TOUCH_CONFIG_RET -> applyTouch(frame.payload)
                Cmd.FIND_DEVICE_ACK -> _state.update {
                    it.copy(supported = it.supported + "find", findAcked = true)
                }
                // Гарнитура подтвердила кадр эквалайзера — только теперь считаем,
                // что модель реально умеет менять кривую на своей стороне.
                Cmd.EQ_INFO_ACK -> _state.update {
                    it.copy(supported = it.supported + "eq", eqAcked = true)
                }
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
        var lMissing = false
        var rMissing = false
        while (i + 1 < p.size) {
            val idx = p[i].toInt() and 0xFF
            if (idx != 0xFF) {
                val level = p[i + 1].toInt() and 0x7F
                val charging = (p[i + 1].toInt() and 0x80) != 0
                // level == 0 у наушника означает «не на связи» (лежит в кейсе),
                // а не разряжен в ноль. Раньше это показывалось как 0%.
                when (idx - 1) {
                    0 -> if (level > 0) l = level else lMissing = true
                    1 -> if (level > 0) r = level else rMissing = true
                    2 -> { cs = level; chg = charging; caseFresh = true }
                }
            }
            i += 2
        }
        _state.update {
            it.copy(
                batteryLeft = if (lMissing) null else l,
                batteryRight = if (rMissing) null else r,
                budInCaseLeft = lMissing, budInCaseRight = rMissing,
                batteryCase = cs, chargingCase = chg,
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
                MiscType.MULTIPOINT -> _state.update { it.copy(multipoint = on, supported = it.supported + "multipoint") }
                MiscType.SPATIAL_AUDIO -> _state.update { it.copy(spatialAudio = on, supported = it.supported + "spatial") }
                MiscType.LDAC -> _state.update { it.copy(supported = it.supported + "ldac") }
                else -> Unit
            }
            i += 2
        }
    }

    /**
     * Таблица возможностей от самой гарнитуры.
     *
     * Отвечает на вопрос «почему у дорогих моделей больше кнопок»: набор
     * функций приходит битовой маской от устройства, а не берётся из
     * зашитого списка моделей. Поэтому у T110 часть плиток честно не появится,
     * а у модели с ANC/LDAC — появится без правок кода.
     */
    private fun applyCapabilities(p: ByteArray) {
        val codes = OppoProtocol.parseCapabilities(p)
        if (codes.isEmpty()) return
        val names = buildSet {
            if (0x0418 in codes) add("eq")
            if (0x0404 in codes) add("anc")
            if (0x0403 in codes) add("misc")
            if (0x0401 in codes) add("touch")
            if (0x0400 in codes) add("find")
            if (0x0105 in codes) add("firmware")
            if (0x0106 in codes) add("battery")
        }
        _state.update {
            it.copy(capabilities = codes, supported = it.supported + names)
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

    fun setGameMode(on: Boolean) {
        // Меняем состояние сразу: раньше плитка «долго включалась», потому
        // что ждала ответа гарнитуры или следующего цикла опроса.
        _state.update { it.copy(gameMode = on) }
        val c = conn ?: return
        scope.launch { c.send(OppoProtocol.miscConfigSet(MiscType.GAME_MODE, on)) }
    }


    fun setSpatialAudio(on: Boolean) {
        _state.update { it.copy(spatialAudio = on) }
        val c = conn ?: return
        scope.launch { c.send(OppoProtocol.miscConfigSet(MiscType.SPATIAL_AUDIO, on)) }
    }

    fun setMultipoint(on: Boolean) {
        _state.update { it.copy(multipoint = on) }
        val c = conn ?: return
        scope.launch { c.send(OppoProtocol.miscConfigSet(MiscType.MULTIPOINT, on)) }
    }

    fun setAnc(mode: AncMode) = send(OppoProtocol.ancModeSet(mode)) { _state.update { it.copy(ancMode = mode) } }

    /**
     * Кривая эквалайзера прямо в гарнитуру.
     *
     * Коды и формат сняты с realme Link, но конкретная модель может команду
     * не поддерживать — поэтому ACK помечает функцию как реально доступную,
     * и до подтверждения UI не выдаёт это за работающее.
     */
    fun setEqGains(gains: List<Int>) {
        val freqs = OppoProtocol.EQ_FREQUENCIES.take(gains.size)
        if (freqs.size != gains.size) return
        send(OppoProtocol.eqInfoSet(gains = gains, frequencies = freqs)) {}
    }

    fun requestEq() = send(OppoProtocol.eqInfoReq()) {}
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
    /**
     * Обновление. Если сокет уже мёртв — переподключаемся, а не молча
     * пишем в закрытый поток: именно поэтому кнопка казалась нерабочей.
     */
    /** Системный Bluetooth подтвердил медиа-подключение, даже если SPP ещё нет. */
    fun markBluetoothPresent(address: String, name: String) {
        _state.update {
            if (it.address == address) it.copy(bluetoothConnected = true)
            else it.copy(bluetoothConnected = true, deviceName = name, address = address)
        }
    }

    /**
     * Системный Bluetooth отвалился. SPP не трогаем, если он ещё жив:
     * канал сам закроется по событию closed. Только снимаем зелёный кружок,
     * чтобы UI не врал.
     */
    fun markBluetoothGone() {
        if (_state.value.bluetoothConnected) {
            _state.update { it.copy(bluetoothConnected = false) }
        }
    }
    fun syncState() {
        val alive = conn?.isConnected == true
        if (!alive && (_state.value.connected || _state.value.connecting)) {
            _state.update { it.copy(connected = false, connecting = false) }
        }
    }

    fun refresh() {
        val c = conn
        val st = _state.value
        if (c == null || !c.isConnected) {
            if (st.address.isNotEmpty()) {
                _state.update { it.copy(connected = false, connecting = false) }
                connect(st.address, st.deviceName)
            }
            return
        }
        scope.launch {
            // Кейс рапортует заряд не всегда с первого раза — просим дважды.
            c.send(OppoProtocol.batteryReq())
            delay(400)
            c.send(OppoProtocol.batteryReq())
            c.send(OppoProtocol.statusReq())
            c.send(OppoProtocol.miscConfigReq(listOf(MiscType.GAME_MODE)))
        }
    }

    /**
     * Состояние обновляем СРАЗУ, отправка идёт параллельно.
     * Раньше плитка ждала подтверждения записи в сокет и «долго срабатывала».
     */
    private fun send(data: ByteArray, onOk: () -> Unit) {
        onOk()
        val c = conn ?: return
        scope.launch { c.send(data) }
    }

    fun disconnect() {
        conn?.close(); conn = null
        _state.value = LiveState()
    }
}
