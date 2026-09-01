package dev.aezochka.budscontrol.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * Локализация всего интерфейса.
 *
 * Словарь вместо data class: полей больше 150, и с классом на 8 языков
 * это превратилось бы в нечитаемую простыню. Ключ отсутствует в языке —
 * берём английский, потом русский, потом сам ключ. Так добавление
 * новой строки не ломает сборку.
 */
class Strings(private val code: String) {

    // Совместимость с уже перенесёнными экранами: все эти свойства
    // смотрят в тот же полный словарь, поэтому язык меняется целиком.
    val next get() = get("next")
    val back get() = get("back")
    val go get() = get("go")
    val pickBudsFirst get() = get("pickBuds")
    val languageTitle get() = get("language")
    val languageHint get() = get("languageHint")
    val devicesTitle get() = get("budsTitle")
    val devicesHint get() = get("budsHint")
    val searchAgain get() = get("searchAgain")
    val scanning get() = get("scanning")
    val bonded get() = get("bonded")
    val nearby get() = get("nearby")
    val add get() = get("add")
    val remove get() = get("remove")
    val added get() = get("added")
    val tabBuds get() = get("tabBuds")
    val tabSound get() = get("tabSound")
    val tabGestures get() = get("tabGestures")
    val tabMore get() = get("tabMore")

    operator fun get(key: String): String =
        table(code)[key] ?: EN[key] ?: RU[key] ?: key

    /** Подстановка одного значения: "%s" в строке. */
    fun f(key: String, value: Any): String = get(key).replace("%s", value.toString())

