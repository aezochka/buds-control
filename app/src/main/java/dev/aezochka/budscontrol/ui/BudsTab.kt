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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DashboardCustomize
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
import androidx.compose.ui.graphics.graphicsLayer
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

    var showEq by remember { mutableStateOf(false) }
    var showAddDevice by remember { mutableStateOf(false) }
    var editTiles by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }

    if (showAddDevice) {
        AddDeviceSheet(vm) { showAddDevice = false }
    }
    if (showEq) {
        EqualizerSheet(
            current = live.eqPreset,
            gains = live.eqGains,
            connected = live.connected,
            supported = "eq" in live.supported,
            onPick = { vm.setEqualizer(it) },
            onGains = { vm.setEqualizerGains(it) },
            onDismiss = { showEq = false },
        )
    }
    if (showSleep) {
        OptionSheet(
            title = "Таймер сна",
            subtitle = "Наушники выключатся сами",
            options = listOf("15 минут", "30 минут", "60 минут", "Выключить"),
            selected = vm.sleepTimerLabel(),
            onPick = { vm.setSleepTimer(it); showSleep = false },
            onDismiss = { showSleep = false },
        )
    }
    if (showVolume) {
        OptionSheet(
            title = "Лимит громкости",
            subtitle = "Защита слуха",
            options = listOf("75 дБ", "85 дБ", "95 дБ", "Без лимита"),
            selected = vm.volumeLimitLabel(),
            onPick = { vm.setVolumeLimit(it); showVolume = false },
            onDismiss = { showVolume = false },
        )
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                ProfileRow(
                    profiles = profiles,
                    onSelect = { vm.selectProfile(it) },
                    onReorder = { vm.reorderProfiles(it) },
                    onAdd = { showAddDevice = true },
                )
                Spacer(Modifier.height(10.dp))
                ProductHero(
                    name = selected?.displayName ?: "Наушники не выбраны",
                    left = live.batteryLeft,
                    right = live.batteryRight,
                    // Раньше свечение зависело только от chargingCase, который
                    // приходит лишь при открытом кейсе — поэтому его не было видно.
                    charging = live.chargingCase || live.budInCaseLeft || live.budInCaseRight,
                    inCaseLeft = live.budInCaseLeft,
                    inCaseRight = live.budInCaseRight,
                    connecting = live.connecting,
                    connected = live.connected,
                    onRefresh = vm::refresh,
                )
            }
        }
        item {
            EditBar(
                editing = editTiles,
                onToggle = { editTiles = !editTiles },
            )
        }
        item {
            BentoGrid(
                vm = vm,
                editing = editTiles,
                onEq = { showEq = true },
                onSleep = { showSleep = true },
                onVolume = { showVolume = true },
            )
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun ProductHero(
    name: String,
    left: Int?,
    right: Int?,
    charging: Boolean,
    inCaseLeft: Boolean,
    inCaseRight: Boolean,
    connecting: Boolean,
    connected: Boolean,
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

    // Пульсирующее свечение, когда кейс на зарядке.
    val glow by transition.animateFloat(
        initialValue = 0.25f, targetValue = if (charging) 1f else 0.25f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "chargeGlow",
    )

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1.05f), contentAlignment = Alignment.Center) {
                if (charging) {
                    // Два круга: внешний дышит сильнее, внутренний мягче.
                    Box(
                        Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = glow * 0.16f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = glow * 0.22f)),
                    )
                }
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
                BatteryChip(left, "L", inCase = inCaseLeft, modifier = Modifier.align(Alignment.CenterStart))
                BatteryChip(right, "R", inCase = inCaseRight, modifier = Modifier.align(Alignment.TopEnd))
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
private fun BatteryChip(percent: Int?, side: String, inCase: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (percent == null && !inCase) return
    Row(
        modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedContent(
            percent?.let { "$it%" } ?: "в кейсе",
            transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
            label = "pct",
        ) { label ->
            Text(
                label,
                style = if (percent != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = if (percent != null) scheme.onSurface else scheme.onSurfaceVariant,
            )
        }
        Text(side, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun BentoGrid(
    vm: BudsViewModel,
    editing: Boolean,
    onEq: () -> Unit,
    onSleep: () -> Unit,
    onVolume: () -> Unit,
) {
    val live by vm.live.collectAsState()
    val settings by vm.settings.collectAsState()
    val hidden = settings.hiddenTiles
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("eq" !in hidden) {
                BentoTile(
                    Modifier.weight(spanWeight(settings, "eq")).wobble(editing),
                    active = live.eqPreset != null, minHeight = 150.dp,
                    onClick = if (editing) { { vm.cycleTileSpan("eq"); Unit } } else onEq,
                ) { primary, secondary ->
                    EqBars(
                        bars = live.eqPreset?.bars ?: listOf(0.4f, 0.6f, 0.9f, 0.5f, 0.45f),
                        animated = live.eqPreset != null,
                        color = primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    TileLabel("Эквалайзер", secondary)
                    AnimatedContent(
                        live.eqPreset?.title ?: "—",
                        transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                        label = "eqName",
                    ) { name -> Text(name, style = MaterialTheme.typography.titleMedium, color = primary) }
                }
            }
            if ("game" !in hidden) BentoTile(
                Modifier.weight(spanWeight(settings, "game")).wobble(editing),
                active = live.gameMode,
                minHeight = 150.dp,
                onClick = if (editing) { { vm.cycleTileSpan("game"); Unit } } else { { vm.setGameMode(!live.gameMode) } },
            ) { primary, secondary ->
                MorphIcon(active = live.gameMode, icon = Icons.Outlined.SportsEsports, tint = primary)
                Spacer(Modifier.height(6.dp))
                TileLabel("Режим", secondary)
                Text(if (live.gameMode) "Игровой" else "Обычный", style = MaterialTheme.typography.titleMedium, color = primary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("case" !in hidden) BentoTile(
                Modifier.weight(spanWeight(settings, "case", 3f)).wobble(editing),
                onClick = if (editing) { { vm.cycleTileSpan("case"); Unit } } else null,
            ) { primary, secondary ->
                Icon(Icons.Outlined.Inventory2, null, tint = secondary, modifier = Modifier.size(24.dp))
                TileLabel(
                    if (live.caseFromMemory) "Кейс · последнее" else "Кейс",
                    secondary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    TileValue(live.batteryCase?.let { "$it" } ?: "…", if (live.batteryCase != null) "%" else null, primary, secondary)
                    if (live.chargingCase) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Bolt, null, tint = scheme.tertiary, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                SmoothBar((live.batteryCase ?: 0) / 100f, scheme.surfaceContainerHighest, scheme.primary)
            }
            if ("find" !in hidden) {
                var ringing by remember { mutableStateOf(false) }
                BentoTile(
                    Modifier.weight(spanWeight(settings, "find")).wobble(editing),
                    active = ringing,
                    onClick = if (editing) {
                        { vm.cycleTileSpan("find"); Unit }
                    } else {
                        { ringing = !ringing; vm.findDevice(ringing) }
                    },
                ) { primary, secondary ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MorphIcon(active = ringing, icon = Icons.Outlined.NotificationsActive, tint = primary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (ringing) "Стоп" else "Найти",
                                style = MaterialTheme.typography.labelMedium,
                                color = secondary,
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("firmware" !in hidden) BentoTile(
                Modifier.weight(spanWeight(settings, "firmware")).wobble(editing),
                onClick = if (editing) { { vm.cycleTileSpan("firmware"); Unit } } else null,
            ) { primary, secondary ->
                Icon(Icons.Outlined.BluetoothConnected, null, tint = secondary, modifier = Modifier.size(23.dp))
                TileLabel("Прошивка", secondary)
                Text(live.firmware ?: "—", style = MaterialTheme.typography.titleMedium, color = primary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if ("sleep" !in hidden) ActionSquare(
                Icons.Outlined.Bedtime, "Сон",
                active = vm.sleepTimerLabel() != "Выключить",
                modifier = Modifier.weight(1f),
            ) { onSleep() }
            if ("volume" !in hidden) ActionSquare(
                Icons.Outlined.VolumeUp, "Лимит",
                active = vm.volumeLimitLabel() != "Без лимита",
                modifier = Modifier.weight(1f),
            ) { onVolume() }
            if ("spatial" !in hidden) ActionSquare(
                Icons.Outlined.SpatialAudio, "3D",
                active = live.spatialAudio,
                supported = "spatial" in live.supported,
                modifier = Modifier.weight(1f),
            ) { vm.setSpatialAudio(!live.spatialAudio) }
            if ("multipoint" !in hidden) ActionSquare(
                Icons.Outlined.Devices, "2 устр.",
                active = live.multipoint,
                supported = "multipoint" in live.supported,
                modifier = Modifier.weight(1f),
            ) { vm.setMultipoint(!live.multipoint) }
        }
    }
}

@Composable
private fun ActionSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    supported: Boolean = true,
    onClick: () -> Unit,
) {
    BentoTile(
        modifier, active = active, minHeight = 104.dp,
        onClick = if (supported) onClick else null,
    ) { primary, secondary ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MorphIcon(active = active, icon = icon, tint = if (supported) primary else secondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (supported) label else "нет",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                )
            }
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


/** Полоска режима правки плиток: включается на самом главном экране. */
@Composable
private fun EditBar(editing: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = editing,
            transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
            label = "editHint",
        ) { on ->
            Text(
                if (on) "Тапни плитку, чтобы изменить размер" else "Плитки",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (editing) scheme.primary else scheme.surfaceContainer)
                .pressBounce(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (editing) Icons.Outlined.Check else Icons.Outlined.DashboardCustomize,
                null,
                tint = if (editing) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Text(
                if (editing) "Готово" else "Настроить",
                style = MaterialTheme.typography.labelMedium,
                color = if (editing) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
    }
}

/** Покачивание плитки в режиме правки — как в макете. */
@Composable
private fun Modifier.wobble(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "tileWobble")
    val angle by transition.animateFloat(
        initialValue = -0.9f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobbleAngle",
    )
    return this.graphicsLayer { rotationZ = angle }
}

/** Вес плитки в ряду: 1..4 колонки, сохраняется в настройках. */
private fun spanWeight(settings: dev.aezochka.budscontrol.data.UserSettings, key: String, default: Float = 1f): Float =
    (settings.tileSpans[key] ?: default.toInt()).coerceIn(1, 4).toFloat()
