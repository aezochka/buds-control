package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.aezochka.budscontrol.i18n.LocalStrings
import dev.aezochka.budscontrol.device.BluetoothScanner

/**
 * Начальный экран: язык → поиск наушников по Bluetooth → доступы.
 * Профили добавляются только из результатов реального скана.
 */
private const val LAST_STEP = 2

@Composable
fun OnboardingScreen(
    vm: BudsViewModel,
    onRequestBluetooth: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    val s = LocalStrings.current
    val language by vm.uiLanguage.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                val active = index == step
                val width by animateDpAsState(if (active) 26.dp else 8.dp, Motion.spatial(), label = "dotW")
                val color by animateColorAsState(
                    if (active) scheme.primary else scheme.surfaceContainerHighest, Motion.effects(), label = "dotC",
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.5.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(Motion.spatial()) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(Motion.spatial()) { -it / 3 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(Motion.spatial()) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(Motion.spatial()) { it / 3 } + fadeOut(tween(180)))
                }
            },
            modifier = Modifier.weight(1f),
            label = "onbStep",
        ) { current ->
            when (current) {
                0 -> LanguageStep(language) { vm.setLanguage(it) }
                1 -> GuideStep()
                else -> DevicesStep(vm, onRequestBluetooth)
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step > 0) {
                PillButton(s.back, filled = false, modifier = Modifier.weight(1f)) { step-- }
            }
            val profiles by vm.profiles.collectAsState()
            // Кнопка блокируется только там, где действительно нельзя идти дальше:
            // на шаге выбора наушников. На «Доступах» она всегда активна.
            val canContinue = when (step) {
                2 -> profiles.isNotEmpty()
                else -> true
            }
            // Короткая подпись: длинная строка ломалась переносом в узкой кнопке.
            val label = when {
                step == 2 && profiles.isEmpty() -> s.pickBudsFirst
                step == LAST_STEP -> s.go
                else -> s.next
            }
            PillButton(
                text = label,
                // «Далее» всегда залита акцентом — серая выглядела выключенной,
                // хотя кликалась (это и выглядело как баг). Серой остаётся
                // только реально заблокированная кнопка.
                filled = canContinue || step < LAST_STEP,
                enabled = canContinue,
                modifier = Modifier.weight(1f),
            ) {
                if (step == LAST_STEP) vm.finishOnboarding(language) else step = (step + 1).coerceAtMost(LAST_STEP)
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Раньше кнопка выглядела серой, но всё равно нажималась. Теперь вид
    // и поведение совпадают: выключенная не реагирует вообще.
    // Серый вид только когда кнопка реально заблокирована.
    val container = when {
        !enabled -> scheme.surfaceContainer
        filled -> scheme.primary
        else -> scheme.surfaceContainerHigh
    }
    val label = when {
        !enabled -> scheme.outline
        filled -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .pressBounce(scaleDown = 0.96f, enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = label,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun LanguageStep(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalStrings.current
    Column {
        Text(s.languageTitle, style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(s.languageHint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        // Тап меняет язык сразу — весь экран перерисовывается на новом языке.
        dev.aezochka.budscontrol.i18n.Strings.available.forEach { (code, title) ->
            val active = selected == code
            val bg by animateColorAsState(
                if (active) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "langBg",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .pressBounce { onSelect(code) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) scheme.onPrimary else scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.88f),
                )
                if (active) {
                    Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideStep() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(tr("guideTitle"), style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(tr("guideHint"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        // ===== Демка 1: плитки — тапни, они включаются =====
        Text(tr("guideTilesTitle"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(tr("guideTilesText"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoToggleTile(icon = Icons.Outlined.NoiseAware, label = tr("noiseControl"))
            DemoToggleTile(icon = Icons.Outlined.SportsEsports, label = tr("actGame"))
        }
        Spacer(Modifier.height(18.dp))

        // ===== Демка 2: жесты — раскрой строку, тапни действие =====
        Text(tr("guideGesturesTitle"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(tr("guideGesturesText"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        DemoGestureRow()
        Spacer(Modifier.height(18.dp))

        // ===== Демка 3: зарядные чипы у фото =====
        Text(tr("guideBudsTitle"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(tr("guideBudsText"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoBatteryChip(percent = 87, side = "L")
            DemoBatteryChip(percent = 64, side = "R")
        }
        Spacer(Modifier.height(18.dp))

        // ===== «Ещё» — кратко, списком =====
        Text(tr("guideMoreTitle"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(tr("guideMoreText"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** Плитка-демка: настоящая логика вкл/выкл, как на главном экране. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DemoToggleTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val scheme = MaterialTheme.colorScheme
    var on by remember { mutableStateOf(false) }
    BentoTile(
        Modifier.weight(1f),
        active = on,
        minHeight = 96.dp,
        onClick = { on = !on },
    ) { primary, secondary ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MorphIcon(active = on, icon = icon, tint = primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (on) tr("on") else tr("off"),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                )
            }
        }
    }
}

/** Строка жеста-демка: раскрывается, действие выбирается тапом по чипу. */
@Composable
private fun DemoGestureRow() {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "demoChev")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainer),
    ) {
        Row(
            Modifier.pressBounce { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("tap2"), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                Text(
                    picked ?: tr("guidePickHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (picked == null) scheme.outline else scheme.primary,
                )
            }
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
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 14.dp)) {
                listOf(
                    Icons.Outlined.PlayArrow to "actPlay",
                    Icons.Outlined.SkipNext to "actNext",
                    Icons.Outlined.MusicNote to "actVolUp",
                ).chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (icon, key) ->
                            val selected = picked == key
                            val bg by animateColorAsState(
                                if (selected) scheme.primary else scheme.surfaceContainerHigh,
                                Motion.effects(), label = "demoChipBg",
                            )
                            val tint by animateColorAsState(
                                if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                                Motion.effects(), label = "demoChipTint",
                            )
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bg)
                                    .pressBounce(scaleDown = 0.93f) { picked = key }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                                Text(
                                    tr(key),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tint,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Зарядный чип-демка: как индикаторы возле фото наушников. */
@Composable
private fun DemoBatteryChip(percent: Int, side: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "$percent%",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        Text(
            side,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DevicesStep(vm: BudsViewModel, onRequestBluetooth: () -> Unit) {


    val scheme = MaterialTheme.colorScheme
    val found by vm.found.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val profiles by vm.profiles.collectAsState()

    LaunchedEffect(Unit) {
        if (vm.bluetoothReady()) vm.startScan() else onRequestBluetooth()
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Наушники", style = MaterialTheme.typography.displayMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (scanning) tr("scanning") else "Тапни, чтобы добавить профиль",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        if (found.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surfaceContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Пока ничего не нашлось", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(
                    tr("takeOutOfCase"),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                // Кнопка на всю ширину: без modifier она сжималась по тексту
                // и выглядела кривой относительно остального экрана.
                PillButton(
                    LocalStrings.current.searchAgain,
                    filled = false,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.startScan() }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Один адрес может прийти и из сопряжённых, и из скана.
                items(found.distinctBy { it.address }, key = { it.address }) { device ->
                    val added = profiles.any { it.address == device.address }
                    DeviceRow(device, added) {
                        // Раньше всегда вызывался addProfile, поэтому «Убрать»
                        // ничего не делало и выглядело как мёртвая кнопка.
                        if (added) vm.removeProfileByAddress(device.address)
                        else vm.addProfile(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BluetoothScanner.Found, added: Boolean, onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (added) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "devBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .pressBounce(onClick = onAdd)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(18.dp))
                .background(if (added) scheme.onPrimary.copy(alpha = 0.18f) else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Headphones, null,
                tint = if (added) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                device.name ?: device.address,
                style = MaterialTheme.typography.bodyLarge,
                color = if (added) scheme.onPrimary else scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (device.bonded) append("Уже сопряжены с телефоном")
                    else append(LocalStrings.current.nearby)
                    device.rssi?.let { append(" · сигнал ${if (it > -60) "сильный" else "средний"}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (added) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (added) LocalStrings.current.remove else LocalStrings.current.add,
            style = MaterialTheme.typography.labelMedium,
            color = if (added) scheme.onPrimary else scheme.primary,
        )
    }
}