    companion object {
        fun of(code: String): Strings = Strings(
            when (code) {
                "en", "uk", "de", "es", "pl", "tr", "zh" -> code
                else -> "ru"
            }
        )

        val available = listOf(
            "ru" to "Русский",
            "en" to "English",
            "uk" to "Українська",
            "de" to "Deutsch",
            "es" to "Español",
            "pl" to "Polski",
            "tr" to "Türkçe",
            "zh" to "中文",
        )

        private fun table(code: String): Map<String, String> = when (code) {
            "en" -> EN
            "uk" -> UK
            "de" -> DE
            "es" -> ES
            "pl" -> PL
            "tr" -> TR
            "zh" -> ZH
            else -> RU
        }

        private val RU = mapOf(
            "next" to "Далее", "back" to "Назад", "go" to "Поехали", "done" to "Готово",
            "reset" to "Сброс", "refresh" to "Обновить", "copy" to "Копировать", "clear" to "Очистить",
            "pickBuds" to "Выбери наушники", "language" to "Язык",
            "languageHint" to "Можно поменять в настройках",
            "budsTitle" to "Наушники", "budsHint" to "Выбери свои — их можно добавить несколько",
            "searchAgain" to "Искать снова", "scanning" to "Ищу по Bluetooth…",
            "bonded" to "Уже сопряжены", "nearby" to "Найдено рядом",
            "add" to "Добавить", "remove" to "Убрать", "added" to "Добавлен",
            "addBuds" to "Добавить наушники", "tapToAdd" to "Тапни, чтобы добавить",
            "nothingFound" to "Пока ничего не нашлось. Достань наушники из кейса и проверь Bluetooth.",
            "takeOutOfCase" to "Достань наушники из кейса и включи Bluetooth. Сопряжённые устройства появятся сразу.",
            "tabBuds" to "Наушники", "tabSound" to "Звук", "tabGestures" to "Жесты", "tabMore" to "Ещё",
            "equalizer" to "Эквалайзер", "eqHint" to "Тяни полосы или тапни по нужной высоте",
            "eqFlat" to "Ровный", "eqTuned" to "Настроен",
            "gameMode" to "Игровой режим", "on" to "Включён", "off" to "Выключен",
            "case" to "Кейс", "caseLast" to "Кейс · последнее", "noData" to "нет данных",
            "sleepTimer" to "Таймер сна", "sleepHint" to "Поставит воспроизведение на паузу",
            "sleepLeft" to "Осталось %s — музыка встанет на паузу",
            "disable" to "Выключить", "min15" to "15 минут", "min30" to "30 минут", "min60" to "60 минут",
            "ownTime" to "Своё время: %s мин", "startFor" to "Запустить на %s мин",
            "volumeLimit" to "Лимит громкости", "noLimit" to "Без лимита",
            "volumeHint" to "Громкость не поднимется выше %s%",
            "quiet" to "50% — тихо", "moderate" to "70% — умеренно", "loud" to "85% — громко",
            "ownLimit" to "Свой предел", "limitTo" to "Ограничить до %s%",
            "findBuds" to "Найти наушники", "playSignal" to "Подать звук", "signalPlaying" to "Играет сигнал",
            "firmware" to "Прошивка", "requesting" to "запрашиваю…", "noLink" to "нет связи",
            "gesturesHint" to "Отдельно для каждого наушника",
            "leftBud" to "Левый наушник", "rightBud" to "Правый наушник",
            "tap2" to "Двойное касание", "tap3" to "Тройное касание", "hold" to "Долгое нажатие",
            "appliedNow" to "Применяется сразу", "readingSettings" to "Читаю настройки с гарнитуры…",
            "noLinkHint" to "Нет связи — изменения уйдут при подключении",
            "defaultPlay" to "по умолчанию: плей / пауза",
            "defaultNext" to "по умолчанию: следующий трек",
            "defaultAssistant" to "по умолчанию: помощник",
            "actNothing" to "Ничего", "actPlay" to "Плей / пауза", "actAssistant" to "Голосовой помощник",
            "actPrev" to "Предыдущий трек", "actNext" to "Следующий трек", "actNoise" to "Переключить шумодав",
            "actVolUp" to "Громче", "actVolDown" to "Тише", "actGame" to "Игровой режим",
            "theme" to "Тема", "themeHint" to "Акцент применяется сразу ко всему приложению",
            "ownColor" to "Свой цвет", "red" to "Красный", "green" to "Зелёный", "blue" to "Синий",
            "picture" to "Картинка",
            "pictureHint" to "Выбери картинку или поправь индикаторы",
            "dragChips" to "Тапни индикатор, чтобы выбрать, и тяни",
            "sizeHint" to "Тяни ползунок — меняется размер",
            "tuneChips" to "Настроить индикаторы", "size" to "Размер",
            "movingBoth" to "Двигаются оба", "movingLeft" to "Двигается левый",
            "movingRight" to "Двигается правый", "movingNone" to "Ничего не выбрано",
            "variants" to "Найденные варианты", "searchingImages" to "Ищу картинки…",
            "noVariants" to "Ничего не нашлось. Попробуй обновить.",
            "haptics" to "Виброотклик", "hapticsHint" to "Отдача при нажатии на плитки",
            "sounds" to "Звуки действий", "soundsHint" to "Щелчки при нажатии и переключении",
            "autoConnect" to "Подключать автоматически", "autoConnectHint" to "Как только гарнитура рядом",
            "lowBattery" to "Уведомлять о низком заряде", "lowBatteryHint" to "Когда наушник ниже 20%",
            "soundLog" to "Лог звука", "soundLogHint" to "Диагностика эквалайзера",
            "logEmpty" to "Пусто. Открой эквалайзер и потяни полосу — тут появится, что произошло.",
            "records" to "%s записей",
            "update" to "Обновление", "updateHint" to "Проверить и установить новую версию",
            "checking" to "Проверяю релизы…", "latestVersion" to "Установлена последняя версия %s",
            "whatsNew" to "Что нового", "noNotes" to "Без описания", "sizeMb" to "Размер: %s МБ",
            "downloading" to "Загрузка %s%", "downloadInstall" to "Скачать и установить",
            "install" to "Установить", "checkAgain" to "Проверить снова",
            "resumeHint" to "Если связь оборвётся, загрузка продолжится с этого места",
            "downloadFailed" to "Не удалось скачать обновление",
            "notConnected" to "Наушники не подключены", "connecting" to "подключаюсь",
            "connected" to "Подключено", "notSelected" to "Наушники не выбраны",
            "langCount" to "8 языков, меняется сразу", "accent" to "Акцент: %s",
            "inCase" to "В кейсе", "unavailable" to "нет",
            "sleep" to "Сон", "limit" to "Лимит", "find" to "Найти", "stop" to "Стоп",
        )

        private val EN = mapOf(
            "next" to "Next", "back" to "Back", "go" to "Let's go", "done" to "Done",
            "reset" to "Reset", "refresh" to "Refresh", "copy" to "Copy", "clear" to "Clear",
            "pickBuds" to "Pick your buds", "language" to "Language",
            "languageHint" to "Can be changed in settings",
            "budsTitle" to "Earbuds", "budsHint" to "Pick yours — you can add several",
            "searchAgain" to "Search again", "scanning" to "Scanning Bluetooth…",
            "bonded" to "Already paired", "nearby" to "Found nearby",
            "add" to "Add", "remove" to "Remove", "added" to "Added",
            "addBuds" to "Add earbuds", "tapToAdd" to "Tap to add",
            "nothingFound" to "Nothing found yet. Take the buds out of the case and check Bluetooth.",
            "takeOutOfCase" to "Take the buds out of the case and turn on Bluetooth. Paired devices show up right away.",
            "tabBuds" to "Buds", "tabSound" to "Sound", "tabGestures" to "Gestures", "tabMore" to "More",
            "equalizer" to "Equalizer", "eqHint" to "Drag the bars or tap at the level you want",
            "eqFlat" to "Flat", "eqTuned" to "Custom",
            "gameMode" to "Game mode", "on" to "On", "off" to "Off",
            "case" to "Case", "caseLast" to "Case · last known", "noData" to "no data",
            "sleepTimer" to "Sleep timer", "sleepHint" to "Pauses playback",
            "sleepLeft" to "%s left — playback will pause",
            "disable" to "Turn off", "min15" to "15 minutes", "min30" to "30 minutes", "min60" to "60 minutes",
            "ownTime" to "Custom: %s min", "startFor" to "Start for %s min",
            "volumeLimit" to "Volume limit", "noLimit" to "No limit",
            "volumeHint" to "Volume won't go above %s%",
            "quiet" to "50% — quiet", "moderate" to "70% — moderate", "loud" to "85% — loud",
            "ownLimit" to "Custom limit", "limitTo" to "Limit to %s%",
            "findBuds" to "Find earbuds", "playSignal" to "Play sound", "signalPlaying" to "Sound playing",
            "firmware" to "Firmware", "requesting" to "requesting…", "noLink" to "no link",
            "gesturesHint" to "Separate for each earbud",
            "leftBud" to "Left earbud", "rightBud" to "Right earbud",
            "tap2" to "Double tap", "tap3" to "Triple tap", "hold" to "Long press",
            "appliedNow" to "Applied instantly", "readingSettings" to "Reading settings from buds…",
            "noLinkHint" to "No link — changes apply once connected",
            "defaultPlay" to "default: play / pause",
            "defaultNext" to "default: next track",
            "defaultAssistant" to "default: assistant",
            "actNothing" to "Nothing", "actPlay" to "Play / pause", "actAssistant" to "Voice assistant",
            "actPrev" to "Previous track", "actNext" to "Next track", "actNoise" to "Toggle noise control",
            "actVolUp" to "Volume up", "actVolDown" to "Volume down", "actGame" to "Game mode",
            "theme" to "Theme", "themeHint" to "Accent applies across the app instantly",
            "ownColor" to "Custom colour", "red" to "Red", "green" to "Green", "blue" to "Blue",
            "picture" to "Picture",
            "pictureHint" to "Pick an image or adjust the badges",
            "dragChips" to "Tap a badge to select it, then drag",
            "sizeHint" to "Drag the slider to change size",
            "tuneChips" to "Adjust badges", "size" to "Size",
            "movingBoth" to "Moving both", "movingLeft" to "Moving left",
            "movingRight" to "Moving right", "movingNone" to "Nothing selected",
            "variants" to "Found images", "searchingImages" to "Searching images…",
            "noVariants" to "Nothing found. Try refreshing.",
            "haptics" to "Haptics", "hapticsHint" to "Feedback when tapping tiles",
            "sounds" to "Action sounds", "soundsHint" to "Clicks on taps and switches",
            "autoConnect" to "Connect automatically", "autoConnectHint" to "As soon as the buds are nearby",
            "lowBattery" to "Low battery alert", "lowBatteryHint" to "When a bud drops below 20%",
            "soundLog" to "Audio log", "soundLogHint" to "Equalizer diagnostics",
            "logEmpty" to "Empty. Open the equalizer and drag a bar — you'll see what happened here.",
            "records" to "%s records",
            "update" to "Update", "updateHint" to "Check and install a new version",
            "checking" to "Checking releases…", "latestVersion" to "You have the latest version %s",
            "whatsNew" to "What's new", "noNotes" to "No description", "sizeMb" to "Size: %s MB",
            "downloading" to "Downloading %s%", "downloadInstall" to "Download and install",
            "install" to "Install", "checkAgain" to "Check again",
            "resumeHint" to "If the connection drops, the download resumes from here",
            "downloadFailed" to "Could not download the update",
            "notConnected" to "Earbuds not connected", "connecting" to "connecting",
            "connected" to "Connected", "notSelected" to "No earbuds selected",
            "langCount" to "8 languages, applies instantly", "accent" to "Accent: %s",
            "inCase" to "In case", "unavailable" to "n/a",
            "sleep" to "Sleep", "limit" to "Limit", "find" to "Find", "stop" to "Stop",
        )

        // Остальные языки: переведено главное, остальное падает на английский.
        private val UK = mapOf(
            "next" to "Далі", "back" to "Назад", "go" to "Почнемо", "done" to "Готово",
            "reset" to "Скинути", "refresh" to "Оновити",
            "pickBuds" to "Обери навушники", "language" to "Мова",
            "budsTitle" to "Навушники", "searchAgain" to "Шукати знову",
            "scanning" to "Шукаю через Bluetooth…", "bonded" to "Вже спарено", "nearby" to "Знайдено поруч",
            "add" to "Додати", "remove" to "Прибрати", "added" to "Додано",
            "tabBuds" to "Навушники", "tabSound" to "Звук", "tabGestures" to "Жести", "tabMore" to "Ще",
            "equalizer" to "Еквалайзер", "eqFlat" to "Рівний", "eqTuned" to "Налаштований",
            "gameMode" to "Ігровий режим", "on" to "Увімкнено", "off" to "Вимкнено",
            "case" to "Кейс", "noData" to "немає даних",
            "sleepTimer" to "Таймер сну", "volumeLimit" to "Ліміт гучності", "noLimit" to "Без ліміту",
            "findBuds" to "Знайти навушники", "firmware" to "Прошивка", "noLink" to "немає зв'язку",
            "leftBud" to "Лівий навушник", "rightBud" to "Правий навушник",
            "tap2" to "Подвійний дотик", "tap3" to "Потрійний дотик", "hold" to "Довге натискання",
            "theme" to "Тема", "picture" to "Зображення", "size" to "Розмір",
            "update" to "Оновлення", "soundLog" to "Лог звуку",
            "sleep" to "Сон", "limit" to "Ліміт", "find" to "Знайти", "stop" to "Стоп",
            "notConnected" to "Навушники не підключені", "connecting" to "підключаюся",
        )

        private val DE = mapOf(
            "next" to "Weiter", "back" to "Zurück", "go" to "Los geht's", "done" to "Fertig",
            "reset" to "Zurücksetzen", "refresh" to "Aktualisieren",
            "pickBuds" to "Kopfhörer wählen", "language" to "Sprache",
            "budsTitle" to "Kopfhörer", "searchAgain" to "Erneut suchen",
            "scanning" to "Bluetooth-Suche…", "bonded" to "Schon gekoppelt", "nearby" to "In der Nähe",
            "add" to "Hinzufügen", "remove" to "Entfernen", "added" to "Hinzugefügt",
            "tabBuds" to "Kopfhörer", "tabSound" to "Klang", "tabGestures" to "Gesten", "tabMore" to "Mehr",
            "equalizer" to "Equalizer", "eqFlat" to "Neutral", "eqTuned" to "Angepasst",
            "gameMode" to "Spielmodus", "on" to "Ein", "off" to "Aus",
            "case" to "Ladecase", "noData" to "keine Daten",
            "sleepTimer" to "Sleeptimer", "volumeLimit" to "Lautstärkegrenze", "noLimit" to "Kein Limit",
            "findBuds" to "Kopfhörer finden", "firmware" to "Firmware", "noLink" to "keine Verbindung",
            "leftBud" to "Linker Kopfhörer", "rightBud" to "Rechter Kopfhörer",
            "tap2" to "Doppeltippen", "tap3" to "Dreifachtippen", "hold" to "Langes Drücken",
            "theme" to "Design", "picture" to "Bild", "size" to "Größe",
            "update" to "Update", "soundLog" to "Audio-Log",
            "sleep" to "Schlaf", "limit" to "Limit", "find" to "Finden", "stop" to "Stopp",
            "notConnected" to "Kopfhörer nicht verbunden", "connecting" to "verbinde",
        )

        private val ES = mapOf(
            "next" to "Siguiente", "back" to "Atrás", "go" to "Vamos", "done" to "Hecho",
            "reset" to "Restablecer", "refresh" to "Actualizar",
            "pickBuds" to "Elige auriculares", "language" to "Idioma",
            "budsTitle" to "Auriculares", "searchAgain" to "Buscar otra vez",
            "scanning" to "Buscando Bluetooth…", "bonded" to "Ya emparejados", "nearby" to "Cerca",
            "add" to "Añadir", "remove" to "Quitar", "added" to "Añadido",
            "tabBuds" to "Auriculares", "tabSound" to "Sonido", "tabGestures" to "Gestos", "tabMore" to "Más",
            "equalizer" to "Ecualizador", "eqFlat" to "Plano", "eqTuned" to "Personalizado",
            "gameMode" to "Modo juego", "on" to "Activado", "off" to "Desactivado",
            "case" to "Estuche", "noData" to "sin datos",
            "sleepTimer" to "Temporizador", "volumeLimit" to "Límite de volumen", "noLimit" to "Sin límite",
            "findBuds" to "Buscar auriculares", "firmware" to "Firmware", "noLink" to "sin conexión",
            "leftBud" to "Auricular izquierdo", "rightBud" to "Auricular derecho",
            "tap2" to "Toque doble", "tap3" to "Toque triple", "hold" to "Pulsación larga",
            "theme" to "Tema", "picture" to "Imagen", "size" to "Tamaño",
            "update" to "Actualización", "soundLog" to "Registro de audio",
            "sleep" to "Dormir", "limit" to "Límite", "find" to "Buscar", "stop" to "Parar",
            "notConnected" to "Auriculares no conectados", "connecting" to "conectando",
        )

        private val PL = mapOf(
            "next" to "Dalej", "back" to "Wstecz", "go" to "Zaczynamy", "done" to "Gotowe",
            "reset" to "Reset", "refresh" to "Odśwież",
            "pickBuds" to "Wybierz słuchawki", "language" to "Język",
            "budsTitle" to "Słuchawki", "searchAgain" to "Szukaj ponownie",
            "scanning" to "Szukam przez Bluetooth…", "bonded" to "Już sparowane", "nearby" to "W pobliżu",
            "add" to "Dodaj", "remove" to "Usuń", "added" to "Dodano",
            "tabBuds" to "Słuchawki", "tabSound" to "Dźwięk", "tabGestures" to "Gesty", "tabMore" to "Więcej",
            "equalizer" to "Korektor", "eqFlat" to "Płaski", "eqTuned" to "Własny",
            "gameMode" to "Tryb gry", "on" to "Włączony", "off" to "Wyłączony",
            "case" to "Etui", "noData" to "brak danych",
            "sleepTimer" to "Wyłącznik czasowy", "volumeLimit" to "Limit głośności", "noLimit" to "Bez limitu",
            "findBuds" to "Znajdź słuchawki", "firmware" to "Oprogramowanie", "noLink" to "brak połączenia",
            "leftBud" to "Lewa słuchawka", "rightBud" to "Prawa słuchawka",
            "tap2" to "Podwójne dotknięcie", "tap3" to "Potrójne dotknięcie", "hold" to "Długie przytrzymanie",
            "theme" to "Motyw", "picture" to "Obraz", "size" to "Rozmiar",
            "update" to "Aktualizacja", "soundLog" to "Dziennik audio",
            "sleep" to "Sen", "limit" to "Limit", "find" to "Znajdź", "stop" to "Stop",
            "notConnected" to "Słuchawki niepodłączone", "connecting" to "łączę",
        )

        private val TR = mapOf(
            "next" to "İleri", "back" to "Geri", "go" to "Başla", "done" to "Tamam",
            "reset" to "Sıfırla", "refresh" to "Yenile",
            "pickBuds" to "Kulaklık seç", "language" to "Dil",
            "budsTitle" to "Kulaklıklar", "searchAgain" to "Yeniden ara",
            "scanning" to "Bluetooth taranıyor…", "bonded" to "Zaten eşleşmiş", "nearby" to "Yakında",
            "add" to "Ekle", "remove" to "Kaldır", "added" to "Eklendi",
            "tabBuds" to "Kulaklık", "tabSound" to "Ses", "tabGestures" to "Hareketler", "tabMore" to "Daha",
            "equalizer" to "Ekolayzer", "eqFlat" to "Düz", "eqTuned" to "Özel",
            "gameMode" to "Oyun modu", "on" to "Açık", "off" to "Kapalı",
            "case" to "Kutu", "noData" to "veri yok",
            "sleepTimer" to "Uyku zamanlayıcı", "volumeLimit" to "Ses sınırı", "noLimit" to "Sınır yok",
            "findBuds" to "Kulaklığı bul", "firmware" to "Yazılım", "noLink" to "bağlantı yok",
            "leftBud" to "Sol kulaklık", "rightBud" to "Sağ kulaklık",
            "tap2" to "Çift dokunma", "tap3" to "Üç kez dokunma", "hold" to "Uzun basma",
            "theme" to "Tema", "picture" to "Görsel", "size" to "Boyut",
            "update" to "Güncelleme", "soundLog" to "Ses kaydı",
            "sleep" to "Uyku", "limit" to "Sınır", "find" to "Bul", "stop" to "Dur",
            "notConnected" to "Kulaklık bağlı değil", "connecting" to "bağlanıyor",
        )

        private val ZH = mapOf(
            "next" to "下一步", "back" to "返回", "go" to "开始", "done" to "完成",
            "reset" to "重置", "refresh" to "刷新",
            "pickBuds" to "选择耳机", "language" to "语言",
            "budsTitle" to "耳机", "searchAgain" to "重新搜索",
            "scanning" to "正在搜索蓝牙…", "bonded" to "已配对", "nearby" to "附近发现",
            "add" to "添加", "remove" to "移除", "added" to "已添加",
            "tabBuds" to "耳机", "tabSound" to "声音", "tabGestures" to "手势", "tabMore" to "更多",
            "equalizer" to "均衡器", "eqFlat" to "平坦", "eqTuned" to "自定义",
            "gameMode" to "游戏模式", "on" to "已开启", "off" to "已关闭",
            "case" to "充电盒", "noData" to "无数据",
            "sleepTimer" to "睡眠定时", "volumeLimit" to "音量限制", "noLimit" to "无限制",
            "findBuds" to "查找耳机", "firmware" to "固件", "noLink" to "未连接",
            "leftBud" to "左耳机", "rightBud" to "右耳机",
            "tap2" to "双击", "tap3" to "三击", "hold" to "长按",
            "theme" to "主题", "picture" to "图片", "size" to "大小",
            "update" to "更新", "soundLog" to "音频日志",
            "sleep" to "睡眠", "limit" to "限制", "find" to "查找", "stop" to "停止",
            "notConnected" to "耳机未连接", "connecting" to "连接中",
        )
    }
}

val LocalStrings = compositionLocalOf { Strings.of("ru") }

/** Локализованная строка прямо в @Composable-коде. */
@androidx.compose.runtime.Composable
fun tr(key: String): String = LocalStrings.current[key]
