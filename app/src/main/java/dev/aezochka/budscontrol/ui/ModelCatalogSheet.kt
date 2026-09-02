package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.data.ModelCatalog
import dev.aezochka.budscontrol.data.ModelSpec
import dev.aezochka.budscontrol.i18n.tr

/**
 * Каталог моделей: что умеет конкретная гарнитура.
 *
 * Данные перенесены из realme Link (assets/headsetRusConfig). Названий моделей
 * в их APK нет — они приходят с сервера, поэтому здесь честно modelId.
 * Выбранная модель попадает в список профилей как заготовка, а реальный набор
 * функций всё равно подтверждает сама гарнитура своей таблицей (0x0100).
 */
@Composable
fun ModelCatalogSheet(
    onPick: (ModelSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val results = remember(query) { ModelCatalog.find(query) }

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
            Text(tr("catalogTitle"), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Text(
                tr("catalogHint"),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            // Поиск по id модели и по названию функции: «anc», «eq», «dolby».
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                Box(Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) {
                        Text(
                            tr("catalogSearch"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "${results.size} / ${ModelCatalog.models.size}",
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (results.isEmpty()) {
                Text(
                    tr("catalogEmpty"),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(results, key = { it.modelId }) { spec ->
                        ModelRow(spec) { onPick(spec) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelRow(spec: ModelSpec, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (expanded) scheme.surfaceContainerHighest else scheme.surfaceContainer,
        Motion.effects(), label = "modelBg",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            // Раскрытие не требует связи с гарнитурой: справочник читается всегда.
            .pressBounce { expanded = !expanded }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxWidth(0.62f)) {
                Text(
                    spec.modelId,
                    style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${typeLabel(spec.deviceType)} · ${spec.features.size} ${tr("catalogFeatures")}",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.fillMaxWidth(0.1f))
            Text(
                tr("add"),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .pressBounce { onPick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    spec.features.forEach { feature ->
                        Text(
                            featureLabel(feature),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(scheme.primary.copy(alpha = 0.16f))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: String) = when (type) {
    "TYPE_TWS" -> "TWS"
    "TYPE_NECK" -> "Neckband"
    "TYPE_HEAD" -> "Over-ear"
    else -> type.removePrefix("TYPE_")
}

/** Коды вендора в читаемый вид: ITEM_NOISE_REDUCTION -> Noise reduction. */
private fun featureLabel(item: String): String = item
    .removePrefix("ITEM_")
    .removePrefix("GROUP_")
    .split('_')
    .joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { it.uppercase() }
    }
