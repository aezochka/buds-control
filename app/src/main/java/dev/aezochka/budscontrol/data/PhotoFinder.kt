package dev.aezochka.budscontrol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * Ищет фото наушников по имени Bluetooth-устройства и готовит его к показу
 * на тёмном фоне: белый фон вырезается.
 *
 * Порядок: сначала запрос с «png transparent background» — вендорские PNG
 * уже идут с альфой и их не нужно обрабатывать. Если такого нет, берём фото
 * с однородным фоном и вырезаем его заливкой от краёв.
 *
 * Результат — готовый PNG на диске, поэтому обработка делается один раз.
 */
object PhotoFinder {
    /**
     * Порог совпадения с цветом фона. 120 был слишком велик: у белых наушников
     * корпус попадал в допуск и выедался вместе с фоном. 42 держит белый продукт.
     */
    private const val TOLERANCE = 42

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36"
    private const val TAG = "PhotoFinder"

    /** Путь к готовому файлу или null. */
    suspend fun find(context: Context, deviceName: String): String? = withContext(Dispatchers.IO) {
        val clean = cleanName(deviceName)
        if (clean.isBlank()) return@withContext null

        val key = clean.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
        val ready = File(context.cacheDir, "photo_$key.png")
        val miss = File(context.cacheDir, "photo_$key.miss")
        if (ready.exists() && ready.length() > 0) return@withContext ready.absolutePath
        if (miss.exists()) return@withContext null

        // Первый проход — ищем сразу прозрачные PNG.
        val bitmap = tryQuery("$clean earbuds png transparent background")
            ?: tryQuery("$clean earbuds product")
            ?: run {
                runCatching { miss.createNewFile() }
                return@withContext null
            }

        runCatching {
            ready.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        if (ready.exists() && ready.length() > 0) ready.absolutePath else null
    }

    private fun tryQuery(query: String): Bitmap? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val page = fetchText("https://duckduckgo.com/?q=$encoded&iax=images&ia=images") ?: return null
        val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1) ?: return null
        val json = fetchText(
            "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$token",
            referer = "https://duckduckgo.com/",
        ) ?: return null
        val items = JSONObject(json).optJSONArray("results") ?: return null

        for (i in 0 until minOf(items.length(), 10)) {
            val item = items.optJSONObject(i) ?: continue
            val raw = item.optString("image")
            // http блокируется политикой cleartext — апгрейдим до https.
            val url = when {
                raw.startsWith("https://") -> raw
                raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
                else -> continue
            }
            val w = item.optInt("width")
            val h = item.optInt("height")
            if (w < 600) continue
            val ratio = if (h == 0) 0f else w.toFloat() / h
            if (ratio !in 0.8f..1.25f) continue

            val bytes = fetchBytes(url) ?: continue
            val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: continue
            val prepared = prepare(decoded) ?: continue
            Log.i(TAG, "Фото принято: $url")
            return prepared
        }
        return null
    }

    /**
     * Готовит картинку: если альфы нет — вырезает однородный фон.
     * Возвращает null, если фон вырезать не удалось (коллаж, сложный фон).
     */
    private fun prepare(source: Bitmap): Bitmap? {
        val scaled = scaleDown(source, 720)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val transparent = pixels.count { (it ushr 24) < 16 }
        if (transparent > pixels.size / 12) {
            // Уже с прозрачностью — ничего не трогаем.
            return scaled.copy(Bitmap.Config.ARGB_8888, false)
        }

        // Цвет фона — медиана рамки. Если рамка неоднородна, это коллаж.
        val border = ArrayList<Int>(2 * (w + h))
        for (x in 0 until w) { border.add(pixels[x]); border.add(pixels[(h - 1) * w + x]) }
        for (y in 0 until h) { border.add(pixels[y * w]); border.add(pixels[y * w + w - 1]) }
        val bgR = median(border) { (it shr 16) and 0xFF }
        val bgG = median(border) { (it shr 8) and 0xFF }
        val bgB = median(border) { it and 0xFF }
        val uniform = border.count { diff(it, bgR, bgG, bgB) < TOLERANCE }.toFloat() / border.size
        if (uniform < 0.9f) {
            Log.i(TAG, "Фон неоднородный — картинка не подходит")
            return null
        }

        // Заливка от краёв: не выедает светлые части внутри продукта.
        val out = pixels.copyOf()
        val queue = ArrayDeque<Int>()
        val seen = BooleanArray(pixels.size)
        fun push(index: Int) {
            if (seen[index]) return
            if (diff(pixels[index], bgR, bgG, bgB) >= TOLERANCE) return
            seen[index] = true
            queue.addLast(index)
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            out[idx] = out[idx] and 0x00FFFFFF
            val x = idx % w
            val y = idx / w
            if (x > 0) push(idx - 1)
            if (x < w - 1) push(idx + 1)
            if (y > 0) push(idx - w)
            if (y < h - 1) push(idx + w)
        }

        val cleared = out.count { (it ushr 24) < 16 }
        // Фон должен занимать разумную долю: иначе вырезали не то.
        if (cleared < pixels.size / 12 || cleared > pixels.size * 9 / 10) {
            Log.i(TAG, "Обтравка дала странный результат — пропускаем")
            return null
        }

        // Проверка на «прогрызание» продукта: считаем прозрачные пиксели
        // в центральной зоне. Там должен быть продукт, а не дырки.
        var centerHoles = 0
        var centerTotal = 0
        val x0 = w / 3; val x1 = w * 2 / 3
        val y0 = h / 3; val y1 = h * 2 / 3
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                centerTotal++
                if ((out[y * w + x] ushr 24) < 16) centerHoles++
            }
        }
        if (centerTotal > 0 && centerHoles > centerTotal / 4) {
            Log.i(TAG, "В центре появились дырки — продукт светлый, картинка не подходит")
            return null
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private inline fun median(list: List<Int>, selector: (Int) -> Int): Int =
        list.map(selector).sorted()[list.size / 2]

    private fun diff(argb: Int, r: Int, g: Int, b: Int): Int {
        val pr = (argb shr 16) and 0xFF
        val pg = (argb shr 8) and 0xFF
        val pb = argb and 0xFF
        return kotlin.math.abs(pr - r) + kotlin.math.abs(pg - g) + kotlin.math.abs(pb - b)
    }

    private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= max) return bitmap
        val scale = max.toFloat() / side
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true,
        )
    }

    private fun cleanName(raw: String): String {
        var name = raw.trim()
        name = name.replace(Regex("""\s*\((?:[0-9A-Fa-f]{2,4}|[LR])\)\s*$"""), "")
        name = name.replace(
            Regex("""[-_\s]+(?:L|R|LE|Left|Right|Stereo|Hands?-?free)$""", RegexOption.IGNORE_CASE), "",
        )
        name = name.replace(Regex("""\s*-\s*(?:Find|Найти)$""", RegexOption.IGNORE_CASE), "")
        return name.replace(Regex("""\s{2,}"""), " ").trim()
    }

    private fun fetchText(url: String, referer: String? = null): String? = runCatching {
        open(url, referer).inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun fetchBytes(url: String): ByteArray? = runCatching {
        open(url, null).inputStream.use { it.readBytes() }
    }.getOrNull()

    private fun open(url: String, referer: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", UA)
            referer?.let { setRequestProperty("Referer", it) }
            connectTimeout = 10_000
            readTimeout = 12_000
        }
}
