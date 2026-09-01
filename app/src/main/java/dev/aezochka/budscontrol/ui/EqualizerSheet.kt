package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.proto.EqPreset

/**
 * Шторка эквалайзера с главного экрана: тап по плитке — снизу выдвигается
 * выбор пресета. Именно так это и просили.
 */
@Composable
fun EqualizerSheet(
    current: EqPreset?,
    connected: Boolean,
    onPick: (EqPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Text("Эквалайзер", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                if (connected) "Меняется сразу на наушниках" else "Наушники не подключены",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            EqPreset.entries.forEach { preset ->
                val active = preset == current
                val bg by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "eqBg",
                )
                val corner by animateDpAsState(if (active) 30.dp else 22.dp, Motion.spatial(), label = "eqCorner")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(corner))
                        .background(bg)
                        .pressBounce(enabled = connected) { onPick(preset) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EqBars(preset.bars, active, if (active) scheme.onPrimary else scheme.primary)
                    Column(Modifier.fillMaxWidth(0.7f)) {
                        Text(
                            preset.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active) scheme.onPrimary else scheme.onSurface,
                        )
                        Text(
                            when (preset) {
                                EqPreset.Balanced -> "Без окраски"
                                EqPreset.BassBoost -> "Больше низов"
                                EqPreset.TrebleBoost -> "Ярче верх"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                        )
                    }
                    if (active) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun EqBars(bars: List<Float>, animated: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    Row(
        Modifier.size(width = 56.dp, height = 42.dp),
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
