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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class Tab(val label: String, val icon: ImageVector) {
    Buds("Наушники", Icons.Outlined.Headphones),
    Sound("Звук", Icons.Outlined.GraphicEq),
    Gestures("Жесты", Icons.Outlined.TouchApp),
    Extras("Экстра", Icons.Outlined.Science),
    Settings("Ещё", Icons.Outlined.Tune),
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
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
                    Tab.Buds -> BudsTab(vm)
                    Tab.Sound -> SoundTab(vm)
                    Tab.Gestures -> GesturesTab(vm)
                    Tab.Extras -> ExtrasTab(vm)
                    Tab.Settings -> SettingsTab(vm)
                }
            }
        }

        FloatingTabBar(
            current = pager.currentPage,
            onSelect = { index ->
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
                .padding(bottom = 14.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        )
    }
}

@Composable
private fun FloatingTabBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon, contentDescription = tab.label, tint = tint,
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(126.dp))
