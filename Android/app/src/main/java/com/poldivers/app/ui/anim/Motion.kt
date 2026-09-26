package com.poldivers.app.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Whether the user wants animations (settings -> "Animacje"). Everything here snaps when off. */
val LocalAnimations = compositionLocalOf { true }

/**
 * Press feedback for anything tappable: squashes a little while pressed and springs back with a
 * bounce, plus the haptic tick and the regular ripple.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.93f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val animate = LocalAnimations.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = com.poldivers.app.core.haptics.LocalHaptics.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
        ) {
            if (haptic) haptics.tap()
            onClick()
        }
}

/** Only the squash-on-press part, for components that handle clicks themselves (Buttons, chips). */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.93f): Modifier = composed {
    val animate = LocalAnimations.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Entrance for list items / panels: fades in, rises and un-shrinks, staggered by [index] so a
 * list cascades in. Plays once per composition of the item.
 */
fun Modifier.appear(index: Int = 0, rise: Dp = 22.dp): Modifier = composed {
    if (!LocalAnimations.current) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 12) * 45L))
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * risePx
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** Zoom-in entrance (dialogs, images, headers). */
fun Modifier.zoomIn(from: Float = 0.7f, durationMs: Int = 450): Modifier = composed {
    if (!LocalAnimations.current) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val s = from + (1f - from) * p
        scaleX = s
        scaleY = s
    }
}

/** Slow endless spin (planet art in details, loaders). */
fun Modifier.slowSpin(periodMs: Int = 60_000): Modifier = composed {
    if (!LocalAnimations.current) return@composed this
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            angle.snapTo(0f)
            angle.animateTo(360f, tween(periodMs, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    graphicsLayer { rotationZ = angle.value }
}

/** A horizontal light sweep over the element every few seconds -- the "scanner" shine on HUD panels. */
fun Modifier.shine(color: Color = Color.White, periodMs: Int = 4200): Modifier = composed {
    if (!LocalAnimations.current) return@composed this
    val sweep = remember { Animatable(-0.4f) }
    LaunchedEffect(Unit) {
        while (true) {
            sweep.snapTo(-0.4f)
            sweep.animateTo(1.4f, tween(1100, easing = FastOutSlowInEasing))
            delay(periodMs.toLong())
        }
    }
    drawBehind {
        val x = sweep.value * size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.07f), Color.Transparent),
                start = Offset(x - size.width * 0.25f, 0f),
                end = Offset(x + size.width * 0.25f, size.height),
            ),
        )
    }
}

/** Number that counts up/down to its new value instead of jumping. */
@Composable
fun animatedNumber(target: Double, durationMs: Int = 900): Double {
    val animate = LocalAnimations.current
    val value = remember { Animatable(if (animate) 0f else target.toFloat()) }
    LaunchedEffect(target, animate) {
        if (animate) value.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) else value.snapTo(target.toFloat())
    }
    return value.value.toDouble()
}

/** Progress (0..1) that fills in from zero on first show and glides on updates. */
@Composable
fun animatedProgress(target: Float, durationMs: Int = 1100): Float {
    val animate = LocalAnimations.current
    val value = remember { Animatable(if (animate) 0f else target) }
    LaunchedEffect(target, animate) {
        if (animate) value.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) else value.snapTo(target)
    }
    return value.value
}

/** HUD progress bar with a glowing head and a moving sheen, filling in on first show. */
@Composable
fun HudProgressBar(
    progress: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val p = animatedProgress(progress.coerceIn(0f, 1f))
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(height / 2))
            .drawBehind {
                drawRect(track)
                val w = size.width * p
                drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color), endX = w.coerceAtLeast(1f)), size = Size(w, size.height))
                // glowing head
                if (w > 2f) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(w - 3.dp.toPx(), 0f),
                        size = Size(3.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            .shine(Color.White, periodMs = 2600),
    )
}

/** Text that briefly flashes bright when it changes (live numbers). */
@Composable
fun FlashText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val animate = LocalAnimations.current
    val flash = remember { Animatable(0f) }
    LaunchedEffect(text) {
        if (!animate) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(900))
    }
    Text(
        text,
        style = style,
        color = androidx.compose.ui.graphics.lerp(color, Color.White, flash.value * 0.8f),
        modifier = modifier.graphicsLayer {
            val s = 1f + 0.06f * flash.value
            scaleX = s
            scaleY = s
        },
    )
}
