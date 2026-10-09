package dev.aezochka.budscontrol.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты кодека протокола: он работает с чистыми байтами, поэтому гоняется
 * на JVM без устройства.
 */
class OppoProtocolTest {

    @Test
    fun `encode decode roundtrip`() {
        val frame = OppoProtocol.encode(OppoProtocol.Cmd.BATTERY_REQ)
        val result = OppoProtocol.decode(frame)
        assertEquals(1, result.frames.size)
        assertEquals(OppoProtocol.Cmd.BATTERY_REQ, result.frames[0].cmd)
        assertEquals(frame.size, result.consumed)
    }

    @Test
    fun `two frames glued in one buffer`() {
        val a = OppoProtocol.encode(OppoProtocol.Cmd.STATUS_REQ)
        val b = OppoProtocol.encode(OppoProtocol.Cmd.FIRMWARE_GET)
        val result = OppoProtocol.decode(a + b)
        assertEquals(
            listOf(OppoProtocol.Cmd.STATUS_REQ, OppoProtocol.Cmd.FIRMWARE_GET),
            result.frames.map { it.cmd },
        )
        assertEquals(a.size + b.size, result.consumed)
    }

    /**
     * Главный случай: за полным кадром в том же буфере идёт обрыв следующего.
     * Раньше такой хвост вытирался целиком — кадр терялся навсегда.
     */
    @Test
    fun `full frame plus partial tail keeps remainder`() {
        val a = OppoProtocol.encode(OppoProtocol.Cmd.BATTERY_REQ)
        val tail = a.copyOfRange(0, 4)
        val result = OppoProtocol.decode(a + tail)
        assertEquals(1, result.frames.size)
        assertEquals(a.size, result.consumed)
    }

    /** Хвост из прошлого теста + следующая порция байт = целый кадр. */
    @Test
    fun `split frame assembles across chunks`() {
        val a = OppoProtocol.encode(OppoProtocol.Cmd.BATTERY_REQ)
        val firstChunk = a.copyOfRange(0, 5)

        val first = OppoProtocol.decode(firstChunk)
        assertTrue(first.frames.isEmpty())
        assertEquals(0, first.consumed)

        // Так накопитель живёт в SppConnection: хвост + новая порция.
        val combined = firstChunk.copyOf(first.consumed) + a.copyOfRange(5, a.size)
        val second = OppoProtocol.decode(combined)
        assertEquals(1, second.frames.size)
        assertEquals(a.size, second.consumed)
    }

    @Test
    fun `garbage bytes before frame are skipped`() {
        val a = OppoProtocol.encode(OppoProtocol.Cmd.BATTERY_REQ)
        val result = OppoProtocol.decode(byteArrayOf(0x00, 0x11, 0x22) + a)
        assertEquals(1, result.frames.size)
        assertEquals(3 + a.size, result.consumed)
    }

    @Test
    fun `sequence numbers increment`() {
        val a = OppoProtocol.encode(OppoProtocol.Cmd.BATTERY_REQ)
        val b = OppoProtocol.encode(OppoProtocol.Cmd.BATTERY_REQ)
        assertEquals(a[6].toInt() + 1, b[6].toInt())
    }

    @Test
    fun `capabilities bitmask maps to command set`() {
        // Статус 0 = успех. Маска 0x82: бит 1 (BATTERY_REQ) и бит 7
        // (MISC_CONFIG_REQ / MISC_CONFIG_SET).
        val payload = byteArrayOf(0x00, 0x82.toByte())
        val cmds = OppoProtocol.parseCapabilities(payload)
        assertEquals(setOf(0x0106, 0x010D, 0x0403), cmds)
    }

    @Test
    fun `capabilities with error status give empty set`() {
        assertTrue(OppoProtocol.parseCapabilities(byteArrayOf(0x01)).isEmpty())
        assertTrue(OppoProtocol.parseCapabilities(ByteArray(0)).isEmpty())
    }

    /** Раскладка кривой EQ должна совпадать со снятой с realme Link. */
    @Test
    fun `eq payload layout matches vendor format`() {
        val frame = OppoProtocol.eqInfoSet(listOf(-3, 0, 2), listOf(60, 230, 910))
        // Заголовок кадра — 9 байт, дальше тело команды.
        assertEquals(0, frame[9].toInt())      // action = EQ_ACTION_SET
        assertEquals(-6, frame[10].toInt())    // minDb
        assertEquals(6, frame[11].toInt())     // maxDb
        assertEquals(0, frame[12].toInt())     // eqId = custom
        assertEquals(0, frame[13].toInt())     // nameLen
        assertEquals(3, frame[14].toInt())     // bandCount
        // Первая полоса: частота LE (2 байта) + усиление (signed).
        assertEquals(60, (frame[15].toInt() and 0xFF) or ((frame[16].toInt() and 0xFF) shl 8))
        assertEquals(-3, frame[17].toInt())
    }
}
