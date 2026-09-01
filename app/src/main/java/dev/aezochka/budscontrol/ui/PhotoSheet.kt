package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.aezochka.budscontrol.BudsViewModel
import java.io.File

/**
 * Тап по фото на главной. Здесь можно выбрать другую картинку из найденных
 * и вручную поправить положение индикаторов заряда.
 */
@Composable
fun PhotoSheet(
    vm: BudsViewModel,
    address: String,
    deviceName: String,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val variants by vm.photoVariants.collectAsState()
    val current by vm.foundPhoto.collectAsState()
    val layout by vm.photoLayout.collectAsState()
    val offset by vm.chipOffset.collectAsState()
    var adjusting by remember { mutableStateOf(false) }

    LaunchedEffect(address) { vm.loadPhotoVariants(address, deviceName) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth(0.78f)) {
                    Text("Картинка", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        if (adjusting) "Тяни превью, чтобы сдвинуть индикаторы"
                        else "Выбери подходящую или поправь индикаторы",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.fillMaxWidth(0.04f))
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer)
                        .pressBounce { vm.loadPhotoVariants(address, deviceName) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Refresh, "Обновить", tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Превью с индикаторами: тут же настраивается их положение.
            val density = LocalDensity.current
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerLow)
                    .then(
                        if (adjusting) Modifier.pointerInput(Unit) {
                            detectDragGestures { _, drag ->
                                vm.setChipOffset(
                                    offset.first + drag.x / size.width,
                                    offset.second + drag.y / size.height,
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                current?.let { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.82f),
                    )
                }
                listOf(
                    Triple(layout.left.x + offset.first, layout.left.y + offset.second, "L"),
                    Triple(layout.right.x + offset.first, layout.right.y + offset.second, "R"),
                ).forEach { (x, y, side) ->
                    Box(
                        Modifier
                            .align(BiasAlignment(x * 2f - 1f, y * 2f - 1f))
                            .clip(CircleShape)
                            .background(if (adjusting) scheme.primary else scheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "100% $side",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (adjusting) scheme.onPrimary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (adjusting) scheme.primary else scheme.surfaceContainer)
                    .pressBounce { adjusting = !adjusting }
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(
                    Icons.Outlined.OpenWith, null,
                    tint = if (adjusting) scheme.onPrimary else scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    if (adjusting) "Готово" else "Настроить индикаторы",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (adjusting) scheme.onPrimary else scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                if (adjusting) {
                    Text(
                        "сброс",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.pressBounce { vm.setChipOffset(0f, 0f) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Найденные варианты", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            if (variants.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.primary)
                    Text("Ищу картинки…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(variants, key = { it }) { path ->
                        val active = path == current
                        Box(
                            Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(scheme.surfaceContainerLow)
                                .border(
                                    if (active) 3.dp else 0.dp,
                                    scheme.primary,
                                    RoundedCornerShape(22.dp),
                                )
                                .pressBounce { vm.choosePhoto(address, path) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(0.84f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
