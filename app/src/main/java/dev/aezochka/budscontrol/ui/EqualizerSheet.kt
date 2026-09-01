package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.proto.EqBands
import dev.aezochka.budscontrol.proto.EqPreset
import kotlin.math.roundToInt

/**
 * Эквалайзер: пресеты плюс ручная настройка пяти полос перетаскиванием,
 * как в realme Link. Раньше были только готовые пресеты.
 */
@Composable
fun EqualizerSheet(
    current: EqPreset?,
    gains: List<Int>,
    connected: Boolean,
    supported: Boolean,
    onPick: (EqPreset) -> Unit,
    onGains: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var local by remember(gains) { mutableStateOf(gains) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text("Эквалайзер", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        when {
                            !connected -> "Наушники не подключены"
                            !supported -> "Гарнитура не ответила на запрос EQ"
                            else -> "Тяни полосы вверх и вниз"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected && !supported) scheme.error else scheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer)
                        .pressBounce(enabled = connected) {
                            local = List(EqBands.size) { 0 }
                            onGains(local)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.RestartAlt, "Сброс", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            BandSliders(local, connected) { index, value ->
                local = local.toMutableList().also { it[index] = value }
                onGains(local)
            }

            if (connected && !supported) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surfaceContainer)
                        .padding(14.dp),
                ) {
                    Text(
                        "T110 не подтвердила поддержку эквалайзера по протоколу. " +
                            "Команды отправляются, но гарнитура их может игнорировать — " +
                            "в realme Link EQ у этой модели тоже ограничен.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Готовые режимы", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            EqPreset.entries.filter { it != EqPreset.Custom }.forEach { preset ->
                val active = preset == current
                val bg by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "eqBg",
                )
                val corner by animateDpAsState(if (active) 28.dp else 20.dp, Motion.spatial(), label = "eqCorner")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(corner))
                        .background(bg)
                        .pressBounce(enabled = connected) { onPick(preset) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EqBars(preset.bars, active, if (active) scheme.onPrimary else scheme.primary)
                    Text(
                        preset.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (active) scheme.onPrimary else scheme.onSurface,
                        modifier = Modifier.fillMaxWidth(0.72f),
                    )
                    if (active) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Пять вертикальных полос с перетаскиванием: -6…+6 дБ. */
@Composable
private fun BandSliders(gains: List<Int>, enabled: Boolean, onChange: (Int, Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackHeight = 172.dp
    val trackPx = with(density) { trackHeight.toPx() }

    Row(
        Modifier.fillMaxWidth().height(trackHeight + 34.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gains.forEachIndexed { index, gain ->
            Column(
                Modifier.fillMaxHeight().width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (gain > 0) "+$gain" else "$gain",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (gain == 0) scheme.onSurfaceVariant else scheme.primary,
                )
                Spacer(Modifier.height(4.dp))

                // Трек: тап или протяжка задаёт усиление.
                Box(
                    Modifier
                        .width(46.dp)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(23.dp))
                        .background(scheme.surfaceContainer)
                        .pointerInput(enabled, index) {
                            if (!enabled) return@pointerInput
                            detectDragGestures { change, _ ->
                                val ratio = 1f - (change.position.y / trackPx).coerceIn(0f, 1f)
                                val value = ((ratio * 12f) - 6f).roundToInt().coerceIn(-6, 6)
                                if (value != gains[index]) onChange(index, value)
                            }
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Заполнение от центра: вверх — плюс, вниз — минус.
                    val fill by animateFloatAsState(
                        ((gain + 6) / 12f).coerceIn(0f, 1f), Motion.spatial(), label = "fill$index",
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fill.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(23.dp))
                            .background(if (enabled) scheme.primary else scheme.surfaceContainerHighest),
                    )
                    // Центральная риска — нулевой уровень.
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(2.dp)
                            .padding(bottom = 0.dp)
                            .background(scheme.outline.copy(alpha = 0.4f)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    EqBands[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EqBars(bars: List<Float>, animated: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    Row(
        Modifier.size(width = 54.dp, height = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, base ->
            val target = if (animated) (base * 0.44f).coerceAtLeast(0.16f) else base
            val h by transition.animateFloat(
                initialValue = base, targetValue = target,
                animationSpec = infiniteRepeatable(
                    tween(560 + index * 120, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "bar$index",
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
