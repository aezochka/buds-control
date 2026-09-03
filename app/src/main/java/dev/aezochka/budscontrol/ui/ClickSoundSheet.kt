package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr

/** Выбор звука нажатий с немедленным предпрослушиванием. */
@Composable
fun ClickSoundSheet(
    current: String,
    vm: BudsViewModel,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val choices = listOf(
        "click_a" to tr("clickSoundA"),
        "click_b" to tr("clickSoundB"),
        "click_c" to tr("clickSoundC"),
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(tr("clickSound"), style = MaterialTheme.typography.headlineSmall)
            Text(
                tr("clickSoundHint"),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            choices.forEach { (key, label) ->
                val selected = key == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceContainer)
                        .pressBounce {
                            vm.setClickSound(key)
                            onDismiss()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.MusicNote,
                        null,
                        tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (selected) Text("✓", color = scheme.primary, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(7.dp))
            }
            BottomSpacer()
        }
    }
}

@Composable
fun clickSoundTitle(key: String): String = when (key) {
    "click_a" -> tr("clickSoundA")
    "click_b" -> tr("clickSoundB")
    "click_c" -> tr("clickSoundC")
    "system" -> tr("clickSoundSystem")
    else -> tr("clickSoundOff")
}
