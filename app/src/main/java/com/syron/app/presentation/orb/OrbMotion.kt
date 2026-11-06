package com.syron.app.presentation.orb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = 6.2831855f

/** نصف قطر الكرة يتمدد بمقدار هذه النسبة عند أعلى مستوى صوت حقيقي. */
private const val LEVEL_RADIUS_GAIN = 0.07f

/** مقدار توسّع الأخضر (وانكماش الأزرق) عند أعلى مستوى صوت حقيقي. */
private const val LEVEL_LIFT_GAIN = 0.16f

private const val GAZE_K = 140f
private const val GAZE_D = 15f

/**
 * حامل حالة الحركة: حلقة إطارات واحدة تحدّث كل القيم (السوائل، النبض، العينين).
 * - لا توجد Animations متعددة متضاربة: كل شيء يُحسب في step().
 * - القيم حقول عادية، ويقرأ الرسام `frame` فقط ليُعاد الرسم دون إعادة تركيب (Recomposition).
 * - الانتقال بين الحالات يتم بتنعيم أسّي لكل المعاملات، فلا قفزات.
 */
internal class OrbMotion(initial: OrbState) {

    /** يُقرأ في مرحلة الرسم فقط ليُبطل الرسم عند كل إطار. */
    var frame by mutableIntStateOf(0)
        private set

    // ---- مدخلات تُحدَّث من الـ Composable ----
    var state: OrbState = initial
    var audioLevel: (() -> Float?)? = null
    var reduceMotion: Boolean = false

    // ---- قيم يقرؤها الرسام ----
    var phase = 3.0f
        private set
    var swirl = lookOf(initial).swirl
        private set
    var energy = lookOf(initial).energy
        private set
    var breath = 1.0f
        private set
    var lift = 0.0f
        private set
    var level = 0.0f
        private set
    var hasLevel = false
        private set
    var eyeX = 0.0f
        private set
    var eyeY = 0.0f
        private set
    var eyeSx = lookOf(initial).eyeSx
        private set
    var eyeSy = lookOf(initial).eyeSy
        private set
    var blinkOpen = 1.0f
        private set

    // ---- داخلي ----
    private var flow = lookOf(initial).flow
    private var pulseHz = lookOf(initial).pulseHz
    private var pulseAmp = lookOf(initial).pulseAmp
    private var pulsePhase = 0.0f
    private var time = 0.0f

    private var evx = 0.0f
    private var evy = 0.0f
    private var sacX = 0.0f
    private var sacY = 0.0f
    private var nextSaccade = 1.5f

    private var blinking = false
    private var blinkT = 0.0f
    private var blinkTimer = 2.5f

    private var touching = false
    private var touchX = 0.0f
    private var touchY = 0.0f

    /** لمس المستخدم: العينان تتبعان الإصبع. g داخل دائرة الوحدة. */
    fun touch(gx: Float, gy: Float) {
        touching = true
        touchX = gx
        touchY = gy
        if (reduceMotion) snapGaze()
    }

    fun release() {
        touching = false
        if (reduceMotion) snapGaze()
    }

    private fun snapGaze() {
        eyeX = if (touching) touchX else 0f
        eyeY = if (touching) touchY else 0f
        evx = 0f
        evy = 0f
        frame += 1
    }

    fun step(dt: Float) {
        val look = lookOf(state)
        val calm = reduceMotion

        // تنعيم كل معاملات الحالة (ثابت زمني ~0.33 ثانية)
        val kLook = 1f - exp(-3f * dt)
        flow += ((if (calm) 0f else look.flow) - flow) * kLook
        swirl += ((if (calm) 0f else look.swirl) - swirl) * kLook
        energy += (look.energy - energy) * kLook
        pulseHz += ((if (calm) 0f else look.pulseHz) - pulseHz) * kLook
        pulseAmp += ((if (calm) 0f else look.pulseAmp) - pulseAmp) * kLook

        phase += dt * flow
        pulsePhase += dt * pulseHz * TWO_PI
        if (pulsePhase > TWO_PI) pulsePhase -= TWO_PI
        time += dt

        // مستوى الصوت: حقيقي فقط. إن لم يوفّر المصدر قيمة فلا تفاعل صوتياً ولا نخترع بيانات.
        val raw = if (state == OrbState.Speaking) audioLevel?.invoke() else null
        hasLevel = raw != null
        val target = (raw ?: 0f).coerceIn(0f, 1f)
        val kLevel = if (target > level) 1f - exp(-30f * dt) else 1f - exp(-8f * dt)
        level += (target - level) * kLevel
        val lv = if (calm) 0f else level

        breath = 1f + pulseAmp * sin(pulsePhase) + lv * LEVEL_RADIUS_GAIN
        lift = lv * LEVEL_LIFT_GAIN

        updateEyes(dt, look, lv, calm)
        frame += 1
    }

