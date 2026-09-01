package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
 * История в той же раскладке, что главный экран: крупная карта сверху,
 * плитки статистики под ней. Никакого «микро-окошка».
 */
@Composable
fun HistoryTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val ordered = remember(sessions) { sessions.sortedByDescending { it.startedAtMillis } }
    var selectedIndex by remember(ordered.size) { mutableStateOf(0) }
    val current = ordered.getOrNull(selectedIndex)

    // Тик раз в секунду: без него длительность на экране не двигалась,
    // пока не перезапустишь приложение.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(current?.id, current?.endedAtMillis) {
        while (current != null && current.endedAtMillis == null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("История", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
                        Text(
                            when {
                                !settings.historyEnabled -> "Запись выключена"
                                current == null -> "Ждём первые точки маршрута"
                                current.endedAtMillis == null -> "Прогулка идёт сейчас"
                                else -> dateLabel(current.startedAtMillis)
                            },
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        )
                    }
                    if (current?.endedAtMillis == null && current != null) PulsingDot(scheme.primary, 10.dp)
                }
            }
        }

        // Крупная карта — аналог фото на главной.
        item {
            Box(Modifier.padding(horizontal = 20.dp)) {
                BigRouteMap(current, settings.historyEnabled) { vm.setHistoryEnabled(true) }
            }
        }

        // Переключатель прогулок чипами, как профили наушников на главной.
        if (ordered.size > 1) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ordered.forEachIndexed { index, session ->
                        val active = index == selectedIndex
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (active) scheme.primary else scheme.surfaceContainer)
                                .pressBounce { selectedIndex = index }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (session.endedAtMillis == null) "сейчас" else shortLabel(session.startedAtMillis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Плитки статистики — bento как на главной.
        item { StatsGrid(current, now) }
        item { BottomSpacer() }
    }
}

@Composable
private fun BigRouteMap(session: WalkSession?, enabled: Boolean, onEnable: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val points = remember(session?.points) { HistoryMath.normalize(session?.points.orEmpty()) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(34.dp))
            .background(scheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        when {
            (session?.points?.size ?: 0) >= 2 -> RouteMapView(session!!.points, Modifier.fillMaxSize())
            else -> Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (enabled) Icons.Outlined.History else Icons.Outlined.LocationOff,
                    null, tint = scheme.surfaceContainerHighest, modifier = Modifier.size(84.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (enabled) "Маршрута пока нет" else "Запись истории выключена",
                    style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (enabled)
                        "Выйди с наушниками на улицу. Точка пишется каждые 10 секунд, маршрут появится здесь сам."
                    else
                        "Нажми, чтобы включить запись. Нужны доступы к геолокации и шагомеру.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = if (enabled) Modifier else Modifier.pressBounce(onClick = onEnable),
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(session: WalkSession?, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val distance = remember(session?.points) { HistoryMath.totalDistance(session?.points.orEmpty()) }
    val duration = remember(session, now) { session?.let { HistoryMath.durationMillis(it, now) } ?: 0L }
    val steps = remember(session) { session?.let { HistoryMath.steps(it) } }

    Column(
        Modifier.padding(horizontal = 20.dp).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            BentoTile(Modifier.weight(1f), minHeight = 132.dp) { primary, secondary ->
                Icon(Icons.Outlined.Straighten, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                TileLabel("Путь", secondary)
                AnimatedContent(
                    HistoryMath.formatDistance(distance),
                    transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                    label = "dist",
                ) { v -> TileValue(v, HistoryMath.distanceUnit(distance), primary, secondary) }
            }
            BentoTile(Modifier.weight(1f), minHeight = 132.dp) { primary, secondary ->
                Icon(Icons.Outlined.Timer, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                TileLabel("Время", secondary)
                Text(
                    if (session == null) "—" else HistoryMath.formatDuration(duration),
                    style = MaterialTheme.typography.headlineSmall, color = primary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            BentoTile(Modifier.weight(1f), minHeight = 120.dp) { primary, secondary ->
                Icon(Icons.Outlined.DirectionsWalk, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                TileLabel("Шаги", secondary)
                TileValue(steps?.toString() ?: "—", null, primary, secondary)
            }
            BentoTile(Modifier.weight(1f), minHeight = 120.dp) { primary, secondary ->
                Icon(Icons.Outlined.Bolt, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                TileLabel("Точек GPS", secondary)
                TileValue(session?.points?.size?.toString() ?: "0", null, primary, secondary)
            }
        }
        if (session != null && session.points.isEmpty()) {
            Text(
                "Точек нет — проверь, выдан ли доступ к геолокации в настройках Android",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun dateLabel(millis: Long): String =
    SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(millis))

private fun shortLabel(millis: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale("ru")).format(Date(millis))
