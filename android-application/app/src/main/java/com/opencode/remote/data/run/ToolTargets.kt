package com.opencode.remote.data.run

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pulls the human-meaningful "target" (a file path, a shell command, a search pattern…)
 * out of a STREAM_TOOL_CALL's free-form `input` object, for the compact
 * `VERB  target` rows on Home and the Run log. Only keys whose values are plain strings
 * are used; if nothing recognisable is present the target is simply omitted rather
 * than guessed.
 */
private val TARGET_KEYS = listOf(
    "filePath", "file_path", "path", "file", "command", "cmd",
    "pattern", "query", "url", "glob", "description"
)

fun extractToolTarget(input: JsonElement?): String? {
    when (input) {
        null -> return null
        is JsonPrimitive -> return if (input.isString) input.content.trim().takeIf { it.isNotEmpty() }?.oneLine(160) else null
        is JsonObject -> {
            for (key in TARGET_KEYS) {
                val v = input[key]
                if (v is JsonPrimitive && v.isString) {
                    val s = v.content.trim()
                    if (s.isNotEmpty()) return s.oneLine(160)
                }
            }
            return null
        }
        else -> return null
    }
}

/** opencode reports a failed tool as an error *object* in STREAM_TOOL_RESULT.output and
 *  a successful one as a string (see StreamToolResultDto). */
fun isErrorToolOutput(output: JsonElement?): Boolean = output is JsonObject
