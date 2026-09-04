package dev.aezochka.budscontrol.ui

import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import dev.aezochka.budscontrol.data.EarbudProfile
import dev.aezochka.budscontrol.i18n.tr
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileRow(
    profiles: List<EarbudProfile>,
    onSelect: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onDragActive: (Boolean) -> Unit = {},
    connectedAddresses: Set<String> = emptySet(),
    nameBlur: androidx.compose.ui.unit.Dp = 0.dp,
    onRename: (String) -> Unit = {},
    onReset: (String) -> Unit = {},
    onForget: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current

    var order by remember { mutableStateOf(profiles.map { it.id }) }
    LaunchedEffect(profiles) {
        val incoming = profiles.map { it.id }
        if (incoming.toSet() != order.toSet()) order = incoming
    }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var slots by remember { mutableStateOf(0) }
    var startIndex by remember { mutableStateOf(0) }
    var menuId by remember { mutableStateOf<String?>(null) }

    val chipWidthPx = with(density) { 132.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val ordered = order.mapNotNull { id -> profiles.firstOrNull { it.id == id } }
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState(), enabled = dragId == null && menuId == null),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEachIndexed { index, profile ->
                val active = profile.isSelected
                val dragging = dragId == profile.id
                val menuOpen = menuId == profile.id
                val targetShift = when {
                    dragId == null -> 0
                    dragging -> 0
                    slots > 0 && index > startIndex && index <= startIndex + slots -> -1
                    slots < 0 && index < startIndex && index >= startIndex + slots -> 1
                    else -> 0
                }
                val liftTarget = when {
                    dragging -> 1.07f
                    menuOpen -> 1.05f
                    else -> 1f
                }
                val lift by animateFloatAsState(liftTarget, Motion.spatialFast(), label = "lift")
                val neighbourShift by animateFloatAsState(
                    targetShift * (chipWidthPx + gapPx),
                    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                    label = "neighbourShift",
                )
                val bg by animateColorAsState(
                    when {
                        active -> scheme.primary
                        dragging || menuOpen -> scheme.surfaceContainerHighest
                        else -> scheme.surfaceContainer
                    },
                    Motion.effects(), label = "chipBg",
                )
                val extScope = rememberCoroutineScope()

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier
                            .zIndex(if (dragging || menuOpen) 1f else 0f)
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                                translationX = if (dragging) dragOffset else neighbourShift
                                shadowElevation = if (dragging || menuOpen) 20f else 0f
                            }
                            .clip(CircleShape)
                            .background(bg)
                            .pointerInput(order, menuId, dragId) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = try { awaitFirstDown(requireUnconsumed = false) } catch (_: Exception) { break }
                                        var longPressed = false
                                        var dragStarted = false
                                        val downPos = down.position
                                        val job = extScope.launch {
                                            delay(longPressTimeout)
                                            longPressed = true
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            menuId = profile.id
                                        }
                                        var finished = false
                                        while (!finished) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            val dist = hypot((change.position.x - downPos.x).toDouble(), (change.position.y - downPos.y).toDouble()).toFloat()
                                            if (!longPressed && dist > touchSlop) {
                                                job.cancel()
                                                finished = true
                                                break
                                            }
                                            if (longPressed && menuId == profile.id && dist > touchSlop && !dragStarted) {
                                                menuId = null
                                                dragStarted = true
                                                dragId = profile.id
                                                startIndex = order.indexOf(profile.id)
                                                slots = 0
                                                dragOffset = 0f
                                                onDragActive(true)
                                            }
                                            if (dragStarted) {
                                                if (change.pressed) {
                                                    val delta = change.position.x - change.previousPosition.x
                                                    dragOffset += delta
                                                    val step = chipWidthPx + gapPx
                                                    val moved = (dragOffset / step).roundToInt()
                                                    val limited = moved.coerceIn(-startIndex, order.lastIndex - startIndex)
                                                    if (limited != slots) slots = limited
                                                    change.consume()
                                                } else {
                                                    val from = order.indexOf(profile.id)
                                                    val to = (startIndex + slots).coerceIn(0, order.lastIndex)
                                                    if (from >= 0 && to != from) {
                                                        val list = order.toMutableList()
                                                        list.removeAt(from)
                                                        list.add(to, profile.id)
                                                        order = list
                                                        onReorder(list)
                                                    }
                                                    dragOffset = 0f
                                                    slots = 0
                                                    dragId = null
                                                    onDragActive(false)
                                                    finished = true
                                                }
                                            } else {
                                                if (!change.pressed) {
                                                    job.cancel()
                                                    if (longPressed && menuId == profile.id) {
                                                        change.consume()
                                                    } else if (!longPressed) {
                                                        onSelect(profile.id)
                                                    }
                                                    finished = true
                                                }
                                            }
                                            if (finished) break
                                        }
                                        job.cancel()
                                        if (dragStarted && dragId != null) {
                                            // ensure cleanup if loop broke early
                                            dragOffset = 0f
                                            slots = 0
                                            dragId = null
                                            onDragActive(false)
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val isConnected = profile.address.isNotBlank() && profile.address.uppercase() in connectedAddresses
                        Icon(Icons.Outlined.Headphones, null, tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(modifier = Modifier.blur(nameBlur), text = profile.displayName.removePrefix("realme ").removePrefix("OnePlus "), style = MaterialTheme.typography.bodyMedium, color = if (active) scheme.onPrimary else scheme.onSurfaceVariant)
                        if (isConnected) Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) scheme.onPrimary else scheme.primary))
                    }
                    if (menuOpen) {
                        Popup(onDismissRequest = { menuId = null }) {
                            Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh)) {
                                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ChipMenuItem(Icons.Outlined.Edit, "Переименовать") { menuId = null; onRename(profile.id) }
                                    ChipMenuItem(Icons.Outlined.RestartAlt, "Сбросить") { menuId = null; onReset(profile.id) }
                                    ChipMenuItem(Icons.Outlined.Delete, "Забыть") { menuId = null; onForget(profile.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(scheme.surfaceContainerHigh).clickable { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, tr("addBuds"), tint = scheme.primary, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ChipMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = scheme.onSurface, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface, maxLines = 1)
    }
}
