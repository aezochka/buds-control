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

        fun of(code: String): Strings = when (code) {
            "en" -> En
            "uk", "ua" -> Uk
            else -> Ru
        }
    }
}

val LocalStrings = compositionLocalOf { Strings.Ru }
