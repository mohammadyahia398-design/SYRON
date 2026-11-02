package com.syron.app

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ChatMsg(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

class GeminiException(message: String) : Exception(message)

class GeminiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeCall: Call? = null

    /** يقطع الاتصال الجاري فوراً (للإيقاف الفوري). */
    fun cancel() {
        activeCall?.cancel()
    }

    fun stream(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMsg>
    ): Flow<String> = flow {
        val contents = JSONArray()
        for (m in history) {
            contents.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
            )
        }
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put("contents", contents)

        val name = model.trim().removePrefix("models/")
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$name:streamGenerateContent?alt=sse"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = http.newCall(request)
        activeCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw GeminiException(explain(resp.code, resp.body?.string().orEmpty()))
                }
                val source = resp.body?.source() ?: throw GeminiException("وصل رد فارغ من الخادم")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val text = extractText(data)
                    if (text.isNotEmpty()) emit(text)
                }
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }.flowOn(Dispatchers.IO)

    private fun extractText(json: String): String {
        return try {
            val parts = JSONObject(json)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun explain(code: Int, body: String): String {
        val msg = try {
            JSONObject(body).getJSONObject("error").optString("message")
        } catch (e: Exception) {
            body.take(200)
        }
        val hint = when (code) {
            400 -> "الطلب مرفوض أو المفتاح غير صالح"
            401, 403 -> "المفتاح غير مقبول أو لا يملك صلاحية"
            404 -> "اسم النموذج غير موجود، غيّره من الإعدادات"
            429 -> "انتهت الحصة أو كثرت الطلبات، انتظر قليلاً"
            else -> "خطأ من الخادم"
        }
        return "$hint ($code): ${msg.take(160)}"
    }
}
