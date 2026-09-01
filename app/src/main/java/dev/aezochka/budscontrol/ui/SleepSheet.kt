package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import kotlin.math.roundToInt

/** Таймер сна: пресеты + свой ползунок до 180 минут, с обратным отсчётом. */
@Composable
fun SleepSheet(vm: BudsViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val minutes by vm.sleepMinutes.collectAsState()
    val left by vm.sleepLeft.collectAsState()
    var custom by remember(minutes) { mutableStateOf(if (minutes > 0) minutes else 30) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 46.dp, height = 5.dp).clip(CircleShape).background(scheme.outline))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Spacer(Modifier.height(10.dp))
            Text("Таймер сна", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                if (left > 0) "Осталось ${formatLeft(left)} — музыка встанет на паузу"
                else "Поставит воспроизведение на паузу",
                style = MaterialTheme.typography.bodySmall,
                color = if (left > 0) scheme.primary else scheme.onSurfaceVariant,
            )

            // Обратный отсчёт крупно, чтобы таймер был виден.
            if (left > 0) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(scheme.primary)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatLeft(left),
                        style = MaterialTheme.typography.displayMedium,
                        color = scheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            listOf(0 to "Выключить", 15 to "15 минут", 30 to "30 минут", 60 to "60 минут").forEach { (value, title) ->
                val active = minutes == value
                val bg by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "sleepBg",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .pressBounce { vm.setSleepTimerMinutes(value) }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) scheme.onPrimary else scheme.onSurface,
                        modifier = Modifier.fillMaxWidth(0.88f),
                    )
                    if (active) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }

            // Своё время — то, чего не было.
            Spacer(Modifier.height(6.dp))
            Text("Своё время: $custom мин", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(scheme.surfaceContainer)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                            custom = (5 + ratio * 175).roundToInt()
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(((custom - 5) / 175f).coerceIn(0f, 1f))
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(scheme.primary),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.primary)
                    .pressBounce { vm.setSleepTimerMinutes(custom) }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Запустить на $custom мин", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

private fun formatLeft(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

/** Лимит громкости: процент от максимума, реально опускает системную громкость. */
@Composable
fun VolumeLimitSheet(vm: BudsViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val limit by vm.volumeLimit.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 46.dp, height = 5.dp).clip(CircleShape).background(scheme.outline))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Spacer(Modifier.height(10.dp))
            Text("Лимит громкости", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                if (limit > 0) "Громкость не поднимется выше $limit%" else "Без ограничения",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            listOf(0 to "Без лимита", 50 to "50% — тихо", 70 to "70% — умеренно", 85 to "85% — громко").forEach { (value, title) ->
                val active = limit == value
                val bg by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "limBg",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .pressBounce { vm.setVolumeLimitPercent(value) }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) scheme.onPrimary else scheme.onSurface,
                        modifier = Modifier.fillMaxWidth(0.88f),
                    )
                    if (active) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
