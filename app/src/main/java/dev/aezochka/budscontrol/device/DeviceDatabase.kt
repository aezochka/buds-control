package dev.aezochka.budscontrol.device

import dev.aezochka.budscontrol.proto.AncMode

/**
 * Стартовые предположения по названию устройства.
 *
 * Это только подсказка для UI на первую секунду после подключения: реальный
 * набор функций всё равно определяет живой опрос. Поэтому неизвестная модель —
 * не проблема, ей достаётся профиль [GENERIC] и полный опрос.
 */
object DeviceDatabase {

    data class Profile(
        val displayName: String,
        val vendor: String,
        val anc: Boolean = false,
        val ancModes: Set<AncMode> = emptySet(),
        val gameMode: Boolean = true,
        val multipoint: Boolean = false,
        val ldac: Boolean = false,
        val touch: Boolean = true,
        val batteryCount: Int = 3,
    )

    private val GENERIC = Profile(
        displayName = "Неизвестная гарнитура",
        vendor = "—",
        anc = false,
        gameMode = true,
        touch = true,
    )

    /**
     * Ключ — подстрока имени по Bluetooth, в нижнем регистре.
     * Порядок важен: сначала более специфичные записи.
     */
    private val profiles: List<Pair<Regex, Profile>> = listOf(
        // --- realme без ANC (только ENC для звонков) ---
        re("realme buds t110") to Profile(
            displayName = "realme Buds T110",
            vendor = "realme",
            anc = false,
            gameMode = true,
            multipoint = false,
            ldac = false,
            touch = true,
            batteryCount = 3,
        ),
        re("realme buds t100") to Profile("realme Buds T100", "realme", anc = false),
        re("realme buds q2") to Profile("realme Buds Q2", "realme", anc = true, ancModes = setOf(AncMode.OFF, AncMode.ON, AncMode.TRANSPARENCY)),

        // --- realme с ANC ---
        re("realme buds t310") to Profile(
            "realme Buds T310", "realme",
            anc = true, ancModes = setOf(AncMode.OFF, AncMode.ON, AncMode.TRANSPARENCY),
            multipoint = true,
        ),
        re("realme buds t300") to Profile("realme Buds T300", "realme", anc = true, ancModes = ALL_ANC, multipoint = true),
        re("realme buds air ?6") to Profile("realme Buds Air 6", "realme", anc = true, ancModes = ALL_ANC, multipoint = true, ldac = true),
        re("realme buds air ?5") to Profile("realme Buds Air 5", "realme", anc = true, ancModes = ALL_ANC, multipoint = true, ldac = true),
        re("realme buds air ?3") to Profile("realme Buds Air 3", "realme", anc = true, ancModes = ALL_ANC, multipoint = true),
        re("realme buds air") to Profile("realme Buds Air", "realme", anc = true, ancModes = ALL_ANC),

        // --- OnePlus ---
        re("oneplus nord buds") to Profile("OnePlus Nord Buds", "OnePlus", anc = true, ancModes = ALL_ANC, multipoint = true),
        re("oneplus buds pro") to Profile("OnePlus Buds Pro", "OnePlus", anc = true, ancModes = ALL_ANC, multipoint = true, ldac = true),
        re("oneplus buds") to Profile("OnePlus Buds", "OnePlus", anc = true, ancModes = ALL_ANC),

        // --- OPPO ---
        re("oppo enco air") to Profile("OPPO Enco Air", "OPPO", anc = true, ancModes = ALL_ANC, multipoint = true),
        re("oppo enco x") to Profile("OPPO Enco X", "OPPO", anc = true, ancModes = ALL_ANC, multipoint = true, ldac = true),
        re("oppo enco") to Profile("OPPO Enco", "OPPO", anc = true, ancModes = ALL_ANC),

        // --- общие маски вендоров ---
        re("realme") to Profile("realme гарнитура", "realme", anc = false),
        re("oneplus") to Profile("OnePlus гарнитура", "OnePlus", anc = true, ancModes = ALL_ANC),
        re("oppo") to Profile("OPPO гарнитура", "OPPO", anc = true, ancModes = ALL_ANC),
    )

    fun lookup(bluetoothName: String?): Profile {
        val name = bluetoothName?.lowercase()?.trim() ?: return GENERIC
        return profiles.firstOrNull { (rx, _) -> rx.containsMatchIn(name) }?.second
            ?: GENERIC.copy(displayName = bluetoothName)
    }

    /** Начальные Capabilities из профиля — всё помечено EXPECTED, не SUPPORTED. */
    fun initialCapabilities(profile: Profile) = Capabilities(
        anc = profile.anc.expected(),
        ancModes = profile.ancModes,
        ancTouchCycle = profile.anc.expected(),
        gameMode = profile.gameMode.expected(),
        multipoint = profile.multipoint.expected(),
        ldac = profile.ldac.expected(),
        findPhone = Support.UNKNOWN,
        findDevice = Support.EXPECTED,
        touch = profile.touch.expected(),
        battery = Support.EXPECTED,
        batteryCount = profile.batteryCount,
        firmware = Support.EXPECTED,
        probeComplete = false,
    )

    private fun Boolean.expected() = if (this) Support.EXPECTED else Support.UNKNOWN
    private fun re(s: String) = Regex(s)
    private val ALL_ANC get() = setOf(AncMode.OFF, AncMode.ON, AncMode.TRANSPARENCY)
}
