package com.opencode.remote.domain.model

/** User-selectable appearance. Dark is the redesign's default. */
enum class Appearance { Dark, Light, System }

/**
 * Pure Kotlin settings model — no Android imports, no DataStore/Preferences
 * types leak this far. Grows as new settings land; each new field is a
 * default-backed addition here plus a matching key in
 * [com.opencode.remote.data.settings.SettingsPreferencesDataSource].
 */
data class UserSettings(
    val appearance: Appearance = Appearance.Dark,
    /** True-black OLED surfaces when the resolved theme is dark. No effect in light mode. */
    val trueBlackEnabled: Boolean = false,
    /** Gate for local notifications posted from bridge NOTIFY frames (permission
     *  requests, errors, task completion) — see data/network/RemoteSessionManager. */
    val notificationsEnabled: Boolean = false,
    /** Logs each raw inbound/outbound WebSocketFrame's JSON to Logcat
     *  (tag "RemoteSessionManager.Trace") for diagnosing protocol issues. */
    val diagnosticTraceEnabled: Boolean = false
)
