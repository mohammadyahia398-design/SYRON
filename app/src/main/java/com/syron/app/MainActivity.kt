package com.syron.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Cyan = Color(0xFF4FD8FF)
private val Bg = Color(0xFF05080D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent { SyronScreen() }
    }
}

// اتجاه النظر نسبة إلى مركز الدائرة، محصور داخل دائرة وحدة
private fun gazeOf(p: Offset, w: Int, h: Int, reach: Float): Offset {
    val vx = (p.x - w / 2f) / reach
    val vy = (p.y - h / 2f) / reach
    val len = hypot(vx, vy)
    return if (len > 1f) Offset(vx / len, vy / len) else Offset(vx, vy)
}

@Composable
fun SyronScreen() {
    // اتجاه النظر: x و y بين -1 و 1 داخل دائرة وحدة
    var gaze by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val gx = remember { Animatable(0f) }
    val gy = remember { Animatable(0f) }
    val blink = remember { Animatable(1f) }

    // حركة العينين بنابض ناعم
    LaunchedEffect(gaze) {
        launch { gx.animateTo(gaze.x, spring(dampingRatio = 0.65f, stiffness = 140f)) }
        launch { gy.animateTo(gaze.y, spring(dampingRatio = 0.65f, stiffness = 140f)) }
    }

    // رمش طبيعي
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500L, 5500L))
            blink.animateTo(0.08f, tween(70))
            blink.animateTo(1f, tween(110))
        }
    }

    // نظرات عشوائية في كل الاتجاهات عند عدم اللمس
    LaunchedEffect(touching) {
        if (!touching) {
            while (true) {
                delay(Random.nextLong(1800L, 4200L))
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val mag = 0.5f + Random.nextFloat() * 0.5f
                gaze = Offset(cos(angle) * mag, sin(angle) * mag)
            }
        }
    }

    // تنفس الدائرة
    val breathing = rememberInfiniteTransition(label = "breathing")
    val breath by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .pointerInput(Unit) {
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
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
        ) {
            val r = size.minDimension / 2f * 0.8f
            val c = center
            val s = 1f + 0.015f * breath

            // التوهج الخارجي
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Cyan.copy(alpha = 0.22f + 0.10f * breath), Color.Transparent),
                    center = c,
                    radius = r * 1.25f
                ),
                radius = r * 1.25f,
                center = c
            )
            // جسم الدائرة الزجاجية الداكنة
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF16222E), Color(0xFF070B11)),
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.35f),
                    radius = r * 1.3f
                ),
                radius = r * s,
                center = c
            )
            // الحافة السماوية
            drawCircle(
                color = Cyan.copy(alpha = 0.35f),
                radius = r * s,
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )
            // لمعة الزجاج
            val hl = Offset(c.x - r * 0.35f, c.y - r * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
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

        Text(
            text = "SYRON",
            color = Cyan.copy(alpha = 0.8f),
            fontSize = 14.sp,
            letterSpacing = 6.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        )
    }
}
