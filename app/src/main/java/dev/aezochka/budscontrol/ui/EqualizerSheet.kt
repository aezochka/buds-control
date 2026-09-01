package dev.aezochka.budscontrol.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import kotlin.math.roundToInt

/**
 * Эквалайзер: вертикальные полосы по частотам, без пресетов.
 *
 * Жесты: awaitEachGesture + consume() на каждом изменении. Иначе шторка
 * ModalBottomSheet перехватывает вертикальную протяжку себе, и полоса
 * успевает сдвинуться лишь на пару пикселей.
 */
@Composable
fun EqualizerSheet(vm: BudsViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gains by vm.eqGains.collectAsState()

    LaunchedEffect(Unit) { vm.attachAudioFx() }

    val freqs = vm.bandFrequencies()
    val (minDb, maxDb) = vm.gainRangeDb()

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
                        tr("eqHint"),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer)
                        .pressBounce { vm.resetEq() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.RestartAlt, tr("reset"), tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            BandSliders(
                gains = gains.ifEmpty { List(freqs.size.coerceAtLeast(5)) { 0 } },
                freqs = freqs,
                minDb = minDb,
                maxDb = maxDb,
            ) { band, db -> vm.setBandDb(band, db) }

            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun BandSliders(
    gains: List<Int>,
    freqs: List<Int>,
    minDb: Int,
    maxDb: Int,
    onChange: (Int, Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackHeight = 200.dp
    val trackPx = with(density) { trackHeight.toPx() }
    val span = (maxDb - minDb).coerceAtLeast(1)

    Row(
        Modifier.fillMaxWidth().height(trackHeight + 46.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        gains.forEachIndexed { index, db ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (db > 0) "+$db" else "$db",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (db == 0) scheme.onSurfaceVariant else scheme.primary,
                )
                Spacer(Modifier.height(5.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainer)
                        // Считаем положение из pointer position ровно как TuneX:
                        // Delta нельзя использовать без начальной координаты — иначе
                        // полоса всегда получает почти нулевое движение.
                        .pointerInput(index, minDb, maxDb, trackPx) {
                            detectDragGestures(
                                onDrag = { change, _ ->
                                    change.consume()
                                    val value = valueAt(
                                        change.position.y.coerceIn(0f, trackPx),
                                        trackPx,
                                        minDb,
                                        span,
                                    )
                                    onChange(index, value)
                                },
                            )
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fill by animateFloatAsState(
                        ((db - minDb).toFloat() / span).coerceIn(0f, 1f),
                        Motion.spatial(), label = "fill$index",
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fill.coerceAtLeast(0.035f))
                            .clip(RoundedCornerShape(20.dp))
                            .background(scheme.primary),
                    )
                    // Риска нуля, чтобы было видно середину.
                    Box(
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(2.dp)
                            .padding(bottom = 0.dp)
                            .background(scheme.outline.copy(alpha = 0.35f)),
                    )
                }

                Spacer(Modifier.height(7.dp))
                Text(
                    freqs.getOrNull(index)?.let { hz ->
                        if (hz >= 1000) "${hz / 1000}к" else "$hz"
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun valueAt(y: Float, trackPx: Float, minDb: Int, span: Int): Int {
    val ratio = 1f - (y / trackPx).coerceIn(0f, 1f)
    return (minDb + ratio * span).roundToInt()
}

/** Полосы на плитке главного экрана. */
@Composable
fun EqBars(bars: List<Float>, animated: Boolean, color: Color) {
    val phase by if (animated) {
        rememberInfiniteTransition(label = "eqPhase").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "phase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Row(
        Modifier.size(width = 54.dp, height = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, base ->
            val shift = if (animated) {
                val local = (phase + index * 0.18f) % 1f
                1f - 0.42f * kotlin.math.abs(local * 2f - 1f)
            } else 1f
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight((base * shift).coerceIn(0.12f, 1f))
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
