package com.syron.app.domain.model

/**
 * ربط الشخصية بصوت وتعبير في المستقبل.
 * محجوز فقط: لا يقرؤه أي كود حالياً، ولا يوجد محرك أصوات بعد.
 * null يعني «لا ربط»: الصوت المستعمل هو الاختيار العام للمستخدم، لأن الصوت مستقل عن الشخصية.
 */
data class VoiceBinding(
    val voiceId: String? = null,
    val expressionId: String? = null
)

/**
 * تعريف شخصية واحدة (بيانات فقط، بلا منطق محادثة).
 * - id: معرّف ثابت يُحفظ في التخزين، فلا يُغيَّر أبداً بعد إصدار الشخصية.
 * - name/summary: ما يعرضه التطبيق للمستخدم.
 * - instructions: تعليمات الأسلوب التي تُضاف إلى توجيه النظام (انظر SystemPrompt).
 * - voice: ربط مستقبلي بالصوت، فارغ افتراضياً.
 */
data class Personality(
    val id: String,
    val name: String,
    val summary: String,
    val instructions: String,
    val voice: VoiceBinding = VoiceBinding()
)
