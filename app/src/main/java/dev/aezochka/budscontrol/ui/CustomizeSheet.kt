package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import dev.aezochka.budscontrol.data.Accent
import dev.aezochka.budscontrol.data.UserSettings

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

/** Рабочая шторка настройки плиток: порядок, видимость. Всё сохраняется. */
@Composable
fun CustomizeTilesSheet(
    settings: UserSettings,
    onMove: (String, Int) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 44.dp, height = 5.dp).clip(CircleShape).background(scheme.outline))
        } },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Плитки", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Стрелки меняют порядок, глаз убирает плитку с главной",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            settings.tileOrder.forEachIndexed { index, key ->
                val hidden = key in settings.hiddenTiles
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tileTitles[key] ?: key,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hidden) scheme.onSurfaceVariant else scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(Icons.Outlined.ArrowUpward, index > 0) { onMove(key, -1) }
                    IconAction(Icons.Outlined.ArrowDownward, index < settings.tileOrder.lastIndex) { onMove(key, +1) }
                    IconAction(
                        if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        true,
                        tint = if (hidden) scheme.outline else scheme.primary,
                    ) { onToggle(key) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .pressBounce(scaleDown = 0.86f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = tint ?: if (enabled) scheme.onSurfaceVariant else scheme.outlineVariant,
            modifier = Modifier.size(20.dp),
        )
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
                val corner by animateDpAsState(if (active) 28.dp else 20.dp, Motion.spatial(), label = "accentCorner")
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
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(accent.seed)),
                    )
                    Text(
                        accent.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) Color(0xFF16210A) else scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) Icon(Icons.Filled.Check, null, tint = Color(0xFF16210A), modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
