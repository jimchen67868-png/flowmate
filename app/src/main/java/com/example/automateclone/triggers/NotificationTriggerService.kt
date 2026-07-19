package com.example.automateclone.triggers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.automateclone.engine.FlowEngine
import com.example.automateclone.model.BlockType
import com.example.automateclone.model.FlowRepository

/**
 * Requires the user to grant "Notification access" in system settings
 * (this can't be requested as a normal runtime permission — the UI links
 * out to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).
 */
class NotificationTriggerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val repo = FlowRepository(applicationContext)
        val engine = FlowEngine(applicationContext)
        val packageName = sbn.packageName
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString().orEmpty()

        repo.loadAll().filter { it.enabled }.forEach { flow ->
            flow.triggerBlocks()
                .filter { it.type == BlockType.NOTIFICATION_RECEIVED }
                .filter { trigger ->
                    val pkgFilter = trigger.config["packageName"].orEmpty()
                    val textFilter = trigger.config["textContains"].orEmpty()
                    (pkgFilter.isBlank() || pkgFilter == packageName) &&
                        (textFilter.isBlank() || text.contains(textFilter, ignoreCase = true))
                }
                .forEach { trigger -> engine.runFrom(flow, trigger) }
        }
    }
}
