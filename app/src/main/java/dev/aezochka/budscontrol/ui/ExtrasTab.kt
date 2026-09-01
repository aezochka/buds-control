package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel

/**
 * Эксклюзив: того, чего нет в realme Link.
 * Всё построено на данных, которые гарнитура реально отдаёт по протоколу.
 */
@Composable
fun ExtrasTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val live by vm.live.collectAsState()
    val settings by vm.settings.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Экстра", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
                Text(
                    "Функции, которых нет в realme Link",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }

        // 1. Уведомление о низком заряде — realme Link этого не делает.
        item {
            ExtraToggle(
                icon = Icons.Outlined.BatteryAlert,
                title = "Предупреждать о низком заряде",
                subtitle = "Уведомление, когда наушник опустится ниже 20%",
                checked = settings.lowBatteryAlert,
                onChange = { vm.setLowBatteryAlert(it) },
            )
        }

        // 2. Асимметрия износа: сравнение левого и правого.
        item {
            val left = live.batteryLeft
            val right = live.batteryRight
            val delta = if (left != null && right != null) kotlin.math.abs(left - right) else null
            ExtraCard(
                icon = Icons.Outlined.Cyclone,
                title = "Износ наушников",
                body = when {
                    delta == null -> "Нужны оба наушника на связи"
                    delta <= 3 -> "Разница $delta% — расходуются ровно"
                    delta <= 10 -> "Разница $delta% — один используется чаще"
                    else -> "Разница $delta% — стоит менять наушники местами"
                },
                accent = delta != null && delta > 10,
            )
        }

        // 3. Оценка остатка по фактическому расходу.
        item {
            ExtraCard(
                icon = Icons.Outlined.Bolt,
                title = "Прогноз работы",
                body = vm.batteryForecast(),
            )
        }

        // 4. Тест каналов: проверить, что играет и левый, и правый.
        item {
            ChannelTestCard(
                onPlayLeft = { vm.playChannelTest(left = true) },
                onPlayRight = { vm.playChannelTest(left = false) },
            )
        }

        // 5. Сырой лог протокола — для разбора, чего нет ни у кого.
        item {
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(scheme.surfaceContainer)
                        .pressBounce { expanded = !expanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Icon(Icons.Outlined.Terminal, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                    Column(Modifier.fillMaxWidth(0.8f)) {
                        Text("Журнал протокола", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                        Text(
                            "${live.log.size} кадров · тап, чтобы раскрыть",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                    exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(scheme.surfaceContainerLow)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (live.log.isEmpty()) {
                            Text(
                                "Пока пусто. Подключи наушники и понажимай функции.",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                        } else {
                            live.log.takeLast(24).reversed().forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { BottomSpacer() }
    }
}

@Composable
private fun ExtraToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer)
            .pressBounce { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.fillMaxWidth(0.74f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        AnimatedSwitch(checked = checked)
    }
}

@Composable
private fun ExtraCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    accent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (accent) scheme.primary else scheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(
            icon, null,
            tint = if (accent) scheme.onPrimary else scheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                title, style = MaterialTheme.typography.bodyLarge,
                color = if (accent) scheme.onPrimary else scheme.onSurface,
            )
            Text(
                body, style = MaterialTheme.typography.bodySmall,
                color = if (accent) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
            )
        }
    }
}

/** Проверка каналов: короткий тон в левый или правый наушник. */
@Composable
private fun ChannelTestCard(onPlayLeft: () -> Unit, onPlayRight: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(Icons.Outlined.Campaign, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Column {
                Text("Проверка каналов", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                Text(
                    "Тон 440 Гц в один наушник",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Левый" to onPlayLeft, "Правый" to onPlayRight).forEach { (label, action) ->
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh)
                        .pressBounce(onClick = action)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
            }
        }
    }
}
