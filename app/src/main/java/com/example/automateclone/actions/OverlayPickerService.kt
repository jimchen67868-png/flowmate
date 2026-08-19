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

    private var startMarkerView: View? = null
    private var endMarkerView: View? = null
    private var lineView: LineOverlayView? = null

    private var controlsView: View? = null
    private var controlsParams: WindowManager.LayoutParams? = null
    private var positionLabel: TextView? = null

    private var pointsNeeded = 1

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        if (markerView != null || startMarkerView != null) {
            return START_NOT_STICKY
        }
        pointsNeeded = intent?.getIntExtra(EXTRA_POINTS_NEEDED, 1) ?: 1

        if (pointsNeeded > 1) {
            showLine()
            startMarkerView = showDraggableMarker(
                fillColor = Color.argb(130, 0, 200, 0),
                strokeColor = Color.GREEN,
                xFraction = 0.35f
            )
            endMarkerView = showDraggableMarker(
                fillColor = Color.argb(130, 255, 0, 0),
                strokeColor = Color.RED,
                xFraction = 0.65f
            )
        } else {
            markerView = showDraggableMarker(
                fillColor = Color.argb(130, 255, 0, 0),
                strokeColor = Color.RED,
                xFraction = 0.5f
            )
        }
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

    private fun showLine() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val view = LineOverlayView(this) { centerOf(startMarkerView) to centerOf(endMarkerView) }
        val params = WindowManager.LayoutParams(
            metrics.widthPixels, metrics.heightPixels,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        windowManager.addView(view, params)
        lineView = view
    }

    private fun showDraggableMarker(fillColor: Int, strokeColor: Int, xFraction: Float): View {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val marker = CrosshairView(this, fillColor, strokeColor)
        val params = WindowManager.LayoutParams(
            MARKER_SIZE_PX, MARKER_SIZE_PX,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels * xFraction).toInt() - MARKER_SIZE_PX / 2
            y = metrics.heightPixels / 2 - MARKER_SIZE_PX / 2
        }

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
                    lineView?.invalidate()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(marker, params)
        return marker
    }

    private fun showControls() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(210, 25, 25, 25))
            setPadding(28, 20, 28, 20)
        }

        val label = TextView(this).apply {
            text = if (pointsNeeded > 1) {
                "Drag the green start and red end markers, then Confirm"
            } else {
                "Drag marker into place, then Confirm"
            }
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }

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

        var downRawX = 0f
        var downRawY = 0f
        var downParamsX = 0
        var downParamsY = 0

        layout.setOnTouchListener { view, event ->
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
                    params.y = downParamsY - (event.rawY - downRawY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(layout, params)
        controlsView = layout
        controlsParams = params
        updateLivePosition()
    }

    private fun centerOf(view: View?): Pair<Float, Float>? {
        val v = view ?: return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return (loc[0] + v.width / 2f) to (loc[1] + v.height / 2f)
    }

    private fun updateLivePosition() {
        if (pointsNeeded > 1) {
            val s = centerOf(startMarkerView)
            val e = centerOf(endMarkerView)
            positionLabel?.text = buildString {
                if (s != null) append("Start: (${s.first.toInt()}, ${s.second.toInt()})  ")
                if (e != null) append("End: (${e.first.toInt()}, ${e.second.toInt()})")
            }
        } else {
            val c = centerOf(markerView)
            if (c != null) positionLabel?.text = "Position: (${c.first.toInt()}, ${c.second.toInt()})"
        }
    }

    private fun onConfirmTapped() {
        if (pointsNeeded > 1) {
            val s = centerOf(startMarkerView)
            val e = centerOf(endMarkerView)
            if (s != null && e != null) {
                finishPicking(listOf(s, e))
            }
        } else {
            val c = centerOf(markerView)
            if (c != null) {
                finishPicking(listOf(c))
            }
        }
    }

    private fun finishPicking(points: List<Pair<Float, Float>>) {
        OverlayPicker.onPicked?.invoke(points)
        OverlayPicker.onPicked = null
        OverlayPicker.onCancelled = null
        stopSelf()
    }

    private fun onCancelTapped() {
        OverlayPicker.onCancelled?.invoke()
        OverlayPicker.onPicked = null
        OverlayPicker.onCancelled = null
        stopSelf()
    }

    private fun removeOverlayViews() {
        markerView?.let { runCatching { windowManager.removeView(it) } }
        startMarkerView?.let { runCatching { windowManager.removeView(it) } }
        endMarkerView?.let { runCatching { windowManager.removeView(it) } }
        lineView?.let { runCatching { windowManager.removeView(it) } }
        controlsView?.let { runCatching { windowManager.removeView(it) } }
        markerView = null
        startMarkerView = null
        endMarkerView = null
        lineView = null
        controlsView = null
        positionLabel = null
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
            .setContentText("Drag the marker(s) into place, then tap Confirm")
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

private class CrosshairView(
    context: Context,
    fillColor: Int,
    strokeColor: Int
) : View(context) {
    private val strokePaint = Paint().apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = fillColor
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

private class LineOverlayView(
    context: Context,
    private val pointsProvider: () -> Pair<Pair<Float, Float>?, Pair<Float, Float>?>
) : View(context) {
    private val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (start, end) = pointsProvider()
        if (start != null && end != null) {
            canvas.drawLine(start.first, start.second, end.first, end.second, linePaint)
        }
    }
}
