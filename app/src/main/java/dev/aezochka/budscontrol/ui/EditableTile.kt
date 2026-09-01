package dev.aezochka.budscontrol.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Обёртка плитки в режиме правки: пунктирная рамка по контуру и точки-ручки
 * внутри для смены размера. Долгое нажатие — перетаскивание.
 */
@Composable
fun EditableTile(
    editing: Boolean,
    span: Int,
    modifier: Modifier = Modifier,
    onSpanChange: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val wobble = rememberInfiniteTransition(label = "editWobble")
    val angle by wobble.animateFloat(
        initialValue = if (editing) -0.8f else 0f,
        targetValue = if (editing) 0.8f else 0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "angle",
    )
    val frame by animateFloatAsState(if (editing) 1f else 0f, Motion.spatial(), label = "frame")

    Box(
        modifier
            .graphicsLayer { rotationZ = angle }
            .then(
                if (editing) Modifier.pointerInput(span) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { _, amount -> onDrag(amount.x) },
                    )
                } else Modifier
            ),
    ) {
        content()

        if (frame > 0.02f) {
            // Рамка по контуру плитки.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = frame }
                    .border(2.dp, scheme.primary.copy(alpha = 0.8f), RoundedCornerShape(28.dp)),
            )
            // Точки-ручки: тап по левой уменьшает, по правой увеличивает.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .graphicsLayer { alpha = frame },
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    val filled = index < span
                    Box(
                        Modifier
                            .size(if (filled) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                            .pressBounce(scaleDown = 0.7f) { onSpanChange(index + 1) },
                    )
                }
            }
        }
    }
}
