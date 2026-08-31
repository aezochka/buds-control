package dev.aezochka.budscontrol.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.DeviceDatabase

@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(
    vm: BudsViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    error: String?,
) {
    var reloadKey by remember { mutableStateOf(0) }
    val devices = remember(permissionsGranted, reloadKey) {
        if (permissionsGranted) vm.pairedDevices() else emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(error, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (!permissionsGranted) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Нужен доступ к Bluetooth", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Без него не видно сопряжённые наушники и нельзя открыть канал управления.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRequestPermissions) { Text("Разрешить") }
                }
            }
            return@Column
        }

        Text("Сопряжённые наушники", style = MaterialTheme.typography.titleMedium)

        if (devices.isEmpty()) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ничего не нашлось", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Сначала подключи наушники в системных настройках Bluetooth, потом вернись сюда.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { reloadKey++ }) { Text("Проверить снова") }
                }
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { device ->
                val profile = DeviceDatabase.lookup(device.name)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.connect(device) },
                ) {
                    ListItem(
                        headlineContent = { Text(device.name ?: device.address) },
                        supportingContent = {
                            Text(
                                if (profile.vendor == "—") "Профиль определится при подключении"
                                else "${profile.vendor} · ${profile.displayName}",
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Headphones, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Приложение работает с гарнитурами на протоколе OPPO/realme/OnePlus. " +
                "Набор функций определяется опросом самого устройства, а не по списку моделей.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
