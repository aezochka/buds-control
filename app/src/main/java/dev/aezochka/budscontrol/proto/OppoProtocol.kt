package dev.aezochka.budscontrol.proto

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Кодек проприетарного протокола OPPO/realme/OnePlus (BBK), который ходит
 * поверх Bluetooth Classic SPP (RFCOMM, UUID 00001101-...).
 *
 * Формат кадра (little-endian):
 *   AA | len(all-2) | 00 | 00 | cmd:u16 | seq:u8 | payloadLen:u16 | payload...
 *
 * Ответ приходит в том же виде, но байты 2..3 могут быть 0x0000 (oppo)
 * или 0x0004 (realme), а старший бит команды выставлен (0x0106 -> 0x8106).
 */
object OppoProtocol {
    const val PREAMBLE: Byte = 0xAA.toByte()
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    /** action в кадре 0x0418: 0..4 — записать, 5 — запросить текущее. */
    const val EQ_ACTION_SET = 0
    const val EQ_ACTION_QUERY = 5

    /** id пользовательской кривой. */
    const val EQ_ID_CUSTOM = 0

    /** Частоты полос, как их показывает realme Link. */
    val EQ_FREQUENCIES = listOf(60, 230, 910, 3600, 14000)

    private var seq = 0

    fun encode(cmd: Cmd, payload: ByteArray = ByteArray(0)): ByteArray {
        val buf = ByteBuffer.allocate(9 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(PREAMBLE)
        buf.put((buf.limit() - 2).toByte())
        buf.put(0)
        buf.put(0)
        buf.putShort(cmd.code)
        buf.put((seq++ and 0xFF).toByte())
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        return buf.array()
    }

    /** Разбирает буфер, в котором может лежать несколько склеенных кадров. */
    fun decode(data: ByteArray): List<Frame> {
        val out = mutableListOf<Frame>()
        val buf = ByteBuffer.wrap(data)
        while (buf.remaining() >= 2) {
            val start = buf.position()
            if (buf.get() != PREAMBLE) continue
            val totalLength = buf.get().toInt() and 0xFF
            if (buf.remaining() < totalLength) {
                buf.position(start)
                break
            }
            val single = ByteArray(totalLength + 2)
            buf.position(start)
            buf.get(single)
            parseSingle(single)?.let(out::add)
        }
        return out
    }

    private fun parseSingle(data: ByteArray): Frame? {
        if (data.size < 9) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.get() != PREAMBLE) return null
        val totalLength = buf.get().toInt() and 0xFF
        if (data.size != totalLength + 2) return null
        buf.short // 0 на oppo, 4 на realme — игнорируем
        val code = buf.short
        val cmd = Cmd.from(code) ?: return Frame(null, code, ByteArray(0))
        buf.get() // seq
        val payloadLen = buf.short.toInt() and 0xFFFF
        if (payloadLen > buf.remaining()) return null
        val payload = ByteArray(payloadLen)
        buf.get(payload)
        return Frame(cmd, code, payload)
    }

    data class Frame(val cmd: Cmd?, val rawCode: Short, val payload: ByteArray)

    // ---- Конкретные команды ----

    /** Спросить у гарнитуры, что она умеет. */
    fun capabilityReq() = encode(Cmd.CAPABILITY_REQ)

