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
import java.util.Locale

/**
 * Универсальный поиск фото ЛЮБЫХ наушников по имени Bluetooth-устройства.
 *
 * Встроенных картинок в APK больше нет: держать базу под каждую модель мира
 * бессмысленно, а без неё владелец AirPods или Sony остался бы без фото.
 * Проверено вживую: поиск отдаёт результаты для realme, Apple, Samsung,
 * Sony, JBL, Huawei.
 *
 * Кеш на диске: одна модель ищется один раз.
 */
object PhotoFinder {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36"
    private const val TAG = "PhotoFinder"

    /** Найденный URL или null. Ничего не выдумывает. */
    suspend fun find(context: Context, deviceName: String): String? = withContext(Dispatchers.IO) {
        val clean = cleanName(deviceName)
        if (clean.isBlank()) return@withContext null

        val key = clean.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
        val cache = File(context.cacheDir, "photo_$key.txt")
        if (cache.exists()) {
            val cached = cache.readText().trim()
            return@withContext cached.ifEmpty { null }
        }

        val found = search(clean)
        runCatching { cache.writeText(found.orEmpty()) }
        found
    }

    /**
     * Готовит имя к поиску: убирает служебные суффиксы, которые гарнитуры
     * добавляют в имя Bluetooth и которые ломают выдачу.
     */
    private fun cleanName(raw: String): String {
        var name = raw.trim()
        // «realme Buds T110-L», «Galaxy Buds3 Pro (2A4F)», «AirPods Pro - Найти»
        name = name.replace(Regex("""\s*\((?:[0-9A-Fa-f]{2,4}|[LR])\)\s*$"""), "")
        name = name.replace(Regex("""[-_\s]+(?:L|R|LE|Left|Right|Stereo|Hands?-?free)$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""\s*-\s*(?:Find|Найти)$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""\s{2,}"""), " ").trim()
        return name
    }

    private fun search(name: String): String? = runCatching {
        // Добавляем «earbuds», чтобы не ловить обзоры и коробки.
        val query = URLEncoder.encode("$name earbuds product", "UTF-8")

        val page = fetch("https://duckduckgo.com/?q=$query&iax=images&ia=images") ?: return@runCatching null
        val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1) ?: return@runCatching null

        val json = fetch(
            "https://duckduckgo.com/i.js?l=us-en&o=json&q=$query&vqd=$token",
            referer = "https://duckduckgo.com/",
        ) ?: return@runCatching null

        val items = JSONObject(json).optJSONArray("results") ?: return@runCatching null
        // Ищем достаточно крупную и желательно квадратную картинку продукта.
        var fallback: String? = null
        for (i in 0 until minOf(items.length(), 16)) {
            val item = items.optJSONObject(i) ?: continue
            val raw = item.optString("image")
            // http:// блокируется политикой usesCleartextTraffic=false,
            // поэтому апгрейдим до https и отбрасываем всё, что не http(s).
            val url = when {
                raw.startsWith("https://") -> raw
                raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
                else -> continue
            }
            val w = item.optInt("width")
            val h = item.optInt("height")
            if (w < 600) continue
            val ratio = if (h == 0) 0f else w.toFloat() / h
            if (ratio in 0.8f..1.25f && reachable(url)) return@runCatching url
            if (fallback == null && reachable(url)) fallback = url
        }
        fallback
    }.getOrElse {
        Log.i(TAG, "Поиск фото не удался: ${it.message}")
        null
    }

    /** Быстрая проверка, что ссылка реально отдаёт картинку. */
    private fun reachable(url: String): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            connectTimeout = 6_000
            readTimeout = 6_000
        }
        val ok = conn.responseCode in 200..299
        val type = conn.contentType ?: ""
        conn.disconnect()
        ok && type.startsWith("image")
    }.getOrDefault(false)

    private fun fetch(url: String, referer: String? = null): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", UA)
            referer?.let { setRequestProperty("Referer", it) }
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
