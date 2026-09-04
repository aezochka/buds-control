package dev.aezochka.budscontrol.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable

/**
 * Пружинная физика Expressive: заметный, но короткий перелёт.
 * Держим в одном месте, чтобы движение по всему приложению было одинаковым.
 */
object Motion {
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

    fun <T> effects(): FiniteAnimationSpec<T> =
        tween(durationMillis = 190, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

    fun <T> jelly(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.42f, stiffness = 900f)
    fun <T> slow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow)
}

/**
 * Нажатие без системного синего highlight: своя реакция — упругое сжатие.
 * Именно это убирает «синюю фигню» из WebView-версии.
 */
fun Modifier.pressBounce(
    scaleDown: Float = 0.955f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = Motion.spatialFast(),
        label = "pressScale",
    )
    this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null, // никакого ripple/подсветки поверх контента
            enabled = enabled,
            onClick = onClick,
        )
}
