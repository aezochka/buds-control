package dev.aezochka.budscontrol

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aezochka.budscontrol.ui.BudsApp
import dev.aezochka.budscontrol.ui.theme.BudsControlTheme

class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = requiredPermissions().all { result[it] == true }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BudsControlTheme {
                val vm: BudsViewModel = viewModel(factory = BudsViewModel.Factory)
                BudsApp(
                    vm = vm,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { requestPermissions.launch(requiredPermissions()) },
                )
            }
        }
        requestPermissions.launch(requiredPermissions())
    }
}
