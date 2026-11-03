package com.syron.app.core.glass

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

/** بيئة الزجاج المشتركة: حجم الشاشة (لمحاذاة نسخة الخلفية) وتفضيل تقليل الحركة. */
class GlassEnv(val reduceMotion: Boolean = false) {
    var rootSize by mutableStateOf(IntSize.Zero)
}

val LocalGlassEnv = staticCompositionLocalOf { GlassEnv() }

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
}

/** المشهد الخلفي: يُرسم مرة للشاشة كلها، ونسخة منه داخل كل سطح زجاجي ليُكسر عبره. */
fun DrawScope.drawScene(w: Float, h: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF070B14), Color(0xFF0A1220), Color(0xFF05080D)),
            startY = 0f,
            endY = h
        ),
        size = Size(w, h)
    )
    blob(Color(0xFF4FD8FF), 0.34f, Offset(w * 0.15f, h * 0.18f), w * 0.62f)
    blob(Color(0xFF7B5CFF), 0.28f, Offset(w * 0.92f, h * 0.46f), w * 0.66f)
    blob(Color(0xFF1FD6B5), 0.22f, Offset(w * 0.28f, h * 0.9f), w * 0.62f)
}

private fun DrawScope.blob(color: Color, alpha: Float, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** شيدر الانكسار (AGSL، أندرويد 13+). أي فشل في إنشائه يعطّله ويعود الزجاج للنمط العادي. */
object GlassShader {
    @Volatile
    var disabled = false

    private val SRC = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float strength;

float sdf(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fc) {
    float2 hs = size * 0.5;
    float r = min(radius, min(hs.x, hs.y));
    float2 p = fc - hs;
    float depth = -sdf(p, hs, r);
    float zone = max(strength * 1.6, 1.0);
    float t = clamp(1.0 - depth / zone, 0.0, 1.0);
    t = t * t;
    float2 g = float2(
        sdf(p + float2(1.0, 0.0), hs, r) - sdf(p - float2(1.0, 0.0), hs, r),
        sdf(p + float2(0.0, 1.0), hs, r) - sdf(p - float2(0.0, 1.0), hs, r));
    float2 n = g / max(length(g), 0.0001);
    float disp = strength * t;
    half4 c1 = content.eval(fc - n * disp * 0.9);
    half4 c2 = content.eval(fc - n * disp * 1.0);
    half4 c3 = content.eval(fc - n * disp * 1.1);
    half4 col = half4(c1.r, c2.g, c3.b, 1.0);
    float rim = pow(t, 1.5) * clamp(dot(n, normalize(float2(-0.6, -0.8))) * 0.5 + 0.5, 0.0, 1.0);
    col.rgb += half3(rim * 0.2);
    col.rgb *= half(1.0 + 0.08 * (1.0 - t));
    return col;
}
"""

    fun effect(w: Float, h: Float, radiusPx: Float, strengthPx: Float): RenderEffect? {
        if (disabled || Build.VERSION.SDK_INT < 33 || w <= 0f || h <= 0f) return null
        return try {
            val shader = android.graphics.RuntimeShader(SRC)
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("radius", radiusPx)
            shader.setFloatUniform("strength", strengthPx)
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        } catch (e: Throwable) {
            disabled = true
            null
        }
    }
}
