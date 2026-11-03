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
import androidx.compose.ui.draw.shadow
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
 * سطح زجاجي كامل: ظل ناعم، ونسخة من الخلفية تُكسر عند الحواف (أندرويد 13+)، ثم طلاء الزجاج.
 * للعناصر الثابتة فقط (أشرطة وأزرار)، لأن كل سطح يحمل شيدراً خاصاً به.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    refract: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.10f),
    borderAlpha: Float = 0.6f,
    content: @Composable BoxScope.() -> Unit
) {
    val env = LocalGlassEnv.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(cornerRadius)
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val radiusPx = with(density) { cornerRadius.toPx() }
    val strengthPx = with(density) { 18.dp.toPx() }
    val effect = remember(boxSize, radiusPx, strengthPx, refract) {
        if (refract) {
            GlassShader.effect(boxSize.width.toFloat(), boxSize.height.toFloat(), radiusPx, strengthPx)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .onGloballyPositioned { rootPos = it.positionInRoot() }
            .onSizeChanged { boxSize = it }
            .clip(shape)
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
    tint: Color = Color.White.copy(alpha = 0.12f),
    borderAlpha: Float = 0.4f,
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

/** طلاء الزجاج: تلوين، لمعة علوية، ظل داخلي سفلي، حافة مضيئة، وخط ضوء داخلي أعلى الحافة. */
internal fun DrawScope.paintGlass(radiusPx: Float, tint: Color, borderAlpha: Float) {
    drawRect(color = tint)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
            startY = size.height * 0.55f,
            endY = size.height
        )
    )
    val sw = 1.4.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (borderAlpha * 1.2f).coerceAtMost(1f)),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = borderAlpha * 0.7f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius((radiusPx - sw / 2f).coerceAtLeast(0f)),
        style = Stroke(width = sw)
    )
    val inset = sw * 1.8f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.4f
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        cornerRadius = CornerRadius((radiusPx - inset).coerceAtLeast(0f)),
        style = Stroke(width = 1.dp.toPx())
    )
}
