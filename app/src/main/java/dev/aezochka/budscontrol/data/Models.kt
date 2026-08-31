package dev.aezochka.budscontrol.data

import kotlinx.serialization.Serializable

@Serializable
data class EarbudProfile(
    val id: String,
    val displayName: String,
    val address: String,
    val vendor: String,
    val imageKey: String? = null,
    val lastSeenMillis: Long = 0L,
    val isSelected: Boolean = false,
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
)

@Serializable
data class UserSettings(
    val onboardingFinished: Boolean = false,
    val language: String = "system",
    val historyEnabled: Boolean = false,
    val pauseOnRemoval: Boolean = false,
    val tileOrder: List<String> = listOf("eq", "game", "case", "find", "latency", "remaining", "sleep", "limit", "autopause", "custom_eq"),
    val tileSpans: Map<String, Int> = emptyMap(),
)

data class RealHistorySummary(
    val distanceMeters: Double = 0.0,
    val steps: Long? = null,
    val durationMillis: Long = 0L,
    val batteryDelta: Int? = null,
    val tracks: Int = 0,
    val points: List<TrackPoint> = emptyList(),
    val playback: List<PlaybackSegment> = emptyList(),
)
