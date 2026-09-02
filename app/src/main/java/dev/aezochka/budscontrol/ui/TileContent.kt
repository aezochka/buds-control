package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import dev.aezochka.budscontrol.audio.Feedback
import dev.aezochka.budscontrol.device.LiveState
import dev.aezochka.budscontrol.proto.AncMode

/**
 * Плитки собраны по ОДНОЙ схеме: иконка 24dp, подпись, значение.
 * Всё выровнено по левому краю на одной сетке — раньше у каждой плитки
 * были свои размеры и выравнивание, поэтому они выглядели вразнобой.
 */
@Composable
fun TileContent(
    key: String,
    vm: BudsViewModel,
    live: LiveState,
    editing: Boolean,
    onEq: () -> Unit,
    onSleep: () -> Unit,
    onVolume: () -> Unit,
    compact: Boolean = false,
) {
    when (key) {
        "eq" -> {
            val eqGains by vm.eqGains.collectAsState()
            val fxOn by vm.fxReady.collectAsState()
            val tuned = eqGains.any { it != 0 }
            StatTile(
                icon = null,
                label = tr("equalizerTitle"),
                value = if (!fxOn) "—" else if (tuned) tr("eqTuned") else tr("eqFlat"),
                active = tuned,
                onClick = { vm.tick(); onEq() },
                topContent = { color ->
                    EqBars(bars = eqCurve(eqGains), animated = tuned, color = color)
                },
            )
        }

        // Шумодав: команда в протоколе была давно, но кнопки не существовало.
        // Тап гоняет режимы по кругу, как на самих наушниках.
        "anc" -> StatTile(
            icon = Icons.Outlined.NoiseAware,
            label = tr("noiseControl"),
            value = when (live.ancMode) {
                AncMode.ON -> tr("ancOn")
                AncMode.TRANSPARENCY -> tr("ancTransparency")
                AncMode.OFF -> tr("off")
                null -> if (live.connected) tr("requesting") else tr("noLink")
            },
            active = live.ancMode == AncMode.ON || live.ancMode == AncMode.TRANSPARENCY,
            onClick = {
                vm.tick(if (live.ancMode == AncMode.OFF) Feedback.Kind.On else Feedback.Kind.Off)
                vm.cycleAnc()
            },
        )

        "game" -> StatTile(
            icon = Icons.Outlined.SportsEsports,
            label = tr("actGame"),
            value = if (live.gameMode) tr("on") else tr("off"),
            active = live.gameMode,
            onClick = {
                vm.tick(if (live.gameMode) Feedback.Kind.Off else Feedback.Kind.On)
                vm.setGameMode(!live.gameMode)
            },
        )

        // AirPods/Beats: заряд из BLE-рекламы. Плитка появляется сама, когда
        // такие наушники рядом, и не мешает основной гарнитуре.
        "airpods" -> {
            val pods by vm.applePods.collectAsState()
            val p = pods
            StatTile(
                icon = Icons.Outlined.Hearing,
                label = p?.status?.model ?: "AirPods",
                value = if (p == null) tr("searching") else buildString {
                    append(p.status.leftBattery?.let { "L $it%" } ?: "L —")
                    append("  ")
                    append(p.status.rightBattery?.let { "R $it%" } ?: "R —")
                },
                active = p != null,
                onClick = null,
                bottomContent = { primary, secondary ->
                    val case = p?.status?.caseBattery
                    Text(
                        if (case != null) "${tr("caseTitle")} $case%" else tr("readOnly"),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondary,
                        maxLines = 1,
                    )
                },
            )
        }

        // Наушник в ухе — датчик носки. Есть и у AirPods, и у моделей с
        // определением посадки.
        "inear" -> {
            val pods by vm.applePods.collectAsState()
            val s = pods?.status
            StatTile(
                icon = Icons.Outlined.Hearing,
                label = tr("wearDetect"),
                value = when {
                    s == null -> tr("noData")
                    s.leftInEar && s.rightInEar -> tr("bothInEar")
                    s.leftInEar -> tr("leftBud")
                    s.rightInEar -> tr("rightBud")
                    else -> tr("notWorn")
                },
                active = s?.let { it.leftInEar || it.rightInEar } == true,
                onClick = null,
            )
        }

        "lid" -> {
            val pods by vm.applePods.collectAsState()
            val s = pods?.status
            StatTile(
                icon = Icons.Outlined.Inventory2,
                label = tr("caseLid"),
                value = when {
                    s == null -> tr("noData")
                    s.lidOpen -> tr("lidOpen")
                    else -> tr("lidClosed")
                },
                active = s?.lidOpen == true,
                onClick = null,
            )
        }

        "spatial" -> StatTile(
            icon = Icons.Outlined.SurroundSound,
            label = tr("spatialAudio"),
            value = if (live.spatialAudio) tr("on") else tr("off"),
            active = live.spatialAudio,
            onClick = {
                vm.tick(if (live.spatialAudio) Feedback.Kind.Off else Feedback.Kind.On)
                vm.setSpatialAudio(!live.spatialAudio)
            },
        )

        "multipoint" -> StatTile(
            icon = Icons.Outlined.Devices,
            label = tr("multipoint"),
            value = if (live.multipoint) tr("on") else tr("off"),
            active = live.multipoint,
            onClick = {
                vm.tick(if (live.multipoint) Feedback.Kind.Off else Feedback.Kind.On)
                vm.setMultipoint(!live.multipoint)
            },
        )

        "case" -> StatTile(
            icon = Icons.Outlined.Inventory2,
            label = if (live.caseFromMemory) tr("caseLast") else tr("caseTitle"),
            value = live.batteryCase?.let { "$it%" } ?: tr("noData"),
            active = false,
            onClick = null,
            bottomContent = { primary, secondary ->
                SmoothBar(
                    progress = (live.batteryCase ?: 0) / 100f,
                    track = secondary.copy(alpha = 0.22f),
                    fill = primary,
                )
            },
        )

        "sleep" -> {
            val minutes by vm.sleepMinutes.collectAsState()
            val left by vm.sleepLeft.collectAsState()
            val short = when {
                left > 0 -> "%d:%02d".format(left / 60, left % 60)
                minutes > 0 -> "$minutes ${tr("min15").take(3)}"
                else -> tr("sleepShort")
            }
            if (compact) {
                MiniTile(Icons.Outlined.Bedtime, short, minutes > 0) { vm.tick(); onSleep() }
            } else {
                StatTile(
                    icon = Icons.Outlined.Bedtime,
                    label = tr("sleepTimer"),
                    value = if (minutes > 0) short else tr("off"),
                    active = minutes > 0,
                    onClick = { vm.tick(); onSleep() },
                )
            }
        }

        "volume" -> {
            val limit by vm.volumeLimit.collectAsState()
            if (compact) {
                MiniTile(
                    Icons.Outlined.VolumeUp,
                    if (limit > 0) "$limit%" else tr("limitShort"),
                    limit > 0,
                ) { vm.tick(); onVolume() }
            } else {
                StatTile(
                    icon = Icons.Outlined.VolumeUp,
                    label = tr("volumeLimit"),
                    value = if (limit > 0) "$limit%" else tr("noLimit"),
                    active = limit > 0,
                    onClick = { vm.tick(); onVolume() },
                )
            }
        }

        "find" -> {
            var ringing by remember { mutableStateOf(false) }
            if (compact) {
                MiniTile(
                    Icons.Outlined.NotificationsActive,
                    if (ringing) tr("stop") else tr("findShort"),
                    ringing,
                ) {
                    ringing = !ringing
                    vm.tick(if (ringing) Feedback.Kind.On else Feedback.Kind.Off)
                    vm.findDevice(ringing)
                }
                return
            }
            StatTile(
                icon = Icons.Outlined.NotificationsActive,
                label = tr("findBuds"),
                value = if (ringing) tr("signalPlaying") else tr("playSignal"),
                active = ringing,
                onClick = {
                    ringing = !ringing
                    vm.tick(if (ringing) Feedback.Kind.On else Feedback.Kind.Off)
                    vm.findDevice(ringing)
                },
            )
        }

        // Прошивка приходит только от подключённой гарнитуры — раньше
        // плитка была просто пустой и выглядела как баг.
        "firmware" -> if (compact) {
            MiniTile(
                Icons.Outlined.Memory,
                live.firmware ?: if (live.connected) "…" else tr("noLink"),
                false,
                null,
            )
        } else {
            StatTile(
                icon = Icons.Outlined.Memory,
                label = tr("firmware"),
                value = live.firmware ?: if (live.connected) tr("requesting") else tr("noLink"),
                active = false,
                onClick = null,
            )
        }
    }
}

