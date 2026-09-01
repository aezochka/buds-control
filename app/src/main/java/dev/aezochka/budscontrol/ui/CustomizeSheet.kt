package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.data.Accent
import dev.aezochka.budscontrol.data.UserSettings
import kotlin.math.roundToInt

private val tileTitles = mapOf(
    "eq" to "Эквалайзер",
    "game" to "Игровой режим",
    "case" to "Заряд кейса",
    "find" to "Найти наушники",
    "firmware" to "Прошивка",
    "inear" to "Датчик в ухе",
    "sleep" to "Таймер сна",
    "volume" to "Лимит громкости",
    "spatial" to "Пространственный звук",
    "multipoint" to "Два устройства",
)

/**
 * Настройка плиток: наглядный список. У каждой плитки видно, сколько колонок
 * она занимает, размер меняется тремя кнопками, порядок — стрелками.
 * Раньше это был режим правки на главном экране, им было неудобно пользоваться.
 */
@Composable
fun CustomizeTilesSheet(
    settings: UserSettings,
    onReorder: (List<String>) -> Unit,
    onSpan: (String, Int) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var order by remember(settings.tileOrder) { mutableStateOf(settings.tileOrder) }

    ModalBottomSheet(
        onDismissRequest = { onReorder(order); onDismiss() },
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Плитки", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Размер — кнопками 1..4, порядок — стрелками, глаз убирает с главной",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(Modifier.height(430.dp)) {
                itemsIndexed(order, key = { _, key -> key }) { index, key ->
                    val hidden = key in settings.hiddenTiles
                    val span = (settings.tileSpans[key] ?: 1).coerceIn(1, 4)

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(scheme.surfaceContainer)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tileTitles[key] ?: key,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (hidden) scheme.onSurfaceVariant else scheme.onSurface,
                                modifier = Modifier.fillMaxWidth(0.62f),
                            )
                            Spacer(Modifier.weight(1f))
                            // Порядок
                            IconSquare(Icons.Outlined.KeyboardArrowUp, enabled = index > 0) {
                                val list = order.toMutableList()
                                list.removeAt(index); list.add(index - 1, key); order = list; onReorder(list)
                            }
                            Spacer(Modifier.width(6.dp))
                            IconSquare(Icons.Outlined.KeyboardArrowDown, enabled = index < order.lastIndex) {
                                val list = order.toMutableList()
                                list.removeAt(index); list.add(index + 1, key); order = list; onReorder(list)
                            }
                            Spacer(Modifier.width(6.dp))
                            IconSquare(
                                if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                tint = if (hidden) scheme.outline else scheme.primary,
                            ) { onToggle(key) }
                        }

                        Spacer(Modifier.height(11.dp))
                        // Наглядный размер: 4 сегмента, залитые = ширина плитки.
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (1..4).forEach { width ->
                                val filled = width <= span
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (filled) scheme.primary else scheme.surfaceContainerHighest)
                                        .pressBounce(scaleDown = 0.9f) { onSpan(key, width) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$width",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (filled) scheme.onPrimary else scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IconSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(scheme.surfaceContainerHigh)
            .pressBounce(scaleDown = 0.88f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = tint ?: if (enabled) scheme.onSurfaceVariant else scheme.outlineVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Выбор темы: сетка цветов плюс «плюсик» для своего цвета. */
@Composable
fun ThemeSheet(
    current: String,
    customAccent: Long,
    onPick: (String) -> Unit,
    onCustom: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Тема", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Акцент применяется сразу ко всему приложению",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Пресеты по 4 в ряд + последняя ячейка «плюсик».
            val items = Accent.entries.toList()
            val rows = items.chunked(4)
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { accent ->
                        val active = accent.key == current && customAccent == 0L
                        ColorCell(
                            color = Color(accent.seed),
                            active = active,
                            modifier = Modifier.weight(1f),
                        ) { onPick(accent.key) }
                    }
                    // Доливаем пустые ячейки, чтобы сетка не растягивалась.
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Свой цвет — плюсик в том же стиле.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (customAccent != 0L) Color(customAccent) else scheme.surfaceContainer
                        )
                        .border(
                            if (customAccent != 0L) 3.dp else 1.dp,
                            if (customAccent != 0L) scheme.onSurface else scheme.outlineVariant,
                            RoundedCornerShape(20.dp),
                        )
                        .pressBounce { picking = !picking },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (picking) Icons.Filled.Check else Icons.Outlined.Add,
                        "Свой цвет",
                        tint = if (customAccent != 0L) Color(0xFF16210A) else scheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                repeat(3) { Spacer(Modifier.weight(1f)) }
            }

            // Полоски-ползунки в том же стиле, что остальной UI.
            AnimatedVisibility(
                visible = picking,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
            ) {
                CustomColorPicker(
                    start = if (customAccent != 0L) customAccent else Accent.from(current).seed,
                    onChange = onCustom,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ColorCell(color: Color, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateDpAsState(if (active) 3.dp else 0.dp, Motion.spatial(), label = "cellBorder")
    val corner by animateDpAsState(if (active) 26.dp else 20.dp, Motion.spatial(), label = "cellCorner")
    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(corner))
            .background(color)
            .border(border, scheme.onSurface, RoundedCornerShape(corner))
            .pressBounce(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) Icon(Icons.Filled.Check, null, tint = Color(0xFF16210A), modifier = Modifier.size(20.dp))
    }
}

/** Три полоски RGB — как ползунки эквалайзера, в стиле приложения. */
@Composable
private fun CustomColorPicker(start: Long, onChange: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var r by remember { mutableStateOf(((start shr 16) and 0xFF).toInt()) }
    var g by remember { mutableStateOf(((start shr 8) and 0xFF).toInt()) }
    var b by remember { mutableStateOf((start and 0xFF).toInt()) }

    fun emit() {
        onChange(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }

    Column(Modifier.padding(top = 6.dp, bottom = 6.dp)) {
        listOf(
            Triple("Красный", r) { v: Int -> r = v },
            Triple("Зелёный", g) { v: Int -> g = v },
            Triple("Синий", b) { v: Int -> b = v },
        ).forEach { (label, value, setter) ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(scheme.surfaceContainer)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                            setter((ratio * 255).toInt())
                            emit()
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(value / 255f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF000000 or (value.toLong() shl 16) or (value.toLong() shl 8) or value.toLong())),
                )
            }
            Spacer(Modifier.height(9.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())),
            )
            Text(
                "Свой цвет",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface,
            )
        }
    }
}
