package dev.aezochka.budscontrol.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import kotlin.math.roundToInt

/**
 * Обновление одной строкой.
 *
 * Раньше это была отдельная шторка с описанием релиза и большой кнопкой —
 * из-за неё процесс читался как «скачать приложение заново». Теперь строка
 * сама показывает состояние, а тап делает единственное осмысленное действие.
 *
 * Про дельта-обновления честно: для APK, установленного вручную, Android их
 * не поддерживает — ставится только целый подписанный файл. Что реально
 * сделано: докачка по Range и повторное использование уже скачанного APK,
 * поэтому второй тап не начинает загрузку с нуля.
 */
@Composable
fun UpdateRow(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val state by vm.updateState.collectAsState()

    // Проверяем один раз при заходе в настройки, без кнопки «проверить».
    LaunchedEffect(Unit) { vm.checkUpdateIfIdle() }

    val release = state.release
    val progress by animateFloatAsState(state.progress, Motion.effects(), label = "updProgress")

    val subtitle = when {
        state.error != null -> state.error.orEmpty()
        state.checking -> tr("checking")
        state.downloading -> "${(state.progress * 100).roundToInt()}%"
        state.readyToInstall && release != null -> tr("updateReady")
        release != null -> "${release.version} · ${sizeMb(release.sizeBytes)} МБ"
        state.currentVersion.isNotEmpty() -> tr("updateLatest")
        else -> tr("updateHint")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (release != null) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainer)
            // Тап осмысленен только когда есть что ставить.
            .let { base ->
                if (release != null && !state.downloading) {
                    base.pressBounce { vm.downloadAndInstall() }
                } else {
                    base
                }
            }
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (release != null) scheme.primary.copy(alpha = 0.22f) else scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Download, null,
                    tint = if (release != null) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.fillMaxWidth(0.74f)) {
                Text(
                    if (release != null) tr("updateAvailable") else tr("update"),
                    style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) scheme.error else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.fillMaxWidth(0.02f))
            if (release != null) {
                Text(
                    if (state.readyToInstall) tr("install") else tr("updateAction"),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
            }
        }

        // Полоса только во время загрузки: в остальное время это шум.
        if (state.downloading) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
    }
}

private fun sizeMb(bytes: Long): Float = (bytes / 1024f / 1024f * 10).roundToInt() / 10f
