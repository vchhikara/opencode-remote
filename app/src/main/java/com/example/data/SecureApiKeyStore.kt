package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stores the bridge pairing API key in an Android Keystore-backed encrypted
 * SharedPreferences file, separate from the plain [dataStore] used for
 * non-sensitive preferences (e.g. last IP). See D9: the key is a bearer
 * credential for the bridge's WebSocket/HTTP auth gate and must not sit in
 * plaintext DataStore.
 */
class SecureApiKeyStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun apiKeyFlow(): Flow<String?> = flowOf(getApiKey())

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val FILE_NAME = "secure_pairing_prefs"
        private const val KEY_API_KEY = "api_key"
    }
}
