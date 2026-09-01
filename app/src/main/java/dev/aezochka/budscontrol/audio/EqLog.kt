package dev.aezochka.budscontrol.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Лог эквалайзера, видимый прямо в приложении.
 *
 * Нужен, потому что вслепую я уже дважды не угадала причину: сначала
 * жест перехватывал detectDragGestures, потом ModalBottomSheet. Теперь
 * можно посмотреть, что реально происходит, без ADB и без догадок.
 */
object EqLog {
    private const val TAG = "EqLog"
    private const val LIMIT = 200

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    fun log(message: String) {
        Log.d(TAG, message)
        val line = "${stamp.format(Date())}  $message"
        _lines.value = (_lines.value + line).takeLast(LIMIT)
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun dump(): String = _lines.value.joinToString("\n")
}
