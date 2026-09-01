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
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val batteryPercent: Int?,
    val steps: Long?,
)

@Serializable
data class PlaybackSegment(
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val artist: String? = null,
    val title: String? = null,
    val packageName: String? = null,
    val wasPlaying: Boolean = false,
)

@Serializable
data class WalkSession(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val profileId: String,
    val startBattery: Int? = null,
    val endBattery: Int? = null,
    val startSteps: Long? = null,
    val endSteps: Long? = null,
    val points: List<TrackPoint> = emptyList(),
    val playback: List<PlaybackSegment> = emptyList(),
    /** Последний признак жизни — обновляется раз в 10 с, даже если стоим. */
    val lastSeenMillis: Long? = null,
)

@Serializable
data class UserSettings(
    val onboardingFinished: Boolean = false,
    val language: String = "system",
    val historyEnabled: Boolean = false,
    val pauseOnRemoval: Boolean = false,
    val accent: String = "lime",
    val tileOrder: List<String> = defaultTileOrder,
    val tileSpans: Map<String, Int> = emptyMap(),
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

data class RealHistorySummary(
    val distanceMeters: Double = 0.0,
    val steps: Long? = null,
    val durationMillis: Long = 0L,
    val batteryDelta: Int? = null,
    val tracks: Int = 0,
    val points: List<TrackPoint> = emptyList(),
    val playback: List<PlaybackSegment> = emptyList(),
    /** Последний признак жизни — обновляется раз в 10 с, даже если стоим. */
    val lastSeenMillis: Long? = null,
)
