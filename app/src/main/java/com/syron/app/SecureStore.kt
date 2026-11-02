package com.syron.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** يحفظ الأسرار (مثل مفتاح API) مشفّرة بمفتاح لا يغادر Android Keystore، والإعدادات العادية بجانبها. */
class SecureStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("syron_store", Context.MODE_PRIVATE)

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun putSecret(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString("secret_$name", packed).apply()
    }

    fun getSecret(name: String): String? {
        val raw = prefs.getString("secret_$name", null) ?: return null
        return try {
            val (iv, data) = raw.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasSecret(name: String): Boolean = getSecret(name) != null

    fun deleteSecret(name: String) {
        prefs.edit().remove("secret_$name").apply()
    }

    fun putPlain(name: String, value: String) {
        prefs.edit().putString("plain_$name", value).apply()
    }

    fun getPlain(name: String, default: String): String =
        prefs.getString("plain_$name", default) ?: default

    private companion object {
        const val ALIAS = "syron_master_key_v1"
    }
}
