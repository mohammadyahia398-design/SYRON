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

/**
 * المشهد الخلفي: شفق ملوّن حيّ بدل الخلفية الداكنة.
 * بقع كبيرة ناعمة للون العام، وكرات ضوء أحدّ حافة ليظهر الانكسار عبر الزجاج.
 */
fun DrawScope.drawScene(w: Float, h: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF2A2073), Color(0xFF1B2A6B), Color(0xFF13164A)),
            startY = 0f,
            endY = h
        ),
        size = Size(w, h)
    )
    blob(Color(0xFF3DD6FF), 0.60f, Offset(w * 0.10f, h * 0.12f), w * 0.80f)
    blob(Color(0xFFFF5CA8), 0.50f, Offset(w * 0.98f, h * 0.30f), w * 0.75f)
    blob(Color(0xFF8E6BFF), 0.55f, Offset(w * 0.50f, h * 0.55f), w * 0.85f)
    blob(Color(0xFFFFB86B), 0.42f, Offset(w * 0.12f, h * 0.88f), w * 0.75f)
    blob(Color(0xFF19E3C2), 0.38f, Offset(w * 0.98f, h * 0.96f), w * 0.65f)
    lightOrb(Color(0xFFFFFFFF), 0.55f, Offset(w * 0.80f, h * 0.10f), w * 0.14f)
    lightOrb(Color(0xFFFFA8D8), 0.60f, Offset(w * 0.16f, h * 0.60f), w * 0.12f)
    lightOrb(Color(0xFF9FF0FF), 0.55f, Offset(w * 0.84f, h * 0.72f), w * 0.13f)
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

private fun DrawScope.lightOrb(color: Color, alpha: Float, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.55f to color.copy(alpha = alpha * 0.55f),
            1f to Color.Transparent,
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
    float zone = max(strength * 1.7, 1.0);
    float t = clamp(1.0 - depth / zone, 0.0, 1.0);
    t = t * t;
    float2 g = float2(
        sdf(p + float2(1.0, 0.0), hs, r) - sdf(p - float2(1.0, 0.0), hs, r),
        sdf(p + float2(0.0, 1.0), hs, r) - sdf(p - float2(0.0, 1.0), hs, r));
    float2 n = g / max(length(g), 0.0001);
    float disp = strength * t;
    float2 uv = p * 0.97 + hs;
    half4 c1 = content.eval(uv - n * disp * 0.85);
    half4 c2 = content.eval(uv - n * disp * 1.0);
    half4 c3 = content.eval(uv - n * disp * 1.15);
    half3 col = half3(c1.r, c2.g, c3.b);
    half luma = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(luma), col, half(1.3));
    col = col * half(1.1) + half3(0.07);
    float rim = pow(t, 1.4) * clamp(dot(n, normalize(float2(-0.6, -0.8))) * 0.5 + 0.5, 0.0, 1.0);
    float rim2 = pow(t, 1.4) * clamp(dot(n, normalize(float2(0.6, 0.8))) * 0.5 + 0.5, 0.0, 1.0);
    col += half3(rim * 0.30 + rim2 * 0.12);
    col = clamp(col, half3(0.0), half3(1.0));
    return half4(col, 1.0);
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
