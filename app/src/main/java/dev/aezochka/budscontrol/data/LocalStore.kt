package dev.aezochka.budscontrol.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.budsDataStore by preferencesDataStore("buds_control")

/**
 * Persistent local store. Nothing gets uploaded: profiles, route points, battery
 * samples and music metadata stay in the app-private DataStore file.
 */
class LocalStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val settingsKey = stringPreferencesKey("settings_v2")
    private val profilesKey = stringPreferencesKey("profiles_v2")
    private val historyKey = stringPreferencesKey("history_v2")

    val settings: Flow<UserSettings> = context.budsDataStore.data.map {
        it[settingsKey]?.let { raw -> runCatching { json.decodeFromString<UserSettings>(raw) }.getOrNull() }
            ?: UserSettings()
    }
    val profiles: Flow<List<EarbudProfile>> = context.budsDataStore.data.map {
        it[profilesKey]?.let { raw -> runCatching { json.decodeFromString<List<EarbudProfile>>(raw) }.getOrNull() }
            ?: emptyList()
    }
    val sessions: Flow<List<WalkSession>> = context.budsDataStore.data.map {
        it[historyKey]?.let { raw -> runCatching { json.decodeFromString<List<WalkSession>>(raw) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun saveSettings(value: UserSettings) = context.budsDataStore.edit { it[settingsKey] = json.encodeToString(value) }
    suspend fun saveProfiles(value: List<EarbudProfile>) = context.budsDataStore.edit { it[profilesKey] = json.encodeToString(value) }
    suspend fun saveSessions(value: List<WalkSession>) = context.budsDataStore.edit { it[historyKey] = json.encodeToString(value.takeLast(60)) }
}
