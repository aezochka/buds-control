package dev.aezochka.budscontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aezochka.budscontrol.ui.BudsApp
import dev.aezochka.budscontrol.ui.OnboardingScreen
import dev.aezochka.budscontrol.ui.theme.BudsControlTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Как только выдан обычный доступ к локации — просим фоновый отдельным шагом.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: BudsViewModel = viewModel(factory = BudsViewModel.Factory)
            // null = DataStore ещё читается. Показываем нейтральный фон,
            // иначе на старте моргает онбординг — это и был баг после рестарта.
            val loaded by vm.settingsOrNull.collectAsState()
            BudsControlTheme(
                accentKey = loaded?.accent ?: "lime",
                customAccent = loaded?.customAccent ?: 0L,
            ) {
                AnimatedContent(
                    targetState = loaded?.onboardingFinished,
                    transitionSpec = {
                        (fadeIn(tween(280)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(180))
                    },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    label = "root",
                ) { finished ->
                    when (finished) {
                        null -> Box(Modifier.fillMaxSize()) // короткий пустой кадр вместо мигания
                        true -> BudsApp(vm)
                        false -> OnboardingScreen(
                            vm = vm,
                            onRequestBluetooth = { permissionLauncher.launch(bluetoothPermissions()) },
                        )
                    }
                }
            }
        }
        // ОДНИМ вызовом: раньше запрос микрофона из LaunchedEffect перебивался
        // этим вызовом BT-разрешений, и диалог про звук вообще не появлялся.
        permissionLauncher.launch(startupPermissions())
    }

    private fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Микрофон нужен системному Visualizer, иначе полосы EQ не двигаются. */
    /**
     * На старте нужен только Bluetooth и уведомления для таймера сна.
     * Микрофон убран: приложение им не пользуется — визуализацию звука
     * через Visualizer я убрала, она требовала записи звука и давала фризы.
     */
    private fun startupPermissions(): Array<String> = buildList {
        addAll(bluetoothPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()


}
