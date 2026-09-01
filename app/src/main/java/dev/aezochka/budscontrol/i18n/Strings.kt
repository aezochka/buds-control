package dev.aezochka.budscontrol.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * Строки интерфейса на трёх языках.
 *
 * Своя таблица вместо ресурсов: нужно, чтобы язык менялся МГНОВЕННО при выборе
 * в онбординге, без пересоздания активити и без перезапуска приложения.
 */
data class Strings(
    val next: String,
    val back: String,
    val go: String,
    val pickBudsFirst: String,
    val languageTitle: String,
    val languageHint: String,
    val devicesTitle: String,
    val devicesHint: String,
    val searchAgain: String,
    val scanning: String,
    val bonded: String,
    val nearby: String,
    val add: String,
    val remove: String,
    val added: String,
    val tabBuds: String,
    val tabSound: String,
    val tabGestures: String,
    val tabMore: String,
) {
    companion object {
        val Ru = Strings(
            next = "Далее", back = "Назад", go = "Поехали",
            pickBudsFirst = "Выбери наушники",
            languageTitle = "Язык", languageHint = "Можно поменять в настройках",
            devicesTitle = "Наушники", devicesHint = "Выбери свои — их можно добавить несколько",
            searchAgain = "Искать снова", scanning = "Ищу по Bluetooth…",
            bonded = "Уже сопряжены", nearby = "Найдено рядом",
            add = "Добавить", remove = "Убрать", added = "Добавлен",
            tabBuds = "Наушники", tabSound = "Звук", tabGestures = "Жесты", tabMore = "Ещё",
        )
        val En = Strings(
            next = "Next", back = "Back", go = "Let's go",
            pickBudsFirst = "Pick your buds",
            languageTitle = "Language", languageHint = "Can be changed in settings",
            devicesTitle = "Earbuds", devicesHint = "Pick yours — you can add several",
            searchAgain = "Search again", scanning = "Scanning Bluetooth…",
            bonded = "Already paired", nearby = "Found nearby",
            add = "Add", remove = "Remove", added = "Added",
            tabBuds = "Buds", tabSound = "Sound", tabGestures = "Gestures", tabMore = "More",
        )
        val Uk = Strings(
            next = "Далі", back = "Назад", go = "Почнемо",
            pickBudsFirst = "Обери навушники",
            languageTitle = "Мова", languageHint = "Можна змінити в налаштуваннях",
            devicesTitle = "Навушники", devicesHint = "Обери свої — можна додати декілька",
            searchAgain = "Шукати знову", scanning = "Шукаю по Bluetooth…",
            bonded = "Вже спарено", nearby = "Знайдено поруч",
            add = "Додати", remove = "Прибрати", added = "Додано",
            tabBuds = "Навушники", tabSound = "Звук", tabGestures = "Жести", tabMore = "Ще",
        )


        val De = Strings(
            next = "Weiter", back = "Zurück", go = "Los geht's",
            pickBudsFirst = "Kopfhörer wählen",
            languageTitle = "Sprache", languageHint = "Später in den Einstellungen änderbar",
            devicesTitle = "Kopfhörer", devicesHint = "Wähle deine — mehrere möglich",
            searchAgain = "Erneut suchen", scanning = "Bluetooth-Suche…",
            bonded = "Schon gekoppelt", nearby = "In der Nähe",
            add = "Hinzufügen", remove = "Entfernen", added = "Hinzugefügt",
            tabBuds = "Kopfhörer", tabSound = "Klang", tabGestures = "Gesten", tabMore = "Mehr",
        )
        val Es = Strings(
            next = "Siguiente", back = "Atrás", go = "Vamos",
            pickBudsFirst = "Elige auriculares",
            languageTitle = "Idioma", languageHint = "Se puede cambiar en ajustes",
            devicesTitle = "Auriculares", devicesHint = "Elige los tuyos — puedes añadir varios",
            searchAgain = "Buscar otra vez", scanning = "Buscando Bluetooth…",
            bonded = "Ya emparejados", nearby = "Cerca",
            add = "Añadir", remove = "Quitar", added = "Añadido",
            tabBuds = "Auriculares", tabSound = "Sonido", tabGestures = "Gestos", tabMore = "Más",
        )
        val Pl = Strings(
            next = "Dalej", back = "Wstecz", go = "Zaczynamy",
            pickBudsFirst = "Wybierz słuchawki",
            languageTitle = "Język", languageHint = "Można zmienić w ustawieniach",
            devicesTitle = "Słuchawki", devicesHint = "Wybierz swoje — możesz dodać kilka",
            searchAgain = "Szukaj ponownie", scanning = "Szukam przez Bluetooth…",
            bonded = "Już sparowane", nearby = "W pobliżu",
            add = "Dodaj", remove = "Usuń", added = "Dodano",
            tabBuds = "Słuchawki", tabSound = "Dźwięk", tabGestures = "Gesty", tabMore = "Więcej",
        )
        val Tr = Strings(
            next = "İleri", back = "Geri", go = "Başla",
            pickBudsFirst = "Kulaklık seç",
            languageTitle = "Dil", languageHint = "Ayarlardan değiştirilebilir",
            devicesTitle = "Kulaklıklar", devicesHint = "Kendinizi seçin — birkaç tane eklenebilir",
            searchAgain = "Yeniden ara", scanning = "Bluetooth taranıyor…",
            bonded = "Zaten eşleşmiş", nearby = "Yakında bulundu",
            add = "Ekle", remove = "Kaldır", added = "Eklendi",
            tabBuds = "Kulaklık", tabSound = "Ses", tabGestures = "Hareketler", tabMore = "Daha",
        )
        val Zh = Strings(
            next = "下一步", back = "返回", go = "开始",
            pickBudsFirst = "选择耳机",
            languageTitle = "语言", languageHint = "可在设置中更改",
            devicesTitle = "耳机", devicesHint = "选择你的耳机，可添加多个",
            searchAgain = "重新搜索", scanning = "正在搜索蓝牙…",
            bonded = "已配对", nearby = "附近发现",
            add = "添加", remove = "移除", added = "已添加",
            tabBuds = "耳机", tabSound = "声音", tabGestures = "手势", tabMore = "更多",
        )

        fun of(code: String): Strings = when (code) {
            "en" -> En
            "uk", "ua" -> Uk
            "de" -> De
            "es" -> Es
            "pl" -> Pl
            "tr" -> Tr
            "zh" -> Zh
            else -> Ru
        }

        /** Список для экрана выбора: код и родное название. */
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
    }
}

val LocalStrings = compositionLocalOf { Strings.Ru }
