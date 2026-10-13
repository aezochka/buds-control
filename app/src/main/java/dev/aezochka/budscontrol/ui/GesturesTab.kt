package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

/** Жесты — своя вкладка, отдельно левый и правый наушник. */
@Composable
fun GesturesTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val live by vm.live.collectAsState()
    val strings = dev.aezochka.budscontrol.i18n.LocalStrings.current

    LazyColumn(Modifier.fillMaxSize()) {
        item { Spacer(Modifier.statusBarsPadding().height(14.dp)) }
        listOf(
            TouchSide.LEFT to strings["leftBud"],
            TouchSide.RIGHT to strings["rightBud"],
        ).forEach { (side, title) ->
            item {
                SideGestures(
                    title = title, side = side, touch = live.touch,
                    enabled = live.connected,
                    strings = strings,
                    onPick = { type, action -> vm.setTouch(side, type, action) },
                )
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun SideGestures(
    title: String,
    side: TouchSide,
    touch: Map<Pair<TouchSide, TouchType>, TouchAction>,
    enabled: Boolean,
    strings: dev.aezochka.budscontrol.i18n.Strings,
    onPick: (TouchType, TouchAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(13.dp)).background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Hearing, null, tint = scheme.primary,
                    modifier = Modifier.size(19.dp).rotate(if (side == TouchSide.RIGHT) 180f else 0f),
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            TouchType.TAP_2 to strings["tap2"],
            TouchType.TAP_3 to strings["tap3"],
            TouchType.HOLD to strings["hold"],
        ).forEachIndexed { index, (type, label) ->
            GestureRow(
                label = label,
                // Три жеста одного наушника — один блок.
                shape = groupShape(index, 3),
                current = touch[side to type] ?: touch[TouchSide.BOTH to type],
                enabled = enabled,
                // Заводское значение, пока гарнитура не прислала своё.
                strings = strings,
                fallbackLabel = when (type) {
                    TouchType.TAP_2 -> strings["defaultPlay"]
                    TouchType.TAP_3 -> strings["defaultNext"]
                    TouchType.HOLD -> strings["defaultAssistant"]
                    else -> "не задано"
                },
                onPick = { onPick(type, it) },
            )
        }
    }
}

@Composable
private fun GestureRow(
    label: String,
    current: TouchAction?,
    enabled: Boolean,
    strings: dev.aezochka.budscontrol.i18n.Strings,
    fallbackLabel: String,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    onPick: (TouchAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "chev")

    Column(
        Modifier
            .fillMaxWidth()
            // 2dp вместо 8dp: строки блока стоят почти вплотную,
            // поэтому прямой стык читается как одна фигура.
            .padding(bottom = 2.dp)
            .clip(shape)
            .background(scheme.surfaceContainer),
    ) {
        Row(
            // Раскрытие доступно всегда: раньше строка не нажималась,
            // пока гарнитура не ответила, и выглядело как «сломано».
            Modifier.pressBounce { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.fillMaxWidth(0.84f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                Text(
                    current?.let { actionLabel(it, strings) } ?: fallbackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current == null) scheme.outline else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.fillMaxWidth(0.08f))
            Icon(
                Icons.Outlined.ExpandMore, null, tint = scheme.outline,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
            exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
        ) {
            // Действия — компактные чипы с иконками вместо текстовых строк:
            // влезает сетка 2 в ряд, выбирается одним тапом, выбранное
            // подсвечено цветом акцента с галочкой.
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 14.dp)) {
                pickableActions.chunked(2).forEach { rowActions ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowActions.forEach { action ->
                            val selected = action == current
                            ActionChip(
                                action = action,
                                selected = selected,
                                strings = strings,
                                modifier = Modifier.weight(1f),
                                onPick = {
                                    onPick(action)
                                    expanded = false
                                },
                            )
                        }
                        if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Чип действия жеста: иконка + подпись. Пружинная реакция на нажатие,
 * выбранное — заливка акцентом с галочкой.
 */
@Composable
private fun ActionChip(
    action: TouchAction,
    selected: Boolean,
    strings: dev.aezochka.budscontrol.i18n.Strings,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh,
        Motion.effects(), label = "chipBg",
    )
    val tint by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        Motion.effects(), label = "chipTint",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .pressBounce(scaleDown = 0.93f) { onPick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            actionIcon(action), null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            actionLabel(action, strings),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check, null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Иконка действия — как в настройках realme: визуальный якорь. */
private fun actionIcon(action: TouchAction) = when (action) {
    TouchAction.PLAY_PAUSE -> Icons.Outlined.PlayArrow
    TouchAction.NEXT -> Icons.Outlined.SkipNext
    TouchAction.PREVIOUS -> Icons.Outlined.SkipPrevious
    TouchAction.VOLUME_UP -> Icons.Outlined.VolumeUp
    TouchAction.VOLUME_DOWN -> Icons.Outlined.VolumeDown
    TouchAction.VOICE_ASSISTANT, TouchAction.VOICE_ASSISTANT_REALME -> Icons.Outlined.Mic
    TouchAction.GAME_MODE -> Icons.Outlined.SportsEsports
    TouchAction.NOISE_CONTROL -> Icons.Outlined.NoiseAware
    else -> Icons.Outlined.Block
}

private val pickableActions = listOf(
    TouchAction.PLAY_PAUSE,
    TouchAction.NEXT,
    TouchAction.PREVIOUS,
    TouchAction.VOLUME_UP,
    TouchAction.VOLUME_DOWN,
    TouchAction.VOICE_ASSISTANT_REALME,
    TouchAction.GAME_MODE,
    TouchAction.OFF,
)

private fun actionLabelKey(action: TouchAction) = when (action) {
    TouchAction.OFF -> "actNothing"
    TouchAction.PLAY_PAUSE -> "actPlay"
    TouchAction.VOICE_ASSISTANT, TouchAction.VOICE_ASSISTANT_REALME -> "actAssistant"
    TouchAction.PREVIOUS -> "actPrev"
    TouchAction.NEXT -> "actNext"
    TouchAction.NOISE_CONTROL -> "actNoise"
    TouchAction.VOLUME_UP -> "actVolUp"
    TouchAction.VOLUME_DOWN -> "actVolDown"
    TouchAction.GAME_MODE -> "actGame"
}

@Composable
private fun actionLabel(action: TouchAction, strings: dev.aezochka.budscontrol.i18n.Strings) = strings[actionLabelKey(action)]
