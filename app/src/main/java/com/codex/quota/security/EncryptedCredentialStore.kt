package com.codex.quota.security

import android.content.Context
import android.content.SharedPreferences

interface CredentialStore {
    fun storeApiKey(accountId: String, apiKey: String)
    fun getApiKey(accountId: String): String?
    fun removeApiKey(accountId: String)
    fun clearAll()
}

class EncryptedCredentialStore(
    context: Context,
    private val keystoreManager: KeystoreManager
) : CredentialStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized override fun storeApiKey(accountId: String, apiKey: String) {
        val encrypted = keystoreManager.encrypt(apiKey)
        check(prefs.edit().putString(KEY_PREFIX + accountId, encrypted).commit()) { "无法保存登录凭据" }
    }

    @Synchronized override fun getApiKey(accountId: String): String? {
        val encrypted = prefs.getString(KEY_PREFIX + accountId, null) ?: return null
        return keystoreManager.decrypt(encrypted)
    }

    @Synchronized override fun removeApiKey(accountId: String) {
        check(prefs.edit().remove(KEY_PREFIX + accountId).commit())
    }

    @Synchronized override fun clearAll() {
        check(prefs.edit().clear().commit())
    }

    companion object {
        private const val PREFS_NAME = "secure_credentials"
        private const val KEY_PREFIX = "key_acc_"
    }
}
