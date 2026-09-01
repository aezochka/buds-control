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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class Tab(val label: String, val icon: ImageVector) {
    Buds("Наушники", Icons.Outlined.Headphones),
    Sound("Звук", Icons.Outlined.GraphicEq),
    Settings("Настройки", Icons.Outlined.Tune),
}

/**
 * Три вкладки, у каждой подпись под иконкой — раньше было непонятно,
 * что делают кнопки внизу. Свайп через HorizontalPager с плавным
 * сдвигом и затуханием страницы (shared-axis X).
 */
@Composable
fun BudsApp(vm: BudsViewModel) {
    val pager = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            // Shared-axis X: страница уезжает и притухает, а не дёргается.
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Box(
                Modifier.graphicsLayer {
                    translationX = size.width * offset * 0.22f
                    alpha = 1f - (offset.absoluteValue * 0.45f).coerceIn(0f, 0.6f)
                }
            ) {
                when (Tab.entries[page]) {
                    Tab.Buds -> BudsTab(vm)
                    Tab.Sound -> SoundTab(vm)
                    Tab.Settings -> SettingsTab(vm)
                }
            }
        }

        FloatingTabBar(
            current = pager.currentPage,
            onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        )
    }
}

@Composable
private fun FloatingTabBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(34.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            val corner by animateDpAsState(if (selected) 20.dp else 30.dp, Motion.spatialFast(), label = "tabCorner")
            val iconScale by animateFloatAsState(if (selected) 1.1f else 1f, Motion.spatial(), label = "tabIcon")

            Column(
                Modifier
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.93f) { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon, contentDescription = tab.label, tint = tint,
                    modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(122.dp))
