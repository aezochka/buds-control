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
import dev.aezochka.budscontrol.update.Updater
import dev.aezochka.budscontrol.data.EarbudProfile
import dev.aezochka.budscontrol.data.LocalStore
import dev.aezochka.budscontrol.data.ModelSpec
import dev.aezochka.budscontrol.data.UserSettings
import dev.aezochka.budscontrol.device.BluetoothLinkMonitor
import dev.aezochka.budscontrol.device.BluetoothScanner
import dev.aezochka.budscontrol.device.BudsSession
import dev.aezochka.budscontrol.device.ApplePodsScanner
import dev.aezochka.budscontrol.device.FaceDownSensor
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val scanner = BluetoothScanner(app)
    private val session = BudsSession(app)
    private val bluetoothLink = BluetoothLinkMonitor(app)

    val live: StateFlow<LiveState> = session.state
    /** Реально подключённые по A2DP/HEADSET адреса — для индикаторов в чипах. */
    private val _connectedAddresses = MutableStateFlow<Set<String>>(emptySet())
    val connectedAddresses: StateFlow<Set<String>> = _connectedAddresses.asStateFlow()
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
    /**
     * Добавляет модель из каталога — без самой гарнитуры под рукой.
     *
     * Профиль-заготовка: адреса ещё нет, поэтому подключение не запускаем.
     * Когда наушники реально найдутся сканом, у них появится свой профиль
     * с MAC-адресом. Набор функций всё равно подтвердит сама гарнитура.
     */
    fun addModelFromCatalog(spec: ModelSpec) = viewModelScope.launch {
        val existing = store.profiles.first()
        val id = "catalog:${spec.modelId}"
        if (existing.any { it.id == id }) return@launch
        val profile = EarbudProfile(
            id = id,
            displayName = spec.modelId,
            address = "",
            vendor = "realme",
            lastSeenMillis = 0L,
            isSelected = false,
        )
        store.saveProfiles(existing + profile)
    }

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
        // SPP откроется сам, когда системный Bluetooth покажет эту гарнитуру
        // как подключённую. Не дёргаем сокет прямо из UI-действия.
    }

    fun selectProfile(id: String) = viewModelScope.launch {
        val current = store.profiles.first()
        val wanted = current.firstOrNull { it.id == id } ?: return@launch

        // Виртуальная модель из каталога — это карточка-справочник, не
        // Bluetooth-устройство. Не делаем её выбранной: иначе приложение
        // переставало видеть реальные подключённые наушники при свайпе по
        // верхнему бару. Реальная гарнитура остаётся активной и на связи.
        if (wanted.address.isBlank()) return@launch

        val list = current.map { it.copy(isSelected = it.id == id) }
        store.saveProfiles(list)
    }

    /**
     * Удаляет модель, добавленную из каталога.
     *
     * У таких профилей нет MAC-адреса, поэтому удаление по адресу для них
     * не работало и убрать их было нельзя.
     */
    fun removeCatalogModel(modelId: String) = viewModelScope.launch {
        val id = "catalog:$modelId"
        val list = store.profiles.first().filterNot { it.id == id }
        val fixed = if (list.none { it.isSelected } && list.isNotEmpty()) {
            list.mapIndexed { i, p -> p.copy(isSelected = i == 0) }
        } else list
        store.saveProfiles(fixed)
    }

    /** Модели из каталога, уже добавленные в список. */
    val catalogModelIds: StateFlow<Set<String>> = profiles
        .map { list ->
            list.filter { it.id.startsWith("catalog:") }
                .map { it.id.removePrefix("catalog:") }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

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
    fun renameProfile(id: String, newName: String) = viewModelScope.launch {
        val list = store.profiles.first().map { if (it.id == id) it.copy(displayName = newName) else it }
        store.saveProfiles(list)
    }
    fun resetDeviceSettings(id: String) = viewModelScope.launch {
        // сброс: скрытые плитки, порядок, кастом цвет — но оставляем профиль
        val cur = store.settings.first() ?: UserSettings()
        store.saveSettings(cur.copy(hiddenTiles = emptySet()))
        // также можно сбросить стартовые настройки гарнитуры если нужно
    }
    fun forgetDevice(id: String) = removeProfile(id)

    /**
     * Вызывается, когда приложение возвращается на экран: сбрасывает
     * залипшее «подключаюсь» и переподключается, если сокет умер в фоне.
     */
    fun onResume() {
        // Состояние связи теперь ведёт BluetoothLinkMonitor, а не экран.
        // ON_RESUME больше не пересоздаёт SPP — иначе возврат во вкладку
        // выглядел как «отключилось».
        session.syncState()
    }

    /**
     * Один владелец соединения.
     *
     * Источник истины — системный Bluetooth (A2DP/HEADSET), не UI. Свайп
     * вкладок, бар профилей и виртуальные карточки каталога сюда не входят.
     * SPP открывается только когда Android уже держит эту гарнитуру, и
     * закрывается только когда системный канал реально пропал.
     */
    private var autoConnectStarted = false

    private fun startAutoConnect() {
        if (autoConnectStarted) return
        autoConnectStarted = true
        viewModelScope.launch {
            // Ошибка в наблюдении за Bluetooth не должна ронять приложение:
            // именно необработанное исключение отсюда крашило запуск.
            runCatching { observeBluetoothLink() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeBluetoothLink() {
            // Следим сразу за ВСЕМИ реальными профилями, а не только за тем,
            // который сейчас показан в верхнем баре. Выбор карточки — это UI:
            // он может быть виртуальной моделью или выключенными ушами и не
            // должен разрывать T110, которые реально играют музыку.
            store.profiles
                .map { list -> list.filter { it.address.isNotBlank() } }
                .distinctUntilChangedBy { list -> list.map { it.address }.toSet() }
                .flatMapLatest { real ->
                    val addresses = real.map { it.address.uppercase() }.toSet()
                    bluetoothLink.observeAny(addresses).map { connected -> real to connected }
                }
                .collect { (real, connectedAddresses) ->
                    // Публикуем актуальный набор для индикаторов в чипах и hero.
                    _connectedAddresses.value = connectedAddresses
                    // Если служебный канал уже жив и его физическая гарнитура
                    // всё ещё подключена — НЕ меняем его при свайпе UI.
                    val currentAddress = session.state.value.address.uppercase()
                    val currentStillConnected = currentAddress.isNotBlank() &&
                        currentAddress in connectedAddresses
                    if (currentStillConnected) {
                        session.markBluetoothPresent(
                            session.state.value.address,
                            session.state.value.deviceName,
                        )
                        return@collect
                    }

                    // Иначе выбираем реально подключённую гарнитуру: сначала
                    // отмеченную пользователем, потом любую из подключённых.
                    val target = real.firstOrNull {
                        it.isSelected && it.address.uppercase() in connectedAddresses
                    } ?: real.firstOrNull { it.address.uppercase() in connectedAddresses }

                    if (target != null) {
                        attachCaseListener(target.id)
                        target.caseBattery?.let {
                            session.seedCaseBattery(it.percent, it.charging, it.atMillis)
                        }
                        session.markBluetoothPresent(target.address, target.displayName)
                        session.connect(target.address, target.displayName)
                    } else {
                        session.markBluetoothGone()
                    }
                }
    }

    /** Сохраняет заряд кейса, когда гарнитура его прислала. */
    private fun attachCaseListener(profileId: String) {
        session.onCaseReported = { percent, charging ->
            viewModelScope.launch {
                val list = store.profiles.first().map {
                    if (it.id == profileId) it.copy(
                        caseBattery = CaseBatteryMemo(percent, charging, System.currentTimeMillis())
                    ) else it
                }
                store.saveProfiles(list)
            }
        }
    }

    /**
     * Выбранная РЕАЛЬНАЯ гарнитура.
     *
     * Виртуальные карточки каталога пропускаем: у них нет MAC-адреса, и
     * попытка «подключиться» к ним обрывала связь с настоящими наушниками.
     */
    private suspend fun selectedRealProfile(): EarbudProfile? {
        val real = store.profiles.first().filter { it.address.isNotBlank() }
        return real.firstOrNull { it.isSelected } ?: real.firstOrNull()
    }

    fun connectSelected() {
        // Явная кнопка обновления: только если Bluetooth уже держит
        // устройство, а SPP почему-то не открыт.
        startAutoConnect()
        viewModelScope.launch {
            val profile = selectedRealProfile() ?: return@launch
            attachCaseListener(profile.id)
            profile.caseBattery?.let {
                session.seedCaseBattery(it.percent, it.charging, it.atMillis)
            }
            session.connect(profile.address, profile.displayName)
        }
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

    // ===== Обновление приложения =====
    data class UpdateState(
        val checking: Boolean = false,
        val currentVersion: String = "",
        val release: Updater.Release? = null,
        val progress: Float = 0f,
        val error: String? = null,
        /** Идёт загрузка: второй тап не должен начинать её заново. */
        val downloading: Boolean = false,
        /** Файл уже на диске — осталось только запустить установку. */
        val readyToInstall: Boolean = false,
    )

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private fun installedVersion(): String = runCatching {
        val app = getApplication<Application>()
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    fun checkUpdate() = viewModelScope.launch {
        val current = installedVersion()
        _updateState.value = UpdateState(checking = true, currentVersion = current)
        val release = Updater.check(current)
        // Работа с диском — вне главного потока, иначе на входе в настройки
        // ловятся микрофризы.
        val ready = withContext(Dispatchers.IO) {
            Updater.clearStaleCache(getApplication(), release?.version)
            release != null && Updater.readyFile(getApplication(), release) != null
        }
        _updateState.value = UpdateState(
            checking = false,
            currentVersion = current,
            release = release,
            readyToInstall = ready,
            progress = if (ready) 1f else 0f,
        )
    }

    fun checkUpdateIfIdle() {
        val s = _updateState.value
        if (s.checking || s.downloading || s.release != null) return
        checkUpdate()
    }

    fun downloadAndInstall() = viewModelScope.launch {
        val state = _updateState.value
        val release = state.release ?: return@launch
        // Повторный тап не должен начинать загрузку заново.
        if (state.downloading) return@launch

        // Файл уже скачан — сразу установка, без повторного скачивания.
        withContext(Dispatchers.IO) { Updater.readyFile(getApplication(), release) }?.let { done ->
            _updateState.value = state.copy(progress = 1f, readyToInstall = true, error = null)
            Updater.install(getApplication(), done)
            return@launch
        }

        _updateState.value = state.copy(downloading = true, error = null)
        val file = Updater.download(getApplication(), release) { p ->
            _updateState.value = _updateState.value.copy(progress = p)
        }
        if (file == null) {
            _updateState.value = _updateState.value.copy(
                downloading = false,
                error = "Не удалось скачать обновление",
            )
            return@launch
        }
        _updateState.value = _updateState.value.copy(
            progress = 1f, downloading = false, readyToInstall = true, error = null,
        )
        Updater.install(getApplication(), file)
    }

    fun setHideNameOnScroll(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(hideNameOnScroll = on))
    }

    /**
     * Телефон лежит экраном вниз.
     *
     * Поток живёт, пока подписан UI: датчик снимается автоматически, поэтому
     * в фоне ничего не тратится.
     */
    private val faceDownSensor = FaceDownSensor(app)

    /**
     * Размытие названия работает как выключатель, а не как индикатор наклона.
     *
     * Перевернул экраном вниз — размытие включилось и осталось, даже когда
     * телефон снова в руках. Перевернул ещё раз — выключилось. Поэтому здесь
     * ловим сам факт переворота и щёлкаем состояние, а не отдаём наружу
     * текущий наклон.
     */
    private val _nameBlurred = MutableStateFlow(false)
    val nameBlurred: StateFlow<Boolean> = _nameBlurred.asStateFlow()

    init {
        // Подключение начинает следить за выбранным профилем сразу, независимо
        // от того, открыт экран наушников или нет.
        startAutoConnect()
        viewModelScope.launch {
            // Датчик тоже не должен ронять запуск, если его нет или он
            // недоступен на устройстве.
            runCatching {
                faceDownSensor.faceDown.collect { down ->
                    // Реагируем только на вход в положение «экраном вниз»:
                    // возврат в нормальное положение состояние не меняет.
                    if (down) _nameBlurred.value = !_nameBlurred.value
                }
            }
        }
    }

    /**
     * AirPods и Beats: состояние приходит BLE-рекламой, без подключения.
     *
     * Поэтому они не занимают SPP-сессию и живут параллельно с обычной
     * гарнитурой. Управления нет — только чтение, об этом честно в UI.
     */
    private val applePodsScanner = ApplePodsScanner(app)
    val applePods: StateFlow<ApplePodsScanner.Found?> = applePodsScanner.scan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), null)

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

    fun setCustomAccent(argb: Long) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(customAccent = argb, dynamicColor = false))
    }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(dynamicColor = on))
    }
    fun setAmoled(on: Boolean) = viewModelScope.launch { store.saveSettings(settings.value.copy(amoledBlack = on)) }
    fun setBalance(v: Float) = viewModelScope.launch { store.saveSettings(settings.value.copy(balance = v.coerceIn(-1f,1f))) }
    fun setMono(on: Boolean) = viewModelScope.launch { store.saveSettings(settings.value.copy(monoMode = on)) }
    fun setAutoPause(on: Boolean) = viewModelScope.launch { store.saveSettings(settings.value.copy(autoPauseOnRemoval = on)) }
    fun setAssistant(v: String) = viewModelScope.launch { store.saveSettings(settings.value.copy(assistant = v)) }
    fun setLastDisconnect(lat: Double, lon: Double) = viewModelScope.launch { store.saveSettings(settings.value.copy(lastDisconnectLat = lat, lastDisconnectLon = lon)) }

    fun toggleTile(key: String) = viewModelScope.launch {
        val hidden = settings.value.hiddenTiles
        store.saveSettings(
            settings.value.copy(hiddenTiles = if (key in hidden) hidden - key else hidden + key)
        )
    }

    fun setTileOrderList(order: List<String>) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(tileOrder = order))
    }


    /**
     * Кнопка обновления.
     *
     * Раньше она работала только если в сессии уже был адрес: при первом
     * запуске или после сброса состояния нажатие не делало ничего. Теперь
     * при отсутствии живого канала берём выбранную гарнитуру из профилей и
     * пробуем подключиться, а иначе просто переспрашиваем данные.
     */
    fun refresh() = viewModelScope.launch {
        startAutoConnect()
        if (session.hasLiveSocket()) {
            session.refresh()
            return@launch
        }
        val profile = selectedRealProfile() ?: run {
            session.refresh()
            return@launch
        }
        attachCaseListener(profile.id)
        profile.caseBattery?.let {
            session.seedCaseBattery(it.percent, it.charging, it.atMillis)
        }
        session.connect(profile.address, profile.displayName, force = true)
    }
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

    private val _photoVariants = MutableStateFlow<List<String>>(emptyList())
    val photoVariants: StateFlow<List<String>> = _photoVariants.asStateFlow()
    private var photoJob: Job? = null

    /**
     * Ищет фото для КОНКРЕТНОГО устройства.
     *
     * Прошлый запрос отменяется: при быстром свайпе между профилями старый
     * результат перезаписывал новый, и картинка «залипала» от другой модели.
     */
    fun ensurePhoto(address: String, deviceName: String) {
        photoJob?.cancel()
        _foundPhoto.value = null
        _photoLayout.value = ImageProbe.Layout.Default
        photoJob = viewModelScope.launch {
            val found = PhotoFinder.find(getApplication(), address, deviceName)
            // Проверяем, что профиль не сменился, пока шёл запрос.
            if (profiles.value.firstOrNull { it.isSelected }?.address == address) {
                _foundPhoto.value = found
            }
        }
    }

    /** Сколько плейсхолдеров показывать в шторке. */
    private val _variantsLoading = MutableStateFlow(false)
    val variantsLoading: StateFlow<Boolean> = _variantsLoading.asStateFlow()
    private var variantsJob: Job? = null

    /**
     * Грузит варианты ПОТОКОВО: каждая картинка появляется в списке сразу,
     * не дожидаясь остальных.
     */
    fun loadPhotoVariants(address: String, deviceName: String) {
        variantsJob?.cancel()
        _photoVariants.value = emptyList()
        _variantsLoading.value = true
        variantsJob = viewModelScope.launch {
            PhotoFinder.variantsStreaming(getApplication(), address, deviceName) { path ->
                _photoVariants.value = _photoVariants.value + path
            }
            _variantsLoading.value = false
        }
    }

    /** Пользователь выбрал картинку руками. */
    fun choosePhoto(address: String, path: String) = viewModelScope.launch {
        PhotoFinder.pick(getApplication(), address, path)
        _foundPhoto.value = path
        _photoLayout.value = ImageProbe.Layout.Default
    }

    /**
     * Ручная правка индикаторов: у левого и правого СВОИ смещения,
     * плюс общий масштаб. Раньше двигались только оба сразу.
     */
    data class ChipTweak(
        val leftDx: Float = 0f,
        val leftDy: Float = 0f,
        val rightDx: Float = 0f,
        val rightDy: Float = 0f,
        val scale: Float = 1f,
    )

    private val _chipTweak = MutableStateFlow(ChipTweak())
    val chipTweak: StateFlow<ChipTweak> = _chipTweak.asStateFlow()

    /** Двигает выбранные чипы: left, right или оба. */
    fun nudgeChips(dx: Float, dy: Float, moveLeft: Boolean, moveRight: Boolean) {
        val t = _chipTweak.value
        _chipTweak.value = t.copy(
            leftDx = if (moveLeft) (t.leftDx + dx).coerceIn(-0.35f, 0.35f) else t.leftDx,
            leftDy = if (moveLeft) (t.leftDy + dy).coerceIn(-0.35f, 0.35f) else t.leftDy,
            rightDx = if (moveRight) (t.rightDx + dx).coerceIn(-0.35f, 0.35f) else t.rightDx,
            rightDy = if (moveRight) (t.rightDy + dy).coerceIn(-0.35f, 0.35f) else t.rightDy,
        )
    }

    fun setChipScale(scale: Float) {
        _chipTweak.value = _chipTweak.value.copy(scale = scale.coerceIn(0.7f, 1.6f))
    }

    fun resetChips() {
        _chipTweak.value = ChipTweak()
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

    private val audioFx = SystemAudioFx(getApplication())
    private val audioTools = AudioTools(getApplication())
    private val feedback = Feedback(getApplication())

    /** Щелчок на действие: звук и вибро по настройкам пользователя. */
    fun setClickSound(pack: String) = viewModelScope.launch {
        store.saveSettings(settings.value.copy(clickSound = pack))
        // Сразу прослушивание выбранного варианта.
        feedback.play(Feedback.Kind.Tap, soundOn = pack != "off", hapticOn = false, soundPack = pack)
    }

    fun tick(kind: Feedback.Kind = Feedback.Kind.Tap) {
        val s = settings.value
        feedback.play(
            kind,
            soundOn = s.soundEffects,
            hapticOn = s.hapticFeedback,
            soundPack = s.clickSound,
        )
    }

    /**
     * Переключение шумодава по кругу: выкл → шумодав → прозрачность.
     *
     * Состояние меняем сразу, не дожидаясь ответа гарнитуры — иначе кнопка
     * выглядит залипшей до следующего опроса.
     */
    fun cycleAnc() {
        val current = live.value.ancMode
        val next = when (current) {
            dev.aezochka.budscontrol.proto.AncMode.OFF -> dev.aezochka.budscontrol.proto.AncMode.ON
            dev.aezochka.budscontrol.proto.AncMode.ON -> dev.aezochka.budscontrol.proto.AncMode.TRANSPARENCY
            else -> dev.aezochka.budscontrol.proto.AncMode.OFF
        }
        session.setAnc(next)
    }

    fun setAnc(mode: dev.aezochka.budscontrol.proto.AncMode) = session.setAnc(mode)

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
        val gains = audioFx.currentGainsDb()
        _eqGains.value = gains
        // Кривая уходит и в гарнитуру: команда 0x0418 из realme Link работает
        // на любом плеере, в отличие от системного AudioEffect.
        session.setEqGains(gains)
    }


    fun applyEqPreset(gains: List<Int>) {
        audioFx.applyPreset(gains)
        val actual = audioFx.currentGainsDb()
        _eqGains.value = actual
        // Пресеты тоже должны улетать в гарнитуру — раньше забывали.
        session.setEqGains(actual)
    }

    fun resetEq() {
        // Сбрасываем и сервис, и гарнитуру. audioFx.applyPreset([0,0,0,0,0])
        // через pushToService остановит EqService.
        audioFx.applyPreset(List(audioFx.bandCount.coerceAtLeast(5)) { 0 })
        val gains = audioFx.currentGainsDb()
        _eqGains.value = gains
        session.setEqGains(gains)
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
