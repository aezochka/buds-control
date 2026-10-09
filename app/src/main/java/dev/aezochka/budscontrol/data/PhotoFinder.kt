package dev.aezochka.budscontrol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
 * Два движка: DuckDuckGo (JSON, но агрессивно ловит ботов — отвечает
 * challenge-страницей) и Bing (HTML, зато стабильнее). Если один молчит,
 * работает второй. Совсем без сети спасает выбор своей картинки из галереи.
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

    /** Картинка-кандидат из поиска: URL и метаданные, если движок их дал. */
    private data class Candidate(val url: String, val width: Int = 0, val height: Int = 0)

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

        val bitmaps = searchBitmaps(clean, limit)
        val saved = mutableListOf<String>()
        bitmaps.forEachIndexed { index, bitmap ->
            val file = File(context.cacheDir, "photo_${key}_$index.png")
            runCatching {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                saved += file.absolutePath
            }
        }
        saved
    }

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

        // Поисковики отдают по одному запросу список URL — все сразу.
        // Готовим картинки последовательно и отдаём по мере готовности.
        val queries = listOf("$clean earbuds png transparent background", "$clean earbuds product")
        val candidates = queries.flatMap { searchCandidates(it) }
        var saved = index
        for (c in candidates) {
            if (saved >= limit) break
            val bitmap = decode(c) ?: continue
            val prepared = prepare(bitmap) ?: continue
            val file = File(context.cacheDir, "photo_${key}_$saved.png")
            runCatching {
                file.outputStream().use { prepared.compress(Bitmap.CompressFormat.PNG, 100, it) }
                onFound(file.absolutePath)
                saved++
            }
        }
    }

    /** Запоминает выбор пользователя. */
    suspend fun pick(context: Context, address: String, path: String) = withContext(Dispatchers.IO) {
        runCatching {
            File(path).copyTo(pickedFile(context, address), overwrite = true)
        }
    }

    /**
     * Своя картинка из галереи: путь выбора пользователя авторитетнее
     * любых эвристик, поэтому фон не трогаем — только уменьшаем.
     */
    suspend fun importPicked(context: Context, uri: Uri, address: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) return@runCatching null
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val target = File(context.cacheDir, "picked_import.png")
                target.outputStream().use {
                    scaleDown(decoded, 720).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                target
            }.getOrNull()?.let { tmp ->
                runCatching { tmp.copyTo(pickedFile(context, address), overwrite = true) }
                    .onFailure { Log.w(TAG, "Импорт фото не сохранён: ${it.message}") }
                tmp.delete()
                pickedFile(context, address).absolutePath
            }
        }

    private fun pickedFile(context: Context, address: String) =
        File(context.cacheDir, "picked_${address.replace(":", "").lowercase(Locale.ROOT)}.png")

    // ===== Поиск =====

    /** Спрашивает оба движка и оставляет уникальные URL. */
    private fun searchCandidates(query: String): List<Candidate> {
        val fromDdg = ddgImages(query)
        val fromBing = bingImages(query)
        val seen = mutableSetOf<String>()
        return (fromDdg + fromBing).filter { it.url.isNotBlank() && seen.add(it.url) }
    }

    private fun searchBitmaps(cleanName: String, limit: Int): List<Bitmap> {
        val queries = listOf("$cleanName earbuds png transparent background", "$cleanName earbuds product")
        val result = mutableListOf<Bitmap>()
        for (query in queries) {
            if (result.size >= limit) break
            for (c in searchCandidates(query)) {
                if (result.size >= limit) break
                val bitmap = decode(c) ?: continue
                val prepared = prepare(bitmap) ?: continue
                result += prepared
            }
        }
        Log.i(TAG, "По имени «$cleanName» подошло ${result.size} картинок")
        return result
    }

    /**
     * DuckDuckGo Images (i.js). Токен vqd живёт в HTML страницы поиска;
     * для «подозрительных» IP вместо результатов приходит JSON с полем
     * challenge — это и была причина «картинка не грузит».
     */
    private fun ddgImages(query: String): List<Candidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // Токен берётся не с первого раза: пробуем трижды с паузой.
        var json: String? = null
        repeat(3) { attempt ->
            if (json != null) return@repeat
            val page = fetchText("https://duckduckgo.com/?q=$encoded&iax=images&ia=images") ?: return@repeat
            val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1) ?: return@repeat
            val body = fetchText(
                "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$token",
                referer = "https://duckduckgo.com/",
            )
            if (body != null && !body.contains("\"challenge\"")) json = body
            else {
                Log.w(TAG, "DDG: попытка ${attempt + 1} отклонена (challenge или пусто)")
                Thread.sleep(400L * (attempt + 1))
            }
        }
        val body = json ?: return emptyList()
        val items = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull() ?: return emptyList()

        val out = mutableListOf<Candidate>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val raw = item.optString("image")
            val url = upgradeHttps(raw) ?: continue
            out += Candidate(url, item.optInt("width"), item.optInt("height"))
        }
        return out
    }

    /**
     * Bing Images (HTML). В разметке карточек лежит JSON в атрибуте m,
     * где murl — адрес оригинала картинки. Отсюда же приходят ссылки на
     * официальный CDN realme (image01.realme.net).
     */
    private fun bingImages(query: String): List<Candidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = fetchText(
            "https://www.bing.com/images/search?q=$encoded&form=HDRSC2&count=35",
            referer = "https://www.bing.com/",
        ) ?: return emptyList()
        val urls = Regex("murl&quot;:&quot;([^&\"]+)").findAll(html).map { it.groupValues[1] }.toList()
        if (urls.isEmpty()) {
            Log.w(TAG, "Bing: разметка без murl — вероятно, тоже отлуп")
            return emptyList()
        }
        return urls.mapNotNull { raw -> upgradeHttps(raw)?.let { Candidate(it) } }
    }

    /** http блокируется политикой cleartext — апгрейдим до https. */
    private fun upgradeHttps(raw: String): String? = when {
        raw.startsWith("https://") -> raw
        raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
        else -> null
    }

    private fun decode(c: Candidate): Bitmap? {
        // Размер знаем заранее — режем заведомо мелочь и не-квадраты.
        if (c.width > 0) {
            if (c.width < 400) return null
            val ratio = if (c.height == 0) 0f else c.width.toFloat() / c.height
            if (ratio !in 0.7f..1.4f) return null
        }
        val bytes = fetchBytes(c.url) ?: return null
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        // Для Bing размеры неизвестны — проверяем после декодирования.
        if (c.width == 0) {
            val side = minOf(decoded.width, decoded.height)
            if (side < 400) return null
            val ratio = decoded.width.toFloat() / decoded.height
            if (ratio !in 0.7f..1.4f) return null
        }
        return decoded
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
            if (y < w - 1) push(idx + w)
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
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            referer?.let { setRequestProperty("Referer", it) }
            connectTimeout = 10_000
            readTimeout = 12_000
        }
}
