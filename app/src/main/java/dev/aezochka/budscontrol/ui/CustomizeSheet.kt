package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
 * Настройка плиток перетаскиванием: зажал, потащил, отпустил — как
 * в согласованном макете. Стрелок больше нет.
 */
@Composable
fun CustomizeTilesSheet(
    settings: UserSettings,
    onReorder: (List<String>) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current

    // Локальный порядок, чтобы перетаскивание было мгновенным.
    var order by remember(settings.tileOrder) { mutableStateOf(settings.tileOrder) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(density) { 62.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = { onReorder(order); onDismiss() },
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Плитки", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Зажми плитку и перетащи, чтобы поменять порядок. Глаз убирает её с главной.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.height(430.dp),
            ) {
                itemsIndexed(order, key = { _, key -> key }) { index, key ->
                    val dragging = draggingIndex == index
                    val hidden = key in settings.hiddenTiles
                    val elevation by animateFloatAsState(if (dragging) 1.04f else 1f, Motion.spatialFast(), label = "dragScale")
                    val bg by animateColorAsState(
                        if (dragging) scheme.surfaceContainerHighest else scheme.surfaceContainer,
                        Motion.effects(), label = "dragBg",
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .graphicsLayer {
                                scaleX = elevation
                                scaleY = elevation
                                translationY = if (dragging) dragOffset else 0f
                                shadowElevation = if (dragging) 18f else 0f
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .background(bg)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Хват для перетаскивания.
                        Box(
                            Modifier
                                .size(36.dp)
                                .pointerInput(order) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingIndex = index; dragOffset = 0f },
                                        onDragEnd = { draggingIndex = null; dragOffset = 0f; onReorder(order) },
                                        onDragCancel = { draggingIndex = null; dragOffset = 0f },
                                        onDrag = { _, amount ->
                                            dragOffset += amount.y
                                            val shift = (dragOffset / rowHeightPx).roundToInt()
                                            if (shift != 0) {
                                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                val to = (from + shift).coerceIn(0, order.lastIndex)
                                                if (to != from) {
                                                    val list = order.toMutableList()
                                                    val moved = list.removeAt(from)
                                                    list.add(to, moved)
                                                    order = list
                                                    draggingIndex = to
                                                    dragOffset -= shift * rowHeightPx
                                                }
                                            }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.DragIndicator, "Перетащить",
                                tint = if (dragging) scheme.primary else scheme.outline,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Text(
                            tileTitles[key] ?: key,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hidden) scheme.onSurfaceVariant else scheme.onSurface,
                            modifier = Modifier.fillMaxWidth(0.72f).padding(start = 6.dp),
                        )

                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .pressBounce(scaleDown = 0.86f) { onToggle(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                null,
                                tint = if (hidden) scheme.outline else scheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Выбор акцента темы — с живым превью цвета. */
@Composable
fun ThemeSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Accent.entries.forEach { accent ->
                val active = accent.key == current
                val bg by animateColorAsState(
                    if (active) Color(accent.seed) else scheme.surfaceContainer, Motion.effects(), label = "accentBg",
                )
                val corner by animateDpAsState(if (active) 30.dp else 20.dp, Motion.spatial(), label = "accentCorner")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(corner))
                        .background(bg)
                        .pressBounce { onPick(accent.key) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(accent.seed)))
                    Text(
                        accent.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) Color(0xFF16210A) else scheme.onSurface,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    if (active) Icon(Icons.Filled.Check, null, tint = Color(0xFF16210A), modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
