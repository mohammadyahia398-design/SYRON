package com.syron.app.core.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * سطح زجاجي كامل: نسخة من الخلفية تُكسر عند الحواف (أندرويد 13+) ثم طلاء الزجاج.
 * للعناصر الثابتة فقط (أشرطة وأزرار)، لأن كل سطح يحمل شيدراً خاصاً به.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    refract: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.06f),
    borderAlpha: Float = 0.45f,
    content: @Composable BoxScope.() -> Unit
) {
    val env = LocalGlassEnv.current
    val density = LocalDensity.current
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val radiusPx = with(density) { cornerRadius.toPx() }
    val strengthPx = with(density) { 14.dp.toPx() }
    val effect = remember(boxSize, radiusPx, strengthPx, refract) {
        if (refract) {
            GlassShader.effect(boxSize.width.toFloat(), boxSize.height.toFloat(), radiusPx, strengthPx)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { rootPos = it.positionInRoot() }
            .onSizeChanged { boxSize = it }
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { renderEffect = effect }
                .drawBehind {
                    val w = env.rootSize.width.toFloat()
                    val h = env.rootSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        translate(-rootPos.x, -rootPos.y) { drawScene(w, h) }
                    }
                }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind { paintGlass(radiusPx, tint, borderAlpha) }
        )
        content()
    }
}

/** لوح زجاجي خفيف بلا شيدر: للقوائم والبطاقات داخل التمرير حتى لا يثقل الأداء. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tint: Color = Color.White.copy(alpha = 0.07f),
    borderAlpha: Float = 0.3f,
    content: @Composable BoxScope.() -> Unit
) {
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawBehind { paintGlass(radiusPx, tint, borderAlpha) },
        content = content
    )
}

internal fun DrawScope.paintGlass(radiusPx: Float, tint: Color, borderAlpha: Float) {
    drawRect(color = tint)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                Color.Transparent,
                Color.White.copy(alpha = 0.04f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )
    val sw = 1.2.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = borderAlpha * 0.55f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius((radiusPx - sw / 2f).coerceAtLeast(0f)),
        style = Stroke(width = sw)
    )
}
