package dev.aezochka.budscontrol.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Buds("Наушники", Icons.Outlined.Headphones),
    History("История", Icons.Outlined.History),
    Sound("Звук", Icons.Outlined.GraphicEq),
    Settings("Настройки", Icons.Outlined.Tune),
}

/**
 * Каркас приложения. Ровно четыре вкладки и четыре кнопки — никаких
 * дублирующих переходов, как было в WebView-версии с пятой кнопкой.
 * Между вкладками работает настоящий свайп через HorizontalPager.
 */
@Composable
fun BudsApp(vm: BudsViewModel) {
    val pager = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
        ) { page ->
            when (Tab.entries[page]) {
                Tab.Buds -> BudsTab(vm)
                Tab.History -> HistoryTab(vm)
                Tab.Sound -> SoundTab(vm)
                Tab.Settings -> SettingsTab(vm)
            }
        }

        FloatingTabBar(
            current = pager.currentPage,
            onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        )
    }
}

@Composable
private fun FloatingTabBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            val selected = index == current
            val bg by animateColorAsState(
                if (selected) scheme.primary else scheme.surfaceContainer,
                Motion.effects(), label = "tabBg",
            )
            val tint by animateColorAsState(
                if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                Motion.effects(), label = "tabTint",
            )
            val corner by animateDpAsState(if (selected) 17.dp else 28.dp, Motion.spatialFast(), label = "tabCorner")
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .pressBounce(scaleDown = 0.9f) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Общий контейнер вкладки: контент уезжает под плавающий таб-бар. */
@Composable
fun TabScaffold(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(120.dp))
