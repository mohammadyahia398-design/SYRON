package com.syron.app.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syron.app.di.AppContainer
import com.syron.app.domain.model.Personalities
import com.syron.app.domain.model.Personality
import com.syron.app.domain.model.SettingsKeys
import com.syron.app.domain.settings.DeleteKeyStatus
import com.syron.app.domain.settings.SaveResult
import com.syron.app.domain.settings.SettingsCoordinator
import com.syron.app.domain.settings.SettingsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class NoticeKind { Success, Info, Error }

data class SettingsNotice(val kind: NoticeKind, val text: String)

/**
 * الكاتب الوحيد للإعدادات من جهة الواجهة. لا يصل إلى التخزين مباشرة:
 * كل شيء عبر SettingsCoordinator المشترك (قفل واحد، وخيط IO).
 * حالة الواجهة تُحدَّث من اللقطة التي أُعيدت قراءتها بعد الكتابة، لا من المدخلات.
 *
 * ملاحظتان عن الرسائل:
 * 1) الحفظ ليس معاملة ذرية. إذا نجح المفتاح وفشل النموذج (أو العكس) تبقى النتيجة جزئية،
 *    والرسالة تسمّي الجزء الناجح والجزء الفاشل، والحالة المعروضة هي ما قُرئ فعلاً من التخزين.
 * 2) التحقق بالقراءة يثبت أن القيمة قابلة للقراءة داخل التطبيق (وللمفتاح: سلامة التشفير
 *    وفكّه)، ولا يثبت وصول الكتابة إلى القرص لأن التخزين يستخدم apply(). لذلك لا تَعِد
 *    الرسائل بأكثر من ذلك.
 */
class SettingsViewModel(private val settings: SettingsCoordinator) : ViewModel() {

    var loaded by mutableStateOf(false)
        private set

    /** true إذا تعذّرت قراءة التخزين: عندها لا يُعرف هل المفتاح موجود، ولا يجوز عرض «لم يُضف بعد». */
    var readFailed by mutableStateOf(false)
        private set
    var hasKey by mutableStateOf(false)
        private set
    var model by mutableStateOf(SettingsKeys.DEFAULT_MODEL)
        private set
    var personalityId by mutableStateOf(Personalities.DEFAULT_ID)
        private set
    var saving by mutableStateOf(false)
        private set
    var notice by mutableStateOf<SettingsNotice?>(null)
        private set

    val personality: Personality
        get() = Personalities.byId(personalityId)

    init {
        viewModelScope.launch {
            try {
                val snap = settings.readSnapshot()
                if (snap != null) {
                    adopt(snap)
                } else {
                    readFailed = true
                    notice = SettingsNotice(NoticeKind.Error, READ_FAILED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                readFailed = true
                notice = SettingsNotice(NoticeKind.Error, READ_FAILED)
            }
            loaded = true
        }
    }

    fun dismissNotice() {
        notice = null
    }

    /** يحفظ المفتاح (إن لم يكن فارغاً) والنموذج (إن تغيّر). onKeySaved يُستدعى فقط بعد التحقق من قراءة المفتاح. */
    fun save(newKey: String, newModel: String, onKeySaved: () -> Unit = {}) {
        if (saving) return
        val key = newKey.trim().ifEmpty { null }
        val mdl = newModel.trim().takeIf { it.isNotEmpty() && it != model }
        if (key == null && mdl == null) {
            notice = SettingsNotice(NoticeKind.Info, "لا توجد تغييرات للحفظ")
            return
        }
        runOp("حدث خطأ غير متوقع أثناء الحفظ، وقد تكون النتيجة غير مكتملة") {
            val out = settings.saveKeyAndModel(key, mdl)
            out.snapshot?.let { adopt(it) }
            if (out.keyOk == true) onKeySaved()
            notice = describe(out)
        }
    }

    fun removeKey() {
        if (saving) return
        runOp("حدث خطأ غير متوقع أثناء حذف المفتاح") {
            val r = settings.deleteKey()
            r.snapshot?.let { adopt(it) }
            notice = when (r.status) {
                DeleteKeyStatus.Confirmed ->
                    SettingsNotice(
                        NoticeKind.Success,
                        "تم حذف المفتاح، ولم يعد قابلاً للقراءة داخل التطبيق"
                    )
                DeleteKeyStatus.Unverified ->
                    SettingsNotice(
                        NoticeKind.Info,
                        "لم يكن هناك مفتاح قابل للقراءة، ولا يمكن التأكد من إزالة بيانات تالفة. أضف مفتاحاً جديداً عند الحاجة"
                    )
                DeleteKeyStatus.Failed ->
                    SettingsNotice(NoticeKind.Error, "تعذّر حذف المفتاح")
            }
        }
    }

    fun selectPersonality(id: String) {
        if (saving || id == personalityId) return
        if (Personalities.all.none { it.id == id }) return
        runOp("حدث خطأ غير متوقع أثناء حفظ الشخصية") {
            val r = settings.savePersonality(id)
            r.snapshot?.let { adopt(it) }
            if (!r.ok || r.snapshot?.personalityId != id) {
                notice = SettingsNotice(NoticeKind.Error, "تعذّر حفظ الشخصية")
            }
        }
    }

    /**
     * يشغّل عملية كتابة بحماية موحّدة:
     * - لا يبتلع CancellationException.
     * - أي استثناء غير متوقع يتحول إلى رسالة خطأ مع محاولة تحديث الحالة من التخزين.
     * - saving يعود إلى false دائماً.
     */
    private fun runOp(failureText: String, block: suspend () -> Unit) {
        saving = true
        notice = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshQuietly()
                notice = SettingsNotice(NoticeKind.Error, failureText)
            } finally {
                saving = false
            }
        }
    }

    private suspend fun refreshQuietly() {
        try {
            settings.readSnapshot()?.let { adopt(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // تبقى القيم المعروضة من آخر لقطة ناجحة.
        }
    }

    private fun adopt(s: SettingsSnapshot) {
        readFailed = false
        hasKey = s.hasKey
        model = s.model
        personalityId = s.personalityId
    }

    private fun describe(out: SaveResult): SettingsNotice {
        if (out.snapshot == null) {
            return SettingsNotice(
                NoticeKind.Error,
                "تعذّر التحقق من الحالة المحفوظة، وقد تكون النتيجة غير مكتملة"
            )
        }
        val requested = listOfNotNull(out.keyOk, out.modelOk)
        return when {
            requested.all { it } ->
                SettingsNotice(NoticeKind.Success, "تم الحفظ والتحقق من القراءة داخل التطبيق")
            out.keyOk == true && out.modelOk == false ->
                SettingsNotice(
                    NoticeKind.Error,
                    "نتيجة جزئية: حُفظ المفتاح، لكن تعذّر حفظ النموذج"
                )
            out.keyOk == false && out.modelOk == true ->
                SettingsNotice(
                    NoticeKind.Error,
                    "نتيجة جزئية: حُفظ النموذج، لكن تعذّر حفظ المفتاح"
                )
            else ->
                SettingsNotice(
                    NoticeKind.Error,
                    "تعذّر التحقق من الحفظ، وقد تكون النتيجة غير مكتملة. راجع الحالة المعروضة"
                )
        }
    }

    private companion object {
        const val READ_FAILED = "تعذّرت قراءة الإعدادات المحفوظة"
    }
}

class SettingsViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(container.settingsCoordinator) as T
}
