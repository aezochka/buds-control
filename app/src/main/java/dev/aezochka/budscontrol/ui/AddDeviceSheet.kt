package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr

/** Шторка добавления гарнитуры: живой Bluetooth-скан по плюсику. */
@Composable
fun AddDeviceSheet(
    vm: BudsViewModel,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val found by vm.found.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val profiles by vm.profiles.collectAsState()
    LaunchedEffect(Unit) { vm.startAddDevice() }

    ModalBottomSheet(
        onDismissRequest = { vm.stopScan(); onDismiss() },
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 46.dp, height = 5.dp).clip(CircleShape).background(scheme.outline))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth(0.86f)) {
                    Text(tr("addBuds"), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        if (scanning) tr("scanning") else tr("tapToAdd"),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.primary)
            }
            // Каталог моделей переехал в «Ещё»: в шторке поиска он мешал
            // главному действию — выбрать реальные наушники рядом.
            Spacer(Modifier.height(16.dp))

            if (found.isEmpty()) {
                Text(
                    "Пока ничего не нашлось. Достань наушники из кейса и проверь, включён ли Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    // Один адрес может прийти и из сопряжённых, и из скана.
                    items(found.distinctBy { it.address }, key = { it.address }) { device ->
                        val added = profiles.any { it.address == device.address }
                        val bg by animateColorAsState(
                            if (added) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "devBg",
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(bg)
                                .pressBounce { if (added) vm.removeProfileByAddress(device.address) else vm.addProfile(device) }
                                .padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (added) scheme.onPrimary.copy(alpha = 0.18f) else scheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Headphones, null,
                                    tint = if (added) scheme.onPrimary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(Modifier.fillMaxWidth(0.72f)) {
                                Text(
                                    device.name ?: device.address,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (added) scheme.onPrimary else scheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (device.bonded) tr("bonded") else tr("nearby"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (added) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                                )
                            }
                            // Добавленное можно снять тем же тапом — раньше
                            // выбор был необратимым.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (added) tr("remove") else tr("add"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (added) scheme.onPrimary else scheme.primary,
                                )
                                if (added) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Outlined.Close, null,
                                        tint = scheme.onPrimary, modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
