package com.opencode.remote.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_remote_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(host: String, port: Int, token: String, deviceName: String) {
        sharedPreferences.edit()
            .putString("host", host)
            .putInt("port", port)
            .putString("token", token)
            .putString("deviceName", deviceName)
            .apply()
    }

    fun getLastCredentials(): Credentials? {
        val host = sharedPreferences.getString("host", null) ?: return null
        val port = sharedPreferences.getInt("port", -1).takeIf { it != -1 } ?: return null
        val token = sharedPreferences.getString("token", null) ?: return null
        val deviceName = sharedPreferences.getString("deviceName", null) ?: return null
        
        return Credentials(host, port, token, deviceName)
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    data class Credentials(val host: String, val port: Int, val token: String, val deviceName: String)
}
