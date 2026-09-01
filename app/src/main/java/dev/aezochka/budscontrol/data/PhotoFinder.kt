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

    /**
     * Фото для устройства. Ключ — АДРЕС, а не имя: у двух профилей может быть
     * одинаковое имя, и раньше картинки путались между собой.
     *
     * Если пользователь выбрал вариант руками, возвращаем его.
     */
    suspend fun find(context: Context, address: String, deviceName: String): String? =
        withContext(Dispatchers.IO) {
            val picked = pickedFile(context, address)
            if (picked.exists() && picked.length() > 0) return@withContext picked.absolutePath

            val variants = variants(context, address, deviceName, limit = 1)
            variants.firstOrNull()
        }

    /**
     * Несколько вариантов картинки для выбора в шторке.
     * Каждый сохраняется отдельным файлом, фон уже вырезан.
     */
    /**
     * Потоковый вариант: каждая готовая картинка отдаётся сразу через [onFound].
     * Раньше список появлялся только после загрузки всех — ждать приходилось долго.
     */
    suspend fun variantsStreaming(
        context: Context,
        address: String,
        deviceName: String,
        limit: Int = 8,
        onFound: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val clean = cleanName(deviceName)
        if (clean.isBlank()) return@withContext
        val key = address.replace(":", "").lowercase(Locale.ROOT)

        // Сначала отдаём то, что уже в кеше — мгновенно.
        var index = 0
        while (index < limit) {
            val cached = File(context.cacheDir, "photo_${key}_$index.png")
            if (!cached.exists() || cached.length() == 0L) break
            onFound(cached.absolutePath)
            index++
        }
        if (index >= limit) return@withContext

        // Дальше ищем и сохраняем по одной.
        for (query in listOf("$clean earbuds png transparent background", "$clean earbuds product")) {
            if (index >= limit) break
            collectStreaming(query, limit - index) { bitmap ->
                val file = File(context.cacheDir, "photo_${key}_$index.png")
                runCatching {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    onFound(file.absolutePath)
                    index++
                }
            }
        }
    }

    suspend fun variants(
        context: Context,
        address: String,
        deviceName: String,
        limit: Int = 6,
    ): List<String> = withContext(Dispatchers.IO) {
        val clean = cleanName(deviceName)
        if (clean.isBlank()) return@withContext emptyList()
        val key = address.replace(":", "").lowercase(Locale.ROOT)

        val cached = (0 until limit).mapNotNull { i ->
            File(context.cacheDir, "photo_${key}_$i.png").takeIf { it.exists() && it.length() > 0 }
        }
        if (cached.size >= limit) return@withContext cached.map { it.absolutePath }

        val bitmaps = collect("$clean earbuds png transparent background", limit) +
            collect("$clean earbuds product", limit)
        val saved = mutableListOf<String>()
        bitmaps.take(limit).forEachIndexed { index, bitmap ->
            val file = File(context.cacheDir, "photo_${key}_$index.png")
            runCatching {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                saved += file.absolutePath
            }
        }
        saved
    }

    /** Запоминает выбор пользователя. */
    suspend fun pick(context: Context, address: String, path: String) = withContext(Dispatchers.IO) {
        runCatching {
            File(path).copyTo(pickedFile(context, address), overwrite = true)
        }
    }

    private fun pickedFile(context: Context, address: String) =
        File(context.cacheDir, "picked_${address.replace(":", "").lowercase(Locale.ROOT)}.png")

    /** Как collect, но отдаёт картинки по мере готовности. */
    private suspend fun collectStreaming(query: String, limit: Int, onEach: suspend (Bitmap) -> Unit) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val page = fetchText("https://duckduckgo.com/?q=$encoded&iax=images&ia=images") ?: return
        val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1) ?: return
        val json = fetchText(
            "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$token",
            referer = "https://duckduckgo.com/",
        ) ?: return
        val items = JSONObject(json).optJSONArray("results") ?: return

        var done = 0
        for (i in 0 until minOf(items.length(), 24)) {
            if (done >= limit) break
            val item = items.optJSONObject(i) ?: continue
            val raw = item.optString("image")
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
            onEach(prepared)
            done++
        }
    }

    /** Собирает до [limit] подходящих картинок по запросу. */
    private fun collect(query: String, limit: Int): List<Bitmap> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val page = fetchText("https://duckduckgo.com/?q=$encoded&iax=images&ia=images") ?: return emptyList()
        val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1) ?: return emptyList()
        val json = fetchText(
            "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$token",
            referer = "https://duckduckgo.com/",
        ) ?: return emptyList()
        val items = JSONObject(json).optJSONArray("results") ?: return emptyList()

        val result = mutableListOf<Bitmap>()
        for (i in 0 until minOf(items.length(), 20)) {
            if (result.size >= limit) break
            val item = items.optJSONObject(i) ?: continue
            val raw = item.optString("image")
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
            result += prepared
        }
        Log.i(TAG, "По запросу «$query» подошло ${result.size}")
        return result
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

        // Убираем светлую кайму: пиксели на границе с прозрачностью были
        // сглажены на белом фоне, из-за чего оставался заметный ореол.
        val cleaned = out.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if ((out[idx] ushr 24) < 16) continue
                val nearTransparent =
                    (out[idx - 1] ushr 24) < 16 || (out[idx + 1] ushr 24) < 16 ||
                        (out[idx - w] ushr 24) < 16 || (out[idx + w] ushr 24) < 16
                if (!nearTransparent) continue
                // Край, похожий на фон, гасим до полупрозрачного.
                if (diff(out[idx], bgR, bgG, bgB) < TOLERANCE * 2) {
                    cleaned[idx] = out[idx] and 0x00FFFFFF
                } else {
                    cleaned[idx] = (out[idx] and 0x00FFFFFF) or (0xB0 shl 24)
                }
            }
        }
        return Bitmap.createBitmap(cleaned, w, h, Bitmap.Config.ARGB_8888)
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
