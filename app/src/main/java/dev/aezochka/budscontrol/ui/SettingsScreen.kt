package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.BudsState
import dev.aezochka.budscontrol.device.Support
import dev.aezochka.budscontrol.proto.AncMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: BudsState, vm: BudsViewModel) {
    Spacer(Modifier.height(8.dp))

    val hasAnyToggle = state.caps.gameMode.yes || state.caps.multipoint.yes || state.caps.ldac.yes

    if (hasAnyToggle) {
        SectionCard(title = "Режимы", subtitle = "Только то, что подтвердила гарнитура") {
            supportGate(state.caps.gameMode) {
                SupportSwitchRow(
                    title = "Игровой режим",
                    description = "Задержка ниже, звук чуть проще",
                    checked = state.gameMode,
                    onCheckedChange = vm::setGameMode,
                )
            }
            supportGate(state.caps.multipoint) {
                SupportSwitchRow(
                    title = "Два устройства сразу",
                    description = "Держать связь с телефоном и ноутом одновременно",
                    checked = state.multipoint,
                    onCheckedChange = vm::setMultipoint,
                )
            }
            supportGate(state.caps.ldac) {
                SupportSwitchRow(
                    title = "LDAC",
                    description = "Кодек высокого битрейта, ест больше батареи",
                    checked = state.ldac,
                    onCheckedChange = vm::setLdac,
                )
            }
        }
    }

    // Набор режимов для перебора касанием
    if (state.caps.ancTouchCycle.yes) {
        SectionCard(
            title = "Перебор шумодава касанием",
            subtitle = "Выбери минимум два режима, между которыми переключаться",
        ) {
            val available = state.caps.ancModes.ifEmpty { AncMode.entries.toSet() }.sortedBy { it.code }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { mode ->
                    val selected = mode in state.ancCycleModes
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = if (selected) state.ancCycleModes - mode else state.ancCycleModes + mode
                            if (next.size >= 2) vm.setAncCycle(next)
                        },
                        label = { Text(label(mode)) },
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }
            if (state.ancCycleModes.size < 2) {
                Text(
                    "Нужно хотя бы два режима, иначе переключать нечего.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (!hasAnyToggle && !state.caps.ancTouchCycle.yes) {
        SectionCard(title = "Настройки", subtitle = "Пусто — и это не баг") {
            Text(
                "Опрос не подтвердил ни одного настраиваемого режима. " +
                    "У простых моделей вроде realme Buds T110 из управляемого — только жесты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun label(mode: AncMode) = when (mode) {
    AncMode.OFF -> "Выкл"
    AncMode.ON -> "Шумодав"
    AncMode.TRANSPARENCY -> "Прозрачность"
}
