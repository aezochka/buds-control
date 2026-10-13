package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.tr
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Вкладки: «Звук» убрана — эквалайзер и таймер живут на главной и в
 * настройках, а бар стал короче и по делу.
 *
 * Классическая тема — обычные иконки Material с подписями, как всегда.
 * Раздельная тема — эмодзи, которые меняются при каждом заходе на вкладку.
 */
private enum class Tab(val labelKey: String, val icon: ImageVector) {
    Buds("tabBuds", Icons.Outlined.Headphones),
    Gestures("tabGestures", Icons.Outlined.TouchApp),
    Settings("tabMore", Icons.Outlined.Tune);
}

/** Эмодзи для раздельной темы: меняются при каждом заходе на вкладку. */
private val tabEmojis = mapOf(
    Tab.Buds to listOf("🎧", "🎵", "🎶", "🔊", "🎤"),
    Tab.Gestures to listOf("👆", "✌️", "👌", "🤙", "👋"),
    Tab.Settings to listOf("⚙️", "🛠️", "✨", "🧰", "🪄"),
)

/**
 * Каркас.
 *
 * Overscroll-эффект (свечение-«полоски» по краям при попытке проскроллить
 * дальше) отключён для всего приложения: эти полосы рисовались поверх
 * интерфейса, включая верхнюю панель выбора устройства.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudsApp(vm: BudsViewModel) {
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()

    // Порядок вкладок зависит от темы бара. В раздельной теме наушники
    // стоят в ЦЕНТРЕ бара, поэтому порядок страниц: жесты — наушники — ещё.
    // Тогда свайп согласован с кнопками: кнопка слева — страница приходит
    // слева, кнопка справа — справа.
    val order = if (settings.barTheme == "split") {
        listOf(Tab.Gestures, Tab.Buds, Tab.Settings)
    } else {
        listOf(Tab.Buds, Tab.Gestures, Tab.Settings)
    }

    // Пока пользователь тащит чип профиля, свайп между вкладками выключен.
    var dragLock by remember { mutableStateOf(false) }

    // Текущая вкладка — табом, а не индексом: при смене темы порядок
    // меняется, и по индексу мы бы оказались на чужой странице.
    var currentTab by remember { mutableStateOf(Tab.Buds) }

    // Пейджер пересоздаётся при смене темы (порядок страниц другой),
    // начальная страница — текущая вкладка в новом порядке.
    val pager = key(settings.barTheme) {
        rememberPagerState(initialPage = order.indexOf(currentTab).coerceAtLeast(0)) { order.size }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            currentTab = order.getOrNull(page) ?: Tab.Buds
        }
    }

    // Эмодзи раздельной темы: смена страницы подкидывает новый.
    var emoji by remember {
        mutableStateOf(Tab.entries.associateWith { tabEmojis.getValue(it).first() })
    }
    LaunchedEffect(pager.currentPage) {
        val tab = order.getOrNull(pager.currentPage) ?: return@LaunchedEffect
        val set = tabEmojis.getValue(tab)
        val current = emoji[tab] ?: set.first()
        emoji = emoji + (tab to set.filter { it != current }.random())
    }

    fun select(tab: Tab) {
        vm.tick(dev.aezochka.budscontrol.audio.Feedback.Kind.Switch)
        scope.launch {
            val target = order.indexOf(tab)
            if ((target - pager.currentPage).absoluteValue > 1) {
                pager.scrollToPage(target)
            } else {
                pager.animateScrollToPage(target)
            }
        }
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                userScrollEnabled = !dragLock,
            ) { page ->
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                Box(
                    Modifier
                        .fillMaxSize()
                        // clipToBounds режет содержимое по границам страницы.
                        .clipToBounds()
                        .graphicsLayer {
                            val near = offset.absoluteValue <= 1f
                            alpha = if (near) 1f - (offset.absoluteValue * 0.4f).coerceIn(0f, 0.45f) else 0f
                        }
                        // Непрозрачный фон: иначе просвечивает соседняя страница.
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (order.getOrNull(page)) {
                        Tab.Buds -> BudsTab(vm, onDragActive = { dragLock = it })
                        Tab.Gestures -> GesturesTab(vm)
                        Tab.Settings -> SettingsTab(vm)
                        null -> {}
                    }
                }
            }

            FloatingTabBar(
                current = pager.currentPage,
                order = order,
                theme = settings.barTheme,
                emoji = emoji,
                onSelect = ::select,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            )
        }
    }
}

@Composable
private fun FloatingTabBar(
    current: Int,
    order: List<Tab>,
    theme: String,
    emoji: Map<Tab, String>,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (theme == "split") SplitTabBar(current, order, emoji, onSelect, modifier)
    else ClassicTabBar(current, order, onSelect, modifier)
}

/**
 * Тема «пилюля»: как всегда — Material-иконки с подписями в общем контейнере.
 */
