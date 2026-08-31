package dev.aezochka.budscontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import dev.aezochka.budscontrol.data.EarbudProfile
import dev.aezochka.budscontrol.data.LocalStore
import dev.aezochka.budscontrol.data.UserSettings
import dev.aezochka.budscontrol.device.BluetoothScanner
import dev.aezochka.budscontrol.device.BudsSession
import dev.aezochka.budscontrol.device.LiveState
import dev.aezochka.budscontrol.proto.TouchAction
import dev.aezochka.budscontrol.proto.TouchSide
import dev.aezochka.budscontrol.proto.TouchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BudsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val scanner = BluetoothScanner(app)
    private val session = BudsSession(app)

    val live: StateFlow<LiveState> = session.state
    val settings: StateFlow<UserSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val profiles: StateFlow<List<EarbudProfile>> =
        store.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _found = MutableStateFlow<List<BluetoothScanner.Found>>(emptyList())
    val found: StateFlow<List<BluetoothScanner.Found>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    fun bluetoothReady(): Boolean = scanner.isEnabled() && scanner.hasPermission()

    /** Скан по Bluetooth: сопряжённые сразу + живой поиск рядом. */
    fun startScan() {
        if (_scanning.value) return
        _scanning.value = true
        _found.value = scanner.bonded()
        viewModelScope.launch {
            runCatching {
                scanner.discover().collect { device ->
                    _found.update { list ->
                        if (list.any { it.address == device.address }) list else list + device
                    }
                }
            }
            _scanning.value = false
        }
    }

    fun stopScan() { _scanning.value = false }

    /** Профиль создаётся из результата скана, руками MAC вводить не нужно. */
    fun addProfile(device: BluetoothScanner.Found) = viewModelScope.launch {
        val existing = store.profiles.first()
        if (existing.any { it.address == device.address }) return@launch
        val vendor = when {
            device.name?.contains("realme", true) == true -> "realme"
            device.name?.contains("oneplus", true) == true -> "OnePlus"
            device.name?.contains("oppo", true) == true || device.name?.contains("enco", true) == true -> "OPPO"
            else -> "—"
        }
        val profile = EarbudProfile(
            id = device.address,
            displayName = device.name ?: device.address,
            address = device.address,
            vendor = vendor,
            lastSeenMillis = System.currentTimeMillis(),
            isSelected = existing.isEmpty(),
        )
        store.saveProfiles(existing + profile)
    }

    fun selectProfile(id: String) = viewModelScope.launch {
        val list = store.profiles.first().map { it.copy(isSelected = it.id == id) }
        store.saveProfiles(list)
        list.firstOrNull { it.isSelected }?.let { session.connect(it.address, it.displayName) }
    }

    fun removeProfile(id: String) = viewModelScope.launch {
        store.saveProfiles(store.profiles.first().filterNot { it.id == id })
    }

    fun connectSelected() = viewModelScope.launch {
        store.profiles.first().firstOrNull { it.isSelected }?.let { session.connect(it.address, it.displayName) }
    }

    fun finishOnboarding(language: String) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(onboardingFinished = true, language = language))
        connectSelected()
    }

    fun setHistoryEnabled(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(historyEnabled = on))
    }
    fun setTileOrder(order: List<String>) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(tileOrder = order))
    }
    fun setTileSpan(key: String, span: Int) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(tileSpans = settings.value.tileSpans + (key to span)))
    }

    fun refresh() = session.refresh()
    fun setGameMode(on: Boolean) = session.setGameMode(on)
    fun findDevice(start: Boolean) = session.findDevice(start)
    fun setTouch(side: TouchSide, type: TouchType, action: TouchAction) = session.setTouch(side, type, action)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BudsViewModel(this[APPLICATION_KEY] as Application) }
        }
    }
}
