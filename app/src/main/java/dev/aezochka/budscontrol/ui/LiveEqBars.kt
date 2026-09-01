package dev.aezochka.budscontrol.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Полосы эквалайзера. Когда есть реальные уровни звука — двигаются под
 * музыку; когда музыки нет — та же «дышащая» анимация, что и раньше.
 */
@Composable
fun LiveEqBars(
    live: List<Float>?,
    fallback: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eqIdle")
    Row(
        modifier.size(width = 54.dp, height = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(5) { index ->
            val base = fallback.getOrElse(index) { 0.5f }
            // Есть музыка — берём её уровень и плавно к нему тянемся.
            val target = live?.getOrNull(index)
            val idle by transition.animateFloat(
                initialValue = base,
                targetValue = (base * 0.44f).coerceAtLeast(0.16f),
                animationSpec = infiniteRepeatable(
                    tween(560 + index * 120, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "idle$index",
            )
            val height by animateFloatAsState(
                targetValue = target ?: idle,
                animationSpec = if (target != null) tween(90) else tween(0),
                label = "bar$index",
            )
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight(height.coerceIn(0.12f, 1f))
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
