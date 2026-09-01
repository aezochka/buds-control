package dev.aezochka.budscontrol.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.data.HistoryMath
import dev.aezochka.budscontrol.data.MapTiles
import dev.aezochka.budscontrol.data.TrackPoint
import kotlin.math.floor

/**
 * Настоящая карта: растровые тайлы OpenStreetMap под маршрутом.
 * Тайлы затемняются цветовой матрицей, чтобы попадать в тёмную тему.
 */
@Composable
fun RouteMapView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val bounds = remember(points) { HistoryMath.bounds(points) }

    // Затемняем светлые тайлы OSM: яркость вниз, лёгкая десатурация.
    val darken = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.42f, 0.10f, 0.06f, 0f, 0f,
                    0.08f, 0.44f, 0.08f, 0f, 0f,
                    0.06f, 0.10f, 0.40f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    val progress = rememberInfiniteTransition(label = "marker")
    val pulse by progress.animateFloat(
        initialValue = 9f, targetValue = 17f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    // Считаем зум и грузим нужные тайлы, когда известен размер и границы.
    val plan = remember(bounds, viewport) {
        if (bounds == null || viewport.width == 0) null
        else {
            val side = minOf(viewport.width, viewport.height).toFloat()
            val zoom = MapTiles.pickZoom(bounds[0], bounds[1], bounds[2], bounds[3], side * 0.92f)
            val cxTile = (MapTiles.lonToTileX(bounds[2], zoom) + MapTiles.lonToTileX(bounds[3], zoom)) / 2
            val cyTile = (MapTiles.latToTileY(bounds[0], zoom) + MapTiles.latToTileY(bounds[1], zoom)) / 2
            Triple(zoom, cxTile, cyTile)
        }
    }

    LaunchedEffect(plan) {
        val (zoom, cx, cy) = plan ?: return@LaunchedEffect
        val side = minOf(viewport.width, viewport.height)
        val span = (side / MapTiles.TILE) / 2 + 2
        val baseX = floor(cx).toInt()
        val baseY = floor(cy).toInt()
        for (dx in -span..span) for (dy in -span..span) {
            val tx = baseX + dx
            val ty = baseY + dy
            val key = "$zoom/$tx/$ty"
            if (tiles.containsKey(key)) continue
            MapTiles.load(context, zoom, tx, ty)?.let { tiles[key] = it }
        }
    }

    Box(modifier.background(scheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            viewport = IntSize(size.width.toInt(), size.height.toInt())
            val current = plan
            if (current == null || points.size < 2) return@Canvas
            val (zoom, cx, cy) = current
            val w = size.width
            val h = size.height

            // Пиксельные координаты центра карты.
            val centerPxX = cx * MapTiles.TILE
            val centerPxY = cy * MapTiles.TILE
            val originX = centerPxX - w / 2f
            val originY = centerPxY - h / 2f

            // Тайлы
            val firstTx = floor(originX / MapTiles.TILE).toInt()
            val firstTy = floor(originY / MapTiles.TILE).toInt()
            val tilesX = (w / MapTiles.TILE).toInt() + 2
            val tilesY = (h / MapTiles.TILE).toInt() + 2
            for (ix in 0..tilesX) for (iy in 0..tilesY) {
                val tx = firstTx + ix
                val ty = firstTy + iy
                val bitmap = tiles["$zoom/$tx/$ty"] ?: continue
                val left = tx * MapTiles.TILE - originX
                val top = ty * MapTiles.TILE - originY
                translate(left, top) {
                    drawImage(bitmap, colorFilter = darken)
                }
            }

            // Маршрут
            val mapped = points.map { p ->
                Offset(
                    (MapTiles.lonToTileX(p.longitude, zoom) * MapTiles.TILE - originX).toFloat(),
                    (MapTiles.latToTileY(p.latitude, zoom) * MapTiles.TILE - originY).toFloat(),
                )
            }
            val path = Path().apply {
                moveTo(mapped[0].x, mapped[0].y)
                for (i in 1 until mapped.size) lineTo(mapped[i].x, mapped[i].y)
            }
            drawPath(
                path, color = scheme.primary,
                style = Stroke(
                    width = 7f, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 16f), 0f),
                ),
            )
            drawCircle(scheme.primary, radius = 11f, center = mapped.first())
            drawCircle(scheme.secondary.copy(alpha = 0.25f), radius = pulse + 9f, center = mapped.last())
            drawCircle(scheme.secondary, radius = 12f, center = mapped.last())
        }

        if (points.size < 2) {
            Text(
                if (points.isEmpty()) "Ищу спутники…" else "Нужна вторая точка маршрута",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
