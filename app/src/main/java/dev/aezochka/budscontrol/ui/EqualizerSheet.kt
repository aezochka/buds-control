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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import kotlin.math.roundToInt

/**
 * РАБОЧИЙ эквалайзер: системный android.media.audiofx, применяется к выводу
 * телефона и реально слышен в наушниках.
 *
 * Протокольный EQ убран: в открытом протоколе OPPO/realme такой команды нет
 * вообще, поэтому гарнитура на неё не отвечала и звук не менялся.
 */
@Composable
fun EqualizerSheet(vm: BudsViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ready by vm.fxReady.collectAsState()
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
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text("Эквалайзер", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        if (ready) "Системный, слышно в наушниках" else "Недоступен на этом устройстве",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) scheme.onSurfaceVariant else scheme.error,
                    )
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer)
                        .pressBounce(enabled = ready) { vm.resetEq() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.RestartAlt, "Сброс", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            if (ready && gains.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                BandSliders(
                    gains = gains,
                    freqs = freqs,
                    minDb = minDb,
                    maxDb = maxDb,
                ) { band, db -> vm.setBandDb(band, db) }

                Spacer(Modifier.height(20.dp))
                Text("Готовые режимы", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                presets(freqs.size).forEach { (title, values) ->
                    val active = gains == values
                    val bg by animateColorAsState(
                        if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "presetBg",
                    )
                    val corner by animateDpAsState(if (active) 28.dp else 20.dp, Motion.spatial(), label = "presetCorner")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(bg)
                            .pressBounce { vm.applyEqPreset(values) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active) scheme.onPrimary else scheme.onSurface,
                            modifier = Modifier.fillMaxWidth(0.85f),
                        )
                        if (active) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Пресеты строятся под фактическое число полос устройства. */
private fun presets(bands: Int): List<Pair<String, List<Int>>> {
    fun shape(vararg values: Int): List<Int> = List(bands) { i ->
        val pos = if (bands <= 1) 0f else i.toFloat() / (bands - 1)
        val idx = (pos * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
        values[idx]
    }
    return listOf(
        "Ровный" to List(bands) { 0 },
        "Бас" to shape(6, 4, 1, 0, 0),
        "Верх" to shape(0, 0, 1, 4, 6),
        "Голос" to shape(-2, 1, 5, 3, 0),
        "Кино" to shape(5, 2, 0, 3, 4),
    )
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
    val trackHeight = 168.dp
    val trackPx = with(density) { trackHeight.toPx() }
    val span = (maxDb - minDb).coerceAtLeast(1)

    Row(
        Modifier.fillMaxWidth().height(trackHeight + 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surfaceContainer)
                        .pointerInput(index, minDb, maxDb) {
                            detectDragGestures { change, _ ->
                                val ratio = 1f - (change.position.y / trackPx).coerceIn(0f, 1f)
                                val value = (minDb + ratio * span).roundToInt().coerceIn(minDb, maxDb)
                                if (value != gains[index]) onChange(index, value)
                            }
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
                            .fillMaxHeight(fill.coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(18.dp))
                            .background(scheme.primary),
                    )
                }
                Spacer(Modifier.height(6.dp))
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

/** Горизонтальная полоса силы эффекта. */
@Composable
fun StrengthBar(value: Int, max: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(scheme.surfaceContainer)
            .pointerInput(max) {
                detectHorizontalDragGestures { change, _ ->
                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange((ratio * max).roundToInt())
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fill by animateFloatAsState(
            (value.toFloat() / max).coerceIn(0f, 1f), Motion.spatial(), label = "strength",
        )
        Box(
            Modifier
                .fillMaxWidth(fill)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(scheme.primary),
        )
        Text(
            "${value * 100 / max}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (fill > 0.12f) scheme.onPrimary else scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

/** Полосы на плитке: под музыку, если есть звук. */
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
