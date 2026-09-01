package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.audio.Feedback
import dev.aezochka.budscontrol.device.LiveState

/**
 * Плитки собраны по ОДНОЙ схеме: иконка 24dp, подпись, значение.
 * Всё выровнено по левому краю на одной сетке — раньше у каждой плитки
 * были свои размеры и выравнивание, поэтому они выглядели вразнобой.
 */
@Composable
fun TileContent(
    key: String,
    vm: BudsViewModel,
    live: LiveState,
    editing: Boolean,
    onEq: () -> Unit,
    onSleep: () -> Unit,
    onVolume: () -> Unit,
) {
    when (key) {
        "eq" -> {
            val eqGains by vm.eqGains.collectAsState()
            val fxOn by vm.fxReady.collectAsState()
            val tuned = eqGains.any { it != 0 }
            StatTile(
                icon = null,
                label = "Эквалайзер",
                value = if (!fxOn) "—" else if (tuned) "Настроен" else "Ровный",
                active = tuned,
                onClick = { vm.tick(); onEq() },
                topContent = { color ->
                    EqBars(bars = eqCurve(eqGains), animated = tuned, color = color)
                },
            )
        }

        "game" -> StatTile(
            icon = Icons.Outlined.SportsEsports,
            label = "Игровой режим",
            value = if (live.gameMode) "Включён" else "Выключен",
            active = live.gameMode,
            onClick = {
                vm.tick(if (live.gameMode) Feedback.Kind.Off else Feedback.Kind.On)
                vm.setGameMode(!live.gameMode)
            },
        )

        "case" -> StatTile(
            icon = Icons.Outlined.Inventory2,
            label = if (live.caseFromMemory) "Кейс · последнее" else "Кейс",
            value = live.batteryCase?.let { "$it%" } ?: "нет данных",
            active = false,
            onClick = null,
            bottomContent = { primary, secondary ->
                SmoothBar(
                    progress = (live.batteryCase ?: 0) / 100f,
                    track = secondary.copy(alpha = 0.22f),
                    fill = primary,
                )
            },
        )

        "sleep" -> {
            val minutes by vm.sleepMinutes.collectAsState()
            val left by vm.sleepLeft.collectAsState()
            StatTile(
                icon = Icons.Outlined.Bedtime,
                label = "Таймер сна",
                value = when {
                    left > 0 -> "%d:%02d".format(left / 60, left % 60)
                    minutes > 0 -> "$minutes мин"
                    else -> "Выключен"
                },
                active = minutes > 0,
                onClick = { vm.tick(); onSleep() },
            )
        }

        "volume" -> {
            val limit by vm.volumeLimit.collectAsState()
            StatTile(
                icon = Icons.Outlined.VolumeUp,
                label = "Лимит громкости",
                value = if (limit > 0) "$limit%" else "Без лимита",
                active = limit > 0,
                onClick = { vm.tick(); onVolume() },
            )
        }

        "find" -> {
            var ringing by remember { mutableStateOf(false) }
            StatTile(
                icon = Icons.Outlined.NotificationsActive,
                label = "Найти наушники",
                value = if (ringing) "Играет сигнал" else "Подать звук",
                active = ringing,
                onClick = {
                    ringing = !ringing
                    vm.tick(if (ringing) Feedback.Kind.On else Feedback.Kind.Off)
                    vm.findDevice(ringing)
                },
            )
        }

        "firmware" -> StatTile(
            icon = Icons.Outlined.Memory,
            label = "Прошивка",
            value = live.firmware ?: "—",
            active = false,
            onClick = null,
        )
    }
}

/**
 * Единый каркас плитки: иконка сверху, подпись, значение.
 * Одинаковые отступы и типографика у всех плиток.
 */
@Composable
private fun StatTile(
    icon: ImageVector?,
    label: String,
    value: String,
    active: Boolean,
    onClick: (() -> Unit)?,
    topContent: (@Composable (Color) -> Unit)? = null,
    bottomContent: (@Composable (Color, Color) -> Unit)? = null,
) {
    BentoTile(
        Modifier.fillMaxWidth(),
        active = active,
        minHeight = 132.dp,
        onClick = onClick,
    ) { primary, secondary ->
        Column(Modifier.fillMaxWidth()) {
            // Верхняя зона фиксированной высоты — иконки всех плиток на одной линии.
            Box(Modifier.height(30.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    topContent != null -> topContent(primary)
                    icon != null -> Icon(icon, null, tint = primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                label = "tileValue",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = primary,
                    maxLines = 1,
                )
            }
            if (bottomContent != null) {
                Spacer(Modifier.height(10.dp))
                bottomContent(primary, secondary)
            }
        }
    }
}

/** Форма полос на плитке — по текущим уровням системного эквалайзера. */
private fun eqCurve(gains: List<Int>): List<Float> {
    if (gains.isEmpty()) return listOf(0.4f, 0.6f, 0.9f, 0.5f, 0.45f)
    val step = (gains.size / 5f).coerceAtLeast(1f)
    return (0 until 5).map { i ->
        val idx = (i * step).toInt().coerceAtMost(gains.lastIndex)
        ((gains[idx] + 12) / 24f).coerceIn(0.18f, 1f)
    }
}