    /**
     * Раскладка битов возможностей — перенесена из realme Link (Protocol.b2).
     *
     * Индекс = номер бита в маске, значение = коды команд, которые он открывает.
     * Пустой список — бит есть, но за ним нет интересной нам команды.
     */
    private val CAPABILITY_BITS: List<List<Int>> = listOf(
        listOf(0x0105), listOf(0x0106), listOf(0x0107), listOf(0x0108, 0x0401),
        listOf(0x0109), listOf(0x0400), listOf(0x0402), listOf(0x010D, 0x0403),
        listOf(0x010C, 0x0404), listOf(0x0405), listOf(0x0406, 0x010F),
        listOf(0x0110, 0x0407), emptyList(), listOf(0x0408), listOf(0x0409),
        listOf(0x040A, 0x0111), emptyList(), emptyList(), emptyList(),
        listOf(0x040E, 0x040D, 0x0115, 0x0116), emptyList(), emptyList(),
        listOf(0x0205), listOf(0x0F00), emptyList(), listOf(0x0118, 0x0411),
        listOf(0x011A, 0x0412), emptyList(), emptyList(), listOf(0x0112, 0x040B),
        listOf(0x011E, 0x011F, 0x0415), emptyList(), listOf(0x0120), emptyList(),
        listOf(0x0122, 0x0418), emptyList(), emptyList(), emptyList(),
        listOf(0x0124, 0x041B), emptyList(), emptyList(), emptyList(),
        emptyList(), listOf(0x041E), emptyList(), emptyList(),
        listOf(0x0128, 0x050E), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), listOf(0x012C),
        emptyList(), emptyList(), emptyList(), listOf(0x0131, 0x0428),
        emptyList(), listOf(0x042C),
    )

    /**
     * Разбор ответа 0x8100 в набор поддерживаемых команд.
     *
     * Формат как в PollCommandManager.l0: [0] статус, дальше битовая маска
     * младшими битами вперёд.
     */
    fun parseCapabilities(payload: ByteArray): Set<Int> {
        if (payload.isEmpty() || payload[0].toInt() != 0 || payload.size <= 1) return emptySet()
        val out = mutableSetOf<Int>()
        val bits = minOf((payload.size - 1) * 8, CAPABILITY_BITS.size)
        for (bit in 0 until bits) {
            val byte = payload[1 + bit / 8].toInt() and 0xFF
            if (byte and (1 shl (bit % 8)) != 0) out += CAPABILITY_BITS[bit]
        }
        return out
    }

    fun batteryReq() = encode(Cmd.BATTERY_REQ)
    fun statusReq() = encode(Cmd.STATUS_REQ)

    fun firmwareReq() = encode(Cmd.FIRMWARE_GET)
    fun findDevice(start: Boolean) =
        encode(Cmd.FIND_DEVICE_REQ, byteArrayOf(if (start) 1 else 0))

    fun touchConfigReq() = encode(Cmd.TOUCH_CONFIG_REQ, byteArrayOf(0x02, 0x03, 0x01))

    fun touchConfigSet(side: TouchSide, type: TouchType, value: TouchAction): ByteArray {
        val buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x01)
        buf.put(side.code.toByte())
        buf.putShort(type.code.toShort())
        buf.put(value.code.toByte())
        return encode(Cmd.TOUCH_CONFIG_SET, buf.array())
    }

    fun miscConfigReq(types: List<MiscType>): ByteArray {
        if (types.isEmpty()) return encode(Cmd.MISC_CONFIG_REQ, byteArrayOf(0x00))
        val payload = ByteArray(1 + types.size)
        payload[0] = types.size.toByte()
        types.forEachIndexed { i, t -> payload[i + 1] = t.code.toByte() }
        return encode(Cmd.MISC_CONFIG_REQ, payload)
    }

    fun miscConfigSet(type: MiscType, enable: Boolean) =
        encode(Cmd.MISC_CONFIG_SET, byteArrayOf(type.code.toByte(), if (enable) 1 else 0))

    /**
     * Загрузка кривой эквалайзера прямо в гарнитуру (0x0418).
     *
     * Формат снят с realme Link (SetCommandManager.p):
     *   [0] action, [1] minDb, [2] maxDb, [3] eqId, [4] nameLen,
     *   name UTF-8, bandCount, затем на полосу: freq(2 байта LE) + gainDb(1 signed)
     *
     * Частоты передаются явно, поэтому кривая произвольная — не фиксированные полосы.
     */
    fun eqInfoSet(
        gains: List<Int>,
        frequencies: List<Int>,
        eqId: Int = EQ_ID_CUSTOM,
        minDb: Int = -6,
        maxDb: Int = 6,
        action: Int = EQ_ACTION_SET,
    ): ByteArray {
        require(gains.size == frequencies.size) { "gains и frequencies должны быть одной длины" }
        val payload = ByteArray(5 + 1 + frequencies.size * 3)
        payload[0] = action.toByte()
        payload[1] = minDb.toByte()
        payload[2] = maxDb.toByte()
        payload[3] = eqId.toByte()
        payload[4] = 0 // nameLen: имя не передаём
        payload[5] = frequencies.size.toByte()
        var i = 6
        frequencies.forEachIndexed { index, freq ->
            payload[i] = (freq and 0xFF).toByte()
            payload[i + 1] = ((freq shr 8) and 0xFF).toByte()
            payload[i + 2] = gains[index].coerceIn(minDb, maxDb).toByte()
            i += 3
        }
        return encode(Cmd.EQ_INFO_SET, payload)
    }

    /** Запрос текущей кривой: тот же кадр с action=5 и пустым телом. */
    fun eqInfoReq() = encode(Cmd.EQ_INFO_SET, ByteArray(5).also { it[0] = EQ_ACTION_QUERY.toByte() })

    /** Включить/выключить эквалайзер в гарнитуре (0x0406). */
    fun eqSwitchSet(on: Boolean) =
        encode(Cmd.EQ_SWITCH_SET, byteArrayOf(if (on) 1 else 0))

    fun ancModeSet(mode: AncMode) =
        encode(Cmd.ANC_CONFIG_SET, byteArrayOf(AncType.MODE.code.toByte(), 0x01, mode.code.toByte()))

    fun ancConfigReq(type: AncType = AncType.MODE) =
        encode(Cmd.ANC_CONFIG_REQ, byteArrayOf(type.code.toByte(), 0x01))

    fun ancCycleModesSet(modes: Set<AncMode>): ByteArray {
        val mask = modes.fold(0) { acc, m -> acc or m.code }
        return encode(
            Cmd.ANC_CONFIG_SET,
            byteArrayOf(AncType.TOUCH_CYCLE_MODES.code.toByte(), 0x01, mask.toByte())
        )
    }

    fun subscribe(types: Set<SubType>): ByteArray {
        require(types.isNotEmpty())
        val payload = ByteArray(1 + types.size)
        payload[0] = 0x09
        types.forEachIndexed { i, t -> payload[i + 1] = t.code.toByte() }
        return encode(Cmd.SUBSCRIPTION_SET, payload)
    }
}

