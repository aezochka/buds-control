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
    /** Свой цвет акцента в ARGB. 0 — не задан, используется accent. */
    val customAccent: Long = 0L,
    val hapticFeedback: Boolean = true,
    val soundEffects: Boolean = true,
    val autoConnect: Boolean = true,
    val lowBatteryAlert: Boolean = false,
    val tileOrder: List<String> = defaultTileOrder,
    val tileSpans: Map<String, Int> = emptyMap(),
    val hiddenTiles: Set<String> = emptySet(),
)

val defaultTileOrder = listOf("eq", "game", "case", "sleep", "volume", "find", "firmware")

/** Акценты темы — выбираются пользователем, сохраняются локально. */
enum class Accent(val key: String, val title: String, val seed: Long) {
    // Насыщенные цвета: пастельные выглядели бледно на тёмном фоне.
    Lime("lime", "Лаймовый", 0xFFA5E82C),
    Rose("rose", "Розовый", 0xFFFF4F8B),
    Amber("amber", "Янтарный", 0xFFFF9F1C),
    Ice("ice", "Ледяной", 0xFF29C7FF),
    Violet("violet", "Фиолетовый", 0xFF9D5CFF),
    Mint("mint", "Мятный", 0xFF00E5B0),
    Coral("coral", "Коралловый", 0xFFFF5E3A),
    Sky("sky", "Небесный", 0xFF2F7BFF),
    Gold("gold", "Золотой", 0xFFFFD400),
    Magenta("magenta", "Пурпурный", 0xFFFF2FB9),
    Emerald("emerald", "Изумрудный", 0xFF12D95F),
    Crimson("crimson", "Багровый", 0xFFFF1F4B);

    companion object {
        fun from(key: String): Accent = entries.firstOrNull { it.key == key } ?: Lime
    }
}

