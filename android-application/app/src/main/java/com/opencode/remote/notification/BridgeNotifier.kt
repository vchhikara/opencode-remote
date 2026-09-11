package com.opencode.remote.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.opencode.remote.data.dto.NotifyPayload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns a bridge NOTIFY frame ([NotifyPayload]) into a local Android notification.
 * Only reachable while this connection is live and the app process is alive — the
 * bridge itself documents this as a foregrounded-client stand-in for real push
 * (bridge/main.js, notifyExternal comment), not background delivery.
 */
class BridgeNotifier(private val context: Context) {

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Permission requests, errors, and task completion from the connected bridge."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private var nextId = 1000

    fun notify(payload: NotifyPayload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val (title, text) = describe(payload)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(nextId++, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check above and this call — drop silently,
            // matches this app's general "never crash on a denied permission" posture.
        }
    }

    private fun describe(payload: NotifyPayload): Pair<String, String> {
        val detail = payload.detail as? JsonObject
        fun field(key: String): String? = detail?.get(key)?.jsonPrimitive?.contentOrNull

        return when (payload.kind) {
            "permission_request" -> "Permission requested" to
                (field("sessionId")?.let { "Session $it needs a decision" } ?: "The agent needs a decision")
            "error" -> "Agent error" to (field("message") ?: "An error occurred")
            "task_completed" -> "Task finished" to (field("taskId")?.let { "Task $it completed" } ?: "A task completed")
            else -> "Agent activity" to payload.kind
        }
    }

    private companion object {
        const val CHANNEL_ID = "agent_activity"
    }
}
