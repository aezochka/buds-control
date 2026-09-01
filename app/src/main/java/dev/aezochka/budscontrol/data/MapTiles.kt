package dev.aezochka.budscontrol.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Растровые тайлы OpenStreetMap: настоящие улицы и дома под маршрутом.
 * Раньше вместо карты рисовалась только сетка — отсюда «карта не отображается».
 *
 * Тайлы кешируются в cacheDir, повторно не качаются. User-Agent обязателен
 * по правилам OSM tile usage policy.
 */
object MapTiles {
    const val TILE = 256
    private const val UA = "BudsControl/2.0 (Android; personal earbuds companion)"

    fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    fun latToTileY(lat: Double, zoom: Int): Double {
        val rad = Math.toRadians(lat.coerceIn(-85.05, 85.05))
        return (1.0 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2.0 * (1 shl zoom)
    }

    /** Подбирает зум так, чтобы весь маршрут влез в квадрат viewportPx. */
    fun pickZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, viewportPx: Float): Int {
        for (z in 18 downTo 3) {
            val w = (lonToTileX(maxLon, z) - lonToTileX(minLon, z)) * TILE
            val h = (latToTileY(minLat, z) - latToTileY(maxLat, z)) * TILE
            if (w <= viewportPx && h <= viewportPx) return z
        }
        return 3
    }

    suspend fun load(context: Context, z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val max = 1 shl z
        if (x < 0 || y < 0 || x >= max || y >= max) return@withContext null
        val dir = File(context.cacheDir, "tiles").apply { mkdirs() }
        val file = File(dir, "${z}_${x}_${y}.png")
        if (file.exists() && file.length() > 0) {
            return@withContext runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
        runCatching {
            val conn = (URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            conn.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.isNotEmpty()) {
                    file.writeBytes(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            }
        }.getOrNull()
    }
}