/**
 * Единый каркас плитки: иконка сверху, подпись, значение.
 * Одинаковые отступы и типографика у всех плиток.
 */
@Composable
private fun StatTile(
    icon: ImageVector?,
    label: String,
    value: String,
    active: Boolean,
    onClick: (() -> Unit)?,
    topContent: (@Composable (Color) -> Unit)? = null,
    bottomContent: (@Composable (Color, Color) -> Unit)? = null,
) {
    BentoTile(
        Modifier.fillMaxWidth(),
        active = active,
        minHeight = 132.dp,
        onClick = onClick,
    ) { primary, secondary ->
        Column(Modifier.fillMaxWidth()) {
            // Верхняя зона фиксированной высоты — иконки всех плиток на одной линии.
            Box(Modifier.height(30.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    topContent != null -> topContent(primary)
                    icon != null -> Icon(icon, null, tint = primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                label = "tileValue",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = primary,
                    maxLines = 1,
                )
            }
            if (bottomContent != null) {
                Spacer(Modifier.height(10.dp))
                bottomContent(primary, secondary)
            }
        }
    }
}

/** Форма полос на плитке — по текущим уровням системного эквалайзера. */
private fun eqCurve(gains: List<Int>): List<Float> {
    if (gains.isEmpty()) return listOf(0.4f, 0.6f, 0.9f, 0.5f, 0.45f)
    val step = (gains.size / 5f).coerceAtLeast(1f)
    return (0 until 5).map { i ->
        val idx = (i * step).toInt().coerceAtMost(gains.lastIndex)
        ((gains[idx] + 12) / 24f).coerceIn(0.18f, 1f)
    }
}

/**
 * Компактная плитка для дополнительных функций: иконка и короткая подпись
 * по центру. Нужна, чтобы главные плитки визуально отличались от мелких —
 * когда все были одного размера, экран выглядел плоско.
 */
@Composable
private fun MiniTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: (() -> Unit)?,
) {
    BentoTile(
        Modifier.fillMaxWidth(),
        active = active,
        minHeight = 84.dp,
        onClick = onClick,
    ) { primary, secondary ->
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
