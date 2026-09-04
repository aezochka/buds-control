package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.UnfoldLess
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType

/** Настройки: профили, тема, плитки, диагностика. */
@Composable
fun SettingsTab(vm: BudsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val live by vm.live.collectAsState()
    var showTheme by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showClickSounds by remember { mutableStateOf(false) }
    // Открыт только один раздел за раз: так список остаётся коротким.
    var openSection by remember { mutableStateOf<String?>(null) }
    val catalogIds by vm.catalogModelIds.collectAsState()
    val sleepMin by vm.sleepMinutes.collectAsState()

    if (showCatalog) {
        ModelCatalogSheet(
            onPick = { vm.addModelFromCatalog(it) },
            addedIds = catalogIds,
            onRemove = { vm.removeCatalogModel(it.modelId) },
            onDismiss = { showCatalog = false },
        )
    }
    val limit by vm.volumeLimit.collectAsState()
    if (showEq) { EqualizerSheet(vm) { showEq = false } }
    if (showLang) { LanguageSheet(vm) { showLang = false } }
    if (showSleep) { SleepSheet(vm) { showSleep = false } }
    if (showVolume) { VolumeLimitSheet(vm) { showVolume = false } }
    if (showClickSounds) {
        ClickSoundSheet(
            current = settings.clickSound,
            vm = vm,
            onDismiss = { showClickSounds = false },
        )
    }
    if (showChangelog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("Чейнджлог") },
            text = {
                androidx.compose.foundation.layout.Column {
                    dev.aezochka.budscontrol.data.changelog.forEach { c ->
                        Text(c.version, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        c.adds.forEach { Text("+"+it, style = MaterialTheme.typography.bodySmall) }
                        c.removes.forEach { Text("-"+it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick={showChangelog=false}){ Text("OK") } }
        )
    }
    if (showTheme) {
        ThemeSheet(
            current = settings.accent,
            customAccent = settings.customAccent,
            dynamicEnabled = settings.dynamicColor,
            onPick = { vm.setAccent(it) },
            onCustom = { vm.setCustomAccent(it) },
            onDynamic = { vm.setDynamicColor(it) },
            onDismiss = { showTheme = false },
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Spacer(Modifier.statusBarsPadding().height(14.dp)) }
        item {
            // Строки собраны в блоки: внутри блока стык прямой, а сам блок
            // скруглён снаружи. Между блоками отступ больше — так видно,
            // где одна группа заканчивается.
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsCategory(
                    icon = Icons.Outlined.GraphicEq,
                    title = tr("sectionSound"),
                    subtitle = tr("sectionSoundHint"),
                    expanded = openSection == "sound",
                    onToggle = { vm.tick(); openSection = if (openSection == "sound") null else "sound" },
                ) {
                    SettingsRow(
                        Icons.Outlined.GraphicEq, "Эквалайзер",
                        "Системный: полосы, басы, пресеты",
                        shape = groupShape(2, 5),
                        onClick = { showEq = true },
                    )
                    SettingsRow(
                        Icons.Outlined.Bedtime, tr("sleepTimer"),
                        if (sleepMin > 0) "Активен: $sleepMin мин" else "Пауза по времени",
                        shape = groupShape(3, 5),
                        onClick = { showSleep = true },
                    )
                    SettingsRow(
                        Icons.Outlined.VolumeUp, tr("volumeLimit"),
                        if (limit > 0) "Не выше $limit%" else "Защита слуха",
                        shape = groupShape(4, 5),
                        onClick = { showVolume = true },
                    )
                }

                SettingsCategory(
                    icon = Icons.Outlined.Vibration,
                    title = tr("sectionFeedback"),
                    subtitle = tr("sectionFeedbackHint"),
                    expanded = openSection == "feedback",
                    onToggle = { vm.tick(); openSection = if (openSection == "feedback") null else "feedback" },
                ) {
                    SettingsToggle(
                        icon = Icons.Outlined.Vibration,
                        title = tr("haptics"),
                        subtitle = tr("hapticsHint"),
                        checked = settings.hapticFeedback,
                        shape = groupShape(1, 7),
                        onToggle = { vm.tick(); vm.setHaptic(it) },
                    )
                    SettingsToggle(
                        icon = Icons.Outlined.MusicNote,
                        title = tr("sounds"),
                        subtitle = tr("soundsHint"),
                        checked = settings.soundEffects,
                        shape = groupShape(2, 7),
                        onToggle = { vm.setSoundEffects(it) },
                    )
                    SettingsRow(
                        Icons.Outlined.MusicNote, tr("clickSound"),
                        clickSoundTitle(settings.clickSound),
                        shape = groupShape(3, 7),
                        onClick = { showClickSounds = true },
                    )
                    SettingsToggle(
                        icon = Icons.Outlined.Bolt,
                        title = tr("autoConnect"),
                        subtitle = tr("autoConnectHint"),
                        checked = settings.autoConnect,
                        shape = groupShape(4, 6),
                        onToggle = { vm.setAutoConnect(it) },
                    )
                    SettingsToggle(
                        icon = Icons.Outlined.UnfoldLess,
                        title = tr("hideName"),
                        subtitle = tr("hideNameHint"),
                        checked = settings.hideNameOnScroll,
                        shape = groupShape(5, 6),
                        onToggle = { vm.setHideNameOnScroll(it) },
                    )
                    SettingsToggle(icon = Icons.Outlined.NightsStay, title = "AMOLED чёрная тема", subtitle = if (settings.amoledBlack) "Глубокий чёрный" else "Тёмная", checked = settings.amoledBlack, shape = groupShape(6, 6), onToggle = { vm.setAmoled(it) })
                    SettingsToggle(icon = Icons.Outlined.PausePresentation, title = "Автопауза при снятии", subtitle = "Пауза когда снял наушник", checked = settings.autoPauseOnRemoval, shape = groupShape(6, 6), onToggle = { vm.setAutoPause(it) })

                }

                SettingsCategory(
                    icon = Icons.Outlined.Palette,
                    title = tr("sectionApp"),
                    subtitle = tr("sectionAppHint"),
                    expanded = openSection == "app",
                    onToggle = { vm.tick(); openSection = if (openSection == "app") null else "app" },
                ) {
                    SettingsRow(Icons.Outlined.Description, "Чейнджлог", "Что нового", shape = groupShape(1, 6), onClick = { showChangelog = true })
                    UpdateRow(vm, shape = groupShape(2, 6))
                    CrashLogRow()
                    SettingsRow(
                        Icons.Outlined.Language, tr("language"),
                        tr("langCount"),
                        shape = groupShape(1, 4),
                        onClick = { showLang = true },
                    )
                    SettingsRow(
                        Icons.Outlined.Palette, "Тема",
                        "Акцент: ${dev.aezochka.budscontrol.data.Accent.from(settings.accent).title}",
                        shape = groupShape(2, 4),
                        onClick = { showTheme = true },
                    )
                    AuthorRow(shape = groupShape(4, 5))
                }

                SettingsCategory(
                    icon = Icons.Outlined.DashboardCustomize,
                    title = tr("sectionDevices"),
                    subtitle = tr("sectionDevicesHint"),
                    expanded = openSection == "devices",
                    onToggle = { vm.tick(); openSection = if (openSection == "devices") null else "devices" },
                ) {
                    // Каталог моделей — справочник, поэтому здесь же.
                    SettingsRow(
                        Icons.Outlined.FormatListBulleted, tr("catalogTitle"),
                        tr("catalogOpenHint"),
                        shape = groupShape(1, profiles.size + 2),
                        onClick = { showCatalog = true },
                    )
                    if (profiles.isNotEmpty()) {
                        profiles.forEachIndexed { index, profile ->
                            SettingsRow(
                                Icons.Outlined.DashboardCustomize,
                                profile.displayName,
                                "${profile.vendor} · ${profile.address}" + if (profile.isSelected) " · активный" else "",
                                shape = groupShape(index + 2, profiles.size + 2),
                                onClick = { vm.selectProfile(profile.id) },
                            )
                        }
                    }
                }
            }
        }
        item { BottomSpacer() }
    }
}

/**
 * Раздел-кнопка: тап раскрывает вложенные настройки.
 *
 * Раньше все строки лежали одним списком и это читалось как куча. Теперь
 * верхний уровень — короткий список категорий, а содержимое раскрывается
 * по нажатию.
 */
@Composable
private fun SettingsCategory(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "catChev")

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(if (expanded) groupShape(0, 2) else RoundedCornerShape(24.dp))
                .background(if (expanded) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainer)
                .pressBounce(onClick = onToggle)
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(17.dp))
                    .background(if (expanded) scheme.primary.copy(alpha = 0.2f) else scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null,
                    tint = if (expanded) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Icon(
                Icons.Outlined.ExpandMore, null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
            exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(scheme.surfaceContainer)
    if (onClick != null) base = base.pressBounce(onClick = onClick)
    Row(
        base.padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(23.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    onToggle: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (checked) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "rowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .pressBounce { onToggle(!checked) }
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(17.dp))
                .background(if (checked) scheme.onPrimary.copy(alpha = 0.16f) else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (checked) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
        }
        // У переключателя подпись остаётся под заголовком: справа уже стоит
        // сам тумблер, и значение там столкнулось бы с ним.
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (checked) scheme.onPrimary else scheme.onSurface)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = if (checked) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
            )
        }
        AnimatedSwitch(checked)
    }
}

/** Свой переключатель: без системного highlight, с пружинным бегунком. */
@Composable
fun AnimatedSwitch(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        if (checked) scheme.onPrimary.copy(alpha = 0.28f) else scheme.surfaceContainerHighest,
        Motion.effects(), label = "swTrack",
    )
    val offset by animateDpAsState(if (checked) 26.dp else 6.dp, Motion.spatialFast(), label = "swOffset")
    val knobSize by animateDpAsState(if (checked) 22.dp else 16.dp, Motion.spatialFast(), label = "swKnob")
    Box(
        Modifier.size(width = 56.dp, height = 34.dp).clip(CircleShape).background(trackColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = offset)
                .size(knobSize)
                .clip(CircleShape)
                .background(if (checked) scheme.onPrimary else scheme.outline),
        )
    }
}
