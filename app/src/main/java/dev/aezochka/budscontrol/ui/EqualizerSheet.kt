package dev.aezochka.budscontrol.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel

/**
 * Эквалайзер: три пресета вместо ползунков.
 *
 * Басс буст усиливает низкие частоты, баланс выравнивает всё, чёткость
 * поднимает высокие. Временное решение: когда появится точная карта частот
 * T110, вернём ползунки с реальными значениями.
 */
@Composable
fun EqualizerSheet(vm: BudsViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gains by vm.eqGains.collectAsState()

    LaunchedEffect(Unit) { vm.attachAudioFx() }

    // Определяем текущий пресет по паттерну усиления.
    val preset = when {
        gains.isEmpty() -> "balanced"
        gains.take(3).average() > gains.drop(gains.size - 3).average() + 2 -> "bass"
        gains.drop(gains.size - 3).average() > gains.take(3).average() + 2 -> "clear"
        else -> "balanced"
    }

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
            Text("Эквалайзер", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Text(
                "Басы, баланс или чёткость",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                PresetTile(
                    label = "Басс буст+",
                    active = preset == "bass",
                    modifier = Modifier.weight(1f),
                ) {
                    // Усиливаем три нижние полосы.
                    vm.applyEqPreset(listOf(6, 4, 3, 0, -1))
                }
                PresetTile(
                    label = "Баланс",
                    active = preset == "balanced",
                    modifier = Modifier.weight(1f),
                ) {
                    vm.resetEq()
                }
                PresetTile(
                    label = "Чёткий",
                    active = preset == "clear",
                    modifier = Modifier.weight(1f),
                ) {
                    // Усиливаем три верхние полосы.
                    vm.applyEqPreset(listOf(-1, 0, 3, 4, 6))
                }
            }

            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun PresetTile(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BentoTile(
        modifier = modifier,
        active = active,
        minHeight = 96.dp,
        onClick = onClick,
    ) { primary, secondary ->
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = primary,
            )
        }
    }
}
