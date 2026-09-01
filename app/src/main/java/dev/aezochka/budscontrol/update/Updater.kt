package dev.aezochka.budscontrol.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление прямо из приложения: проверяет релизы на GitHub и ставит новую
 * версию, не заставляя качать APK руками.
 *
 * Про «докачивать новые файлы, как в Steam»: для APK, установленного вручную,
 * это невозможно. Android умеет дельта-патчи только через Play Store
 * (там сервер собирает патч между конкретными версиями), а при sideload
 * система принимает только целый подписанный APK. Что реально сделано:
 * загрузка идёт с докачкой по Range — если связь оборвалась, файл
 * продолжится с того же места, а не начнётся заново.
 */
object Updater {
    private const val API = "https://api.github.com/repos/aezochka/buds-control/releases/latest"
    private const val TAG = "Updater"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    /** Возвращает релиз, если он новее установленного. */
    suspend fun check(currentVersion: String): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val json = fetch(API) ?: return@runCatching null
            val root = JSONObject(json)
            val tag = root.optString("tag_name").removePrefix("v")
            if (tag.isBlank() || !isNewer(tag, currentVersion)) return@runCatching null

            val assets = root.optJSONArray("assets") ?: return@runCatching null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk")) continue
                return@runCatching Release(
                    version = tag,
                    notes = root.optString("body").take(600),
                    apkUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                )
            }
            null
        }.getOrElse {
            Log.i(TAG, "Проверка обновления не удалась: ${it.message}")
            null
        }
    }

    /**
     * Качает APK с докачкой: повторный вызов продолжает файл, а не
     * начинает заново. [onProgress] получает 0..1.
     */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "update_${release.version}.apk")
        runCatching {
            val done = if (target.exists()) target.length() else 0L
            if (release.sizeBytes > 0 && done >= release.sizeBytes) {
                onProgress(1f)
                return@runCatching target
            }

            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "BudsControl")
                // Докачка с прерванного места.
                if (done > 0) setRequestProperty("Range", "bytes=$done-")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val resumed = conn.responseCode == 206
            val total = if (release.sizeBytes > 0) release.sizeBytes
            else conn.contentLengthLong + if (resumed) done else 0L

            conn.inputStream.use { input ->
                java.io.FileOutputStream(target, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = if (resumed) done else 0L
                    if (!resumed) target.setLastModified(System.currentTimeMillis())
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            target
        }.getOrElse {
            Log.e(TAG, "Загрузка не удалась: ${it.message}")
            null
        }
    }

    /** Открывает системный установщик. */
    fun install(context: Context, apk: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.updates", apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { Log.e(TAG, "Установка не запустилась: ${it.message}") }
    }

    /** Сравнение версий вида 3.1.2. */
    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split(".", "-").mapNotNull { it.toIntOrNull() }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun fetch(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "BudsControl")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
