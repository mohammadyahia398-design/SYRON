package com.syron.app.di

import android.content.Context
import com.syron.app.data.local.SecureStore
import com.syron.app.data.remote.GeminiClient
import com.syron.app.domain.repository.ChatRepository
import com.syron.app.domain.repository.SettingsRepository
import com.syron.app.domain.settings.SettingsCoordinator

/**
 * حقن يدوي: المكان الوحيد الذي يعرف الأصناف الحقيقية.
 * التخزين خاص: لا يصل إلى الإعدادات أحد إلا عبر settingsCoordinator.
 */
class AppContainer(context: Context) {
    private val settingsStore: SettingsRepository = SecureStore(context)
    val settingsCoordinator = SettingsCoordinator(settingsStore)
    val chat: ChatRepository = GeminiClient()
}
