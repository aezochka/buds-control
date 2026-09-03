package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SpatialAudio
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.aezochka.budscontrol.data.ImageProbe
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr

/** Главная вкладка: фото продукта, живой заряд, bento-плитки. */
@Composable
fun BudsTab(vm: BudsViewModel, onDragActive: (Boolean) -> Unit = {}) {
    val live by vm.live.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selected = profiles.firstOrNull { it.isSelected }

    // Подключением управляет ViewModel, а не экран. HorizontalPager держит
    // beyondViewportPageCount = 0, поэтому при свайпе на другую вкладку эта
    // composable уничтожается, а при возврате LaunchedEffect срабатывал заново
    // и переподключался. Теперь связь живёт вне жизненного цикла экрана.

    // Возврат на экран: сбрасываем залипшее «подключаюсь» и переподключаемся.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Само определяет модель и ищет фото, если её нет в каталоге.
    LaunchedEffect(selected?.address) {
        selected?.let { vm.ensurePhoto(it.address, it.displayName) }
    }
    val foundPhoto by vm.foundPhoto.collectAsState()
    val photoLayout by vm.photoLayout.collectAsState()
    val chipTweak by vm.chipTweak.collectAsState()

    var showEq by remember { mutableStateOf(false) }
    var showAddDevice by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }

    // Шторки взаимоисключающие: две сразу роняли приложение.
    if (showAddDevice) {
        AddDeviceSheet(vm = vm) { showAddDevice = false }
    }

    if (showPhoto && selected != null) {
        PhotoSheet(
            vm = vm,
            address = selected.address,
            deviceName = selected.displayName,
        ) { showPhoto = false }
    }
    if (showEq) {
        EqualizerSheet(vm) { showEq = false }
    }
    if (showSleep) {
        SleepSheet(vm) { showSleep = false }
    }
    if (showVolume) {
        VolumeLimitSheet(vm) { showVolume = false }
    }

    // Скрытие панели по НАПРАВЛЕНИЮ жеста, как строка поиска в Telegram.
    // По индексу элемента не работало: элементов в списке всего три,
    // firstVisibleItemIndex почти не менялся и панель висела всегда.
    val listState = rememberLazyListState()
    // Шторка профилей прячется по НАПРАВЛЕНИЮ скролла — как было.
    // Датчик положения тут не участвует: он влияет только на размытие
    // названия под фото, а не на весь интерфейс.
    var barVisible by remember { mutableStateOf(true) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < -4f) barVisible = false   // палец вверх => контент вниз
                if (dy > 4f) barVisible = true
                return Offset.Zero
            }
        }
    }

    // Панель открывается сама, только когда список ДЕЙСТВИТЕЛЬНО в самом
    // верху и пользователь не тянет вниз. Раньше порог 12px срабатывал во
    // время жеста, панель раскрывалась и отдавала скролл списку — экран
    // прыгал наверх и свернуть бар было невозможно.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0 &&
                !listState.isScrollInProgress
        }.collect { atTop -> if (atTop) barVisible = true }
    }

    // Размытие названия — переключатель: держится, пока телефон не перевернут
    // ещё раз. Анимация нужна только для плавного перехода между двумя
    // состояниями, а не для отслеживания наклона.
    val budsSettings by vm.settings.collectAsState()
    val blurred by vm.nameBlurred.collectAsState()
    val blurTarget = if (budsSettings.hideNameOnScroll && blurred) 9f else 0f
    val nameBlurValue by animateFloatAsState(
        blurTarget,
        tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "nameBlur",
    )
    val nameBlur = nameBlurValue.dp

    Column(Modifier.fillMaxWidth().nestedScroll(scrollConnection)) {
        // Верхний бар анимируется тем же движением, что и нижний: раньше он
        // использовал пружину с перелётом и дёргался на скрытии.
        AnimatedVisibility(
            visible = barVisible,
            enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) +
                fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) +
                fadeOut(tween(160, easing = FastOutSlowInEasing)),
        ) {
            // Непрозрачный фон и слой выше фото. Без этого шторка во время
            // анимации выхода оставалась полупрозрачной поверх картинки —
            // получалась чёрная полоса, залезающая на наушники.
            Column(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                ProfileRow(
                    profiles = profiles,
                    onSelect = { vm.selectProfile(it) },
                    onReorder = { vm.reorderProfiles(it) },
                    onAdd = { showAddDevice = true },
                    onDragActive = onDragActive,
                    // Название скрывается и здесь: иначе блюр под фото есть,
                    // а в баре сверху модель по-прежнему читается.
                    nameBlur = nameBlur,
                )
            }
        }

        // Отступ под статус-бар держим ВСЕГДА: раньше он появлялся только
        // когда шторка скрыта, и фото прыгало под системную панель.
        Spacer(Modifier.statusBarsPadding())

        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            item {
                ProductHero(
                    name = selected?.displayName ?: tr("notSelected"),
                    left = live.batteryLeft,
                    right = live.batteryRight,
                    // Раньше свечение зависело только от chargingCase, который
                    // приходит лишь при открытом кейсе — поэтому его не было видно.
                    charging = live.chargingCase || live.budInCaseLeft || live.budInCaseRight,
                    inCaseLeft = live.budInCaseLeft,
                    inCaseRight = live.budInCaseRight,
                    foundPhoto = foundPhoto,
                    photoLayout = photoLayout,
                    chipTweak = chipTweak,
                    onPhotoClick = { showPhoto = true },
                    onPhotoLoaded = vm::onPhotoLoaded,
                    connecting = live.connecting,
                    // Фото и индикаторы ориентируются на системное Bluetooth-
                    // подключение, а не на служебный SPP. Иначе наушники уже
                    // играют музыку, но UI рисует их «в кейсе» до открытия SPP.
                    connected = live.bluetoothConnected,
                    nameBlur = nameBlur,
                    onRefresh = vm::refresh,
                )
            }
            item {
                BentoGrid(
                    vm = vm,
                    editing = false,
                    onEq = { showEq = true },
                    onSleep = { showSleep = true },
                    onVolume = { showVolume = true },
                )
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun ProductHero(
    name: String,
    left: Int?,
    right: Int?,
    charging: Boolean,
    inCaseLeft: Boolean,
    inCaseRight: Boolean,
    foundPhoto: String?,
    photoLayout: ImageProbe.Layout,
    chipTweak: BudsViewModel.ChipTweak,
    onPhotoClick: () -> Unit,
    onPhotoLoaded: (androidx.compose.ui.graphics.ImageBitmap) -> Unit,
    connecting: Boolean,
    connected: Boolean,
    /** Радиус размытия названия — растёт, когда телефон кладут экраном вниз. */
    nameBlur: androidx.compose.ui.unit.Dp,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(
        initialValue = 0f, targetValue = -11f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )

    // Наушники в кейсе физически отключаются от телефона, поэтому
    // отсутствие связи == кейс закрыт. Раньше это состояние не учитывалось.
    val bothInCase = !connected || (inCaseLeft && inCaseRight)
    val oneOut = connected && (inCaseLeft != inCaseRight)
    val restScale by animateFloatAsState(
        targetValue = when {
            bothInCase -> 0.8f
            oneOut -> 1.06f
            else -> 1f
        },
        animationSpec = Motion.spatial(),
        label = "restScale",
    )
    val restLift by animateFloatAsState(
        targetValue = when {
            bothInCase -> 16f
            oneOut -> -14f
            else -> 0f
        },
        animationSpec = Motion.spatial(),
        label = "restLift",
    )
    val restTilt by animateFloatAsState(
        targetValue = if (oneOut) -4.5f else 0f,
        animationSpec = Motion.spatial(),
        label = "restTilt",
    )
    // Закрытый кейс — приглушённая картинка: видно, что связи нет.
    val restAlpha by animateFloatAsState(
        targetValue = if (bothInCase) 0.55f else 1f,
        animationSpec = Motion.spatial(),
        label = "restAlpha",
    )

    // Пульсирующее свечение, когда кейс на зарядке.
    val glow by transition.animateFloat(
        initialValue = 0.25f, targetValue = if (charging) 1f else 0.25f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "chargeGlow",
    )

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1.05f)
                    // Тап по фото — выбор картинки и настройка индикаторов.
                    .pressBounce(scaleDown = 0.985f, onClick = onPhotoClick),
                contentAlignment = Alignment.Center,
            ) {
                if (charging) {
                    // Два круга: внешний дышит сильнее, внутренний мягче.
                    Box(
                        Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = glow * 0.16f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = glow * 0.22f)),
                    )
                }
                // Автоподбор: локальный ассет, иначе CDN вендора по имени
                // устройства, иначе нейтральный силуэт. Никаких ручных правок.
                // Фото — готовый PNG с вырезанным фоном из кеша приложения.
                // Белый фон убирается при загрузке, поэтому на тёмной теме
                // не остаётся светлого прямоугольника.
                val photoModel = foundPhoto?.let { java.io.File(it) }
                if (photoModel != null) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        // Разбираем картинку, чтобы понять, где наушники.
                        onSuccess = { state ->
                            onPhotoLoaded(state.result.drawable.toBitmap().asImageBitmap())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = offset + restLift
                                scaleX = restScale
                                scaleY = restScale
                                rotationZ = restTilt
                                alpha = restAlpha
                            },
                    )
                } else {
                    // Пока фото ищется — силуэт, а не пустота.
                    Icon(
                        Icons.Outlined.Headphones, null,
                        tint = scheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(150.dp)
                            .graphicsLayer {
                                translationY = offset + restLift
                                scaleX = restScale
                                scaleY = restScale
                                rotationZ = restTilt
                                alpha = restAlpha
                            },
                    )
                }
                // Позиции считаются по самому фото: чип стоит рядом со своим
                // наушником, а не в фиксированном углу картинки.
                BatteryChip(
                    percent = if (connected) left else null,
                    side = "L",
                    inCase = inCaseLeft || !connected,
                    scale = chipTweak.scale,
                    modifier = Modifier.align(BiasAlignment(
                        horizontalBias = (photoLayout.left.x + chipTweak.leftDx) * 2f - 1f,
                        verticalBias = (photoLayout.left.y + chipTweak.leftDy) * 2f - 1f,
                    )),
                )
                BatteryChip(
                    percent = if (connected) right else null,
                    side = "R",
                    inCase = inCaseRight || !connected,
                    scale = chipTweak.scale,
                    modifier = Modifier.align(BiasAlignment(
                        horizontalBias = (photoLayout.right.x + chipTweak.rightDx) * 2f - 1f,
                        verticalBias = (photoLayout.right.y + chipTweak.rightDy) * 2f - 1f,
                    )),
                )
            }

            // Отступ снизу: строка с названием и кнопкой обновления больше
            // не прилипает к первой плитке.
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                AnimatedContent(
                    targetState = when {
                        connecting -> "connecting"
                        connected -> "connected"
                        else -> "idle"
                    },
                    transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
                    label = "status",
                ) { status ->
                    when (status) {
                        "connecting" -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = scheme.primary)
                        "connected" -> PulsingDot(scheme.primary)
                        else -> PulsingDot(scheme.outline)
                    }
                }
                // Название и кнопка в одной строке: раньше кнопка стояла
                // ниже отдельным блоком и упиралась в плитки под ней.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        buildString {
                            append(name)
                            if (connecting) append(" · подключаюсь")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        // Размывается ТОЛЬКО название, когда телефон
                        // переворачивают экраном вниз. Значение непрерывное,
                        // поэтому эффект нарастает и спадает как рычаг.
                        modifier = Modifier.blur(nameBlur),
                    )
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainer)
                            .pressBounce(onClick = onRefresh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Refresh, tr("refresh"), tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryChip(
    percent: Int?,
    side: String,
    inCase: Boolean,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Чип виден ВСЕГДА: раньше при отсутствии данных он исчезал целиком,
    // и было непонятно, где вообще индикатор.
    val container by animateColorAsState(
        if (inCase) scheme.surfaceContainerLow else scheme.surfaceContainerHigh,
        Motion.effects(), label = "chipBg",
    )
    val labelColor by animateColorAsState(
        if (inCase) scheme.outline else scheme.onSurface,
        Motion.effects(), label = "chipFg",
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Вместо слова «в кейсе» — иконка кейса: короче и понятнее.
        AnimatedContent(
            targetState = percent,
            transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
            label = "pct",
        ) { value ->
            if (value != null) {
                Text(
                    "$value%",
                    style = MaterialTheme.typography.titleSmall,
                    color = labelColor,
                )
            } else {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = tr("inCase"),
                    tint = labelColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Text(
            side,
            style = MaterialTheme.typography.labelSmall,
            color = if (inCase) scheme.outlineVariant else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BentoGrid(
    vm: BudsViewModel,
    editing: Boolean,
    onEq: () -> Unit,
    onSleep: () -> Unit,
    onVolume: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val live by vm.live.collectAsState()
    val applePods by vm.applePods.collectAsState()

    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // Скрываем функцию ТОЛЬКО когда точно знаем, что её нет: опрос
        // завершён, а гарнитура о ней не сообщила. Пока идёт handshake или
        // связи нет — показываем всё, иначе рабочие плитки (как игровой режим)
        // пропадают на ровном месте.
        // Правило одно: после завершённого опроса на связи показываем ТОЛЬКО
        // подтверждённые гарнитурой функции. Ранее нажатая плитка больше не
        // может остаться на экране — именно так у T110 висел чужой шумодав.
        fun show(key: String): Boolean = when {
            live.probeComplete -> key in live.supported
            else -> true
        }

        val big = buildList {
            add("eq")
            if (show("anc")) add("anc")
            if (show("game")) add("game")
            if (show("spatial")) add("spatial")
            if (show("multipoint")) add("multipoint")
            // Из плиток AirPods оставлено только ношение: заряд дублировал
            // чипы на фото, а состояние крышки кейса ничего не решало.
            if (applePods != null) add("inear")
        }

        big.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                pair.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            // Сетка меняет размер плавно, когда набор плиток
                            // перестраивается после опроса возможностей.
                            .animateContentSize(Motion.spatial()),
                    ) {
                        // Смена содержимого плитки — с мягким проявлением,
                        // а не мгновенной подменой.
                        AnimatedContent(
                            targetState = key,
                            transitionSpec = {
                                (fadeIn(Motion.effects()) +
                                    scaleIn(Motion.spatial(), initialScale = 0.94f)) togetherWith
                                    (fadeOut(Motion.effects()) +
                                        scaleOut(Motion.spatial(), targetScale = 0.94f))
                            },
                            label = "tile",
                        ) { tileKey ->
                            TileContent(tileKey, vm, live, editing, onEq, onSleep, onVolume)
                        }
                    }
                }
                // Нечётный остаток не должен растягиваться на всю ширину.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Кейс — отдельной широкой плиткой: заряд кейса на фото не показан,
        // и без неё пропадала единственная индикация зарядки футляра.
        Box(Modifier.fillMaxWidth()) {
            TileContent("case", vm, live, editing, onEq, onSleep, onVolume)
        }

        // Дополнительные — компактный ряд.
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("sleep", "volume", "find", "firmware").forEach { key ->
                Box(Modifier.weight(1f)) {
                    TileContent(key, vm, live, editing, onEq, onSleep, onVolume, compact = true)
                }
            }
        }
    }
}

@Composable
fun ActionSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    supported: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 112.dp,
    onClick: () -> Unit,
) {
    BentoTile(
        modifier, active = active, minHeight = minHeight,
        onClick = if (supported) onClick else null,
    ) { primary, secondary ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MorphIcon(active = active, icon = icon, tint = if (supported) primary else secondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (supported) label else tr("unavailable"),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                )
            }
        }
    }
}

/** Иконка, которая при активации мягко подрастает и доворачивается. */
@Composable
fun MorphIcon(active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val scale by animateFloatAsState(
        if (active) 1.14f else 1f, Motion.spatial(), label = "iconScale",
    )
    val rotation by animateFloatAsState(
        if (active) -8f else 0f, Motion.spatial(), label = "iconRot",
    )
    Icon(
        icon, null, tint = tint,
        modifier = Modifier
            .size(26.dp)
            .scale(scale)
            .rotate(rotation),
    )
}


/** Полоска режима правки плиток: включается на самом главном экране. */

/** Покачивание плитки в режиме правки — как в макете. */
@Composable
private fun Modifier.wobble(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "tileWobble")
    val angle by transition.animateFloat(
        initialValue = -0.9f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobbleAngle",
    )
    return this.graphicsLayer { rotationZ = angle }
}

/** Вес плитки в ряду: 1..4 колонки, сохраняется в настройках. */
private fun spanWeight(settings: dev.aezochka.budscontrol.data.UserSettings, key: String, default: Float = 1f): Float =
    (settings.tileSpans[key] ?: default.toInt()).coerceIn(1, 4).toFloat()
