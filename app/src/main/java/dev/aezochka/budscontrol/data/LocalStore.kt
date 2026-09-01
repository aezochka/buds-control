package dev.aezochka.budscontrol.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.budsDataStore by preferencesDataStore("buds_control")

/**
 * Локальное хранилище. Наружу ничего не уходит: профили, точки маршрута,
 * заряд и метаданные треков лежат в приватном DataStore приложения.
 *
 * settings отдаёт null, пока диск ещё не прочитан — так UI понимает
 * «данных пока нет» и не мигает онбордингом на старте.
 */
class LocalStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val settingsKey = stringPreferencesKey("settings_v2")
    private val profilesKey = stringPreferencesKey("profiles_v2")
    private val historyKey = stringPreferencesKey("history_v2")

    /** null = ещё читаем с диска. Не путать с «настройки по умолчанию». */
    val settings: Flow<UserSettings?> = context.budsDataStore.data.map { prefs ->
        val raw = prefs[settingsKey]
        if (raw == null) UserSettings()
        else runCatching { json.decodeFromString<UserSettings>(raw) }.getOrElse { UserSettings() }
    }

    val profiles: Flow<List<EarbudProfile>> = context.budsDataStore.data.map { prefs ->
        prefs[profilesKey]?.let { raw ->
            runCatching { json.decodeFromString<List<EarbudProfile>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val sessions: Flow<List<WalkSession>> = context.budsDataStore.data.map { prefs ->
        prefs[historyKey]?.let { raw ->
            runCatching { json.decodeFromString<List<WalkSession>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun saveSettings(value: UserSettings) =
        context.budsDataStore.edit { it[settingsKey] = json.encodeToString(value) }

    suspend fun saveProfiles(value: List<EarbudProfile>) =
        context.budsDataStore.edit { it[profilesKey] = json.encodeToString(value) }

    suspend fun saveSessions(value: List<WalkSession>) =
        context.budsDataStore.edit { it[historyKey] = json.encodeToString(value.takeLast(60)) }

    /** Обновляет одну сессию по id — сервис пишет точки инкрементально. */
    suspend fun upsertSession(session: WalkSession) = context.budsDataStore.edit { prefs ->
        val current = prefs[historyKey]?.let { raw ->
            runCatching { json.decodeFromString<List<WalkSession>>(raw) }.getOrNull()
        } ?: emptyList()
        val merged = current.filterNot { it.id == session.id } + session
        prefs[historyKey] = json.encodeToString(merged.takeLast(60))
    }
}
