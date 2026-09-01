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
    val autoConnect: Boolean = true,
    val lowBatteryAlert: Boolean = false,
    val tileOrder: List<String> = defaultTileOrder,
    val tileSpans: Map<String, Int> = emptyMap(),
    val hiddenTiles: Set<String> = emptySet(),
)

val defaultTileOrder = listOf("eq", "game", "case", "find", "firmware", "sleep", "volume", "bass", "lowlatency")

/** Акценты темы — выбираются пользователем, сохраняются локально. */
enum class Accent(val key: String, val title: String, val seed: Long) {
    Lime("lime", "Лаймовый", 0xFFB8E86B),
    Rose("rose", "Розовый", 0xFFFFB0C8),
    Amber("amber", "Янтарный", 0xFFF2BE8C),
    Ice("ice", "Ледяной", 0xFF9CD8FF),
    Violet("violet", "Фиолетовый", 0xFFD0BCFF),
    Mint("mint", "Мятный", 0xFF7BE0C3),
    Coral("coral", "Коралловый", 0xFFFF9E80),
    Sky("sky", "Небесный", 0xFF82B1FF),
    Sand("sand", "Песочный", 0xFFE8D5A3),
    Magenta("magenta", "Пурпурный", 0xFFF48FB1),
    Emerald("emerald", "Изумрудный", 0xFF69D98A),
    Steel("steel", "Стальной", 0xFFB0BEC5);

    companion object {
        fun from(key: String): Accent = entries.firstOrNull { it.key == key } ?: Lime
    }
}

