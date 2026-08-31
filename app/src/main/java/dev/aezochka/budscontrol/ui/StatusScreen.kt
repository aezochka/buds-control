package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.HearingDisabled
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.BudsState
import dev.aezochka.budscontrol.device.Support
import dev.aezochka.budscontrol.proto.AncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(state: BudsState, vm: BudsViewModel) {
    Spacer(Modifier.height(8.dp))

    // --- Заряд ---
    if (state.caps.battery.yes) {
        SectionCard(
            title = "Заряд",
            subtitle = "Устройство рапортует ${state.caps.batteryCount} ${cells(state.caps.batteryCount)}",
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BatteryCell("Левый", state.batteryLeft, state.chargingLeft, Modifier.weight(1f))
                BatteryCell("Правый", state.batteryRight, state.chargingRight, Modifier.weight(1f))
                if (state.batteryCase != null) {
                    BatteryCell("Кейс", state.batteryCase, state.chargingCase, Modifier.weight(1f))
                }
            }
        }
    }

    // --- Шумоподавление ---
    if (state.caps.anc.yes) {
        SectionCard(
            title = "Шумоподавление",
            subtitle = "Режим применяется сразу",
        ) {
            val modes = state.caps.ancModes.ifEmpty {
                setOf(AncMode.OFF, AncMode.ON, AncMode.TRANSPARENCY)
            }.sortedBy { it.code }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.ancMode == mode,
                        onClick = { vm.setAnc(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {},
                        label = { Text(ancLabel(mode)) },
                    )
                }
            }
        }
    } else if (state.caps.anc == Support.UNSUPPORTED) {
        SectionCard(
            title = "Шумоподавление",
            subtitle = "Эти наушники его не поддерживают",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.HearingDisabled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Гарнитура не ответила на запрос ANC. Есть только шумоподавление " +
                        "микрофона в звонках (ENC) — оно работает всегда и не настраивается.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // --- Найти наушники ---
    if (state.caps.findDevice.yes) {
        SectionCard(title = "Найти наушники", subtitle = "Наушники начнут пищать") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { vm.findDevice(true) }) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Пищать")
                }
                OutlinedButton(onClick = { vm.findDevice(false) }) { Text("Стоп") }
            }
        }
    }

    // --- Итог опроса ---
    SectionCard(
        title = "Что умеет эта гарнитура",
        subtitle = "Определено живым опросом устройства",
    ) {
        CapabilityLine("Заряд", state.caps.battery)
        CapabilityLine("Прошивка", state.caps.firmware)
        CapabilityLine("Активное шумоподавление", state.caps.anc)
        CapabilityLine("Игровой режим", state.caps.gameMode)
        CapabilityLine("Два устройства сразу", state.caps.multipoint)
        CapabilityLine("LDAC", state.caps.ldac)
        CapabilityLine("Настройка жестов", state.caps.touch)
        CapabilityLine("Поиск наушников", state.caps.findDevice)
    }
}

private fun cells(n: Int) = when {
    n == 1 -> "ячейку"
    n in 2..4 -> "ячейки"
    else -> "ячеек"
}

private fun ancLabel(mode: AncMode) = when (mode) {
    AncMode.OFF -> "Выкл"
    AncMode.ON -> "Шумодав"
    AncMode.TRANSPARENCY -> "Прозрачность"
}

@Composable
private fun BatteryCell(label: String, level: Int?, charging: Boolean, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (level != null) "$level%" else "—",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (charging) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = "Заряжается",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (level != null) {
                LinearProgressIndicator(
                    progress = { level / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CapabilityLine(name: String, support: Support) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            when (support) {
                Support.SUPPORTED -> "есть"
                Support.UNSUPPORTED -> "нет"
                Support.EXPECTED -> "проверяю"
                Support.UNKNOWN -> "—"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (support) {
                Support.SUPPORTED -> MaterialTheme.colorScheme.primary
                Support.UNSUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