enum class Cmd(val code: Short) {
    /**
     * Таблица возможностей модели (realme Link: Protocol.k = 33024).
     *
     * Гарнитура отдаёт битовую маску: бит = индекс в таблице команд.
     * Именно так вендорское приложение решает, какие кнопки показывать —
     * у дорогих моделей появляются новые, потому что у них выставлены
     * дополнительные биты, а не потому что в приложении зашит список моделей.
     */
    CAPABILITY_REQ(0x0100),
    CAPABILITY_RET(0x8100.toShort()),
    BATTERY_REQ(0x0106),
    BATTERY_RET(0x8106.toShort()),
    STATUS_REQ(0x0109),
    STATUS_RET(0x8109.toShort()),
    /**
     * Коды подтверждены декомпиляцией realme Link 5.5.514
     * (Protocol.F1 = 1048, Protocol.j1 = 1030).
     *
     * Раньше EQUALIZER_SET стоял на 0x0406 — но это переключатель
     * «включён ли эквалайзер», а не загрузка полос. Кривая уходит в 0x0418.
     */
    EQ_SWITCH_SET(0x0406),
    EQ_SWITCH_ACK(0x8406.toShort()),
    EQ_INFO_SET(0x0418),
    EQ_INFO_ACK(0x8418.toShort()),
    SUBSCRIPTION_SET(0x0205),
    SUBSCRIPTION_ACK(0x8205.toShort()),
    SUBSCRIPTION_RET(0x0204),
    FIRMWARE_GET(0x0105),
    FIRMWARE_RET(0x8105.toShort()),
    TOUCH_CONFIG_REQ(0x0108),
    TOUCH_CONFIG_SET(0x0401),
    TOUCH_CONFIG_RET(0x8108.toShort()),
    TOUCH_CONFIG_ACK(0x8401.toShort()),
    FIND_DEVICE_REQ(0x0400),
    FIND_DEVICE_ACK(0x8400.toShort()),
    MISC_CONFIG_SET(0x0403),
    MISC_CONFIG_REQ(0x010D),
    MISC_CONFIG_ACK(0x8403.toShort()),
    MISC_CONFIG_RET(0x810D.toShort()),
    ANC_CONFIG_SET(0x0404),
    ANC_CONFIG_REQ(0x010C),
    ANC_CONFIG_ACK(0x8404.toShort()),
    ANC_CONFIG_RET(0x810C.toShort());

    companion object {
        private val map = entries.associateBy { it.code }
        fun from(code: Short): Cmd? = map[code]
    }
}

enum class AncType(val code: Int) { MODE(0x01), TOUCH_CYCLE_MODES(0x02) }


enum class AncMode(val code: Int, val prefId: String) {
    OFF(0x01, "0"),
    TRANSPARENCY(0x02, "2"),
    ON(0x08, "1");

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
        fun fromMask(mask: Int) = entries.filter { mask and it.code == it.code }.toSet()
    }
}

enum class MiscType(val code: Int) {
    GAME_MODE(0x06),
    MULTIPOINT(0x11),
    LDAC(0x18),
    SPATIAL_AUDIO(0x1B),
    FIND_PHONE(0x26);

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
    }
}

enum class SubType(val code: Int) {
    BATTERY(0x01), STATUS(0x02), ANC_SELECTOR(0x03), GAME_MODE(0x05);

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
    }
}

enum class TouchSide(val code: Int) {
    LEFT(0x01), RIGHT(0x02), BOTH(0x04);

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
    }
}

enum class TouchType(val code: Int) {
    UNK_1(0x0101), TAP_2(0x0201), TAP_3(0x0301), HOLD(0x0401);

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
    }
}

enum class TouchAction(val code: Int) {
    OFF(0x00),
    PLAY_PAUSE(0x01),
    VOICE_ASSISTANT(0x03),
    VOICE_ASSISTANT_REALME(0x04),
    PREVIOUS(0x05),
    NEXT(0x06),
    NOISE_CONTROL(0x08),
    VOLUME_UP(0x0B),
    VOLUME_DOWN(0x0C),
    GAME_MODE(0x11);

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
    }
}