@Composable
private fun ClassicTabBar(current: Int, order: List<Tab>, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEachIndexed { index, tab ->
            val selected = index == current
            val bg by animateColorAsState(
                if (selected) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "tabBg",
            )
            val tint by animateColorAsState(
                if (selected) scheme.onPrimary else scheme.onSurfaceVariant, Motion.effects(), label = "tabTint",
            )
            val corner by animateDpAsState(if (selected) 19.dp else 28.dp, Motion.spatial(), label = "tabCorner")
            val iconScale by animateFloatAsState(if (selected) 1.12f else 1f, Motion.spatial(), label = "tabIcon")

            Column(
                Modifier
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.93f) { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon, contentDescription = tab.label(),
                    tint = tint,
                    modifier = Modifier.size(21.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
                Spacer(Modifier.height(3.dp))
                Text(tab.label(), style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

/**
 * Тема «раздельные кнопки»: три отдельные квадратные кнопки с полукруглыми
 * углами, наушники — в центре и вытянуты по ширине, по бокам жесты и «ещё».
 * Только эмодзи, без текста.
 *
 * Порядок кнопок совпадает с порядком страниц: свайп и тап согласованы —
 * кнопка слева открывает страницу, которая приезжает слева.
 */
@Composable
private fun SplitTabBar(current: Int, order: List<Tab>, emoji: Map<Tab, String>, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.padding(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEach { tab ->
            val selected = tab == order.getOrNull(current)
            val bg by animateColorAsState(
                if (selected) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "splitBg",
            )
            // Квадратные кнопки с полукруглыми углами; центральная (наушники)
            // вытянута по ширине.
            val width = if (tab == Tab.Buds) 104.dp else 56.dp
            val height = 46.dp
            val corner = 23.dp
            val scale by animateFloatAsState(if (selected) 1.06f else 1f, Motion.spatial(), label = "splitScale")

            Box(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .size(width = width, height = height)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.88f) { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                EmojiSlot(
                    emoji = emoji[tab] ?: "🎧",
                    selected = selected,
                    fontSize = if (tab == Tab.Buds) 26 else 22,
                )
            }
        }
    }
}

/**
 * Эмодзи со сменой «прыжком»: новое появляется с пружинным увеличением,
 * старое сжимается. Именно это делает смену эмодзи заметной приколюхой.
 */
@Composable
private fun EmojiSlot(emoji: String, selected: Boolean, fontSize: Int) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, Motion.spatial(), label = "emojiScale")
    AnimatedContent(
        targetState = emoji,
        transitionSpec = {
            (scaleIn(initialScale = 0.3f, animationSpec = Motion.spatial()) + fadeIn(Motion.effects())) togetherWith
                (scaleOut(targetScale = 0.3f, animationSpec = Motion.effects()) + fadeOut(Motion.effects()))
        },
        label = "emojiSwap",
    ) { e ->
        Text(
            e,
            fontSize = fontSize.sp,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
private fun Tab.label(): String = tr(labelKey)

@Composable
fun BottomSpacer() = Spacer(Modifier.height(126.dp))
