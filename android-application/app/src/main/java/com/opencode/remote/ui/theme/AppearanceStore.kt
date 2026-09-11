package com.opencode.remote.ui.theme

import android.content.Context

/** Persists the appearance choice. Non-sensitive, so plain SharedPreferences — kept
 *  entirely separate from TokenStorage's encrypted credential store. */
class AppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    fun load(): Appearance =
        prefs.getString(KEY, null)?.let { name -> Appearance.entries.firstOrNull { it.name == name } } ?: Appearance.Dark

    fun save(appearance: Appearance) {
        prefs.edit().putString(KEY, appearance.name).apply()
    }

    private companion object {
        const val KEY = "appearance"
    }
}
