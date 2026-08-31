package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.BudsState

@Composable
fun AboutScreen(state: BudsState, vm: BudsViewModel) {
    Spacer(Modifier.height(8.dp))

    SectionCard(title = "Устройство") {
        InfoRow("Модель", state.deviceName)
        InfoRow("Производитель", state.vendor)
        InfoRow("MAC", state.deviceAddress)
        InfoRow("Прошивка", state.firmware ?: "не сообщила")
        InfoRow("Канал", "Bluetooth Classic SPP (RFCOMM)")
    }

    SectionCard(
        title = "Как определяются функции",
        subtitle = "Почему список у разных наушников разный",
    ) {
        Text(
            "Приложение отправляет по одному запросу на каждую функцию и ждёт ответ. " +
                "Гарнитура, которая функцию не умеет, просто молчит — так и получается, " +
                "что список подстраивается под конкретную модель, даже если её нет в базе.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "Соединение") {
        OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Отключиться")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
