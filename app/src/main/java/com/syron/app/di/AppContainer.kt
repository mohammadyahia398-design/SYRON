package com.syron.app.di

import android.content.Context
import com.syron.app.data.local.SecureStore
import com.syron.app.data.remote.GeminiClient
import com.syron.app.domain.repository.ChatRepository
import com.syron.app.domain.repository.SettingsRepository

/** حقن يدوي: المكان الوحيد الذي يعرف الأصناف الحقيقية. */
class AppContainer(context: Context) {
    val settings: SettingsRepository = SecureStore(context)
    val chat: ChatRepository = GeminiClient()
}
