package com.syron.app.presentation.orb

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syron.app.core.glass.LocalGlassEnv
import com.syron.app.core.theme.SyronColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Cyan = SyronColors.Cyan

// اتجاه النظر نسبة إلى مركز الدائرة، محصور داخل دائرة وحدة
private fun gazeOf(p: Offset, w: Int, h: Int, reach: Float): Offset {
    val vx = (p.x - w / 2f) / reach
    val vy = (p.y - h / 2f) / reach
    val len = hypot(vx, vy)
    return if (len > 1f) Offset(vx / len, vy / len) else Offset(vx, vy)
}


/**
 * الدائرة والعينان كمكوّن قابل لإعادة الاستعمال.
 * interactive: تتبع العينان اللمس. busy: تتجول العينان يمنة ويسرة أثناء التفكير.
 */
@Composable
fun SyronOrb(
    modifier: Modifier = Modifier,
    orbSize: Dp = 300.dp,
    interactive: Boolean = true,
    busy: Boolean = false
) {
    val reduce = LocalGlassEnv.current.reduceMotion
    var gaze by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val gx = remember { Animatable(0f) }
    val gy = remember { Animatable(0f) }
    val blink = remember { Animatable(1f) }
    val busyAmp by animateFloatAsState(if (busy) 1f else 0f, label = "orbBusy")

    LaunchedEffect(gaze, reduce) {
        if (reduce) {
            gx.snapTo(gaze.x)
            gy.snapTo(gaze.y)
        } else {
            launch { gx.animateTo(gaze.x, spring(dampingRatio = 0.65f, stiffness = 140f)) }
            launch { gy.animateTo(gaze.y, spring(dampingRatio = 0.65f, stiffness = 140f)) }
        }
    }

    LaunchedEffect(reduce) {
        if (!reduce) {
            while (true) {
                delay(Random.nextLong(2500L, 5500L))
                blink.animateTo(0.08f, tween(70))
                blink.animateTo(1f, tween(110))
            }
        }
    }

    LaunchedEffect(touching, busy, reduce) {
        if (!touching && !reduce) {
            if (busy) {
                var dir = 1f
                while (true) {
                    gaze = Offset(0.75f * dir, -0.35f)
                    dir = -dir
                    delay(750L)
                }
            } else {
                gaze = Offset.Zero
                while (true) {
                    delay(Random.nextLong(1800L, 4200L))
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val mag = 0.5f + Random.nextFloat() * 0.5f
                    gaze = Offset(cos(angle) * mag, sin(angle) * mag)
                }
            }
        }
    }

    val breathing = rememberInfiniteTransition(label = "breathing")
    val breathState by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breath"
    )

    val gestureModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val reach = 150.dp.toPx()
                val down = awaitFirstDown()
                touching = true
                gaze = gazeOf(down.position, size.width, size.height, reach)
                do {
                    val event = awaitPointerEvent()
                    val p = event.changes.firstOrNull() ?: break
                    gaze = gazeOf(p.position, size.width, size.height, reach)
                } while (event.changes.any { it.pressed })
                touching = false
                gaze = Offset.Zero
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
            val breath = if (reduce) 0f else breathState
            val r = size.minDimension / 2f * 0.8f
            val c = center
            val s = 1f + (0.015f + 0.02f * busyAmp) * breath

            // التوهج الخارجي
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Cyan.copy(alpha = 0.40f + 0.14f * breath), Color.Transparent),
                    center = c,
                    radius = r * 1.25f
                ),
                radius = r * 1.25f,
                center = c
            )
            // جسم الدائرة الزجاجية الداكنة
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF4A5CB0), Color(0xFF141B45)),
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.35f),
                    radius = r * 1.3f
                ),
                radius = r * s,
                center = c
            )
            // الحافة السماوية
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = r * s,
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )
            // لمعة الزجاج
            val hl = Offset(c.x - r * 0.35f, c.y - r * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                    center = hl,
                    radius = r * 0.6f
                ),
                radius = r * 0.6f,
                center = hl
            )

            // العينان: مستطيلان عموديان بزوايا مستديرة، تتحركان داخل الدائرة
            val eyeW = r * 0.24f
            val eyeH = r * 0.62f * blink.value
            val gap = r * 0.30f
            val ox = gx.value * r * 0.34f
            val oy = gy.value * r * 0.44f
            for (side in listOf(-1f, 1f)) {
                val cx = c.x + side * (gap / 2f + eyeW / 2f) + ox
                val cy = c.y + oy
                val corner = CornerRadius(eyeW * 0.45f)
                // هالة العين
                val pad = eyeW * 0.35f
                drawRoundRect(
                    color = Cyan.copy(alpha = 0.18f),
                    topLeft = Offset(cx - eyeW / 2f - pad, cy - eyeH / 2f - pad),
                    size = Size(eyeW + pad * 2f, eyeH + pad * 2f),
                    cornerRadius = CornerRadius(eyeW * 0.8f)
                )
                // قلب العين
                drawRoundRect(
                    color = Cyan,
                    topLeft = Offset(cx - eyeW / 2f, cy - eyeH / 2f),
                    size = Size(eyeW, eyeH),
                    cornerRadius = corner
                )
            }
        }
    }
}
