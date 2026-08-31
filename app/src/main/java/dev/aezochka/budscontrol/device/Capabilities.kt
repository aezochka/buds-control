package dev.aezochka.budscontrol.device

import dev.aezochka.budscontrol.proto.AncMode
import dev.aezochka.budscontrol.proto.MiscType
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

/**
 * Что умеет конкретная гарнитура.
 *
 * Заполняется в два прохода:
 *  1. [DeviceDatabase] даёт стартовое предположение по имени устройства;
 *  2. живой опрос ([CapabilityProbe]) подтверждает или снимает каждый пункт —
 *     наушники, которые не умеют функцию, просто не отвечают на её запрос.
 *
 * Пункт остаётся [Support.UNKNOWN], пока опрос не закончится, поэтому UI
 * не показывает переключатели, которых на самом деле нет.
 */
data class Capabilities(
    val anc: Support = Support.UNKNOWN,
    val ancModes: Set<AncMode> = emptySet(),
    val ancTouchCycle: Support = Support.UNKNOWN,
    val gameMode: Support = Support.UNKNOWN,
    val multipoint: Support = Support.UNKNOWN,
    val ldac: Support = Support.UNKNOWN,
    val findPhone: Support = Support.UNKNOWN,
    val findDevice: Support = Support.UNKNOWN,
    val touch: Support = Support.UNKNOWN,
    val touchSlots: Set<Pair<TouchSide, TouchType>> = emptySet(),
    val battery: Support = Support.UNKNOWN,
    val batteryCount: Int = 0,
    val firmware: Support = Support.UNKNOWN,
    val probeComplete: Boolean = false,
) {
    val supportedMisc: Set<MiscType>
        get() = buildSet {
            if (gameMode.yes) add(MiscType.GAME_MODE)
            if (multipoint.yes) add(MiscType.MULTIPOINT)
            if (ldac.yes) add(MiscType.LDAC)
            if (findPhone.yes) add(MiscType.FIND_PHONE)
        }

    /** Сколько функций подтвердилось — для строки «поддерживается N из M». */
    val confirmedCount: Int
        get() = listOf(anc, ancTouchCycle, gameMode, multipoint, ldac, findPhone, findDevice, touch, battery, firmware)
            .count { it.yes }

    val totalCount: Int get() = 10
}

enum class Support {
    /** Опрос ещё не дал ответа. */
    UNKNOWN,

    /** Устройство ответило на запрос — функция есть. */
    SUPPORTED,

    /** Опрос прошёл, ответа не было — функции нет. */
    UNSUPPORTED,

    /** Функция описана в базе для этой модели, но живого подтверждения ещё нет. */
    EXPECTED;

    val yes: Boolean get() = this == SUPPORTED
    val known: Boolean get() = this == SUPPORTED || this == UNSUPPORTED
}
