package com.syron.app.presentation.orb

import android.os.Build
import androidx.compose.ui.graphics.ShaderBrush

/**
 * شيدر AGSL للكرة السائلة (أندرويد 13+): تدفق FBM مع التواء المجال، أزرق عضوي أعلى اليسار،
 * أخضر ليموني أسفل ووسط، إضاءة جيلاتينية، نقاط Halftone، وهدب لوني دقيق عند الحافة.
 * كل المخرجات مضروبة مسبقاً بالألفا (premultiplied) كما يتطلب Skia.
 */
private val ORB_AGSL = """
uniform float2 center;
uniform float radius;
uniform float t;
uniform float swirl;
uniform float energy;
uniform float lift;
uniform float dotPx;
uniform float halftone;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fc) {
    float2 p = (fc - center) / radius;
    float d = length(p);

    // عدسة: ينحني أخذ العينات قرب الحافة فيظهر انكسار جيلاتيني
    float edgeK = d * d * d;
    float2 q = p * (1.0 - 0.20 * edgeK);

    // مجال ملتوٍ بتدفق دائري عضوي
    float ang = swirl * 1.1 * (1.0 - min(d * d, 1.0)) + t * 0.10;
    float sn = sin(ang);
    float cs = cos(ang);
    float2 r = float2(cs * q.x - sn * q.y, sn * q.x + cs * q.y);
    float2 w = float2(
        fbm(r * 1.5 + float2(t * 0.20, -t * 0.14)),
        fbm(r * 1.5 + float2(5.2 - t * 0.17, 1.3 + t * 0.11)));
    float n = fbm(r * 1.2 + w * (1.4 + swirl) + float2(0.0, t * 0.08));

    // الأزرق مثبّت أعلى اليسار (~30% من القرص)، والأخضر يملأ الباقي
    float bl = dot(q, float2(-0.7071, -0.7071));
    float v = bl + (n - 0.5) * 0.95 - lift;
    float blueW = smoothstep(0.02, 0.62, v);

    float3 blueDeep = float3(0.10, 0.32, 0.96);
    float3 blueLight = float3(0.50, 0.76, 1.00);
    float3 limeA = float3(0.58, 0.93, 0.18);
    float3 limeB = float3(0.86, 1.00, 0.46);
    float3 teal = float3(0.18, 0.88, 0.78);

    float3 lime = mix(limeA, limeB, smoothstep(0.30, 0.80, n + 0.25 * q.y));
    float3 blue = mix(blueDeep, blueLight, smoothstep(0.25, 0.85, n + 0.30 * bl));
    float3 col = mix(lime, blue, blueW);
    float zone = blueW * (1.0 - blueW) * 4.0;
    col = mix(col, teal, zone * 0.30);

    // إضاءة جيلاتينية: حافة مضيئة ناعمة، لمعة علوية، ظل خفيف أسفل اليمين
    float rimT = pow(clamp(d, 0.0, 1.0), 3.5);
    float spec = 1.0 - smoothstep(0.0, 0.45, length(q - float2(-0.38, -0.46)));
    float shade = 0.12 * smoothstep(0.1, 1.0, dot(q, float2(0.6, 0.8)));
    col = col * (0.90 + 0.18 * energy) - float3(shade);
    col += float3(0.55, 0.85, 1.0) * rimT * 0.30;
    col += float3(spec * 0.28);
    col += float3((n - 0.5) * 0.10);

    // Halftone: نقاط منتظمة ناعمة، أكبر قليلاً في المناطق الأعمق
    float2 g = fc / dotPx;
    float2 f = fract(g) - float2(0.5);
    float tone = clamp(0.30 + 0.45 * d * d + 0.35 * (1.0 - n), 0.0, 1.0);
    float dotR = 0.14 + 0.28 * tone;
    float dm = 1.0 - smoothstep(dotR - 0.10, dotR + 0.10, length(f));
    col = mix(col, col * 0.70, dm * 0.50 * halftone);

    // حافة ناعمة بهدب لوني دقيق: الأحمر أبعد قليلاً والأزرق أقرب
    float eR = 1.0 - smoothstep(0.967, 1.012, d);
    float eG = 1.0 - smoothstep(0.955, 1.000, d);
    float eB = 1.0 - smoothstep(0.943, 0.988, d);
    float bodyA = max(eR, max(eG, eB));
    float3 bodyRGB = clamp(col, float3(0.0), float3(1.0)) * float3(eR, eG, eB);

    // توهج خارجي ناعم يتلاشى قبل حد منطقة الرسم
    float outD = max(d - 0.96, 0.0);
    float fade = 1.0 - smoothstep(1.10, 1.42, d);
    float glow = exp(-outD * 5.5) * 0.30 * (0.55 + 0.9 * energy) * fade;
    float gb = smoothstep(-0.3, 0.5, dot(p, float2(-0.7071, -0.7071)));
    float3 gcol = mix(limeA, blueLight, gb);

    float3 outRGB = bodyRGB + gcol * glow * (1.0 - bodyA);
    float outA = bodyA + glow * (1.0 - bodyA);
    return half4(half3(outRGB), half(outA));
}
"""

internal class OrbShader {
    private val shader = android.graphics.RuntimeShader(ORB_AGSL)
    val brush = ShaderBrush(shader)

    fun update(
        cx: Float,
        cy: Float,
        radius: Float,
        t: Float,
        swirl: Float,
        energy: Float,
        lift: Float,
        dotPx: Float,
        halftone: Float
    ) {
        shader.setFloatUniform("center", cx, cy)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("t", t)
        shader.setFloatUniform("swirl", swirl)
        shader.setFloatUniform("energy", energy)
        shader.setFloatUniform("lift", lift)
        shader.setFloatUniform("dotPx", dotPx)
        shader.setFloatUniform("halftone", halftone)
    }
}

/** ينشئ الشيدر على أندرويد 13+ فقط؛ أي فشل يعطّله نهائياً ويعود الرسم إلى المسار البديل. */
internal object OrbShaderFactory {
    @Volatile
    var disabled = false

    fun create(): OrbShader? {
        if (disabled || Build.VERSION.SDK_INT < 33) return null
        return try {
            OrbShader()
        } catch (e: Throwable) {
            disabled = true
            null
        }
    }
}
