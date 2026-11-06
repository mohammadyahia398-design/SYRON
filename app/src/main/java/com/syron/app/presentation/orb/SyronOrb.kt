package com.syron.app.presentation.orb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syron.app.core.glass.LocalGlassEnv
import kotlin.math.hypot

// اتجاه النظر نسبة إلى مركز الدائرة، محصور داخل دائرة وحدة
private fun gazeOf(p: Offset, w: Int, h: Int, reach: Float): Offset {
    val vx = (p.x - w / 2f) / reach
    val vy = (p.y - h / 2f) / reach
    val len = hypot(vx, vy)
    return if (len > 1f) Offset(vx / len, vy / len) else Offset(vx, vy)
}

/**
 * SYRON ORB: كرة سائلة جيلاتينية وعينان. الرسم في OrbRenderer/OrbShader، والحركة في OrbMotion.
 *
 * التوقيع القديم محفوظ (modifier, orbSize, interactive, busy) فلا تتغير الشاشات الحالية.
 * - busy: يُترجم إلى Thinking (هذا هو الربط الفعلي بـ ChatViewModel.generating)، وإلا Idle.
 * - state: تجاوز صريح (مثلاً Listening أو Speaking) حين يتوفر مصدرهما الحقيقي لاحقاً.
 * - audioLevel: نقطة ربط لمستوى صوت SYRON الحقيقي (0..1) أثناء Speaking. تُستدعى كل إطار على
 *   الخيط الرئيسي، وتعيد null إن لم تتوفر بيانات. لا تُخترع بيانات: بلا مصدر لا يوجد تفاعل صوتي.
 * - interactive: تتبع العينان اللمس.
 */
@Composable
fun SyronOrb(
    modifier: Modifier = Modifier,
    orbSize: Dp = 300.dp,
    interactive: Boolean = true,
    busy: Boolean = false,
    state: OrbState? = null,
    audioLevel: (() -> Float?)? = null
) {
    val reduce = LocalGlassEnv.current.reduceMotion
    val effective = state ?: if (busy) OrbState.Thinking else OrbState.Idle
    val motion = rememberOrbMotion(effective, audioLevel, reduce)

    val shader = remember { OrbShaderFactory.create() }
    val dens = LocalDensity.current.density
    val cellPx = remember(dens) { (5f * dens).toInt().coerceAtLeast(2) }
    val halftoneBrush = remember(cellPx) { buildHalftoneBrush(cellPx) }
    val clipPath = remember { Path() }

    val gestureModifier = if (interactive) {
        Modifier.pointerInput(motion) {
            awaitEachGesture {
                val reach = 150.dp.toPx()
                val down = awaitFirstDown()
                val g0 = gazeOf(down.position, size.width, size.height, reach)
                motion.touch(g0.x, g0.y)
                do {
                    val event = awaitPointerEvent()
                    val p = event.changes.firstOrNull() ?: break
                    val g = gazeOf(p.position, size.width, size.height, reach)
                    motion.touch(g.x, g.y)
                } while (event.changes.any { it.pressed })
                motion.release()
            }
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(gestureModifier)) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(orbSize)
        ) {
            // قراءة frame هنا تجعل كل إطار يعيد الرسم فقط، بلا إعادة تركيب
            @Suppress("UNUSED_VARIABLE")
            val tick = motion.frame

            val c = center
            // قطر الكرة ≈ 70% من مساحة الرسم
            val r = size.minDimension * 0.35f * motion.breath
            val dotPx = 5f * density
            val diameterDp = r * 2f / density
            val halftoneStrength = ((diameterDp - 50f) / 90f).coerceIn(0f, 1f)

            var drawn = false
            if (shader != null && !OrbShaderFactory.disabled) {
                drawn = try {
                    shader.update(
                        cx = c.x,
                        cy = c.y,
                        radius = r,
                        t = motion.phase,
                        swirl = motion.swirl,
                        energy = motion.energy,
                        lift = motion.lift,
                        dotPx = dotPx,
                        halftone = halftoneStrength
                    )
                    drawCircle(brush = shader.brush, radius = r * 1.42f, center = c)
                    true
                } catch (e: Throwable) {
                    OrbShaderFactory.disabled = true
                    false
                }
            }
            if (!drawn) {
                drawOrbFallbackBody(c, r, motion, halftoneBrush, halftoneStrength, clipPath)
            }

            drawOrbEyes(c, r, motion)
        }
    }
}
