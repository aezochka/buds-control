package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import kotlin.math.roundToInt

/** Обновление приложения: что нового, прогресс загрузки, установка. */
@Composable
fun UpdateSheet(vm: BudsViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by vm.updateState.collectAsState()
    val pending = state.release

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Download, null, tint = scheme.primary, modifier = Modifier.size(26.dp))
                Column {
                    Text("Обновление", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        when {
                            state.checking -> "Проверяю релизы…"
                            pending == null -> "Установлена последняя версия ${state.currentVersion}"
                            else -> "Доступна ${pending.version}, у тебя ${state.currentVersion}"
                        },
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }

            val release = pending
            if (release != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(scheme.surfaceContainer)
                        .padding(16.dp),
                ) {
                    Text(
                        "Что нового",
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        release.notes.ifBlank { "Без описания" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Размер: ${(release.sizeBytes / 1024f / 1024f * 10).roundToInt() / 10f} МБ",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }

                if (state.progress > 0f && state.progress < 1f) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Загрузка ${(state.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium, color = scheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainer),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.progress)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Если связь оборвётся, загрузка продолжится с этого места",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.primary)
                        .pressBounce { vm.downloadAndInstall() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.progress >= 1f) "Установить" else "Скачать и установить",
                        style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary,
                    )
                }
            } else if (!state.checking) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainer)
                        .pressBounce { vm.checkUpdate() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Проверить снова", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
