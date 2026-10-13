package dev.aezochka.budscontrol.ui

import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.aezochka.budscontrol.data.EarbudProfile
import dev.aezochka.budscontrol.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Список профилей сверху главного экрана.
 *
 * Жесты ведёт ОДИН обработчик-автомат: Idle → (LongPress) → Drag или Menu.
 * Долгое нажатие БЕЗ движения открывает меню чипа (переименовать/удалить);
 * то же долгое нажатие с движением — перенос. Короткий тап — выбор.
 */
@Composable
fun ProfileRow(
    profiles: List<EarbudProfile>,
    onSelect: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDragActive: (Boolean) -> Unit = {},
    /** Размытие названия — то же, что под фото. */
    nameBlur: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    val gestureScope = rememberCoroutineScope()

    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val longPressTimeout = remember { ViewConfiguration.getLongPressTimeout().toLong() }

    // Локальный порядок — двигаем визуально, наружу отдаём только при отпускании.
    var order by remember { mutableStateOf(profiles.map { it.id }) }
    LaunchedEffect(profiles) {
        val incoming = profiles.map { it.id }
        if (incoming.toSet() != order.toSet()) order = incoming
    }
    var dragId by remember { mutableStateOf<String?>(null) }
    var pressedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var slots by remember { mutableStateOf(0) }
    var startIndex by remember { mutableStateOf(0) }
    var settlingId by remember { mutableStateOf<String?>(null) }
    var justReordered by remember { mutableStateOf(false) }
    // Чип, для которого открыто контекстное меню (долгое нажатие без движения).
    var menuId by remember { mutableStateOf<String?>(null) }
    // Чип, который сейчас переименовывают (инлайн-поле).
    var renamingId by remember { mutableStateOf<String?>(null) }

    val chipWidths = remember { mutableStateMapOf<String, Float>() }

    DisposableEffect(Unit) {
        onDispose { onDragActive(false) }
    }

    val fallbackStepPx = with(density) { 132.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }

    fun stepFor(id: String): Float =
        (chipWidths[id]?.takeIf { it > 0f }?.plus(gapPx)) ?: (fallbackStepPx + gapPx)

    val ordered = order.mapNotNull { id -> profiles.firstOrNull { it.id == id } }

    Box {
        // ПЕРЕХВАТЧИК «тап вне» — ПЕРВЫМ ребёнком: он ПОД чипами и меню,
        // поэтому не крадёт их клики (раньше был последним — рисовался
        // поверх меню, и пункты не нажимались).
        if (menuId != null || renamingId != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0f }
                    .pointerInput(menuId, renamingId) {
                        awaitPointerEventScope {
                            awaitFirstDown(requireUnconsumed = false)
                            menuId = null
                            renamingId = null
                        }
                    },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState(), enabled = dragId == null),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ordered.forEachIndexed { index, profile ->
                    val active = profile.isSelected
                    val dragging = dragId == profile.id
                    val menuOpen = menuId == profile.id || renamingId == profile.id
                    val chipStep = stepFor(profile.id)

                    val targetShift = when {
                        dragId == null -> 0
                        dragging -> 0
                        slots > 0 && index > startIndex && index <= startIndex + slots -> -1
                        slots < 0 && index < startIndex && index >= startIndex + slots -> 1
                        else -> 0
                    }
                    val lift by animateFloatAsState(
                        when {
                            dragging -> 1.07f
                            pressedId == profile.id -> 0.955f
                            else -> 1f
                        },
                        Motion.spatialFast(), label = "lift",
                    )
                    // Палец ведём напрямую; на отпускании чип доезжает до
                    // слота одной пружиной от ТОЧКИ ОТПУСКАНИЯ пальца.
                    val chipShift = remember(profile.id) { Animatable(0f) }
                    val neighbourShift by animateFloatAsState(
                        targetShift * chipStep,
                        if (justReordered) snap() else spring(
                            dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "neighbourShift",
                    )
                    val bg by animateColorAsState(
                        when {
                            active -> scheme.primary
                            dragging -> scheme.surfaceContainerHighest
                            else -> scheme.surfaceContainer
                        },
                        Motion.effects(), label = "chipBg",
                    )

                    // Обёртка чипа: zIndex ПОДНИМАЕТСЯ, когда открыто меню или
                    // поле переименования, или идёт перенос — иначе соседние
                    // чипы (они позже в Row) рисуются ПОВЕРХ меню и съедают
                    // его клики. Это и было «меню на заднем фоне».
                    Box(
                        Modifier.zIndex(if (dragging || menuOpen) 10f else 0f),
                    ) {
                        Row(
                            Modifier
                                .onSizeChanged { size -> chipWidths[profile.id] = size.width.toFloat() }
                                .graphicsLayer {
                                    scaleX = lift
                                    scaleY = lift
                                    translationX = when {
                                        dragging -> dragOffset
                                        profile.id == settlingId -> chipShift.value
                                        else -> neighbourShift
                                    }
                                    shadowElevation = if (dragging) 20f else 0f
                                }
                                .clip(CircleShape)
                                .background(bg)
                                // ЕДИНСТВЕННЫЙ обработчик жестов на чипе.
                                .pointerInput(profile.id, order) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val start = down.position
                                            pressedId = profile.id
                                            var longPressed = false
                                            var cancelled = false
                                            // Двигали ли палец после long press.
                                            var movedAfterHold = false
                                            var menuShown = false
                                            // Меню уже открылось по паузе зажатия (палец ещё на экране).
                                            var menuShownEarly = false

                                            var settleFrom: Float? = null
                                            val hold = gestureScope.launch {
                                                delay(longPressTimeout)
                                                longPressed = true
                                                pressedId = null
                                                startIndex = order.indexOf(profile.id).coerceAtLeast(0)
                                                slots = 0
                                                dragOffset = 0f
                                                settlingId = null
                                                justReordered = false
                                                onDragActive(true)
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                dragId = profile.id
                                                // Зажали и НЕ двигаемся ещё немного — меню
                                                // появляется сразу, палец можно не отпускать.
                                                delay(260)
                                                if (dragId == profile.id && dragOffset == 0f) {
                                                    menuShownEarly = true
                                                    dragId = null
                                                    menuId = profile.id
                                                    onDragActive(false)
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                }
                                            }

                                            var finished = false
                                            while (!finished) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null) {
                                                    cancelled = true
                                                    finished = true
                                                    break
                                                }
                                                val dist = hypot(
                                                    change.position.x - start.x,
                                                    change.position.y - start.y,
                                                )
                                                if (!longPressed && dist > touchSlop) {
                                                    cancelled = true
                                                    finished = true
                                                    break
                                                }
                                                if (longPressed) {
                                                    if (change.pressed) {
                                                        if (!movedAfterHold && abs(change.position.x - start.x) > touchSlop) {
                                                            movedAfterHold = true
                                                            // Двинули ПОСЛЕ того, как меню успело открыться —
                                                            // закрываем его и возобновляем перенос прямо
                                                            // из-под пальца.
                                                            if (menuShownEarly || menuId == profile.id) {
                                                                menuShownEarly = false
                                                                menuId = null
                                                                dragId = profile.id
                                                                onDragActive(true)
                                                                startIndex = order.indexOf(profile.id).coerceAtLeast(0)
                                                                dragOffset = 0f
                                                            }
                                                        }
                                                        if (movedAfterHold && dragId == profile.id) {
                                                            dragOffset += change.position.x - change.previousPosition.x
                                                            val moved = (dragOffset / chipStep).roundToInt()
                                                            val limited = moved.coerceIn(
                                                                -startIndex,
                                                                (order.lastIndex - startIndex).coerceAtLeast(0),
                                                            )
                                                            if (limited != slots) slots = limited
                                                            change.consume()
                                                        }
                                                    } else {
                                                        // Отпустили.
                                                        if (!movedAfterHold) {
                                                            // Отпустили, не двигая: меню уже открыто
                                                            // по паузе зажатия — оставляем; если нет
                                                            // (отпустили раньше) — открываем.
                                                            if (!menuShownEarly) menuShown = true
                                                        }
                                                        if (movedAfterHold) {
                                                            val from = order.indexOf(profile.id)
                                                            val to = (startIndex + slots)
                                                                .coerceIn(0, order.lastIndex.coerceAtLeast(0))
                                                            if (from >= 0 && to != from) {
                                                                val list = order.toMutableList()
                                                                list.removeAt(from)
                                                                list.add(to, profile.id)
                                                                order = list
                                                                onReorder(list)
                                                                justReordered = true
                                                                settleFrom = dragOffset - (to - from) * chipStep
                                                            }
                                                        }
                                                        finished = true
                                                    }
                                                } else if (!change.pressed) {
                                                    onSelect(profile.id)
                                                    finished = true
                                                }
                                            }

                                            hold.cancel()
                                            pressedId = null
                                            if (dragId == profile.id) {
                                                if (menuShown) {
                                                    // Меню: чип возвращается на место мягко,
                                                    // без «доезда» переноса.
                                                    dragId = null
                                                    menuId = profile.id
                                                    onDragActive(false)
                                                    gestureScope.launch {
                                                        chipShift.snapTo(0f)
                                                    }
                                                } else {
                                                    settlingId = profile.id
                                                    dragId = null
                                                    slots = 0
                                                    onDragActive(false)
                                                    val startFrom = settleFrom
                                                        ?: if (movedAfterHold) dragOffset else 0f
                                                    if (startFrom != 0f) {
                                                        // Доезд ОДНОЙ пружиной от точки, где палец
                                                        // отпустил чип: snapTo ставит старт мгновенно
                                                        // (это и есть позиция пальца — скачка нет,
                                                        // чип УЖЕ там), дальше только пружина до слота.
                                                        gestureScope.launch {
                                                            chipShift.snapTo(startFrom)
                                                            chipShift.animateTo(
                                                                0f,
                                                                spring(
                                                                    dampingRatio = 0.8f,
                                                                    stiffness = Spring.StiffnessMedium,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                                dragOffset = 0f
                                            }
                                            // Чужое меню закрывается касанием любого чипа.
                                            if (menuId != null && menuId != profile.id) menuId = null
                                        }
                                    }
                                }
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
                                modifier = Modifier.blur(nameBlur),
                                text = profile.displayName.removePrefix("realme ").removePrefix("OnePlus "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                            )
                        }

                        // Контекстное меню чипа: появляется под ним по долгому
                        // удержанию без движения.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = menuId == profile.id,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .graphicsLayer { translationY = 50.dp.toPx() },
                            enter = androidx.compose.animation.expandVertically(Motion.spatial()) +
                                androidx.compose.animation.fadeIn(Motion.effects()),
                            exit = androidx.compose.animation.shrinkVertically(Motion.spatialFast()) +
                                androidx.compose.animation.fadeOut(Motion.effects()),
                        ) {
                            ChipMenu(
                                onRename = { menuId = null; renamingId = profile.id },
                                onDelete = { menuId = null; onDelete(profile.id) },
                            )
                        }

                        // Переименование: инлайн-поле прямо в чипе.
                        if (renamingId == profile.id) {
                            var text by remember(profile.id) { mutableStateOf(profile.displayName) }
                            Row(
                                Modifier
                                    .graphicsLayer { translationY = 50.dp.toPx() }
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(scheme.surfaceContainerHighest)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                    BasicTextField(
                                        value = text,
                                        onValueChange = { if (it.length <= 24) text = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = scheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(scheme.primary),
                                        modifier = Modifier.width(160.dp),
                                    )
                                    Icon(
                                        Icons.Outlined.DriveFileRenameOutline, tr("done"),
                                        tint = scheme.primary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .pressBounce(scaleDown = 0.85f) {
                                                renamingId = null
                                                if (text.isNotBlank()) onRename(profile.id, text.trim())
                                            },
                                    )
                            }
                        }
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
                Icon(Icons.Outlined.Add, tr("addBuds"), tint = scheme.primary, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun ChipMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(6.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .pressBounce { onRename() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.DriveFileRenameOutline, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Text(tr("renameDevice"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .pressBounce { onDelete() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.DeleteOutline, null, tint = scheme.error, modifier = Modifier.size(18.dp))
            Text(tr("deleteDevice"), style = MaterialTheme.typography.bodyMedium, color = scheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
