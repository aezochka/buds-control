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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.LocalStrings
import dev.aezochka.budscontrol.i18n.tr
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Вкладки: «Звук» убрана — эквалайзер и таймер живут на главной и в
 * настройках, а бар стал короче и по делу.
 */
private enum class Tab(val labelKey: String) {
    Buds("tabBuds"),
    Gestures("tabGestures"),
    Settings("tabMore");
}

/**
 * Эмодзи вкладок. Приколюха: при каждом заходе на вкладку её эмодзи в баре
 * меняется на случайный из набора — как сменный аватар.
 */
private val tabEmojis = mapOf(
    Tab.Buds to listOf("🎧", "🎵", "🎶", "🔊", "🎤"),
    Tab.Gestures to listOf("👆", "✌️", "👌", "🤙", "👋"),
    Tab.Settings to listOf("⚙️", "🛠️", "✨", "🧰", "🪄"),
)

/**
 * Каркас. Страницы обрезаются по своим границам.
 *
 * Параллакс-сдвиг соседних страниц (translationX 0.12 ширины) убран:
 * край соседней страницы залезал в кадр «полоской» сбоку и перекрывал
 * верхнюю панель выбора устройства и открытые шторки. Осталась только
 * мягкая прозрачность при свайпе.
 */
@Composable
fun BudsApp(vm: BudsViewModel) {
    val pager = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()
    // Пока пользователь тащит чип профиля, свайп между вкладками выключен —
    // раньше жест перехватывался пейджером и экран уезжал в сторону.
    var dragLock by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsState()

    // Текущий эмодзи каждой вкладки; смена страницы подкидывает новый.
    var emoji by remember {
        mutableStateOf(Tab.entries.associateWith { tabEmojis.getValue(it).first() })
    }
    LaunchedEffect(pager.currentPage) {
        val tab = Tab.entries[pager.currentPage]
        val set = tabEmojis.getValue(tab)
        val current = emoji[tab] ?: set.first()
        // Свежий эмодзи, отличный от текущего.
        emoji = emoji + (tab to set.filter { it != current }.random())
    }

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
                when (Tab.entries[page]) {
                    Tab.Buds -> BudsTab(vm, onDragActive = { dragLock = it })
                    Tab.Gestures -> GesturesTab(vm)
                    Tab.Settings -> SettingsTab(vm)
                }
            }
        }

        FloatingTabBar(
            current = pager.currentPage,
            theme = settings.barTheme,
            emoji = emoji,
            onSelect = { index ->
                vm.tick(dev.aezochka.budscontrol.audio.Feedback.Kind.Switch)
                scope.launch {
                    // Прыжок на дальнюю вкладку: без прокрутки через середину.
                    if ((index - pager.currentPage).absoluteValue > 1) {
                        pager.scrollToPage(index)
                    } else {
                        pager.animateScrollToPage(index)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        )
    }
}

@Composable
private fun FloatingTabBar(
    current: Int,
    theme: String,
    emoji: Map<Tab, String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (theme == "split") SplitTabBar(current, emoji, onSelect, modifier)
    else ClassicTabBar(current, emoji, onSelect, modifier)
}

/**
 * Тема «пилюля»: как раньше — общий контейнер, эмодзи + подпись.
 */
@Composable
private fun ClassicTabBar(current: Int, emoji: Map<Tab, String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            val selected = index == current
            val bg by animateColorAsState(
                if (selected) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "tabBg",
            )
            val tint by animateColorAsState(
                if (selected) scheme.onPrimary else scheme.onSurfaceVariant, Motion.effects(), label = "tabTint",
            )
            val corner by animateDpAsState(if (selected) 19.dp else 28.dp, Motion.spatial(), label = "tabCorner")

            Column(
                Modifier
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.93f) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmojiSlot(
                    emoji = emoji[tab] ?: "🎧",
                    selected = selected,
                    fontSize = 21,
                )
                Spacer(Modifier.height(3.dp))
                Text(tab.label(), style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

/**
 * Тема «раздельные кнопки»: три отдельные капсулы, наушники — в центре и
 * крупнее, по бокам жесты и «ещё». Только эмодзи, без текста.
 */
@Composable
private fun SplitTabBar(current: Int, emoji: Map<Tab, String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.padding(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Порядок визуальный: жесты — наушники — ещё.
        listOf(Tab.Gestures to 1, Tab.Buds to 0, Tab.Settings to 2).forEach { (tab, index) ->
            val selected = index == current
            val bg by animateColorAsState(
                if (selected) scheme.primary else scheme.surfaceContainer, Motion.effects(), label = "splitBg",
            )
            val corner by animateDpAsState(if (selected) 28.dp else 22.dp, Motion.spatial(), label = "splitCorner")
            val scale by animateFloatAsState(if (selected) 1.08f else 1f, Motion.spatial(), label = "splitScale")
            val side = if (tab == Tab.Buds) 58.dp else 50.dp

            Box(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .size(side)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.88f) { onSelect(index) },
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
    val scheme = MaterialTheme.colorScheme
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
