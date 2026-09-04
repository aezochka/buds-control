package dev.aezochka.budscontrol.data
data class Change(val version: String, val adds: List<String>, val removes: List<String>)
val changelog = listOf(
    Change("8.1", listOf("Чипы: long-press меню (переименовать/сбросить/забыть) без ломки drag","Жесты: одинарное касание, звонок (ответ/сброс/мьют), громкость, кастом (фонарик/приложение/Tasker/EQ/ANC)","Звук: баланс L/R, моно","Найти: L/R раздельно + место разрыва на карте","Уведомление: управление","AMOLED чёрная тема","Автопауза при снятии"), listOf()),
    Change("8.0", listOf("Динамика Material You в шторке темы","Air6 Pro батарея 1%/3% фикс","ANC переключение"), listOf("Изгой-тумблер из настроек")),
    Change("7.9", listOf("Свой цвет теперь применяется"), listOf()),
)
