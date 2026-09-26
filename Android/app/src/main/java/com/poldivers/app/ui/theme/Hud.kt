package com.poldivers.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Winamp"-style hardware panel: dark brushed gradient, bevelled edge (light top-left, dark
 * bottom-right), accent frame with corner brackets and a soft coloured bloom around it.
 */
fun Modifier.hudPanel(accent: Color = SuperEarthYellow, glow: Boolean = true, corner: Dp = 4.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    val base = if (glow) {
        this.shadow(10.dp, shape, ambientColor = accent.copy(alpha = 0.55f), spotColor = accent.copy(alpha = 0.55f))
    } else {
        this
    }
    return base
        .clip(shape)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2029), Color(0xFF10141A), Color(0xFF0C0F14))))
            // subtle scanlines
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            val px = 1.dp.toPx()
            // bevel
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, px / 2), Offset(w, px / 2), px)
            drawLine(Color.White.copy(alpha = 0.06f), Offset(px / 2, 0f), Offset(px / 2, h), px)
            drawLine(Color.Black.copy(alpha = 0.6f), Offset(0f, h - px / 2), Offset(w, h - px / 2), px)
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - px / 2, 0f), Offset(w - px / 2, h), px)
            // accent frame
            drawRect(accent.copy(alpha = 0.30f), style = androidx.compose.ui.graphics.drawscope.Stroke(px))
            // corner brackets
            val l = 10.dp.toPx()
            val t = 2.dp.toPx()
            val c = accent.copy(alpha = 0.95f)
            drawLine(c, Offset(0f, t / 2), Offset(l, t / 2), t)
            drawLine(c, Offset(t / 2, 0f), Offset(t / 2, l), t)
            drawLine(c, Offset(w - l, h - t / 2), Offset(w, h - t / 2), t)
            drawLine(c, Offset(w - t / 2, h - l), Offset(w - t / 2, h), t)
        }
}

/** Card replacement used across the app. */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    accent: Color = SuperEarthYellow,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides TextPrimary) {
        Column(
            modifier
                .hudPanel(accent, glow)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            content = content,
        )
    }
}

/** Neon bloom for headline text. */
fun TextStyle.glow(color: Color, radius: Float = 18f): TextStyle =
    copy(shadow = Shadow(color = color.copy(alpha = 0.85f), offset = Offset.Zero, blurRadius = radius))

/** App background: faint tactical grid + vignette, like the war-table screens. */
fun Modifier.hudBackground(): Modifier = drawBehind {
    drawRect(Color(0xFF0A0D11))
    val step = 28.dp.toPx()
    val line = Color(0xFF7FA7C9).copy(alpha = 0.045f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            center = Offset(size.width / 2, size.height / 3),
            radius = maxOf(size.width, size.height),
        ),
    )
}

