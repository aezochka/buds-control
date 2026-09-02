package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

/** Настройки: профили, тема, плитки, диагностика. */
@Composable
fun SettingsTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val live by vm.live.collectAsState()
    var showTheme by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    val sleepMin by vm.sleepMinutes.collectAsState()
    val limit by vm.volumeLimit.collectAsState()
    if (showEq) { EqualizerSheet(vm) { showEq = false } }
    if (showLang) { LanguageSheet(vm) { showLang = false } }
    if (showSleep) { SleepSheet(vm) { showSleep = false } }
    if (showVolume) { VolumeLimitSheet(vm) { showVolume = false } }
    if (showTheme) {
        ThemeSheet(
            current = settings.accent,
            customAccent = settings.customAccent,
            onPick = { vm.setAccent(it) },
            onCustom = { vm.setCustomAccent(it) },
            onDismiss = { showTheme = false },
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Spacer(Modifier.statusBarsPadding().height(14.dp)) }
        item {
            // Плотный зазор внутри группы: иерархия задаётся расстоянием,
            // а не разделителями.
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRow(
                    Icons.Outlined.GraphicEq, "Эквалайзер",
                    "Системный: полосы, басы, пресеты",
                    onClick = { showEq = true },
                )
                SettingsRow(
                    Icons.Outlined.Bedtime, tr("sleepTimer"),
                    if (sleepMin > 0) "Активен: $sleepMin мин" else "Пауза по времени",
                    onClick = { showSleep = true },
                )
                SettingsRow(
                    Icons.Outlined.VolumeUp, tr("volumeLimit"),
                    if (limit > 0) "Не выше $limit%" else "Защита слуха",
                    onClick = { showVolume = true },
                )
                SettingsToggle(
                    icon = Icons.Outlined.Vibration,
                    title = tr("haptics"),
                    subtitle = tr("hapticsHint"),
                    checked = settings.hapticFeedback,
                    onToggle = { vm.tick(); vm.setHaptic(it) },
                )
                SettingsToggle(
                    icon = Icons.Outlined.MusicNote,
                    title = tr("sounds"),
                    subtitle = tr("soundsHint"),
                    checked = settings.soundEffects,
                    onToggle = { vm.setSoundEffects(it) },
                )
                SettingsToggle(
                    icon = Icons.Outlined.Bolt,
                    title = tr("autoConnect"),
                    subtitle = tr("autoConnectHint"),
                    checked = settings.autoConnect,
                    onToggle = { vm.setAutoConnect(it) },
                )
                SettingsToggle(
                    icon = Icons.Outlined.BatteryAlert,
                    title = tr("lowBattery"),
                    subtitle = "Когда наушник ниже 20%",
                    checked = settings.lowBatteryAlert,
                    onToggle = { vm.setLowBatteryAlert(it) },
                )
                // Обновление одной строкой: состояние и прогресс прямо здесь,
                // без отдельной шторки — она выглядела как «скачать заново».
                UpdateRow(vm)
                // Появляется только если краш реально был: пустую строку
                // показывать смысла нет.
                CrashLogRow()
                SettingsRow(
                    Icons.Outlined.Language, tr("language"),
                    tr("langCount"),
                    onClick = { showLang = true },
                )
                SettingsRow(
                    Icons.Outlined.Palette, "Тема",
                    "Акцент: ${dev.aezochka.budscontrol.data.Accent.from(settings.accent).title}",
                    onClick = { showTheme = true },
                )
                profiles.forEach { profile ->
                    SettingsRow(
                        Icons.Outlined.DashboardCustomize,
                        profile.displayName,
                        "${profile.vendor} · ${profile.address}" + if (profile.isSelected) " · активный" else "",
                        onClick = { vm.selectProfile(profile.id) },
                    )
                }
                // Автор — в самом низу списка настроек.
                AuthorRow()
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    var base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(scheme.surfaceContainer)
    if (onClick != null) base = base.pressBounce(onClick = onClick)
    Row(
        base.padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(23.dp)) }
        // Заголовок слева, значение справа — как на системном экране
        // «Об устройстве»: так значение читается сразу, без поиска глазами.
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(0.52f),
        )
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (checked) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "rowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .pressBounce { onToggle(!checked) }
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(17.dp))
                .background(if (checked) scheme.onPrimary.copy(alpha = 0.16f) else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (checked) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
        }
        // У переключателя подпись остаётся под заголовком: справа уже стоит
        // сам тумблер, и значение там столкнулось бы с ним.
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (checked) scheme.onPrimary else scheme.onSurface)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = if (checked) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
            )
        }
        AnimatedSwitch(checked)
    }
}

/** Свой переключатель: без системного highlight, с пружинным бегунком. */
@Composable
fun AnimatedSwitch(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        if (checked) scheme.onPrimary.copy(alpha = 0.28f) else scheme.surfaceContainerHighest,
        Motion.effects(), label = "swTrack",
    )
    val offset by animateDpAsState(if (checked) 26.dp else 6.dp, Motion.spatialFast(), label = "swOffset")
    val knobSize by animateDpAsState(if (checked) 22.dp else 16.dp, Motion.spatialFast(), label = "swKnob")
    Box(
        Modifier.size(width = 56.dp, height = 34.dp).clip(CircleShape).background(trackColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = offset)
                .size(knobSize)
                .clip(CircleShape)
                .background(if (checked) scheme.onPrimary else scheme.outline),
        )
    }
}
