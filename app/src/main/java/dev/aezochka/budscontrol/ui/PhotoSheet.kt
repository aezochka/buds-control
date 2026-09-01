package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import java.io.File
import kotlin.math.roundToInt

/**
 * Тап по фото на главной: выбор картинки и правка индикаторов.
 *
 * Превью повторяет главный экран один в один (те же пропорции, тот же
 * масштаб фото), чтобы результат совпадал с тем, что видно на главной.
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
    val tweak by vm.chipTweak.collectAsState()
    val loading by vm.variantsLoading.collectAsState()

    var editing by remember { mutableStateOf(false) }
    // Что двигаем: по умолчанию оба, тапом можно оставить один.
    var pickLeft by remember { mutableStateOf(true) }
    var pickRight by remember { mutableStateOf(true) }
    var sizing by remember { mutableStateOf(false) }

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
                Column(Modifier.fillMaxWidth(0.72f)) {
                    Text(tr("picture"), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        when {
                            sizing -> tr("sizeHint")
                            editing -> tr("dragChips")
                            else -> tr("pictureHint")
                        },
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (editing) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainer)
                            .pressBounce { vm.resetChips() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.RestartAlt, tr("reset"), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainer)
                        .pressBounce { vm.loadPhotoVariants(address, deviceName) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Refresh, tr("refresh"), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Пропорции и масштаб как на главном экране.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerLow)
                    .then(
                        if (editing) Modifier.pointerInput(pickLeft, pickRight) {
                            detectDragGestures { _, drag ->
                                vm.nudgeChips(
                                    dx = drag.x / size.width,
                                    dy = drag.y / size.height,
                                    moveLeft = pickLeft,
                                    moveRight = pickRight,
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

                // Индикаторы в тех же позициях, что и на главной.
                EditableChip(
                    x = layout.left.x + tweak.leftDx,
                    y = layout.left.y + tweak.leftDy,
                    side = "L",
                    scale = tweak.scale,
                    selected = pickLeft,
                    editing = editing,
                    onTap = { if (editing) pickLeft = !pickLeft },
                )
                EditableChip(
                    x = layout.right.x + tweak.rightDx,
                    y = layout.right.y + tweak.rightDy,
                    side = "R",
                    scale = tweak.scale,
                    selected = pickRight,
                    editing = editing,
                    onTap = { if (editing) pickRight = !pickRight },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (editing) scheme.primary else scheme.surfaceContainer)
                    .pressBounce(
                        onClick = {
                            editing = !editing
                            if (!editing) sizing = false
                        },
                    )
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(
                    Icons.Outlined.OpenWith, null,
                    tint = if (editing) scheme.onPrimary else scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text(
                        if (editing) tr("done") else tr("tuneChips"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (editing) scheme.onPrimary else scheme.onSurface,
                    )
                    if (editing) {
                        Text(
                            when {
                                pickLeft && pickRight -> tr("movingBoth")
                                pickLeft -> tr("movingLeft")
                                pickRight -> tr("movingRight")
                                else -> tr("movingNone")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Ползунок размера — по зажатию кнопки под превью.
            AnimatedVisibility(
                visible = editing,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("size"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(tweak.scale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium, color = scheme.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surfaceContainer)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                    vm.setChipScale(0.7f + ratio * 0.9f)
                                }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(((tweak.scale - 0.7f) / 0.9f).coerceIn(0.04f, 1f))
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(scheme.primary),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(tr("variants"), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // Готовые картинки + скелетоны на те, что ещё грузятся.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(variants, key = { it }) { path ->
                    val active = path == current
                    VariantCard(active = active, onClick = { vm.choosePhoto(address, path) }) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(0.84f),
                        )
                    }
                }
                if (loading) {
                    items(List(3) { "skeleton_$it" }, key = { it }) {
                        SkeletonCard()
                    }
                }
            }
            if (!loading && variants.isEmpty()) {
                Text(
                    tr("noVariants"),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

/** Индикатор в редакторе: кликабельный, с подсветкой выбора. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EditableChip(
    x: Float,
    y: Float,
    side: String,
    scale: Float,
    selected: Boolean,
    editing: Boolean,
    onTap: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            editing && selected -> scheme.primary
            editing -> scheme.surfaceContainerHighest
            else -> scheme.surfaceContainerHigh
        },
        Motion.effects(), label = "chipEditBg",
    )
    val fg = if (editing && selected) scheme.onPrimary else scheme.onSurfaceVariant

    Row(
        Modifier
            .align(BiasAlignment(x * 2f - 1f, y * 2f - 1f))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .then(if (editing) Modifier.pressBounce(scaleDown = 0.9f, onClick = onTap) else Modifier)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("100%", style = MaterialTheme.typography.titleSmall, color = fg)
        Text(side, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
    }
}

/**
 * Карточка варианта. Рамка выбора нарисована ПОДЛОЖКОЙ, а не border:
 * тонкий border давал заметные пиксельные ступеньки на скруглении.
 */
@Composable
private fun VariantCard(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(
        if (active) scheme.primary else scheme.surfaceContainerHighest,
        Motion.effects(), label = "ring",
    )
    Box(
        Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(ring)
            .pressBounce(scaleDown = 0.94f, onClick = onClick)
            .padding(if (active) 4.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(23.dp))
                .background(scheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** Скелетон вместо спиннера: карточка уже есть, картинка ещё грузится. */
@Composable
private fun SkeletonCard() {
    val scheme = MaterialTheme.colorScheme
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmer.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(23.dp))
                .background(scheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            // Силуэт наушников, как будто карточка уже загрузилась.
            Icon(
                Icons.Outlined.Headphones,
                contentDescription = null,
                tint = scheme.surfaceContainerHighest.copy(alpha = alpha),
                modifier = Modifier.size(44.dp),
            )
        }
    }
}
