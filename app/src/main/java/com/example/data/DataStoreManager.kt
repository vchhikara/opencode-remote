package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    companion object {
        val LAST_IP = stringPreferencesKey("last_ip")
    }

    // The API key is a bearer credential for the bridge's auth gate, so it is
    // kept in an Android Keystore-backed encrypted store, not plain DataStore.
    private val secureApiKeyStore = SecureApiKeyStore(context)

    val apiKeyFlow: Flow<String?> = secureApiKeyStore.apiKeyFlow()

    val lastIpFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_IP]
    }

    suspend fun saveApiKey(key: String) {
        secureApiKeyStore.saveApiKey(key)
    }

    suspend fun saveLastIp(ip: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_IP] = ip
        }
    }
}
