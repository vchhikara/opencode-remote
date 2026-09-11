package com.opencode.remote.ui.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Plain-JVM formatting helpers (no Android/Compose), unit-tested in isolation. */
object Formatting {

    /** "mm:ss", or "h:mm:ss" past an hour. */
    fun elapsed(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    /** Some bridges report epoch seconds, some milliseconds — normalise to ms. */
    fun normalizeEpoch(value: Long): Long = if (value in 1..99_999_999_999L) value * 1000 else value

    /** Compact age: "now", "5m", "2h", "Mon", "12 Aug", "12 Aug 2024". */
    fun compactAge(epochMs: Long, now: Long, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()): String {
        val diff = now - epochMs
        if (diff < 60_000L) return "now"
        if (diff < 3_600_000L) return "${diff / 60_000L}m"
        if (diff < 86_400_000L) return "${diff / 3_600_000L}h"
        if (diff < 6 * 86_400_000L) return SimpleDateFormat("EEE", locale).apply { timeZone = zone }.format(epochMs)
        return shortDate(epochMs, now, locale, zone)
    }

    fun shortDate(epochMs: Long, now: Long, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMs }
        val nowCal = Calendar.getInstance(zone).apply { timeInMillis = now }
        val pattern = if (cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) "d MMM" else "d MMM yyyy"
        return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(epochMs)
    }

    fun clock(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(epochMs)

    /** "just now", "4 minutes ago", "2 hours ago", "on 12 Aug". */
    fun agoPhrase(epochMs: Long, now: Long): String {
        val diff = now - epochMs
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> { val m = diff / 60_000L; if (m == 1L) "1 minute ago" else "$m minutes ago" }
            diff < 86_400_000L -> { val h = diff / 3_600_000L; if (h == 1L) "1 hour ago" else "$h hours ago" }
            else -> "on " + shortDate(epochMs, now)
        }
    }

    /** Parses the bridge's ISO-8601 UTC audit timestamps ("2026-01-01T00:00:00.000Z").
     *  Returns null for anything else; callers then show the raw string. */
    fun parseIsoUtc(value: String): Long? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                return fmt.parse(value)?.time
            } catch (_: ParseException) {
            }
        }
        return null
    }

    /** Flattens an audit entry's free-form `detail` into one readable line. */
    fun auditDetail(detail: JsonElement?): String = when (detail) {
        null, JsonNull -> ""
        is JsonPrimitive -> detail.content
        is JsonArray -> detail.joinToString(", ") { auditDetail(it) }
        is JsonObject -> detail.entries.joinToString(" · ") { (k, v) ->
            val value = auditDetail(v)
            if (k == "command" || k == "decision" || k == "path") value else "$k $value"
        }
    }
}
