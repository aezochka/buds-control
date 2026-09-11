package dev.aezochka.budscontrol.ui

import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.aezochka.budscontrol.data.EarbudProfile
import dev.aezochka.budscontrol.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Список профилей сверху главного экрана.
 *
 * Жесты ведёт ОДИН обработчик-автомат: Idle → LongPress → Drag.
 * Раньше их было два — `detectDragGesturesAfterLongPress` и `clickable`
 * из `pressBounce`. Они конкурировали за один и тот же down-евент: тап
 * иногда не доходил до выбора профиля, а перетаскивание срывалось на
 * середине, оставляя `dragId` и блокировку свайпа вкладок включёнными.
 * Именно из-за этого залипания «не работало перемещение устройств» и
 * потом не нажималось вообще ничего.
 */
@Composable
fun ProfileRow(
    profiles: List<EarbudProfile>,
    onSelect: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
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
    // Синхронизируем с приходящим списком, но НЕ пересоздаём состояние:
    // remember(profiles) сбрасывал его сразу после сохранения порядка, и чип
    // повторно перескакивал — это и выглядело как лаг при отпускании.
    LaunchedEffect(profiles) {
        val incoming = profiles.map { it.id }
        if (incoming.toSet() != order.toSet()) order = incoming
    }
    var dragId by remember { mutableStateOf<String?>(null) }
    var pressedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    // На сколько позиций уехал палец от исходного места.
    var slots by remember { mutableStateOf(0) }
    var startIndex by remember { mutableStateOf(0) }

    // Страховка от залипания: если строка ушла из композиции прямо во время
    // жеста (свайп вкладки, сворачивание шторки), блокировку пейджера надо
    // снять — иначе экран остаётся «мёртвым» до перезапуска приложения.
    DisposableEffect(Unit) {
        onDispose { onDragActive(false) }
    }

    val chipWidthPx = with(density) { 132.dp.toPx() }
    // Тот же зазор, что в Arrangement.spacedBy ниже: сосед должен уехать
    // ровно на своё место, иначе чипы визуально наезжают друг на друга.
    val gapPx = with(density) { 8.dp.toPx() }
    val ordered = order.mapNotNull { id -> profiles.firstOrNull { it.id == id } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                // Прокрутку выключаем во время перетаскивания, иначе список
                // уезжает вместе с пальцем.
                .horizontalScroll(rememberScrollState(), enabled = dragId == null),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEachIndexed { index, profile ->
                val active = profile.isSelected
                val dragging = dragId == profile.id

                // Смещение соседей: они уступают место перетаскиваемому чипу.
                val targetShift = when {
                    dragId == null -> 0
                    dragging -> 0
                    // чип уехал вправо — те, кто между старым и новым местом, сдвигаются влево
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
                // Палец ведём БЕЗ анимации: сглаживание давало лаг и чип
                // «догонял» палец. Плавность нужна соседям, а не тому чипу,
                // который держат в руке.
                val neighbourShift by animateFloatAsState(
                    targetShift * (chipWidthPx + gapPx),
                    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
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

                Row(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            scaleX = lift
                            scaleY = lift
                            translationX = if (dragging) dragOffset else neighbourShift
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

                                    // Долгое удержание = начало переноса.
                                    val hold = gestureScope.launch {
                                        delay(longPressTimeout)
                                        longPressed = true
                                        pressedId = null
                                        startIndex = order.indexOf(profile.id).coerceAtLeast(0)
                                        slots = 0
                                        dragOffset = 0f
                                        dragId = profile.id
                                        onDragActive(true)
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                                        // Двинули палец ДО долгого нажатия — это прокрутка
                                        // строки, жест отдаём родителю.
                                        if (!longPressed && dist > touchSlop) {
                                            cancelled = true
                                            finished = true
                                            break
                                        }
                                        if (longPressed) {
                                            if (change.pressed) {
                                                dragOffset += change.position.x - change.previousPosition.x
                                                val step = chipWidthPx + gapPx
                                                val moved = (dragOffset / step).roundToInt()
                                                val limited = moved.coerceIn(
                                                    -startIndex,
                                                    (order.lastIndex - startIndex).coerceAtLeast(0),
                                                )
                                                if (limited != slots) slots = limited
                                                change.consume()
                                            } else {
                                                // Отпустили: порядок применяем один раз,
                                                // синхронно, до сброса перетаскивания.
                                                val from = order.indexOf(profile.id)
                                                val to = (startIndex + slots)
                                                    .coerceIn(0, order.lastIndex.coerceAtLeast(0))
                                                if (from >= 0 && to != from) {
                                                    val list = order.toMutableList()
                                                    list.removeAt(from)
                                                    list.add(to, profile.id)
                                                    order = list
                                                    onReorder(list)
                                                }
                                                finished = true
                                            }
                                        } else if (!change.pressed) {
                                            // Короткий тап — выбор профиля.
                                            onSelect(profile.id)
                                            finished = true
                                        }
                                    }

                                    hold.cancel()
                                    pressedId = null
                                    // Чистим состояние на ЛЮБОМ выходе, включая отмену:
                                    // пропущенная отмена и была причиной мёртвого экрана.
                                    if (dragId == profile.id || cancelled) {
                                        dragOffset = 0f
                                        slots = 0
                                        if (dragId == profile.id) {
                                            dragId = null
                                            onDragActive(false)
                                        }
                                    }
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
