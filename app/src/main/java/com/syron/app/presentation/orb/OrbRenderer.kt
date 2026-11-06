package com.syron.app.presentation.orb

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ألوان المسار البديل (أندرويد 10–12 أو فشل الشيدر): نفس هوية الشيدر
private val OrbBlueDeep = Color(0xFF1A52F5)
private val OrbBlueLight = Color(0xFF80C2FF)
private val OrbLimeA = Color(0xFF94ED2E)
private val OrbLimeB = Color(0xFFDBFF75)
private val OrbTeal = Color(0xFF2EE0C7)
private val OrbDeepShade = Color(0xFF0B1A6B)
private val OrbGlowBlue = Color(0xFF7FB8FF)
private val OrbGlowLime = Color(0xFFB4F56B)

/** بلاطة نقاط Halftone صغيرة تتكرر بلا تكلفة، للمسار البديل فقط. */
internal fun buildHalftoneBrush(cellPx: Int): ShaderBrush {
    val s = cellPx.coerceAtLeast(2)
    val bitmap = ImageBitmap(s, s)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = Color.Black.copy(alpha = 0.30f)
        isAntiAlias = true
    }
    canvas.drawCircle(Offset(s / 2f, s / 2f), s * 0.30f, paint)
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

private fun DrawScope.orbBlob(color: Color, alpha: Float, center: Offset, radius: Float) {
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

/**
 * المسار البديل: نفس التكوين بتدرجات شعاعية داخل قصّ دائري.
 * أبسط من الشيدر (لا FBM حقيقي)، لكنه يحفظ الهوية: أزرق أعلى اليسار، أخضر ليموني أسفل ووسط،
 * إضاءة جيلاتينية، هدب لوني ناعم، وHalftone. تدفق السوائل هنا حركة كتل ناعمة وليس ضوضاء.
 */
internal fun DrawScope.drawOrbFallbackBody(
    c: Offset,
    r: Float,
    m: OrbMotion,
    halftone: ShaderBrush?,
    halftoneStrength: Float,
    clip: Path
) {
    val t = m.phase
    val amp = 0.6f + 0.8f * m.swirl

    // توهج خارجي ناعم (لا حلقات): كتلتان متداخلتان أزرق/ليموني
    val glowA = 0.30f * (0.55f + 0.9f * m.energy)
    val gr = r * 1.40f
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to OrbGlowBlue.copy(alpha = glowA),
            0.70f to OrbGlowBlue.copy(alpha = glowA * 0.9f),
            1.0f to Color.Transparent,
            center = Offset(c.x - r * 0.18f, c.y - r * 0.18f),
            radius = gr
        ),
        radius = gr,
        center = Offset(c.x - r * 0.18f, c.y - r * 0.18f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to OrbGlowLime.copy(alpha = glowA),
            0.70f to OrbGlowLime.copy(alpha = glowA * 0.9f),
            1.0f to Color.Transparent,
            center = Offset(c.x + r * 0.15f, c.y + r * 0.22f),
            radius = gr
        ),
        radius = gr,
        center = Offset(c.x + r * 0.15f, c.y + r * 0.22f)
    )

    clip.reset()
    clip.addOval(Rect(center = c, radius = r))
    clipPath(clip) {
        // قاعدة ليمونية
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(OrbLimeB, OrbLimeA),
                center = Offset(c.x, c.y + r * 0.35f),
                radius = r * 1.35f
            ),
            radius = r * 1.02f,
            center = c
        )
        // كتل تتحرك ببطء
        orbBlob(
            OrbLimeB, 0.75f,
            Offset(c.x + r * 0.40f * amp * cos(t * 0.30f), c.y + r * (0.30f + 0.30f * amp * sin(t * 0.26f))),
            r * 0.75f
        )
        orbBlob(
            OrbTeal, 0.38f,
            Offset(c.x + r * (-0.10f + 0.20f * amp * sin(t * 0.22f)), c.y + r * (-0.05f + 0.20f * amp * cos(t * 0.27f))),
            r * 0.70f
        )
        // الأزرق مثبّت أعلى اليسار؛ يتقلص قليلاً حين يتوسع الأخضر مع الصوت
        val shrink = 1f - m.lift * 1.2f
        orbBlob(
            OrbBlueDeep, 0.95f,
            Offset(c.x + r * (-0.40f + 0.07f * amp * sin(t * 0.33f)), c.y + r * (-0.42f + 0.07f * amp * cos(t * 0.29f))),
            r * 0.98f * shrink
        )
        orbBlob(
            OrbBlueLight, 0.65f,
            Offset(c.x + r * (-0.55f + 0.08f * amp * sin(t * 0.41f + 1f)), c.y + r * (-0.20f + 0.10f * amp * cos(t * 0.37f))),
            r * 0.55f
        )
        // ظل عمق أسفل اليمين
        orbBlob(OrbDeepShade, 0.18f, Offset(c.x + r * 0.55f, c.y + r * 0.60f), r * 0.90f)

        // حافة مضيئة ناعمة
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.74f to Color.Transparent,
                1.0f to Color.White.copy(alpha = 0.34f),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        // هدب لوني دقيق: بارد داخل الحافة ودافئ عند الحافة نفسها
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.90f to Color.Transparent,
                0.965f to Color(0xFF5CE1FF).copy(alpha = 0.20f),
                1.0f to Color.Transparent,
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.96f to Color.Transparent,
                0.995f to Color(0xFFFF6A5C).copy(alpha = 0.18f),
                1.0f to Color.Transparent,
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        // لمعة علوية
        orbBlob(Color.White, 0.30f, Offset(c.x - r * 0.38f, c.y - r * 0.46f), r * 0.40f)

        if (halftone != null && halftoneStrength > 0.02f) {
            drawCircle(brush = halftone, radius = r, center = c, alpha = halftoneStrength)
        }
    }
}

/**
 * العينان: كبسولتان عموديتان بيضاوان بتوهج ناعم. مستقلتان تماماً عن حركة السوائل:
 * موضعهما من النظر (OrbMotion.eyeX/eyeY)، وارتفاعهما من الحالة والرمش.
 * لا أنف ولا فم ولا حواجب ولا أي عنصر آخر.
 */
internal fun DrawScope.drawOrbEyes(c: Offset, r: Float, m: OrbMotion) {
    val eyeW = r * 0.25f * m.eyeSx
    val eyeH = (r * 0.72f * m.eyeSy * m.blinkOpen).coerceAtLeast(r * 0.04f)
    val gap = r * 0.34f
    val ox = m.eyeX * r * 0.26f
    val oy = m.eyeY * r * 0.26f
    val cy = c.y - r * 0.12f + oy
    val glow = 0.55f + 0.9f * m.energy

    repeat(2) { i ->
        val side = if (i == 0) -1f else 1f
        val cx = c.x + side * (gap / 2f + eyeW / 2f) + ox

        // توهج ناعم بثلاث طبقات تتناقص اتساعاً وتزداد كثافة
        for (k in 0..2) {
            val pad = eyeW * (0.60f - 0.21f * k)
            val a = (0.05f + 0.035f * k) * glow
            val w = eyeW + pad * 2f
            val h = eyeH + pad * 2f
            drawRoundRect(
                color = Color.White.copy(alpha = a.coerceAtMost(0.30f)),
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(min(w, h) / 2f)
            )
        }
        // قلب العين
        drawRoundRect(
            color = Color.White.copy(alpha = 0.97f),
            topLeft = Offset(cx - eyeW / 2f, cy - eyeH / 2f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(min(eyeW, eyeH) / 2f)
        )
    }
}
