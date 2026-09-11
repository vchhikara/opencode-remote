package com.opencode.remote.data.run

/**
 * Helpers for the bridge's free-text AGENT_STATE vocabulary. The bridge relays whatever
 * the wrapped CLI emits ("Idle", "Thinking...", anything else in future) — there is no
 * enum on the wire, so everything here is a tolerant, case-insensitive *reading* of that
 * text. Unknown strings are never rejected; callers fall back to a generic "working"
 * presentation and show the raw text verbatim.
 */
object AgentStateText {
    fun normalized(raw: String): String = raw.trim().lowercase()

    fun isIdle(raw: String): Boolean {
        val s = normalized(raw)
        return s.isEmpty() || s.contains("idle") || s == "ready" || s == "done" ||
            s.startsWith("finished") || s.startsWith("completed")
    }

    fun isError(raw: String): Boolean {
        val s = normalized(raw)
        return s.contains("error") || s.contains("fail")
    }

    fun isThinking(raw: String): Boolean {
        val s = normalized(raw)
        return s.contains("think") || s.contains("plan") || s.contains("reason") ||
            s.contains("analy") || s.contains("start")
    }
}

/** Collapses whitespace to one line and ellipsizes past [max] characters. */
fun String.oneLine(max: Int): String {
    val s = replace(Regex("\\s+"), " ").trim()
    return if (s.length > max) s.take((max - 1).coerceAtLeast(1)) + "…" else s
}
