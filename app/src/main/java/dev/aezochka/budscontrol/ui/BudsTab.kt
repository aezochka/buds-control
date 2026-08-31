package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SpatialAudio
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.data.ProductCatalog

/** Главная вкладка: фото продукта, живой заряд, bento-плитки. */
@Composable
fun BudsTab(vm: BudsViewModel) {
    val live by vm.live.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selected = profiles.firstOrNull { it.isSelected }

    // Ключ только по адресу: раньше эффект перезапускался на каждое изменение
    // live.connected и уходил в бесконечный цикл подключений.
    LaunchedEffect(selected?.address) { if (selected != null) vm.connectSelected() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                ProfileChips(vm)
                Spacer(Modifier.height(10.dp))
                ProductHero(
                    name = selected?.displayName ?: "Наушники не выбраны",
                    left = live.batteryLeft,
                    right = live.batteryRight,
                    connecting = live.connecting,
                    connected = live.connected,
                    inEar = (live.inEarLeft == true) || (live.inEarRight == true),
                    onRefresh = vm::refresh,
                )
            }
        }
        item { BentoGrid(vm) }
        item { BottomSpacer() }
    }
}

@Composable
private fun ProfileChips(vm: BudsViewModel) {
    val profiles by vm.profiles.collectAsState()
    if (profiles.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        profiles.forEach { profile ->
            val active = profile.isSelected
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) scheme.primary else scheme.surfaceContainer)
                    .pressBounce { vm.selectProfile(profile.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Headphones, null,
                    tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    profile.displayName.removePrefix("realme ").removePrefix("OnePlus "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProductHero(
    name: String,
    left: Int?,
    right: Int?,
    connecting: Boolean,
    connected: Boolean,
    inEar: Boolean,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val asset = ProductCatalog.localAsset(name)
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(
        initialValue = 0f, targetValue = -11f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1.05f), contentAlignment = Alignment.Center) {
                if (asset != null) {
                    AsyncImage(
                        model = "file:///android_asset/$asset",
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = (-offset).dp),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Headphones, null,
                        tint = scheme.surfaceContainerHighest,
                        modifier = Modifier.size(150.dp).scale(1f + offset / 260f),
                    )
                }
                if (left != null) BatteryChip(left, "L", Modifier.align(Alignment.CenterStart))
                if (right != null) BatteryChip(right, "R", Modifier.align(Alignment.TopEnd))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnimatedContent(
                    targetState = when {
                        connecting -> "connecting"
                        connected -> "connected"
                        else -> "idle"
                    },
                    transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                    label = "status",
                ) { status ->
                    when (status) {
                        "connecting" -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = scheme.primary)
                        "connected" -> PulsingDot(scheme.primary)
                        else -> PulsingDot(scheme.outline)
                    }
                }
                Text(
                    buildString {
                        append(name)
                        if (connected && inEar) append(" · в ухе")
                        if (connecting) append(" · подключаюсь")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape).pressBounce(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Refresh, "Обновить", tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun BatteryChip(percent: Int, side: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedContent(percent, transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) }, label = "pct") {
            Text("$it%", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        }
        Text(side, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun BentoGrid(vm: BudsViewModel) {
    val live by vm.live.collectAsState()
    val settings by vm.settings.collectAsState()
    val hidden = settings.hiddenTiles
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("eq" !in hidden) {
                var eqIndex by remember { mutableStateOf(0) }
                val eqNames = listOf("Бас", "Ровный", "Верх")
                BentoTile(
                    Modifier.weight(1f), active = true, minHeight = 150.dp,
                    onClick = { eqIndex = (eqIndex + 1) % eqNames.size },
                ) { primary, secondary ->
                    EqualizerBars(active = true, color = primary)
                    Spacer(Modifier.height(6.dp))
                    TileLabel("Эквалайзер", secondary)
                    AnimatedContent(
                        eqNames[eqIndex],
                        transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                        label = "eqName",
                    ) { name -> Text(name, style = MaterialTheme.typography.titleMedium, color = primary) }
                }
            }
            if ("game" !in hidden) BentoTile(
                Modifier.weight(1f),
                active = live.gameMode,
                minHeight = 150.dp,
                onClick = { vm.setGameMode(!live.gameMode) },
            ) { primary, secondary ->
                MorphIcon(active = live.gameMode, icon = Icons.Outlined.SportsEsports, tint = primary)
                Spacer(Modifier.height(6.dp))
                TileLabel("Режим", secondary)
                Text(if (live.gameMode) "Игровой" else "Обычный", style = MaterialTheme.typography.titleMedium, color = primary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("case" !in hidden) BentoTile(Modifier.weight(3f)) { primary, secondary ->
                Icon(Icons.Outlined.Inventory2, null, tint = secondary, modifier = Modifier.size(24.dp))
                TileLabel("Кейс", secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    TileValue(live.batteryCase?.let { "$it" } ?: "—", "%", primary, secondary)
                    if (live.chargingCase) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Bolt, null, tint = scheme.tertiary, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                SmoothBar((live.batteryCase ?: 0) / 100f, scheme.surfaceContainerHighest, scheme.primary)
            }
            if ("find" !in hidden) BentoTile(Modifier.weight(1f), onClick = { vm.findDevice(true) }) { primary, _ ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NotificationsActive, "Найти", tint = primary, modifier = Modifier.size(27.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("firmware" !in hidden) BentoTile(Modifier.weight(1f)) { primary, secondary ->
                Icon(Icons.Outlined.BluetoothConnected, null, tint = secondary, modifier = Modifier.size(23.dp))
                TileLabel("Прошивка", secondary)
                Text(live.firmware ?: "—", style = MaterialTheme.typography.titleMedium, color = primary)
            }
            if ("inear" !in hidden) BentoTile(Modifier.weight(1f)) { primary, secondary ->
                Icon(Icons.Outlined.Hearing, null, tint = secondary, modifier = Modifier.size(23.dp))
                TileLabel("В ухе", secondary)
                Text(
                    when {
                        live.inEarLeft == true && live.inEarRight == true -> "Оба"
                        live.inEarLeft == true -> "Левый"
                        live.inEarRight == true -> "Правый"
                        live.connected -> "Сняты"
                        else -> "—"
                    },
                    style = MaterialTheme.typography.titleMedium, color = primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("sleep" !in hidden) SquareToggle(Icons.Outlined.Bedtime, Modifier.weight(1f))
            if ("volume" !in hidden) SquareToggle(Icons.Outlined.VolumeUp, Modifier.weight(1f))
            if ("spatial" !in hidden) SquareToggle(Icons.Outlined.SpatialAudio, Modifier.weight(1f))
            if ("multipoint" !in hidden) SquareToggle(Icons.Outlined.Devices, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SquareToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(false) }
    BentoTile(modifier, active = active, minHeight = 96.dp, onClick = { active = !active }) { primary, _ ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MorphIcon(active = active, icon = icon, tint = primary)
        }
    }
}

/** Иконка, которая при активации мягко подрастает и доворачивается. */
@Composable
private fun MorphIcon(active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val scale by animateFloatAsState(
        if (active) 1.14f else 1f, Motion.spatial(), label = "iconScale",
    )
    val rotation by animateFloatAsState(
        if (active) -8f else 0f, Motion.spatial(), label = "iconRot",
    )
    Icon(
        icon, null, tint = tint,
        modifier = Modifier
            .size(26.dp)
            .scale(scale)
            .rotate(rotation),
    )
}
