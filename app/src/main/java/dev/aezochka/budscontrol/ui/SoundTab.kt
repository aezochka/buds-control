package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel

/**
 * Звук: системный эквалайзер (реально влияет на воспроизведение),
 * усиление басов, таймер сна и лимит громкости.
 */
@Composable
fun SoundTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val ready by vm.fxReady.collectAsState()
    val gains by vm.eqGains.collectAsState()
    val sleepMin by vm.sleepMinutes.collectAsState()
    val sleepLeft by vm.sleepLeft.collectAsState()
    val limit by vm.volumeLimit.collectAsState()

    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.attachAudioFx() }

    if (showEq) EqualizerSheet(vm) { showEq = false }
    if (showSleep) SleepSheet(vm) { showSleep = false }
    if (showVolume) VolumeLimitSheet(vm) { showVolume = false }

    LazyColumn(Modifier.fillMaxSize()) {
        // Как на главной: без крупного заголовка-«воды», сразу содержимое.
        item { Spacer(Modifier.statusBarsPadding().height(14.dp)) }

        // Эквалайзер: показываем текущие полосы прямо в списке.
        item {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surfaceContainer)
                    .pressBounce(enabled = ready) { showEq = true }
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = scheme.primary, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Эквалайзер", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        Text(
                            when {
                                !ready -> "Недоступен"
                                gains.any { it != 0 } -> "Настроен вручную"
                                else -> "Ровный"
                            },
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (ready && gains.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    // Мини-превью полос: видно форму кривой.
                    Row(
                        Modifier.fillMaxWidth().height(52.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val (minDb, maxDb) = vm.gainRangeDb()
                        val span = (maxDb - minDb).coerceAtLeast(1)
                        gains.forEach { db ->
                            val fill = ((db - minDb).toFloat() / span).coerceIn(0.06f, 1f)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height((52 * fill).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.primary),
                            )
                        }
                    }
                }
            }
        }

        // Сон и лимит — в одном фоне, отдельно от эквалайзера.
        item { GroupLabel("Тише и по времени") }
        item {
            SoundRow(
                icon = Icons.Outlined.Bedtime,
                title = "Таймер сна",
                value = when {
                    sleepLeft > 0 -> "%d:%02d".format(sleepLeft / 60, sleepLeft % 60)
                    sleepMin > 0 -> "$sleepMin мин"
                    else -> "Выключен"
                },
                active = sleepMin > 0,
            ) { showSleep = true }
        }
        item {
            SoundRow(
                icon = Icons.Outlined.VolumeUp,
                title = "Лимит громкости",
                value = if (limit > 0) "$limit%" else "Без лимита",
                active = limit > 0,
            ) { showVolume = true }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun SoundRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 11.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) scheme.primary else scheme.surfaceContainer)
            .pressBounce(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(
            icon, null,
            tint = if (active) scheme.onPrimary else scheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.fillMaxWidth(0.78f)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge,
                color = if (active) scheme.onPrimary else scheme.onSurface,
            )
            Text(
                value, style = MaterialTheme.typography.bodySmall,
                color = if (active) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
            )
        }
    }
}

/** Подпись группы: разделяет блоки без крупных заголовков. */
@Composable
private fun GroupLabel(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(1.dp).background(scheme.surfaceContainerHighest))
    }
}
