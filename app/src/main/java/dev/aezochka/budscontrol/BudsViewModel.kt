package dev.aezochka.budscontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import dev.aezochka.budscontrol.audio.AudioTools
import dev.aezochka.budscontrol.audio.Feedback
import dev.aezochka.budscontrol.notify.SleepNotifier
import dev.aezochka.budscontrol.audio.SystemAudioFx
import kotlinx.coroutines.Job
import dev.aezochka.budscontrol.data.CaseBatteryMemo
import dev.aezochka.budscontrol.data.ImageProbe
import dev.aezochka.budscontrol.data.PhotoFinder
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BudsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val scanner = BluetoothScanner(app)
    private val session = BudsSession(app)

    val live: StateFlow<LiveState> = session.state
    /** null пока DataStore не прочитан — MainActivity на это время держит сплэш. */
    val settingsOrNull: StateFlow<UserSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<UserSettings> =
        store.settings.filterNotNull().stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
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
            // Discovery itself can run until the platform stops it. UI never blocks on it.
            launch { delay(12_000); _scanning.value = false }
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
        val newList = (existing + profile).map { it.copy(isSelected = it.id == profile.id) }
        store.saveProfiles(newList)
        // User explicitly tapped this device: connect once, not in a loop.
        session.connect(profile.address, profile.displayName)
    }

    fun selectProfile(id: String) = viewModelScope.launch {
        val list = store.profiles.first().map { it.copy(isSelected = it.id == id) }
        store.saveProfiles(list)
        list.firstOrNull { it.isSelected }?.let { session.connect(it.address, it.displayName) }
    }

    fun removeProfileByAddress(address: String) = viewModelScope.launch {
        val list = store.profiles.first().filterNot { it.address == address }
        // Если сняли активный — активируем первый оставшийся.
        val fixed = if (list.none { it.isSelected } && list.isNotEmpty()) {
            list.mapIndexed { i, p -> p.copy(isSelected = i == 0) }
        } else list
        store.saveProfiles(fixed)
    }

    /** Порядок профилей на главной — перетаскиванием. */
    fun reorderProfiles(order: List<String>) = viewModelScope.launch {
        val current = store.profiles.first()
        val sorted = order.mapNotNull { id -> current.firstOrNull { it.id == id } }
        store.saveProfiles(sorted + current.filterNot { it.id in order })
    }

    fun removeProfile(id: String) = viewModelScope.launch {
        store.saveProfiles(store.profiles.first().filterNot { it.id == id })
    }

    fun connectSelected() = viewModelScope.launch {
        val profile = store.profiles.first().firstOrNull { it.isSelected } ?: return@launch
        // Сохраняем заряд кейса, когда гарнитура его прислала.
        session.onCaseReported = { percent, charging ->
            viewModelScope.launch {
                val list = store.profiles.first().map {
                    if (it.id == profile.id) it.copy(
                        caseBattery = CaseBatteryMemo(percent, charging, System.currentTimeMillis())
                    ) else it
                }
                store.saveProfiles(list)
            }
        }
        session.connect(profile.address, profile.displayName)
        // Подставляем последнее известное значение: кейс молчит, когда закрыт.
        profile.caseBattery?.let { session.seedCaseBattery(it.percent, it.charging, it.atMillis) }
    }

    fun finishOnboarding(language: String) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(onboardingFinished = true, language = language))
        connectSelected()
    }


    fun setTileOrder(order: List<String>) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(tileOrder = order))
    }
    /** Перемещение плитки на шаг влево/вправо при перетаскивании. */
    fun moveTile(key: String, step: Int) = viewModelScope.launch {
        val order = settings.value.tileOrder.toMutableList()
        val from = order.indexOf(key)
        if (from < 0) return@launch
        val to = (from + step).coerceIn(0, order.lastIndex)
        if (to == from) return@launch
        order.removeAt(from)
        order.add(to, key)
        store.saveSettings(settings.value.copy(tileOrder = order))
    }

    /** Тап в режиме правки: 1 → 2 → 3 → 4 → 1 колонки. */
    fun cycleTileSpan(key: String) = viewModelScope.launch {
        val current = settings.value.tileSpans[key] ?: 1
        val next = if (current >= 4) 1 else current + 1
        store.saveSettings(settings.value.copy(tileSpans = settings.value.tileSpans + (key to next)))
    }

    fun setTileSpan(key: String, span: Int) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(tileSpans = settings.value.tileSpans + (key to span)))
    }

    fun setAccent(key: String) = viewModelScope.launch {
        // Выбор пресета сбрасывает свой цвет.
        store.saveSettings(settings.value.copy(accent = key, customAccent = 0L))
    }

    /** Свой цвет темы: ARGB, перебивает пресет. */
    /**
     * Язык интерфейса. Меняется мгновенно: значение кладём в StateFlow сразу,
     * а запись в DataStore идёт следом.
     */
    private val _uiLanguage = MutableStateFlow("ru")
    val uiLanguage: StateFlow<String> = _uiLanguage.asStateFlow()

    fun setLanguage(code: String) {
        _uiLanguage.value = code
        viewModelScope.launch { store.saveSettings(settings.value.copy(language = code)) }
    }

    fun setSoundEffects(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(soundEffects = on))
        if (on) tick(Feedback.Kind.On)
    }

    fun setHaptic(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(hapticFeedback = on))
    }

    fun setAutoConnect(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(autoConnect = on))
    }

    fun setLowBatteryAlert(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(lowBatteryAlert = on))
    }

    fun setCustomAccent(argb: Long) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(customAccent = argb))
    }

    fun toggleTile(key: String) = viewModelScope.launch {
        val hidden = settings.value.hiddenTiles
        store.saveSettings(
            settings.value.copy(hiddenTiles = if (key in hidden) hidden - key else hidden + key)
        )
    }

    fun setTileOrderList(order: List<String>) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(tileOrder = order))
    }


    fun refresh() = session.refresh()
    fun setGameMode(on: Boolean) = session.setGameMode(on)
    fun setSpatialAudio(on: Boolean) = session.setSpatialAudio(on)
    fun setMultipoint(on: Boolean) = session.setMultipoint(on)

    /** Найденное в сети фото для модели, которой нет в каталоге. */
    private val _foundPhoto = MutableStateFlow<String?>(null)
    val foundPhoto: StateFlow<String?> = _foundPhoto.asStateFlow()

    /** Куда ставить индикаторы заряда — считается по самому фото. */
    private val _photoLayout = MutableStateFlow(ImageProbe.Layout.Default)
    val photoLayout: StateFlow<ImageProbe.Layout> = _photoLayout.asStateFlow()

    fun onPhotoLoaded(image: androidx.compose.ui.graphics.ImageBitmap) = viewModelScope.launch {
        _photoLayout.value = ImageProbe.analyze(image)
    }

    fun ensurePhoto(deviceName: String) = viewModelScope.launch {
        _foundPhoto.value = null
        _foundPhoto.value = PhotoFinder.find(getApplication(), deviceName)
    }




    fun startAddDevice() = startScan()

    // ===== Системный звук: работает независимо от протокола гарнитуры =====
    init {
        viewModelScope.launch {
            val saved = store.settings.filterNotNull().first()
            _uiLanguage.value = if (saved.language == "system") systemLanguage() else saved.language
        }
    }

    private fun systemLanguage(): String = when (java.util.Locale.getDefault().language) {
        "en" -> "en"
        "uk" -> "uk"
        else -> "ru"
    }

    private val audioFx = SystemAudioFx()
    private val audioTools = AudioTools(getApplication())
    private val feedback = Feedback(getApplication())

    /** Щелчок на действие: звук и вибро по настройкам пользователя. */
    fun tick(kind: Feedback.Kind = Feedback.Kind.Tap) {
        val s = settings.value
        feedback.play(kind, soundOn = s.soundEffects, hapticOn = s.hapticFeedback)
    }

    private val _fxReady = MutableStateFlow(false)
    val fxReady: StateFlow<Boolean> = _fxReady.asStateFlow()
    private val _eqGains = MutableStateFlow<List<Int>>(emptyList())
    val eqGains: StateFlow<List<Int>> = _eqGains.asStateFlow()

    fun bandFrequencies(): List<Int> = audioFx.bandFrequencies
    fun gainRangeDb(): Pair<Int, Int> = (audioFx.minGainMb / 100) to (audioFx.maxGainMb / 100)

    fun attachAudioFx() {
        if (audioFx.attach()) {
            _fxReady.value = true
            _eqGains.value = audioFx.currentGainsDb()
        }
    }

    fun setBandDb(band: Int, db: Int) {
        audioFx.setBandDb(band, db)
        _eqGains.value = audioFx.currentGainsDb()
    }


    fun applyEqPreset(gains: List<Int>) {
        audioFx.applyPreset(gains)
        _eqGains.value = audioFx.currentGainsDb()
    }

    fun resetEq() {
        repeat(audioFx.bandCount) { audioFx.setBandDb(it, 0) }
        _eqGains.value = audioFx.currentGainsDb()
    }

    // ===== Таймер сна =====
    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes.asStateFlow()
    private val _sleepLeft = MutableStateFlow(0L)
    val sleepLeft: StateFlow<Long> = _sleepLeft.asStateFlow()
    private var sleepJob: Job? = null

    fun setSleepTimerMinutes(minutes: Int) {
        sleepJob?.cancel()
        _sleepMinutes.value = minutes
        if (minutes <= 0) { _sleepLeft.value = 0; return }
        sleepJob = viewModelScope.launch {
            var left = minutes * 60L
            while (left > 0) {
                _sleepLeft.value = left
                delay(1000)
                left--
            }
            _sleepLeft.value = 0
            _sleepMinutes.value = 0
            audioTools.pausePlayback()
            // Слышимый и видимый финал вместо тихой паузы.
            feedback.play(Feedback.Kind.Alarm, soundOn = true, hapticOn = true)
            SleepNotifier.notifyFinished(getApplication())
        }
    }

    // ===== Лимит громкости =====
    private val _volumeLimit = MutableStateFlow(0)
    val volumeLimit: StateFlow<Int> = _volumeLimit.asStateFlow()
    private var limitJob: Job? = null

    fun setVolumeLimitPercent(percent: Int) {
        _volumeLimit.value = percent
        limitJob?.cancel()
        if (percent <= 0) return
        // Держим лимит: система может поднять громкость кнопками.
        limitJob = viewModelScope.launch {
            while (_volumeLimit.value > 0) {
                audioTools.enforceLimit(_volumeLimit.value)
                delay(700)
            }
        }
    }
    fun findDevice(start: Boolean) = session.findDevice(start)
    fun setTouch(side: TouchSide, type: TouchType, action: TouchAction) = session.setTouch(side, type, action)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BudsViewModel(this[APPLICATION_KEY] as Application) }
        }
    }
}
