package dev.aezochka.budscontrol.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Форма строки внутри сгруппированного блока.
 *
 * Блок из нескольких строк выглядит как один цельный элемент: у первой строки
 * скруглён только верх, у последней только низ, у средних углы прямые. Так
 * стык между соседними строками остаётся прямым, а весь блок — скруглённым
 * снаружи. Одна строка в блоке скругляется со всех сторон.
 */
private val BIG = 24.dp
private val FLAT = 6.dp

fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val single = count <= 1
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (single || first) BIG else FLAT,
        topEnd = if (single || first) BIG else FLAT,
        bottomStart = if (single || last) BIG else FLAT,
        bottomEnd = if (single || last) BIG else FLAT,
    )
}
