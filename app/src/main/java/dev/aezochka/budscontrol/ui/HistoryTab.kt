package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.data.HistoryMath
import dev.aezochka.budscontrol.data.WalkSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * История прогулок на реальных данных: маршрут из записанных GPS-точек,
 * дистанция по гаверсинусу, шаги из системного шагомера.
 */
@Composable
fun HistoryTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val ordered = remember(sessions) { sessions.sortedByDescending { it.startedAtMillis } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("История", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !settings.historyEnabled -> "Запись выключена — включи в настройках"
                        ordered.isEmpty() -> "Запись идёт, ждём первые точки маршрута"
                        else -> "${ordered.size} ${plural(ordered.size)}"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }

        if (ordered.isEmpty()) {
            item { EmptyHistory(settings.historyEnabled) { vm.setHistoryEnabled(true) } }
        } else {
            items(ordered, key = { it.id }) { session ->
                WalkCard(session, onDelete = { vm.deleteSession(session.id) })
            }
        }
        item { BottomSpacer() }
    }
}

private fun plural(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "прогулка"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "прогулки"
    else -> "прогулок"
}

@Composable
private fun EmptyHistory(enabled: Boolean, onEnable: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp)) {
        BentoTile(Modifier.fillMaxWidth(), minHeight = 190.dp, onClick = if (enabled) null else onEnable) { primary, secondary ->
            Icon(
                if (enabled) Icons.Outlined.History else Icons.Outlined.LocationOff,
                null, tint = secondary, modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (enabled) "Прогулок пока нет" else "Запись истории выключена",
                style = MaterialTheme.typography.titleMedium, color = primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (enabled)
                    "Выйди с наушниками на улицу — маршрут, дистанция и шаги появятся здесь. Точки пишутся раз в 10 секунд."
                else
                    "Нажми, чтобы включить. Понадобятся доступы к геолокации и шагомеру — данные остаются на телефоне.",
                style = MaterialTheme.typography.bodyMedium, color = secondary,
            )
        }
    }
}

@Composable
private fun WalkCard(session: WalkSession, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val distance = remember(session.points) { HistoryMath.totalDistance(session.points) }
    val duration = remember(session) { HistoryMath.durationMillis(session) }
    val steps = remember(session) { HistoryMath.steps(session) }
    val normalized = remember(session.points) { HistoryMath.normalize(session.points) }
    val dateLabel = remember(session.startedAtMillis) {
        SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(session.startedAtMillis))
    }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(scheme.surfaceContainer)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(
                        if (session.endedAtMillis == null) "идёт сейчас" else "завершена",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (session.endedAtMillis == null) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
                if (session.endedAtMillis == null) PulsingDot(scheme.primary)
            }

            Spacer(Modifier.height(14.dp))
            RouteMap(normalized, scheme.primary, scheme.surfaceContainerHighest, scheme.secondary)

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell(
                    Icons.Outlined.Straighten, "Путь",
                    HistoryMath.formatDistance(distance), HistoryMath.distanceUnit(distance),
                    Modifier.weight(1f),
                )
                StatCell(
                    Icons.Outlined.Timer, "Время",
                    HistoryMath.formatDuration(duration), null, Modifier.weight(1f),
                )
                StatCell(
                    Icons.Outlined.DirectionsWalk, "Шаги",
                    steps?.toString() ?: "—", null, Modifier.weight(1f),
                )
            }

            if (session.points.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Точек маршрута нет — возможно, не выдан доступ к геолокации",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Маршрут на Canvas: пунктир по реальным точкам, как в согласованном макете.
 * Линия «прорисовывается» пружинной анимацией при появлении.
 */
@Composable
private fun RouteMap(points: List<Pair<Float, Float>>, line: Color, grid: Color, marker: Color) {
    val progress by animateFloatAsState(
        targetValue = if (points.size > 1) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "routeDraw",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(22.dp))
            .background(grid.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (points.size < 2) {
            Text(
                "Маршрут появится, когда наберутся точки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val w = size.width
            val h = size.height
            // сетка
            val step = h / 4f
            for (i in 1..3) {
                drawLine(grid, Offset(0f, step * i), Offset(w, step * i), strokeWidth = 1f)
            }
            val mapped = points.map { (x, y) -> Offset(x * w, y * h) }
            val shown = (mapped.size * progress).toInt().coerceAtLeast(2)
            val path = Path().apply {
                moveTo(mapped[0].x, mapped[0].y)
                for (i in 1 until shown) lineTo(mapped[i].x, mapped[i].y)
            }
            drawPath(
                path = path,
                color = line,
                style = Stroke(
                    width = 5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 14f), 0f),
                ),
            )
            drawCircle(line, radius = 9f, center = mapped.first())
            drawCircle(marker, radius = 11f, center = mapped[shown - 1])
        }
    }
}

@Composable
private fun StatCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        TileLabel(label, scheme.onSurfaceVariant)
        AnimatedContent(
            value,
            transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
            label = "stat",
        ) { shown -> TileValue(shown, unit, scheme.onSurface, scheme.onSurfaceVariant) }
    }
}
