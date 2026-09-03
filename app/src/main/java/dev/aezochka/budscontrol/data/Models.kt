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
    /** Набор клика: click_a/click_b/system/off. */
    val clickSound: String = "click_a",
    val autoConnect: Boolean = true,
    val tileOrder: List<String> = defaultTileOrder,
    val tileSpans: Map<String, Int> = emptyMap(),
    /** Прятать панель с названием наушников при прокрутке вниз. */
    val hideNameOnScroll: Boolean = true,
    val hiddenTiles: Set<String> = emptySet(),
)

val defaultTileOrder = listOf(
    "eq", "anc", "game", "spatial", "multipoint",
    "case", "sleep", "volume", "find", "firmware",
)

/**
 * Плитки, добавленные после первых версий.
 *
 * Порядок плиток сохраняется в настройках, поэтому у тех, кто уже пользовался
 * приложением, лежит старый список — и новые плитки не появлялись сами.
 * Их нужно доложить в сохранённый порядок при чтении настроек.
 */
val laterTiles = listOf("anc", "spatial", "multipoint")

/**
 * Докладывает новые плитки в уже сохранённый порядок.
 *
 * Вставляем сразу после "eq", а не в конец: иначе шумодав уезжал бы вниз,
 * под мелкие плитки. Скрытые пользователем плитки не трогаем.
 */
fun UserSettings.withLaterTiles(): UserSettings {
    val missing = laterTiles.filter { it !in tileOrder }
    if (missing.isEmpty()) return this
    val updated = tileOrder.toMutableList()
    val at = (updated.indexOf("eq") + 1).coerceAtLeast(0)
    updated.addAll(at, missing)
    return copy(tileOrder = updated)
}

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

