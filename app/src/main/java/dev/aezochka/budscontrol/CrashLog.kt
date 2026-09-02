package dev.aezochka.budscontrol

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Записывает необработанные исключения в файл.
 *
 * Нужен потому, что краши воспроизводятся не всегда: без стектрейса
 * остаётся только угадывать причину, а это уже приводило к правкам наугад.
 * Лог лежит в приватном каталоге приложения и переживает перезапуск.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val LIMIT = 40_000

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: String, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val text = buildString {
            appendLine("=== $stamp | thread=$thread ===")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(error.stackTraceToString())
        }
        val file = File(context.filesDir, FILE)
        // Не даём файлу расти бесконечно: держим только свежий хвост.
        val existing = if (file.exists()) file.readText().takeLast(LIMIT) else ""
        file.writeText(existing + text)
    }

    fun read(context: Context): String =
        File(context.filesDir, FILE).let { if (it.exists()) it.readText() else "" }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
