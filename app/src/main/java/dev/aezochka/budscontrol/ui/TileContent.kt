package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Devices
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.LiveState

/** Содержимое конкретной плитки по её ключу. */
@Composable
fun BoxScope.TileContent(
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
        "eq" -> BentoTile(
            Modifier.fillMaxWidth(), active = live.eqPreset != null, minHeight = 150.dp,
            onClick = if (editing) null else onEq,
        ) { primary, secondary ->
            EqBars(
                bars = live.eqPreset?.bars ?: listOf(0.4f, 0.6f, 0.9f, 0.5f, 0.45f),
                animated = live.eqPreset != null, color = primary,
            )
            Spacer(Modifier.height(6.dp))
            TileLabel("Эквалайзер", secondary)
            AnimatedContent(
                live.eqPreset?.title ?: "—",
                transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                label = "eqName",
            ) { name -> Text(name, style = MaterialTheme.typography.titleMedium, color = primary) }
        }

        "game" -> BentoTile(
            Modifier.fillMaxWidth(), active = live.gameMode, minHeight = 150.dp,
            onClick = if (editing) null else ({ vm.setGameMode(!live.gameMode) }),
        ) { primary, secondary ->
            MorphIcon(active = live.gameMode, icon = Icons.Outlined.SportsEsports, tint = primary)
            Spacer(Modifier.height(6.dp))
            TileLabel("Игровой режим", secondary)
            Text(
                if (live.gameMode) "Включён" else "Выключен",
                style = MaterialTheme.typography.titleMedium, color = primary,
            )
        }

        "case" -> BentoTile(Modifier.fillMaxWidth(), onClick = null) { primary, secondary ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null, tint = secondary, modifier = Modifier.size(23.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.fillMaxWidth(0.62f)) {
                    TileLabel(if (live.caseFromMemory) "Кейс · последнее" else "Кейс", secondary)
                    Text(
                        live.batteryCase?.let { "$it%" } ?: "нет данных",
                        style = MaterialTheme.typography.titleMedium, color = primary,
                    )
                }
                if (live.batteryCase != null) {
                    Box(Modifier.fillMaxWidth()) {
                        SmoothBar(
                            progress = live.batteryCase / 100f,
                            track = secondary.copy(alpha = 0.25f),
                            fill = primary,
                        )
                    }
                }
            }
        }

        "find" -> {
            var ringing by remember { mutableStateOf(false) }
            BentoTile(
                Modifier.fillMaxWidth(), active = ringing,
                onClick = if (editing) null else ({ ringing = !ringing; vm.findDevice(ringing) }),
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

        "firmware" -> BentoTile(Modifier.fillMaxWidth(), onClick = null) { primary, secondary ->
            Icon(Icons.Outlined.Memory, null, tint = secondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            TileLabel("Прошивка", secondary)
            Text(live.firmware ?: "—", style = MaterialTheme.typography.titleSmall, color = primary)
        }

        "sleep" -> ActionSquare(
            Icons.Outlined.Bedtime, "Сон",
            active = vm.sleepTimerLabel() != "Выключить",
            modifier = Modifier.fillMaxWidth(),
            onClick = if (editing) ({}) else onSleep,
        )

        "volume" -> ActionSquare(
            Icons.Outlined.VolumeUp, "Лимит",
            active = vm.volumeLimitLabel() != "Без лимита",
            modifier = Modifier.fillMaxWidth(),
            onClick = if (editing) ({}) else onVolume,
        )

        "spatial" -> ActionSquare(
            Icons.Outlined.SpatialAudio, "3D",
            active = live.spatialAudio,
            supported = "spatial" in live.supported,
            modifier = Modifier.fillMaxWidth(),
            onClick = if (editing) ({}) else ({ vm.setSpatialAudio(!live.spatialAudio) }),
        )

        "multipoint" -> ActionSquare(
            Icons.Outlined.Devices, "2 устр.",
            active = live.multipoint,
            supported = "multipoint" in live.supported,
            modifier = Modifier.fillMaxWidth(),
            onClick = if (editing) ({}) else ({ vm.setMultipoint(!live.multipoint) }),
        )
    }
}
