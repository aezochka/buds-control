package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.aezochka.budscontrol.device.BluetoothScanner

/**
 * Начальный экран: язык → поиск наушников по Bluetooth → доступы.
 * Профили добавляются только из результатов реального скана.
 */
private const val LAST_STEP = 2

@Composable
fun OnboardingScreen(
    vm: BudsViewModel,
    onRequestBluetooth: () -> Unit,
    onRequestHistory: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var language by remember { mutableStateOf("ru") }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                val active = index == step
                val width by animateDpAsState(if (active) 26.dp else 8.dp, Motion.spatial(), label = "dotW")
                val color by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainerHighest, Motion.effects(), label = "dotC",
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.5.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(Motion.spatial()) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(Motion.spatial()) { -it / 3 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(Motion.spatial()) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(Motion.spatial()) { it / 3 } + fadeOut(tween(180)))
                }
            },
            modifier = Modifier.weight(1f),
            label = "onbStep",
        ) { current ->
            when (current) {
                0 -> LanguageStep(language) { language = it }
                1 -> DevicesStep(vm, onRequestBluetooth)
                else -> PermissionsStep(onRequestHistory)
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step > 0) {
                PillButton("Назад", filled = false, modifier = Modifier.weight(1f)) { step-- }
            }
            val profiles by vm.profiles.collectAsState()
            // Кнопка блокируется только там, где действительно нельзя идти дальше:
            // на шаге выбора наушников. На «Доступах» она всегда активна.
            val canContinue = when (step) {
                1 -> profiles.isNotEmpty()
                else -> true
            }
            val label = when {
                step == 1 && profiles.isEmpty() -> "Выбери наушники"
                step == LAST_STEP -> "Поехали"
                else -> "Далее"
            }
            PillButton(
                text = label,
                filled = true,
                enabled = canContinue,
                modifier = Modifier.weight(1f),
            ) {
                if (step == LAST_STEP) vm.finishOnboarding(language) else step = (step + 1).coerceAtMost(LAST_STEP)
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Раньше кнопка выглядела серой, но всё равно нажималась. Теперь вид
    // и поведение совпадают: выключенная не реагирует вообще.
    val container = when {
        !enabled -> scheme.surfaceContainer
        filled -> scheme.primary
        else -> scheme.surfaceContainerHigh
    }
    val label = when {
        !enabled -> scheme.outline
        filled -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .pressBounce(scaleDown = 0.96f, enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = label)
    }
}

@Composable
private fun LanguageStep(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text("Язык", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            "Можно сменить позже в настройках",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        listOf("ru" to "Русский", "en" to "English", "uk" to "Українська", "system" to "Как в системе").forEach { (code, title) ->
            val active = code == selected
            val bg by animateColorAsState(
                if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "langBg",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bg)
                    .pressBounce { onSelect(code) }
                    .padding(17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) scheme.onPrimary else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (active) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    null,
                    tint = if (active) scheme.onPrimary else scheme.outline,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun DevicesStep(vm: BudsViewModel, onRequestBluetooth: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val found by vm.found.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val profiles by vm.profiles.collectAsState()

    LaunchedEffect(Unit) {
        if (vm.bluetoothReady()) vm.startScan() else onRequestBluetooth()
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Наушники", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (scanning) "Ищу по Bluetooth…" else "Тапни, чтобы добавить профиль",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        if (found.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surfaceContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Пока ничего не нашлось", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(
                    "Достань наушники из кейса и включи Bluetooth. Сопряжённые устройства появятся сразу.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                PillButton("Искать снова", filled = false, enabled = true) { vm.startScan() }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(found, key = { it.address }) { device ->
                    val added = profiles.any { it.address == device.address }
                    DeviceRow(device, added) { vm.addProfile(device) }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BluetoothScanner.Found, added: Boolean, onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (added) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "devBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .pressBounce(onClick = onAdd)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(18.dp))
                .background(if (added) scheme.onPrimary.copy(alpha = 0.18f) else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Headphones, null,
                tint = if (added) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                device.name ?: device.address,
                style = MaterialTheme.typography.bodyLarge,
                color = if (added) scheme.onPrimary else scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (device.bonded) append("Уже сопряжены с телефоном")
                    else append("Найдено рядом")
                    device.rssi?.let { append(" · сигнал ${if (it > -60) "сильный" else "средний"}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (added) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (added) "Убрать" else "Добавить",
            style = MaterialTheme.typography.labelMedium,
            color = if (added) scheme.onPrimary else scheme.primary,
        )
    }
}

@Composable
private fun PermissionsStep(onRequestHistory: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { onRequestHistory() }
    Column {
        Text("Доступы", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            "Bluetooth обязателен. Остальное нужно только для истории прогулок — можно не давать.",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        listOf(
            Triple(Icons.Outlined.Bluetooth, "Bluetooth", "Канал управления наушниками"),
            Triple(Icons.Outlined.DirectionsWalk, "Шагомер", "Шаги на маршруте"),
            Triple(Icons.Outlined.LocationOn, "Геолокация", "Карта прогулки"),
            Triple(Icons.Outlined.MusicNote, "Уведомления", "Названия треков"),
        ).forEach { (icon, title, subtitle) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
