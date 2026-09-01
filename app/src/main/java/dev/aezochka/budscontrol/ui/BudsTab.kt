package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.outlined.DashboardCustomize
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
    // Само определяет модель и ищет фото, если её нет в каталоге.
    LaunchedEffect(selected?.displayName) {
        selected?.displayName?.let { vm.ensurePhoto(it) }
    }
    val foundPhoto by vm.foundPhoto.collectAsState()

    var showEq by remember { mutableStateOf(false) }
    var showAddDevice by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }

    if (showAddDevice) {
        AddDeviceSheet(vm) { showAddDevice = false }
    }
    if (showEq) {
        EqualizerSheet(vm) { showEq = false }
    }
    if (showSleep) {
        SleepSheet(vm) { showSleep = false }
    }
    if (showVolume) {
        VolumeLimitSheet(vm) { showVolume = false }
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
                    foundPhoto = foundPhoto,
                    connecting = live.connecting,
                    connected = live.connected,
                    onRefresh = vm::refresh,
                )
            }
        }
        item {
            BentoGrid(
                vm = vm,
                editing = false,
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
    foundPhoto: String?,
    connecting: Boolean,
    connected: Boolean,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(
        initialValue = 0f, targetValue = -11f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )

    // Наушники в кейсе физически отключаются от телефона, поэтому
    // отсутствие связи == кейс закрыт. Раньше это состояние не учитывалось.
    val bothInCase = !connected || (inCaseLeft && inCaseRight)
    val oneOut = connected && (inCaseLeft != inCaseRight)
    val restScale by animateFloatAsState(
        targetValue = when {
            bothInCase -> 0.8f
            oneOut -> 1.06f
            else -> 1f
        },
        animationSpec = Motion.spatial(),
        label = "restScale",
    )
    val restLift by animateFloatAsState(
        targetValue = when {
            bothInCase -> 16f
            oneOut -> -14f
            else -> 0f
        },
        animationSpec = Motion.spatial(),
        label = "restLift",
    )
    val restTilt by animateFloatAsState(
        targetValue = if (oneOut) -4.5f else 0f,
        animationSpec = Motion.spatial(),
        label = "restTilt",
    )
    // Закрытый кейс — приглушённая картинка: видно, что связи нет.
    val restAlpha by animateFloatAsState(
        targetValue = if (bothInCase) 0.55f else 1f,
        animationSpec = Motion.spatial(),
        label = "restAlpha",
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
                // Автоподбор: локальный ассет, иначе CDN вендора по имени
                // устройства, иначе нейтральный силуэт. Никаких ручных правок.
                val photoModel = ProductCatalog.localAsset(name)
                    ?.let { "file:///android_asset/$it" }
                    ?: foundPhoto
                if (photoModel != null) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = offset + restLift
                                scaleX = restScale
                                scaleY = restScale
                                rotationZ = restTilt
                                alpha = restAlpha
                            },
                    )
                } else {
                    Icon(
                        Icons.Outlined.Headphones, null,
                        tint = scheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(150.dp)
                            .graphicsLayer {
                                translationY = offset + restLift
                                scaleX = restScale
                                scaleY = restScale
                                rotationZ = restTilt
                                alpha = restAlpha
                            },
                    )
                }
                BatteryChip(
                    percent = if (connected) left else null,
                    side = "L",
                    inCase = inCaseLeft || !connected,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                BatteryChip(
                    percent = if (connected) right else null,
                    side = "R",
                    inCase = inCaseRight || !connected,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainer)
                            .pressBounce(onClick = onRefresh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Refresh, "Обновить", tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryChip(percent: Int?, side: String, inCase: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (percent == null && !inCase) return
    // В кейсе — приглушённая «пимба»: сразу видно, что наушник не на связи.
    val container by animateColorAsState(
        if (inCase) scheme.surfaceContainerLow else scheme.surfaceContainerHigh,
        Motion.effects(), label = "chipBg",
    )
    val labelColor by animateColorAsState(
        if (inCase) scheme.outline else scheme.onSurface,
        Motion.effects(), label = "chipFg",
    )
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
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
                color = labelColor,
            )
        }
        Text(
            side,
            style = MaterialTheme.typography.labelSmall,
            color = if (inCase) scheme.outlineVariant else scheme.onSurfaceVariant,
        )
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

    // Ровная сетка: две крупные плитки одинаковой высоты сверху,
    // широкая плитка кейса, затем ряд одинаковых квадратов.
    // Раньше размеры плиток скакали и раскладка выглядела рвано.
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.weight(1f)) {
                TileContent("eq", vm, live, editing, onEq, onSleep, onVolume)
            }
            Box(Modifier.weight(1f)) {
                TileContent("game", vm, live, editing, onEq, onSleep, onVolume)
            }
        }

        Box(Modifier.fillMaxWidth()) {
            TileContent("case", vm, live, editing, onEq, onSleep, onVolume)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            listOf("sleep", "volume", "lowlatency").forEach { key ->
                Box(Modifier.weight(1f)) {
                    TileContent(key, vm, live, editing, onEq, onSleep, onVolume)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            listOf("find", "firmware").forEach { key ->
                Box(Modifier.weight(1f)) {
                    TileContent(key, vm, live, editing, onEq, onSleep, onVolume)
                }
            }
        }
    }
}

@Composable
fun ActionSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    supported: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 112.dp,
    onClick: () -> Unit,
) {
    BentoTile(
        modifier, active = active, minHeight = minHeight,
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
fun MorphIcon(active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
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
