package dev.aezochka.budscontrol.data

import kotlinx.serialization.Serializable

/** Последний известный заряд кейса — держим на диске, гарнитура молчит при закрытом кейсе. */
@Serializable
data class CaseBatteryMemo(
    val percent: Int,
    val charging: Boolean,
    val atMillis: Long,
)

@Serializable
data class EarbudProfile(
    val id: String,
    val displayName: String,
    val address: String,
    val vendor: String,
    val imageKey: String? = null,
    val lastSeenMillis: Long = 0L,
    val isSelected: Boolean = false,
    val caseBattery: CaseBatteryMemo? = null,
)




@Serializable
data class UserSettings(
    val onboardingFinished: Boolean = false,
    val language: String = "system",
    val pauseOnRemoval: Boolean = false,
    val accent: String = "lime",
    val tileOrder: List<String> = defaultTileOrder,
    val tileSpans: Map<String, Int> = emptyMap(),
    val lowBatteryAlert: Boolean = false,
    val hiddenTiles: Set<String> = emptySet(),
)

val defaultTileOrder = listOf("eq", "game", "case", "find", "firmware", "inear", "sleep", "volume", "spatial", "multipoint")

/** Акценты темы — выбираются пользователем, сохраняются локально. */
enum class Accent(val key: String, val title: String, val seed: Long) {
    Lime("lime", "Лаймовый", 0xFFB8E86B),
    Rose("rose", "Розовый", 0xFFFFB0C8),
    Amber("amber", "Янтарный", 0xFFF2BE8C),
    Ice("ice", "Ледяной", 0xFF9CD8FF),
    Violet("violet", "Фиолетовый", 0xFFD0BCFF);

    companion object {
        fun from(key: String): Accent = entries.firstOrNull { it.key == key } ?: Lime
    }
}

