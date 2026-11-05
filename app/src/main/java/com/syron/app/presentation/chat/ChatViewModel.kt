package com.syron.app.presentation.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.syron.app.di.AppContainer
import com.syron.app.domain.model.ChatMsg
import com.syron.app.domain.model.Personalities
import com.syron.app.domain.repository.ChatException
import com.syron.app.domain.repository.ChatRepository
import com.syron.app.domain.settings.SettingsCoordinator

/** المحادثة فقط. الإعدادات تُقرأ عبر المنسّق المشترك لحظة الإرسال (على IO) ولا تُكتب من هنا. */
class ChatViewModel(
    private val settings: SettingsCoordinator,
    private val client: ChatRepository
) : ViewModel() {

    val messages = mutableStateListOf<ChatMsg>()

    var generating by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var stopped = false
    private var nextId = 1L

    fun clearChat() {
        stop()
        messages.clear()
    }

    fun stop() {
        stopped = true
        client.cancel()
        job?.cancel()
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || generating) return

        generating = true
        stopped = false
        job = viewModelScope.launch {
            var replyId = NO_REPLY
            val sb = StringBuilder()
            try {
                val cfg = try {
                    settings.readChatConfig()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    messages.add(
                        ChatMsg(nextId++, false, "تعذّرت قراءة الإعدادات (${e.javaClass.simpleName})", true)
                    )
                    return@launch
                }

                // إذا ضغط المستخدم «إيقاف» أثناء قراءة الإعدادات (أو صادف الإلغاء لحظة انتهائها)،
                // نخرج هنا قبل إضافة أي رسالة وقبل أي طلب إلى Gemini.
                ensureActive()

                val key = cfg.key
                if (key == null) {
                    messages.add(
                        ChatMsg(nextId++, false, "أضف مفتاح Gemini (أو أعد حفظه) من الإعدادات أولاً.", true)
                    )
                    return@launch
                }

                messages.add(ChatMsg(nextId++, true, t))
                replyId = nextId++
                messages.add(ChatMsg(replyId, false, ""))

                // آخر 20 رسالة فقط لتوفير التوكنز، ويجب أن تبدأ برسالة المستخدم
                val history = messages
                    .filter { !it.isError && it.id != replyId && it.text.isNotBlank() }
                    .takeLast(20)
                    .dropWhile { !it.fromUser }

                val system = Personalities.systemPrompt(cfg.personality)

                try {
                    ensureActive()
                    client.stream(key, cfg.model, system, history).collect { delta ->
                        sb.append(delta)
                        setText(replyId, sb.toString())
                    }
                    if (sb.isEmpty()) fail(replyId, "", "لم يصل أي رد من النموذج.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!stopped) fail(replyId, sb.toString(), friendly(e))
                }
            } finally {
                generating = false
                if (replyId != NO_REPLY) {
                    val i = messages.indexOfFirst { it.id == replyId }
                    if (i >= 0 && messages[i].text.isEmpty() && !messages[i].isError) {
                        messages.removeAt(i)
                    }
                }
            }
        }
    }

    private fun setText(id: Long, text: String) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = messages[i].copy(text = text)
    }

    private fun fail(replyId: Long, partial: String, msg: String) {
        val i = messages.indexOfFirst { it.id == replyId }
        if (i < 0) return
        if (partial.isEmpty()) {
            messages[i] = messages[i].copy(text = msg, isError = true)
        } else {
            messages.add(i + 1, ChatMsg(nextId++, false, msg, true))
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is ChatException -> e.message ?: "خطأ من الخادم"
        is IOException -> "تعذّر الاتصال بالخادم، تحقق من الإنترنت. (${e.message ?: ""})"
        else -> "خطأ غير متوقع: ${e.message}"
    }

    private companion object {
        const val NO_REPLY = -1L
    }
}

class ChatViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(container.settingsCoordinator, container.chat) as T
}
