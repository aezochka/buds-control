package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Hearing
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.proto.EqPreset
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

/**
 * Звук и жесты. Эквалайзер с пресетами и живыми полосами, жесты —
 * отдельно для левого и правого наушника, выбор действия из списка,
 * а не циклом по тапу.
 */
@Composable
fun SoundTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val live by vm.live.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("Звук", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
                Text(
                    if (live.connected) "Меняется сразу на наушниках" else "Наушники не подключены",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }

        item { EqualizerSection(live.eqPreset, live.connected) { vm.setEqualizer(it) } }

        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
                Text("Жесты", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Отдельно для каждого наушника",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
        }

        // Явно разделено по сторонам — раньше было непонятно, какой наушник.
        listOf(
            TouchSide.LEFT to "Левый наушник",
            TouchSide.RIGHT to "Правый наушник",
        ).forEach { (side, title) ->
            item {
                SideGestures(
                    title = title,
                    side = side,
                    touch = live.touch,
                    onPick = { type, action -> vm.setTouch(side, type, action) },
                )
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun EqualizerSection(current: EqPreset?, connected: Boolean, onPick: (EqPreset) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        EqPreset.entries.forEach { preset ->
            val active = preset == current
            val bg by animateColorAsState(
                if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "eqBg",
            )
            val corner by animateDpAsState(if (active) 32.dp else 26.dp, Motion.spatial(), label = "eqCorner")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(enabled = connected) { onPick(preset) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EqPreview(
                    bars = preset.bars,
                    animated = active,
                    color = if (active) scheme.onPrimary else scheme.primary,
                )
                Column(Modifier.fillMaxWidth(0.72f)) {
                    Text(
                        preset.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (active) scheme.onPrimary else scheme.onSurface,
                    )
                    Text(
                        when (preset) {
                            EqPreset.Balanced -> "Без окраски, как задумано"
                            EqPreset.BassBoost -> "Больше низов, 10 мм драйвер"
                            EqPreset.TrebleBoost -> "Ярче верх и детали"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    )
                }
                if (active) {
                    Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (!connected) {
            Text(
                "Подключи наушники, чтобы менять эквалайзер",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Пять полос: у активного пресета плавно колышутся. */
@Composable
private fun EqPreview(bars: List<Float>, animated: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "eqPrev")
    Row(
        Modifier.size(width = 54.dp, height = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, base ->
            val target = if (animated) (base * 0.45f).coerceAtLeast(0.16f) else base
            val h by transition.animateFloat(
                initialValue = base, targetValue = target,
                animationSpec = infiniteRepeatable(
                    tween(560 + index * 120, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "eqBar$index",
            )
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight(h.coerceIn(0.12f, 1f))
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun SideGestures(
    title: String,
    side: TouchSide,
    touch: Map<Pair<TouchSide, TouchType>, TouchAction>,
    onPick: (TouchType, TouchAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Hearing, null, tint = scheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (side == TouchSide.RIGHT) 180f else 0f),
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            TouchType.TAP_2 to "Двойное касание",
            TouchType.TAP_3 to "Тройное касание",
            TouchType.HOLD to "Долгое нажатие",
        ).forEach { (type, label) ->
            GestureRow(
                label = label,
                current = touch[side to type] ?: touch[TouchSide.BOTH to type],
                onPick = { onPick(type, it) },
            )
        }
    }
}

/** Строка жеста с раскрывающимся списком действий. */
@Composable
private fun GestureRow(label: String, current: TouchAction?, onPick: (TouchAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "chev")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer),
    ) {
        Row(
            Modifier.pressBounce { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.fillMaxWidth(0.82f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                Text(
                    current?.let(::actionLabel) ?: "не задано",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.fillMaxWidth(0.1f))
            Icon(
                Icons.Outlined.TouchApp, null, tint = scheme.outline,
                modifier = Modifier.size(19.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
            exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                pickableActions.forEach { action ->
                    val selected = action == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
                            .pressBounce {
                                onPick(action)
                                expanded = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            actionLabel(action),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) scheme.onPrimary else scheme.onSurface,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                        if (selected) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private val pickableActions = listOf(
    TouchAction.PLAY_PAUSE,
    TouchAction.NEXT,
    TouchAction.PREVIOUS,
    TouchAction.VOLUME_UP,
    TouchAction.VOLUME_DOWN,
    TouchAction.VOICE_ASSISTANT_REALME,
    TouchAction.GAME_MODE,
    TouchAction.OFF,
)

private fun actionLabel(action: TouchAction) = when (action) {
    TouchAction.OFF -> "Ничего"
    TouchAction.PLAY_PAUSE -> "Плей / пауза"
    TouchAction.VOICE_ASSISTANT, TouchAction.VOICE_ASSISTANT_REALME -> "Голосовой помощник"
    TouchAction.PREVIOUS -> "Предыдущий трек"
    TouchAction.NEXT -> "Следующий трек"
    TouchAction.NOISE_CONTROL -> "Переключить шумодав"
    TouchAction.VOLUME_UP -> "Громче"
    TouchAction.VOLUME_DOWN -> "Тише"
    TouchAction.GAME_MODE -> "Игровой режим"
}
