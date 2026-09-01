package dev.aezochka.budscontrol.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Автоподбор фото для модели, которой нет в локальном каталоге.
 *
 * Двухшаговый поиск: сначала берём токен со страницы поиска, затем
 * запрашиваем JSON с картинками. Проверено вживую — отдаёт прямые ссылки.
 * Результат кешируется на диск, чтобы не ходить в сеть повторно.
 */
object PhotoFinder {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36"
    private const val TAG = "PhotoFinder"

    /** Возвращает URL картинки или null. Ничего не выдумывает. */
    suspend fun find(context: Context, deviceName: String): String? = withContext(Dispatchers.IO) {
        val key = deviceName.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val cache = File(context.cacheDir, "photo_$key.txt")
        if (cache.exists()) {
            val cached = cache.readText().trim()
            return@withContext cached.ifEmpty { null }
        }

        val result = runCatching {
            val query = "$deviceName earbuds"
            val encoded = URLEncoder.encode(query, "UTF-8")

            val page = fetch("https://duckduckgo.com/?q=$encoded&iax=images&ia=images") ?: return@runCatching null
            val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1)
                ?: return@runCatching null

            val json = fetch(
                "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$token",
                referer = "https://duckduckgo.com/",
            ) ?: return@runCatching null

            val items = JSONObject(json).optJSONArray("results") ?: return@runCatching null
            // Берём первую достаточно крупную картинку.
            for (i in 0 until minOf(items.length(), 8)) {
                val item = items.optJSONObject(i) ?: continue
                val width = item.optInt("width")
                val url = item.optString("image")
                if (width >= 600 && url.startsWith("http")) return@runCatching url
            }
            null
        }.getOrElse {
            Log.i(TAG, "Поиск фото не удался: ${it.message}")
            null
        }

        // Пишем даже пустой результат, чтобы не долбить сеть каждую секунду.
        runCatching { cache.writeText(result.orEmpty()) }
        result
    }

    private fun fetch(url: String, referer: String? = null): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", UA)
            referer?.let { setRequestProperty("Referer", it) }
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
