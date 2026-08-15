package com.example.automateclone.actions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.automateclone.engine.FlowLog
import com.example.automateclone.engine.LogLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GestureAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.capabilities = info.capabilities or AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES
        serviceInfo = info

        val caps = serviceInfo?.capabilities ?: -1
        val hasGestureCap = (caps and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
        FlowLog.add(
            "System",
            "Gestures: service connected, capabilities=$caps canPerformGestures=$hasGestureCap"
        )
        if (!hasGestureCap) {
            FlowLog.add(
                "System",
                "Gestures: CAPABILITY_CAN_PERFORM_GESTURES still missing after forcing it — device may restrict this",
                LogLevel.ERROR
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — this service only performs gestures, it never
        // inspects screen content or events.
    }

    override fun onInterrupt() { }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) instance = null
        FlowLog.add("System", "Gestures: accessibility service disconnected", LogLevel.ERROR)
        return super.onUnbind(intent)
    }

    suspend fun tap(x: Float, y: Float): String =
        performStroke(x, y, x, y, 50L, "Tap")

    suspend fun longPress(x: Float, y: Float, durationMs: Long): String =
        performStroke(x, y, x, y, durationMs.coerceAtLeast(400L), "Long Press")

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): String =
        performStroke(x1, y1, x2, y2, durationMs.coerceAtLeast(50L), "Swipe")

    private suspend fun performStroke(
        x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, label: String
    ): String {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val completed = withTimeoutOrNull(durationMs + 5000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        FlowLog.add("System", "Gestures: onCompleted fired for $label")
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        FlowLog.add("System", "Gestures: onCancelled fired for $label", LogLevel.ERROR)
                        if (cont.isActive) cont.resume(false)
                    }
                }
                val dispatched = dispatchGesture(gesture, callback, mainHandler)
                FlowLog.add("System", "Gestures: dispatchGesture($label) returned $dispatched")
                if (!dispatched && cont.isActive) {
                    cont.resume(false)
                }
            }
        }

        val target = if (x1 != x2 || y1 != y2) "($x1,$y1) -> ($x2,$y2)" else "($x1,$y1)"
        return when (completed) {
            true -> "$label OK $target"
            false -> "Error: $label was cancelled or failed to dispatch $target"
            null -> "Error: $label timed out $target"
        }
    }

    companion object {
        var instance: GestureAccessibilityService? = null
            private set
    }
}
