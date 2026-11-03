package com.syron.app.domain.repository

interface SettingsRepository {
    fun putSecret(name: String, value: String)
    fun getSecret(name: String): String?
    fun hasSecret(name: String): Boolean
    fun deleteSecret(name: String)
    fun putPlain(name: String, value: String)
    fun getPlain(name: String, default: String): String
}
