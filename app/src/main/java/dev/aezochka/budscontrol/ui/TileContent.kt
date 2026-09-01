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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SpatialAudio
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
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.audio.Feedback
import dev.aezochka.budscontrol.device.LiveState

/** Содержимое конкретной плитки по её ключу. */
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
    val scheme = MaterialTheme.colorScheme
    when (key) {
        "eq" -> {
            val levels by vm.musicLevels.collectAsState()
            val fxOn by vm.fxReady.collectAsState()
            val eqGains by vm.eqGains.collectAsState()
            BentoTile(
            Modifier.fillMaxWidth(), active = eqGains.any { it != 0 }, minHeight = 152.dp,
            onClick = if (editing) null else ({ vm.tick(); onEq() }),
        ) { primary, secondary ->
            // Есть звук — полосы идут под музыку, нет — обычная анимация.
            LiveEqBars(
                live = levels,
                fallback = listOf(0.4f, 0.6f, 0.9f, 0.5f, 0.45f),
                color = primary,
            )
            Spacer(Modifier.height(6.dp))
            TileLabel("Эквалайзер", secondary)
            AnimatedContent(
                if (!fxOn) "—" else if (eqGains.any { it != 0 }) "Настроен" else "Ровный",
                transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                label = "eqName",
            ) { name -> Text(name, style = MaterialTheme.typography.titleMedium, color = primary) }
            }
        }

        "game" -> BentoTile(
            Modifier.fillMaxWidth(), active = live.gameMode, minHeight = 152.dp,
            onClick = if (editing) null else ({
                vm.tick(if (live.gameMode) Feedback.Kind.Off else Feedback.Kind.On)
                vm.setGameMode(!live.gameMode)
            }),
        ) { primary, secondary ->
            MorphIcon(active = live.gameMode, icon = Icons.Outlined.SportsEsports, tint = primary)
            Spacer(Modifier.height(6.dp))
            TileLabel("Игровой режим", secondary)
            Text(
                if (live.gameMode) "Включён" else "Выключен",
                style = MaterialTheme.typography.titleMedium, color = primary,
            )
        }

        // Кейс: полоска ВНИЗУ во всю ширину, а не сбоку от текста.
        "case" -> BentoTile(Modifier.fillMaxWidth(), minHeight = 132.dp, onClick = null) { primary, secondary ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null, tint = secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(9.dp))
                TileLabel(if (live.caseFromMemory) "Кейс · последнее" else "Кейс", secondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                live.batteryCase?.let { "$it%" } ?: "нет данных",
                style = MaterialTheme.typography.headlineSmall, color = primary,
            )
            Spacer(Modifier.height(10.dp))
            SmoothBar(
                progress = (live.batteryCase ?: 0) / 100f,
                track = secondary.copy(alpha = 0.22f),
                fill = primary,
            )
        }

        "find" -> {
            var ringing by remember { mutableStateOf(false) }
            BentoTile(
                Modifier.fillMaxWidth(), minHeight = 112.dp, active = ringing,
                onClick = if (editing) null else ({
                    ringing = !ringing
                    vm.tick(if (ringing) Feedback.Kind.On else Feedback.Kind.Off)
                    vm.findDevice(ringing)
                }),
            ) { primary, secondary ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MorphIcon(active = ringing, icon = Icons.Outlined.NotificationsActive, tint = primary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (ringing) "Стоп" else "Найти",
                            style = MaterialTheme.typography.labelMedium, color = secondary,
                        )
                    }
                }
            }
        }

        "firmware" -> BentoTile(Modifier.fillMaxWidth(), minHeight = 112.dp, onClick = null) { primary, secondary ->
            Icon(Icons.Outlined.Memory, null, tint = secondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            TileLabel("Прошивка", secondary)
            Text(live.firmware ?: "—", style = MaterialTheme.typography.titleSmall, color = primary)
        }

        "sleep" -> {
            val minutes by vm.sleepMinutes.collectAsState()
            val left by vm.sleepLeft.collectAsState()
            ActionSquare(
                Icons.Outlined.Bedtime,
                // Таймер виден прямо на плитке.
                if (left > 0) "%d:%02d".format(left / 60, left % 60) else "Сон",
                active = minutes > 0,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 112.dp,
                onClick = { vm.tick(); onSleep() },
            )
        }

        "volume" -> {
            val limit by vm.volumeLimit.collectAsState()
            ActionSquare(
                Icons.Outlined.VolumeUp,
                if (limit > 0) "$limit%" else "Лимит",
                active = limit > 0,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 112.dp,
                onClick = { vm.tick(); onVolume() },
            )
        }

        // 4-я плитка: игровой режим низкой задержки по протоколу.
        "lowlatency" -> ActionSquare(
            Icons.Outlined.Bolt,
            "Задержка",
            active = live.gameMode,
            supported = live.connected,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 112.dp,
            onClick = {
                vm.tick(if (live.gameMode) Feedback.Kind.Off else Feedback.Kind.On)
                vm.setGameMode(!live.gameMode)
            },
        )
    }
}
