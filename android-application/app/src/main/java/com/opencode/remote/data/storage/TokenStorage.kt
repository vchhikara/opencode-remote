package com.opencode.remote.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStorage(context: Context) {
    private val appContext = context.applicationContext

    // Keystore initialization requires I/O; defer it off the Activity UI thread.
    private val sharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_remote_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Commit synchronously on an I/O dispatcher before acknowledging rotation. */
    fun saveCredentials(host: String, port: Int, token: String, deviceName: String, scheme: String = "ws"): Boolean {
        return sharedPreferences.edit()
            .putString("host", host)
            .putInt("port", port)
            .putString("token", token)
            .putString("deviceName", deviceName)
            .putString("scheme", scheme)
            .commit()
    }

    fun getLastCredentials(): Credentials? {
        val host = sharedPreferences.getString("host", null) ?: return null
        val port = sharedPreferences.getInt("port", -1).takeIf { it != -1 } ?: return null
        val token = sharedPreferences.getString("token", null) ?: return null
        val deviceName = sharedPreferences.getString("deviceName", null) ?: return null
        
        val scheme = sharedPreferences.getString("scheme", "ws").takeIf { it == "ws" || it == "wss" } ?: "ws"
        return Credentials(host, port, token, deviceName, scheme)
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    data class Credentials(val host: String, val port: Int, val token: String, val deviceName: String, val scheme: String = "ws")
}
