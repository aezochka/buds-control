package dev.aezochka.budscontrol.proto

/**
 * Разбор BLE-рекламы Apple (manufacturer id 0x004C, тип 0x07).
 *
 * AirPods не говорят по SPP как realme/OPPO: заряд и состояние они
 * транслируют в широковещательной рекламе. Сопряжение не нужно — достаточно
 * BLE-сканирования.
 *
 * Формат снят с открытой реализации LibrePods (parseProximityMessage).
 * Работает без ключа шифрования: заряд лежит в открытой части пакета.
 * Управление (ANC, жесты) требует L2CAP и подмены VendorID — здесь не делаем,
 * поэтому AirPods — режим только чтения.
 */
object ApplePods {
    /** Company ID Apple в BLE-рекламе. */
    const val APPLE_MANUFACTURER_ID = 0x004C

    /** Тип сообщения Proximity Pairing. */
    private const val TYPE_PROXIMITY = 0x07

    /** Минимальная длина, при которой все нужные поля на месте. */
    private const val MIN_LENGTH = 11

    data class Status(
        val model: String,
        val modelId: Int,
        val leftBattery: Int?,
        val rightBattery: Int?,
        val caseBattery: Int?,
        val leftCharging: Boolean,
        val rightCharging: Boolean,
        val caseCharging: Boolean,
        val leftInEar: Boolean,
        val rightInEar: Boolean,
        val lidOpen: Boolean,
    )

    /** Известные модели Apple по modelId из рекламы. */
    private val MODELS = mapOf(
        0x2002 to "AirPods 1",
        0x200F to "AirPods 2",
        0x2013 to "AirPods 3",
        0x2019 to "AirPods 4",
        0x201B to "AirPods 4 ANC",
        0x200E to "AirPods Pro",
        0x2014 to "AirPods Pro 2",
        0x2024 to "AirPods Pro 2 USB-C",
        0x2026 to "AirPods Pro 3",
        0x200A to "AirPods Max",
        0x201F to "AirPods Max USB-C",
        0x2003 to "Powerbeats 3",
        0x200D to "Powerbeats 4",
        0x200B to "Powerbeats Pro",
        0x2011 to "Powerbeats Pro 2",
        0x2010 to "Beats Solo Pro",
        0x2005 to "Beats Solo 3",
        0x2017 to "Beats Studio 3",
        0x2012 to "Beats Studio Buds",
        0x2016 to "Beats Studio Buds+",
        0x201A to "Beats Studio Pro",
        0x2006 to "Beats Studio Buds Fit",
        0x200C to "Beats Flex",
        0x201C to "Beats Fit Pro",
        0x2009 to "Beats Studio Wireless",
    )

    fun isApplePods(payload: ByteArray): Boolean =
        payload.size >= MIN_LENGTH && (payload[0].toInt() and 0xFF) == TYPE_PROXIMITY

    /**
     * Разбирает пакет. null, если это не Proximity Pairing или он короткий.
     *
     * Заряд идёт полубайтами: 0..9 = проценты десятками, 0xA..0xE = 100,
     * 0xF = «неизвестно» (наушник не на связи или лежит в кейсе).
     */
    fun parse(payload: ByteArray): Status? {
        if (!isApplePods(payload)) return null

        val modelId = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
        val status = payload[5].toInt() and 0xFF
        val podsBattery = payload[6].toInt() and 0xFF
        val flagsCase = payload[7].toInt() and 0xFF
        val lid = payload[8].toInt() and 0xFF

        // Наушники могут меняться ролями: какой из них «главный», говорит бит 5.
        // Без этой проверки левый и правый время от времени меняются местами.
        val primaryLeft = ((status shr 5) and 0x01) == 1
        val thisInCase = ((status shr 6) and 0x01) == 1
        val xorFactor = primaryLeft xor thisInCase
        val flipped = !primaryLeft

        val leftInEar = if (xorFactor) (status and 0x08) != 0 else (status and 0x02) != 0
        val rightInEar = if (xorFactor) (status and 0x02) != 0 else (status and 0x08) != 0

        val leftNibble = if (flipped) (podsBattery shr 4) and 0x0F else podsBattery and 0x0F
        val rightNibble = if (flipped) podsBattery and 0x0F else (podsBattery shr 4) and 0x0F
        val caseNibble = flagsCase and 0x0F
        val flags = (flagsCase shr 4) and 0x0F

        return Status(
            model = MODELS[modelId] ?: "AirPods",
            modelId = modelId,
            leftBattery = decodeBattery(leftNibble),
            rightBattery = decodeBattery(rightNibble),
            caseBattery = decodeBattery(caseNibble),
            leftCharging = if (flipped) (flags and 0x02) != 0 else (flags and 0x01) != 0,
            rightCharging = if (flipped) (flags and 0x01) != 0 else (flags and 0x02) != 0,
            caseCharging = (flags and 0x04) != 0,
            leftInEar = leftInEar,
            rightInEar = rightInEar,
            lidOpen = ((lid shr 3) and 0x01) == 0,
        )
    }

    private fun decodeBattery(nibble: Int): Int? = when (nibble) {
        in 0x0..0x9 -> nibble * 10
        in 0xA..0xE -> 100
        else -> null
    }

    /** Модель по id — для показа в списке найденных устройств. */
    fun modelName(modelId: Int): String? = MODELS[modelId]
}
