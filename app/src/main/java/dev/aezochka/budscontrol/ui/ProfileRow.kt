package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.data.EarbudProfile
import kotlin.math.roundToInt

/**
 * Список профилей сверху главного экрана. Зажал чип — можно тащить и менять
 * порядок; тап переключает активную гарнитуру. Справа плюсик.
 */
@Composable
fun ProfileRow(
    profiles: List<EarbudProfile>,
    onSelect: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var order by remember(profiles.map { it.id }) { mutableStateOf(profiles.map { it.id }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val chipWidthPx = with(density) { 128.dp.toPx() }

    val ordered = order.mapNotNull { id -> profiles.firstOrNull { it.id == id } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEach { profile ->
                val active = profile.isSelected
                val isDragging = dragging == profile.id
                val bg by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "chipBg",
                )
                val lift by animateFloatAsState(if (isDragging) 1.08f else 1f, Motion.spatialFast(), label = "lift")

                // Наклон только у перетаскиваемого чипа. Раньше бесконечная
                // анимация создавалась для КАЖДОГО чипа и крутилась всегда —
                // это и был источник микрофризов.
                val angle by animateFloatAsState(
                    targetValue = if (isDragging) 1.4f else 0f,
                    animationSpec = Motion.spatialFast(),
                    label = "angle",
                )

                Row(
                    Modifier
                        .graphicsLayer {
                            scaleX = lift; scaleY = lift
                            rotationZ = angle
                            translationX = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 16f else 0f
                        }
                        .clip(CircleShape)
                        .background(bg)
                        .pointerInput(order) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = profile.id; dragOffset = 0f },
                                onDragEnd = { dragging = null; dragOffset = 0f; onReorder(order) },
                                onDragCancel = { dragging = null; dragOffset = 0f },
                                onDrag = { _, amount ->
                                    dragOffset += amount.x
                                    val shift = (dragOffset / chipWidthPx).roundToInt()
                                    if (shift != 0) {
                                        val from = order.indexOf(profile.id)
                                        val to = (from + shift).coerceIn(0, order.lastIndex)
                                        if (to != from) {
                                            val list = order.toMutableList()
                                            list.removeAt(from)
                                            list.add(to, profile.id)
                                            order = list
                                            dragOffset -= shift * chipWidthPx
                                        }
                                    }
                                },
                            )
                        }
                        .pressBounce { onSelect(profile.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Headphones, null,
                        tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        profile.displayName.removePrefix("realme ").removePrefix("OnePlus "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh)
                .pressBounce(scaleDown = 0.88f) { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "Добавить наушники", tint = scheme.primary, modifier = Modifier.size(21.dp))
        }
    }
}
