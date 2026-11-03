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
import kotlinx.coroutines.launch
import com.syron.app.di.AppContainer
import com.syron.app.domain.model.ChatMsg
import com.syron.app.domain.model.Personalities
import com.syron.app.domain.model.Personality
import com.syron.app.domain.repository.ChatException
import com.syron.app.domain.repository.ChatRepository
import com.syron.app.domain.repository.SettingsRepository

class ChatViewModel(
    private val store: SettingsRepository,
    private val client: ChatRepository
) : ViewModel() {

    val messages = mutableStateListOf<ChatMsg>()

    var generating by mutableStateOf(false)
        private set
    var hasKey by mutableStateOf(store.hasSecret(KEY_NAME))
        private set
    var model by mutableStateOf(store.getPlain("model", DEFAULT_MODEL))
        private set
    var personalityId by mutableStateOf(store.getPlain("personality", Personalities.DEFAULT_ID))
        private set

    val personality: Personality
        get() = Personalities.byId(personalityId)

    private var job: Job? = null
    private var stopped = false
    private var nextId = 1L

    fun saveSettings(newKey: String, newModel: String) {
        try {
            if (newKey.isNotBlank()) {
                store.putSecret(KEY_NAME, newKey.trim())
                hasKey = true
            }
            if (newModel.isNotBlank()) {
                model = newModel.trim()
                store.putPlain("model", model)
            }
        } catch (e: Exception) {
            messages.add(ChatMsg(nextId++, false, "تعذّر حفظ الإعدادات: ${e.message}", true))
        }
    }

    fun removeKey() {
        store.deleteSecret(KEY_NAME)
        hasKey = false
    }

    fun selectPersonality(id: String) {
        personalityId = id
        store.putPlain("personality", id)
    }

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
        val key = store.getSecret(KEY_NAME)
        if (key == null) {
            messages.add(ChatMsg(nextId++, false, "أضف مفتاح Gemini من الإعدادات أولاً.", true))
            return
        }

        messages.add(ChatMsg(nextId++, true, t))
        val replyId = nextId++
        messages.add(ChatMsg(replyId, false, ""))

        // آخر 20 رسالة فقط لتوفير التوكنز، ويجب أن تبدأ برسالة المستخدم
        val history = messages
            .filter { !it.isError && it.id != replyId && it.text.isNotBlank() }
            .takeLast(20)
            .dropWhile { !it.fromUser }

        val system = Personalities.systemPrompt(personality)
        val currentModel = model

        generating = true
        stopped = false
        job = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                client.stream(key, currentModel, system, history).collect { delta ->
                    sb.append(delta)
                    setText(replyId, sb.toString())
                }
                if (sb.isEmpty()) fail(replyId, "", "لم يصل أي رد من النموذج.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!stopped) fail(replyId, sb.toString(), friendly(e))
            } finally {
                generating = false
                val i = messages.indexOfFirst { it.id == replyId }
                if (i >= 0 && messages[i].text.isEmpty() && !messages[i].isError) {
                    messages.removeAt(i)
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

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        private const val KEY_NAME = "gemini_key"
    }
}
class ChatViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(container.settings, container.chat) as T
}
