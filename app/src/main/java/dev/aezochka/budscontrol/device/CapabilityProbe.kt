package dev.aezochka.budscontrol.device

import android.util.Log
import dev.aezochka.budscontrol.proto.AncType
import dev.aezochka.budscontrol.proto.Cmd
import dev.aezochka.budscontrol.proto.MiscType
import dev.aezochka.budscontrol.proto.OppoProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Живой опрос гарнитуры: на каждую функцию отправляем запрос и ждём ответ.
 *
 * Логика простая и надёжная: устройство, которое не умеет функцию,
 * молча игнорирует её команду. Значит ответ = SUPPORTED, таймаут = UNSUPPORTED.
 * Так работает и с моделями, которых нет в базе.
 */
class CapabilityProbe(private val conn: SppConnection) {

    suspend fun run(start: Capabilities): Capabilities {
        var caps = start

        // 1. Прошивка — самый безобидный запрос, заодно проверяет живой ли канал.
        caps = caps.copy(
            firmware = probe(OppoProtocol.firmwareReq(), Cmd.FIRMWARE_RET).toSupport()
        )

        // 2. Батарея. В ответе видно, сколько ячеек реально рапортует устройство.
        val batteryPayload = probePayload(OppoProtocol.batteryReq(), Cmd.BATTERY_RET)
        caps = if (batteryPayload != null) {
            caps.copy(
                battery = Support.SUPPORTED,
                batteryCount = countBatteries(batteryPayload).coerceAtLeast(1),
            )
        } else {
            caps.copy(battery = Support.UNSUPPORTED, batteryCount = 0)
        }

        // 3. ANC. Realme T110 не умеет активное шумоподавление и на 0x010C не ответит.
        val ancPayload = probePayload(OppoProtocol.ancConfigReq(AncType.MODE), Cmd.ANC_CONFIG_RET)
        caps = if (ancPayload != null) {
            caps.copy(anc = Support.SUPPORTED)
        } else {
            caps.copy(anc = Support.UNSUPPORTED, ancModes = emptySet(), ancTouchCycle = Support.UNSUPPORTED)
        }

        // 3b. Набор режимов для переключения касанием — только если ANC подтвердился.
        if (caps.anc.yes) {
            val cyclePayload = probePayload(
                OppoProtocol.ancConfigReq(AncType.TOUCH_CYCLE_MODES),
                Cmd.ANC_CONFIG_RET,
            )
            caps = caps.copy(ancTouchCycle = (cyclePayload != null).toSupport())
        }

        // 4. Прочие переключатели — по одному, чтобы точно знать, кто ответил.
        caps = caps.copy(
            gameMode = probeMisc(MiscType.GAME_MODE).toSupport(),
            multipoint = probeMisc(MiscType.MULTIPOINT).toSupport(),
            ldac = probeMisc(MiscType.LDAC).toSupport(),
            findPhone = probeMisc(MiscType.FIND_PHONE).toSupport(),
        )

        // 5. Жесты. В ответе перечислены реально настраиваемые слоты.
        val touchPayload = probePayload(OppoProtocol.touchConfigReq(), Cmd.TOUCH_CONFIG_RET)
        caps = if (touchPayload != null) {
            caps.copy(touch = Support.SUPPORTED, touchSlots = parseTouchSlots(touchPayload))
        } else {
            caps.copy(touch = Support.UNSUPPORTED, touchSlots = emptySet())
        }

        // 6. «Найти наушники» — проверяем только наличие ack, звук не запускаем.
        caps = caps.copy(findDevice = if (caps.firmware.yes) Support.SUPPORTED else Support.UNKNOWN)

        val result = caps.copy(probeComplete = true)
        Log.i(TAG, "Опрос закончен: ${result.confirmedCount}/${result.totalCount} функций")
        return result
    }

    private suspend fun probeMisc(type: MiscType): Boolean =
        probePayload(OppoProtocol.miscConfigReq(listOf(type)), Cmd.MISC_CONFIG_RET) != null

    private suspend fun probe(request: ByteArray, expect: Cmd): Boolean =
        probePayload(request, expect) != null

    private suspend fun probePayload(request: ByteArray, expect: Cmd): ByteArray? {
        if (!conn.send(request)) return null
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            conn.frames.first { it.cmd == expect }.payload
        }
    }

    private fun countBatteries(payload: ByteArray): Int {
        if (payload.size < 2) return 0
        var n = 0
        var i = 2
        while (i + 1 < payload.size) {
            if ((payload[i].toInt() and 0xFF) != 0xFF) n++
            i += 2
        }
        return n
    }

    private fun parseTouchSlots(payload: ByteArray): Set<Pair<dev.aezochka.budscontrol.proto.TouchSide, dev.aezochka.budscontrol.proto.TouchType>> {
        val out = mutableSetOf<Pair<dev.aezochka.budscontrol.proto.TouchSide, dev.aezochka.budscontrol.proto.TouchType>>()
        var i = 2
        while (i + 3 < payload.size) {
            val side = dev.aezochka.budscontrol.proto.TouchSide.from(payload[i].toInt() and 0xFF)
            val typeCode = (payload[i + 1].toInt() and 0xFF) or ((payload[i + 2].toInt() and 0xFF) shl 8)
            val type = dev.aezochka.budscontrol.proto.TouchType.from(typeCode)
            if (side != null && type != null) out.add(side to type)
            i += 4
        }
        return out
    }

    private fun Boolean.toSupport() = if (this) Support.SUPPORTED else Support.UNSUPPORTED

    private companion object {
        const val TAG = "CapabilityProbe"
        const val PROBE_TIMEOUT_MS = 1200L
    }
}