    private fun updateEyes(dt: Float, look: OrbLook, lv: Float, calm: Boolean) {
        // شكل العينين حسب الحالة
        val kEye = 1f - exp(-6f * dt)
        val speakingPulse = if (state == OrbState.Speaking) lv * 0.06f else 0f
        eyeSx += (look.eyeSx - eyeSx) * kEye
        eyeSy += (look.eyeSy + speakingPulse - eyeSy) * kEye

        // هدف النظر
        var tx = 0f
        var ty = 0f
        if (touching) {
            tx = touchX
            ty = touchY
        } else if (!calm) {
            when (state) {
                OrbState.Thinking -> {
                    tx = 0.62f * sin(time * 0.9f)
                    ty = -0.26f
                }
                OrbState.Listening -> {
                    tx = 0.06f * sin(time * 0.7f)
                    ty = -0.12f
                }
                else -> {
                    if (time >= nextSaccade) {
                        val a = Random.nextFloat() * TWO_PI
                        val mag = (0.5f + Random.nextFloat() * 0.5f) * look.saccade
                        sacX = cos(a) * mag
                        sacY = sin(a) * mag
                        nextSaccade = time + 1.8f + Random.nextFloat() * 2.4f
                    }
                    tx = sacX
                    ty = sacY
                }
            }
        }

        // نابض خفيف (استجابة طبيعية مع تجاوز بسيط)
        evx += ((tx - eyeX) * GAZE_K - evx * GAZE_D) * dt
        evy += ((ty - eyeY) * GAZE_K - evy * GAZE_D) * dt
        eyeX += evx * dt
        eyeY += evy * dt
        val len = hypot(eyeX, eyeY)
        if (len > 1f) {
            eyeX /= len
            eyeY /= len
        }

        // الرمش: إغلاق سريع ثم فتح أبطأ
        if (blinking) {
            blinkT += dt
            blinkOpen = when {
                blinkT < 0.07f -> 1f - 0.92f * (blinkT / 0.07f)
                blinkT < 0.18f -> 0.08f + 0.92f * ((blinkT - 0.07f) / 0.11f)
                else -> {
                    blinking = false
                    1f
                }
            }
        } else if (!calm) {
            blinkOpen = 1f
            blinkTimer -= dt
            if (blinkTimer <= 0f) {
                blinking = true
                blinkT = 0f
                blinkTimer = look.blinkMin + Random.nextFloat() * (look.blinkMax - look.blinkMin)
            }
        } else {
            blinkOpen = 1f
        }
    }
}

/**
 * ينشئ حامل الحركة ويشغّل حلقة إطارات واحدة:
 * - تتوقف الحلقة عندما يخرج التطبيق من الواجهة (STOP) وتعود عند START، فلا استهلاك في الخلفية.
 * - مع «تقليل الحركة» لا تعمل حلقة مستمرة: حلقة قصيرة (~0.9 ثانية) فقط لتثبيت الشكل عند تغير الحالة.
 */
@Composable
internal fun rememberOrbMotion(
    state: OrbState,
    audioLevel: (() -> Float?)?,
    reduce: Boolean
): OrbMotion {
    val motion = remember { OrbMotion(state) }
    SideEffect {
        motion.state = state
        motion.audioLevel = audioLevel
        motion.reduceMotion = reduce
    }

    val owner = LocalLifecycleOwner.current
    var started by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(started, reduce, if (reduce) state else null) {
        if (!started) return@LaunchedEffect
        var last = 0L
        var elapsed = 0f
        while (!reduce || elapsed < 0.9f) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            elapsed += dt
            motion.step(dt)
        }
    }
    return motion
}
