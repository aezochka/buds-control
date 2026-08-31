package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.BudsViewModel
import dev.aezochka.budscontrol.device.ConnectionState

private enum class Tab(val label: String, val icon: ImageVector) {
    STATUS("Наушники", Icons.Outlined.Headphones),
    GESTURES("Жесты", Icons.Outlined.TouchApp),
    SETTINGS("Настройки", Icons.Outlined.Settings),
    ABOUT("О девайсе", Icons.Outlined.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudsApp(
    vm: BudsViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 720.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.connection == ConnectionState.DISCONNECTED) "Buds Control"
                            else state.deviceName,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.connection == ConnectionState.PROBING) {
                            Text("Определяю возможности…", style = MaterialTheme.typography.bodySmall)
                        } else if (state.connection == ConnectionState.CONNECTED) {
                            Text(
                                "Поддерживается ${state.caps.confirmedCount} из ${state.caps.totalCount} функций",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    if (state.connection == ConnectionState.CONNECTED) {
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            if (!wide && state.connection == ConnectionState.CONNECTED) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide && state.connection == ConnectionState.CONNECTED) {
                NavigationRail {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }

            when (state.connection) {
                ConnectionState.DISCONNECTED, ConnectionState.ERROR ->
                    DeviceListScreen(
                        vm = vm,
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = onRequestPermissions,
                        error = state.error,
                    )

                ConnectionState.CONNECTING, ConnectionState.PROBING ->
                    ConnectingScreen(
                        probing = state.connection == ConnectionState.PROBING,
                        onCancel = vm::disconnect,
                    )

                ConnectionState.CONNECTED -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    when (tab) {
                        Tab.STATUS -> StatusScreen(state, vm)
                        Tab.GESTURES -> GesturesScreen(state, vm)
                        Tab.SETTINGS -> SettingsScreen(state, vm)
                        Tab.ABOUT -> AboutScreen(state, vm)
                    }
                    Spacer(Modifier.height(112.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun ConnectingScreen(probing: Boolean, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                if (probing) "Спрашиваю наушники, что они умеют" else "Подключаюсь",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (probing) "Проверяю каждую функцию по очереди — это пара секунд"
                else "Открываю канал управления",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            Button(onClick = onCancel) { Text("Отмена") }
        }
    }
}
