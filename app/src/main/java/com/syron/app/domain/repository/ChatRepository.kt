package com.syron.app.domain.repository

import com.syron.app.domain.model.ChatMsg
import kotlinx.coroutines.flow.Flow

class ChatException(message: String) : Exception(message)

interface ChatRepository {
    fun stream(apiKey: String, model: String, system: String, history: List<ChatMsg>): Flow<String>
    fun cancel()
}
