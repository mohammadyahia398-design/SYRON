package com.syron.app.presentation.orb

/**
 * حالات الـ Orb البصرية. مصدر الحالة الفعلي اليوم هو `ChatViewModel.generating` فقط
 * (يُترجم إلى Thinking عند التوليد، وIdle عدا ذلك). Listening وSpeaking لا مصدر لهما بعد
 * لأن نظام الصوت لم يُبنَ، فتُمرَّران صراحةً عبر المعامل `state` حين يوجد مصدرهما الحقيقي.
 */
enum class OrbState { Idle, Listening, Thinking, Speaking }

/** قيم الهدف لكل حالة. تُنعَّم الانتقالات بينها في OrbMotion فلا توجد قفزات. */
internal class OrbLook(
    /** سرعة تدفق السوائل الداخلية (وحدة الزمن في الشيدر). */
    val flow: Float,
    /** شدة التدفق الدائري العضوي (0..1). */
    val swirl: Float,
    /** سطوع/حيوية عامة وشدة التوهج (0..1). */
    val energy: Float,
    /** تردد النبض الهادئ (هرتز) وسعته كنسبة من نصف القطر. */
    val pulseHz: Float,
    val pulseAmp: Float,
    /** مقياس عرض/ارتفاع العينين في هذه الحالة. */
    val eyeSx: Float,
    val eyeSy: Float,
    /** مجال الفاصل الزمني بين الرمشات (ثوانٍ). */
    val blinkMin: Float,
    val blinkMax: Float,
    /** مقدار الحركة العشوائية الصغيرة للنظر (0 = لا شيء). */
    val saccade: Float
)

private val IdleLook = OrbLook(
    flow = 0.55f, swirl = 0.25f, energy = 0.45f,
    pulseHz = 0.28f, pulseAmp = 0.012f,
    eyeSx = 1.00f, eyeSy = 1.00f,
    blinkMin = 2.5f, blinkMax = 5.5f, saccade = 1.0f
)

private val ListeningLook = OrbLook(
    flow = 0.95f, swirl = 0.45f, energy = 0.70f,
    pulseHz = 0.60f, pulseAmp = 0.028f,
    eyeSx = 1.06f, eyeSy = 1.10f,
    blinkMin = 4.0f, blinkMax = 7.0f, saccade = 0.0f
)

private val ThinkingLook = OrbLook(
    flow = 1.50f, swirl = 1.00f, energy = 0.80f,
    pulseHz = 0.45f, pulseAmp = 0.016f,
    eyeSx = 0.98f, eyeSy = 0.86f,
    blinkMin = 3.0f, blinkMax = 6.0f, saccade = 0.0f
)

private val SpeakingLook = OrbLook(
    flow = 1.90f, swirl = 0.55f, energy = 0.75f,
    pulseHz = 0.90f, pulseAmp = 0.008f,
    eyeSx = 1.00f, eyeSy = 1.00f,
    blinkMin = 2.5f, blinkMax = 5.5f, saccade = 0.45f
)

internal fun lookOf(state: OrbState): OrbLook = when (state) {
    OrbState.Idle -> IdleLook
    OrbState.Listening -> ListeningLook
    OrbState.Thinking -> ThinkingLook
    OrbState.Speaking -> SpeakingLook
}
