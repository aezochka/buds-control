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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Плитка bento-сетки: контейнер, форма и масштаб анимируются пружиной.
 * Активная плитка отдаёт контенту onPrimary, поэтому текст на ярком
 * лаймовом фоне остаётся читаемым (в WebView-версии он сливался).
 */
@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    minHeight: Dp = 124.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.(primary: Color, secondary: Color) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.surfaceContainer,
        animationSpec = Motion.effects(), label = "tileBg",
    )
    val corner by animateDpAsState(if (active) 34.dp else 28.dp, Motion.spatial(), label = "tileCorner")

    var base = modifier
        .defaultMinSize(minHeight = minHeight)
        .clip(RoundedCornerShape(corner))
        .background(container)
    if (onClick != null) base = base.pressBounce(onClick = onClick)

    Column(base.padding(17.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        content(
            if (active) scheme.onPrimary else scheme.onSurface,
            if (active) scheme.onPrimary.copy(alpha = 0.80f) else scheme.onSurfaceVariant,
        )
    }
}

/** Столбики эквалайзера: бесконечная плавная анимация, когда активно. */
@Composable
fun EqualizerBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bases = listOf(0.42f, 0.78f, 1f, 0.6f, 0.5f)
    Row(modifier.height(36.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        bases.forEachIndexed { index, base ->
            val target = if (active) (base * 0.42f).coerceAtLeast(0.16f) else base
            val h by transition.animateFloat(
                initialValue = base, targetValue = target,
                animationSpec = infiniteRepeatable(
                    animation = tween(600 + index * 140, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h.coerceIn(0.12f, 1f))
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Пульсирующая точка статуса. */
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale",
    )
    Box(Modifier.size(size).scale(scale).clip(CircleShape).background(color))
}

/** Прогресс-бар с пружинной анимацией ширины. */
@Composable
fun SmoothBar(progress: Float, track: Color, fill: Color, height: Dp = 6.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.spatial(), label = "bar")
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(CircleShape).background(fill))
    }
}

@Composable
fun TileLabel(text: String, color: Color) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelSmall,
    color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
)

@Composable
fun TileValue(value: String, unit: String?, color: Color, unitColor: Color) = Row(verticalAlignment = Alignment.Bottom) {
    Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
    if (unit != null) {
        Spacer(Modifier.width(3.dp))
        Text(unit, style = MaterialTheme.typography.bodySmall, color = unitColor, modifier = Modifier.padding(bottom = 3.dp))
    }
}
