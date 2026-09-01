package dev.aezochka.budscontrol.audio

import android.content.Context

/** Хранит последнюю кривую, чтобы receiver нового плеера применял её сразу. */
internal object LevelStore {
    private const val PREFS = "eq_levels"
    private const val KEY = "levels"

    fun save(context: Context, levels: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, levels.joinToString(","))
            .apply()
    }

    fun load(context: Context): List<Int> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, "")
        .orEmpty()
        .split(',')
        .mapNotNull { it.toIntOrNull() }
}
