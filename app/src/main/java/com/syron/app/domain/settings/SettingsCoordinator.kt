package com.syron.app.domain.settings

import com.syron.app.domain.model.Personalities
import com.syron.app.domain.model.Personality
import com.syron.app.domain.model.SettingsKeys
import com.syron.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** لقطة من القيم المحفوظة فعلياً في التخزين. */
data class SettingsSnapshot(
    val hasKey: Boolean,
    val model: String,
    val personalityId: String
)

/** إعدادات الإرسال: تُقرأ معاً داخل قفل واحد لتكون متناسقة. */
class ChatConfig(
    val key: String?,
    val model: String,
    val personality: Personality
)

/**
 * نتيجة حفظ المفتاح والنموذج. null = لم تُطلب العملية، true/false = نتيجتها بعد القراءة للتحقق.
 * قد تكون النتيجة جزئية (مثلاً keyOk=true وmodelOk=false): لا يوجد تراجع.
 */
data class SaveResult(
    val keyOk: Boolean?,
    val modelOk: Boolean?,
    val snapshot: SettingsSnapshot?
)

data class PersonalityResult(
    val ok: Boolean,
    val snapshot: SettingsSnapshot?
)

enum class DeleteKeyStatus {
    /** كان المفتاح قابلاً للقراءة قبل الحذف ولم يعد قابلاً للقراءة بعده. */
    Confirmed,

    /** لم يكن هناك مفتاح قابل للقراءة أصلاً، فلا يمكن إثبات إزالة أي بيانات. */
    Unverified,

    /** وقع استثناء أثناء الحذف، أو بقي المفتاح قابلاً للقراءة. */
    Failed
}

data class DeleteKeyResult(
    val status: DeleteKeyStatus,
    val snapshot: SettingsSnapshot?
)

/**
 * نقطة الوصول الوحيدة إلى الإعدادات. نسخة واحدة مشتركة (من AppContainer).
 *
 * ما يضمنه:
 * - عدم تداخل أي عمليتين (قراءة أو كتابة): كل عملية تأخذ القفل وتنفّذ كل ما تحتاجه داخله.
 * - قراءة إعدادات الإرسال (المفتاح والنموذج والشخصية) معاً داخل قسم واحد،
 *   فلا ترى حالة قراءة نصفية أثناء حفظ آخر.
 * - تنفيذ عمليات التخزين (Keystore وSharedPreferences) على IO لا على الخيط الرئيسي.
 * - أقسام الكتابة غير قابلة للإلغاء حتى لا تنقطع في المنتصف بسبب إغلاق شاشة.
 *
 * ما لا يضمنه:
 * - ليست معاملة ذرية (Atomic Transaction) ولا يوجد تراجع (Rollback):
 *   إذا نجح حفظ المفتاح وفشل النموذج تبقى النتيجة جزئية، ويعكس SaveResult ذلك بدقة.
 * - وصول الكتابة إلى القرص: التخزين يستخدم apply() الذي لا يبلغ عن الفشل،
 *   والقراءة بعد الكتابة تثبت القابلية للقراءة داخل التطبيق فقط (وللمفتاح: سلامة التشفير).
 * - سلامة البيانات إن أُنهيت العملية (Process) في منتصف قسم كتابة.
 */
class SettingsCoordinator(
    private val store: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val lock = Mutex()

    suspend fun readSnapshot(): SettingsSnapshot? =
        lock.withLock { withContext(io) { snapshotLocked() } }

    /** قابلة للإلغاء. يرمي الاستثناء عند فشل القراءة؛ المستدعي يعالجه. */
    suspend fun readChatConfig(): ChatConfig =
        lock.withLock {
            withContext(io) {
                ChatConfig(
                    key = store.getSecret(SettingsKeys.GEMINI_KEY),
                    model = store.getPlain(SettingsKeys.MODEL, SettingsKeys.DEFAULT_MODEL)
                        .ifBlank { SettingsKeys.DEFAULT_MODEL },
                    personality = Personalities.byId(
                        store.getPlain(SettingsKeys.PERSONALITY, Personalities.DEFAULT_ID)
                    )
                )
            }
        }

    /** key: null إن لم يُطلب حفظ المفتاح. model: null إن لم يُطلب حفظ النموذج. */
    suspend fun saveKeyAndModel(key: String?, model: String?): SaveResult =
        lock.withLock {
            withContext(NonCancellable + io) {
                var keyOk: Boolean? = null
                if (key != null) {
                    keyOk = try {
                        store.putSecret(SettingsKeys.GEMINI_KEY, key)
                        store.getSecret(SettingsKeys.GEMINI_KEY) == key
                    } catch (e: Exception) {
                        false
                    }
                }
                var modelOk: Boolean? = null
                if (model != null) {
                    modelOk = try {
                        store.putPlain(SettingsKeys.MODEL, model)
                        store.getPlain(SettingsKeys.MODEL, "") == model
                    } catch (e: Exception) {
                        false
                    }
                }
                SaveResult(keyOk, modelOk, snapshotLocked())
            }
        }

    suspend fun savePersonality(id: String): PersonalityResult =
        lock.withLock {
            withContext(NonCancellable + io) {
                val ok = try {
                    store.putPlain(SettingsKeys.PERSONALITY, id)
                    store.getPlain(SettingsKeys.PERSONALITY, "") == id
                } catch (e: Exception) {
                    false
                }
                PersonalityResult(ok, snapshotLocked())
            }
        }

    suspend fun deleteKey(): DeleteKeyResult =
        lock.withLock {
            withContext(NonCancellable + io) {
                val readableBefore = readableLocked()
                val deleted = try {
                    store.deleteSecret(SettingsKeys.GEMINI_KEY)
                    true
                } catch (e: Exception) {
                    false
                }
                val readableAfter = readableLocked()
                val status = when {
                    !deleted || readableAfter -> DeleteKeyStatus.Failed
                    readableBefore -> DeleteKeyStatus.Confirmed
                    else -> DeleteKeyStatus.Unverified
                }
                DeleteKeyResult(status, snapshotLocked())
            }
        }

    // ---- داخلي: يُستدعى فقط ونحن داخل القفل وعلى IO ----

    private fun readableLocked(): Boolean = try {
        store.getSecret(SettingsKeys.GEMINI_KEY) != null
    } catch (e: Exception) {
        false
    }

    private fun snapshotLocked(): SettingsSnapshot? = try {
        SettingsSnapshot(
            hasKey = store.hasSecret(SettingsKeys.GEMINI_KEY),
            model = store.getPlain(SettingsKeys.MODEL, SettingsKeys.DEFAULT_MODEL)
                .ifBlank { SettingsKeys.DEFAULT_MODEL },
            personalityId = Personalities.byId(
                store.getPlain(SettingsKeys.PERSONALITY, Personalities.DEFAULT_ID)
            ).id
        )
    } catch (e: Exception) {
        null
    }
}
