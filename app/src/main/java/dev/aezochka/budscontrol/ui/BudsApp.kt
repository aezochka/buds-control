package dev.aezochka.budscontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.i18n.LocalStrings
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class Tab(val icon: ImageVector) {
    Buds(Icons.Outlined.Headphones),
    Sound(Icons.Outlined.GraphicEq),
    Gestures(Icons.Outlined.TouchApp),
    Settings(Icons.Outlined.Tune);
}

@Composable
private fun Tab.label(): String {
    val s = LocalStrings.current
    return when (this) {
        Tab.Buds -> s.tabBuds
        Tab.Sound -> s.tabSound
        Tab.Gestures -> s.tabGestures
        Tab.Settings -> s.tabMore
    }
}

/**
 * Каркас. Четыре вкладки, чтобы звук и жесты не были свалены в одну.
 * Страница обрезается по своим границам — раньше текст соседней вкладки
 * заезжал в кадр во время свайпа.
 */
@Composable
fun BudsApp(vm: BudsViewModel) {
    val pager = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()
    // Пока пользователь тащит чип профиля, свайп между вкладками выключен —
    // раньше жест перехватывался пейджером и экран уезжал в сторону.
    var dragLock by remember { mutableStateOf(false) }

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
                    // graphicsLayer{clip} этого не делал для LazyColumn, из-за
                    // чего текст соседней вкладки заезжал в кадр при свайпе.
                    .clipToBounds()
                    .graphicsLayer {
                        val near = offset.absoluteValue <= 1f
                        translationX = if (near) size.width * offset * 0.12f else 0f
                        alpha = if (near) 1f - (offset.absoluteValue * 0.4f).coerceIn(0f, 0.45f) else 0f
                    }
                    // Непрозрачный фон: иначе просвечивает соседняя страница.
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (Tab.entries[page]) {
                    Tab.Buds -> BudsTab(vm, onDragActive = { dragLock = it })
                    Tab.Sound -> SoundTab(vm)
                    Tab.Gestures -> GesturesTab(vm)
                    Tab.Settings -> SettingsTab(vm)
                }
            }
        }

        FloatingTabBar(
            current = pager.currentPage,
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
private fun FloatingTabBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            // Прямоугольник со скруглением, по ширине контента — раньше
            // растягивался на весь экран и выглядел слишком длинным.
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
            val corner by animateDpAsState(if (selected) 19.dp else 28.dp, Motion.spatialFast(), label = "tabCorner")
            val iconScale by animateFloatAsState(if (selected) 1.12f else 1f, Motion.spatial(), label = "tabIcon")

            Column(
                Modifier
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.93f) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon, contentDescription = tab.label(), tint = tint,
                    modifier = Modifier.size(21.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
                Spacer(Modifier.height(3.dp))
                Text(tab.label(), style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(126.dp))
