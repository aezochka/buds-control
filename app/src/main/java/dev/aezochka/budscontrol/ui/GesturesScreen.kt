package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.BudsState
import dev.aezochka.budscontrol.device.Support
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

@Composable
fun GesturesScreen(state: BudsState, vm: BudsViewModel) {
    Spacer(Modifier.height(8.dp))

    if (state.caps.touch != Support.SUPPORTED) {
        SectionCard(
            title = "Жесты",
            subtitle = "Настройка недоступна",
        ) {
            Text(
                "Гарнитура не ответила на запрос конфигурации касаний. " +
                    "Управление работает по заводской схеме и не меняется.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Слоты, которые устройство реально перечислило в ответе.
    val slots = state.caps.touchSlots.ifEmpty {
        // если ответ пришёл пустым — предлагаем стандартный набор
        setOf(
            TouchSide.BOTH to TouchType.TAP_2,
            TouchSide.BOTH to TouchType.TAP_3,
            TouchSide.BOTH to TouchType.HOLD,
        )
    }.sortedWith(compareBy({ it.first.ordinal }, { it.second.ordinal }))

    val bySide = slots.groupBy { it.first }

    bySide.forEach { (side, sideSlots) ->
        SectionCard(
            title = sideLabel(side),
            subtitle = "Действие применяется сразу после выбора",
        ) {
            sideSlots.forEach { slot ->
                ActionPicker(
                    label = typeLabel(slot.second),
                    current = state.touchConfig[slot] ?: TouchAction.OFF,
                    onPick = { action -> vm.setTouch(slot.first, slot.second, action) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPicker(label: String, current: TouchAction, onPick: (TouchAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = TouchAction.entries.filter { it != TouchAction.VOICE_ASSISTANT }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = actionLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { action ->
                DropdownMenuItem(
                    text = { Text(actionLabel(action)) },
                    onClick = {
                        expanded = false
                        onPick(action)
                    },
                )
            }
        }
    }
}

private fun sideLabel(side: TouchSide) = when (side) {
    TouchSide.LEFT -> "Левый наушник"
    TouchSide.RIGHT -> "Правый наушник"
    TouchSide.BOTH -> "Оба наушника"
}

private fun typeLabel(type: TouchType) = when (type) {
    TouchType.UNK_1 -> "Одно касание"
    TouchType.TAP_2 -> "Двойное касание"
    TouchType.TAP_3 -> "Тройное касание"
    TouchType.HOLD -> "Долгое нажатие"
}

private fun actionLabel(action: TouchAction) = when (action) {
    TouchAction.OFF -> "Ничего"
    TouchAction.PLAY_PAUSE -> "Плей / пауза"
    TouchAction.VOICE_ASSISTANT, TouchAction.VOICE_ASSISTANT_REALME -> "Голосовой помощник"
    TouchAction.PREVIOUS -> "Предыдущий трек"
    TouchAction.NEXT -> "Следующий трек"
    TouchAction.NOISE_CONTROL -> "Переключить шумодав"
    TouchAction.VOLUME_UP -> "Громче"
    TouchAction.VOLUME_DOWN -> "Тише"
    TouchAction.GAME_MODE -> "Игровой режим"
}
