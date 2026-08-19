package com.example.automateclone.actions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayPickerService : Service() {

    private lateinit var windowManager: WindowManager
    private var markerView: View? = null
    private var markerParams: WindowManager.LayoutParams? = null
    private var controlsView: View? = null
    private var positionLabel: TextView? = null
    private var instructionLabel: TextView? = null

    private var pointsNeeded = 1
    private val collectedPoints = mutableListOf<Pair<Float, Float>>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        if (markerView != null) {
            // A picker is already up — ignore the duplicate start request
            // instead of stacking a second overlapping marker.
            return START_NOT_STICKY
        }
        pointsNeeded = intent?.getIntExtra(EXTRA_POINTS_NEEDED, 1) ?: 1
        collectedPoints.clear()
        showMarker()
        showControls()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayViews()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun showMarker() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val marker = CrosshairView(this)
        val params = WindowManager.LayoutParams(
            MARKER_SIZE_PX, MARKER_SIZE_PX,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels / 2 - MARKER_SIZE_PX / 2
            y = metrics.heightPixels / 2 - MARKER_SIZE_PX / 2
        }

        // Use a single fixed reference captured at ACTION_DOWN (raw touch
        // position + window position at that instant), then apply deltas
        // from that ONE reference for the whole gesture. Recomputing
        // getLocationOnScreen() on every ACTION_MOVE (previous approach)
        // creates a compounding feedback loop once the window itself starts
        // moving mid-gesture, causing the marker to run away across the
        // screen — this fixed-reference approach avoids that entirely.
        var downRawX = 0f
        var downRawY = 0f
        var downParamsX = 0
        var downParamsY = 0

        marker.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamsX = params.x
                    downParamsY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = downParamsX + (event.rawX - downRawX).toInt()
                    params.y = downParamsY + (event.rawY - downRawY).toInt()
                    windowManager.updateViewLayout(view, params)
                    updateLivePosition()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(marker, params)
        markerView = marker
        markerParams = params
    }

    private fun showControls() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(210, 25, 25, 25))
            setPadding(28, 20, 28, 20)
        }

        val label = TextView(this).apply {
            text = if (pointsNeeded > 1) "Drag marker to the START point, then Confirm" else "Drag marker into place, then Confirm"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        instructionLabel = label

        val posLabel = TextView(this).apply {
            setTextColor(Color.YELLOW)
            textSize = 12f
            setPadding(0, 0, 0, 12)
        }
        positionLabel = posLabel

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val confirmButton = Button(this).apply {
            text = "Confirm"
            setOnClickListener { onConfirmTapped() }
        }
        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener { onCancelTapped() }
        }
        buttonRow.addView(confirmButton)
        buttonRow.addView(cancelButton)

        layout.addView(label)
        layout.addView(posLabel)
        layout.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        windowManager.addView(layout, params)
        controlsView = layout
        updateLivePosition()
    }

    private fun currentMarkerCenter(): Pair<Float, Float>? {
        val marker = markerView ?: return null
        val loc = IntArray(2)
        marker.getLocationOnScreen(loc)
        return (loc[0] + marker.width / 2f) to (loc[1] + marker.height / 2f)
    }

    private fun updateLivePosition() {
        val center = currentMarkerCenter() ?: return
        positionLabel?.text = "Position: (${center.first.toInt()}, ${center.second.toInt()})"
    }

    private fun onConfirmTapped() {
        val center = currentMarkerCenter() ?: return
        collectedPoints += center

        if (collectedPoints.size >= pointsNeeded) {
            OverlayPicker.onPicked?.invoke(collectedPoints.toList())
            OverlayPicker.onPicked = null
            OverlayPicker.onCancelled = null
            stopSelf()
        } else {
            instructionLabel?.text = "Now drag to the END point, then Confirm"
        }
    }

    private fun onCancelTapped() {
        OverlayPicker.onCancelled?.invoke()
        OverlayPicker.onPicked = null
        OverlayPicker.onCancelled = null
        stopSelf()
    }

    private fun removeOverlayViews() {
        markerView?.let { runCatching { windowManager.removeView(it) } }
        controlsView?.let { runCatching { windowManager.removeView(it) } }
        markerView = null
        controlsView = null
        positionLabel = null
        instructionLabel = null
    }

    private fun startForegroundWithNotification() {
        val channelId = "flowmate_overlay_picker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Flowmate Position Picker", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Positioning overlay active")
            .setContentText("Drag the marker into place, then tap Confirm")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(3, notification)
        }
    }

    companion object {
        const val EXTRA_POINTS_NEEDED = "pointsNeeded"
        private const val MARKER_SIZE_PX = 120
    }
}

object OverlayPicker {
    var onPicked: ((List<Pair<Float, Float>>) -> Unit)? = null
    var onCancelled: (() -> Unit)? = null
}

private class CrosshairView(context: Context) : View(context) {
    private val strokePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(120, 255, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 6f
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, strokePaint)
    }
}
