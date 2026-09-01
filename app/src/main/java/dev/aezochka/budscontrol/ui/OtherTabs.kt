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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
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
    var showTiles by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    if (showTiles) {
        CustomizeTilesSheet(
            settings = settings,
            onReorder = vm::setTileOrderList,
            onToggle = vm::toggleTile,
            onDismiss = { showTiles = false },
        )
    }
    if (showTheme) {
        ThemeSheet(
            current = settings.accent,
            onPick = { vm.setAccent(it) },
            onDismiss = { showTheme = false },
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(20.dp)) {
                Text("Настройки", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    live.firmware?.let { "Прошивка $it" } ?: "Прошивка неизвестна",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SettingsRow(
                    Icons.Outlined.DashboardCustomize, "Настроить плитки",
                    "Порядок и что показывать",
                    onClick = { showTiles = true },
                )
                SettingsRow(
                    Icons.Outlined.Terminal, "Результат опроса",
                    if (live.supported.isEmpty()) "Гарнитура не отвечала" else "Подтверждено: ${live.supported.size}",
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
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
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
private fun AnimatedSwitch(checked: Boolean) {
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
