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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Hearing
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
import androidx.compose.ui.text.style.TextAlign
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
        ).forEach { (type, label) ->
            GestureRow(
                label = label,
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
    onPick: (TouchAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "chev")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer),
    ) {
        Row(
            // Раскрытие доступно всегда: раньше строка не нажималась,
            // пока гарнитура не ответила, и выглядело как «сломано».
            Modifier.pressBounce { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Жест слева, назначенное действие справа.
            Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                current?.let { actionLabel(it, strings) } ?: fallbackLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (current == null) scheme.outline else scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(0.52f),
            )
            Spacer(Modifier.size(10.dp))
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
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                pickableActions.forEach { action ->
                    val selected = action == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
                            .pressBounce { onPick(action); expanded = false }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            actionLabel(action, strings),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) scheme.onPrimary else scheme.onSurface,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                        if (selected) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
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
