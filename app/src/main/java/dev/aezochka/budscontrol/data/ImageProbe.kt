package dev.aezochka.budscontrol.data

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ищет на фото продукта, где именно находятся наушники, чтобы поставить
 * индикаторы заряда рядом с ними, а не в углах картинки.
 *
 * Работает по яркости: фон у товарных фото светлый или прозрачный,
 * сам продукт — тёмное пятно. Картинку уменьшаем, поэтому анализ дешёвый.
 */
object ImageProbe {

    /** Относительные координаты 0..1 внутри картинки. */
    data class Spot(val x: Float, val y: Float)

    data class Layout(
        val left: Spot,
        val right: Spot,
    ) {
        companion object {
            /** Запасной вариант: по бокам на середине высоты. */
            val Default = Layout(Spot(0.12f, 0.5f), Spot(0.88f, 0.34f))
        }
    }

    suspend fun analyze(image: ImageBitmap): Layout = withContext(Dispatchers.Default) {
        runCatching { probe(image.asAndroidBitmap()) }.getOrDefault(Layout.Default)
    }

    private fun probe(source: Bitmap): Layout {
        val step = 64
        val small = Bitmap.createScaledBitmap(source, step, step, true)
        val pixels = IntArray(step * step)
        small.getPixels(pixels, 0, step, 0, 0, step, step)

        // Порог: считаем «продуктом» всё, что заметно темнее среднего и не прозрачное.
        var sum = 0L
        var counted = 0
        for (p in pixels) {
            if ((p ushr 24) < 40) continue
            sum += luminance(p)
            counted++
        }
        if (counted == 0) return Layout.Default
        val threshold = (sum / counted) * 0.82

        // Центроиды по половинам: левый наушник в левой части, правый в правой.
        var lx = 0.0; var ly = 0.0; var ln = 0
        var rx = 0.0; var ry = 0.0; var rn = 0
        for (y in 0 until step) {
            for (x in 0 until step) {
                val p = pixels[y * step + x]
                if ((p ushr 24) < 40) continue
                if (luminance(p) > threshold) continue
                if (x < step / 2) { lx += x; ly += y; ln++ } else { rx += x; ry += y; rn++ }
            }
        }
        if (ln < 12 || rn < 12) return Layout.Default

        // Границы продукта по X: нужны, чтобы чип встал СНАРУЖИ силуэта,
        // а не поверх крышки кейса.
        var minX = step; var maxX = 0
        for (y in 0 until step) {
            for (x in 0 until step) {
                val p = pixels[y * step + x]
                if ((p ushr 24) < 40 || luminance(p) > threshold) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
        }

        // Наушники — в ВЕРХНЕЙ части фото, кейс снизу. Берём центр масс только
        // верхних 55% силуэта, иначе чип выравнивался по кейсу, а не по наушнику.
        val cut = (step * 0.55f).toInt()
        var uly = 0.0; var uln = 0
        var ury = 0.0; var urn = 0
        for (y in 0 until cut) {
            for (x in 0 until step) {
                val p = pixels[y * step + x]
                if ((p ushr 24) < 40 || luminance(p) > threshold) continue
                if (x < step / 2) { uly += y; uln++ } else { ury += y; urn++ }
            }
        }
        val leftY = if (uln > 8) (uly / uln / step).toFloat().coerceIn(0.16f, 0.7f)
        else (ly / ln / step).toFloat().coerceIn(0.16f, 0.7f)
        val rightY = if (urn > 8) (ury / urn / step).toFloat().coerceIn(0.16f, 0.7f)
        else (ry / rn / step).toFloat().coerceIn(0.16f, 0.7f)

        // X — за кромкой силуэта с увеличенным зазором, чип не должен касаться
        // продукта и не должен прилипать к краю картинки.
        // Гарантированный клиренс: при 0.075 самые тесные случаи давали ~10px.
        val gap = 0.11f
        val leftX = ((minX.toFloat() / step) - gap).coerceIn(0.10f, 0.32f)
        val rightX = ((maxX.toFloat() / step) + gap).coerceIn(0.68f, 0.90f)

        return Layout(
            left = Spot(leftX, leftY),
            right = Spot(rightX, rightY),
        )
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
