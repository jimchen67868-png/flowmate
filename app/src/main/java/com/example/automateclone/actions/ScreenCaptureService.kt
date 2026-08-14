package com.example.automateclone.actions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.automateclone.engine.FlowLog
import com.example.automateclone.engine.LogLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            FlowLog.add("System", "Screenshot: projection onStop() fired — token invalidated", LogLevel.ERROR)
            tearDownCaptureSurface()
            mediaProjection = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundWithNotification()
        } catch (e: Exception) {
            FlowLog.add("System", "Screenshot: startForeground failed — ${e.javaClass.simpleName}: ${e.message}", LogLevel.ERROR)
            instance = this
            return START_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        FlowLog.add("System", "Screenshot: onStartCommand resultCode=$resultCode hasData=${data != null}")
        if (resultCode != 0 && data != null) {
            try {
                tearDownCaptureSurface()
                mediaProjection?.unregisterCallback(projectionCallback)
                mediaProjection?.stop()

                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = manager.getMediaProjection(resultCode, data)
                projection.registerCallback(projectionCallback, mainHandler)
                mediaProjection = projection
                setUpCaptureSurface(projection)
                FlowLog.add("System", "Screenshot: mediaProjection acquired + capture surface ready")
            } catch (e: Exception) {
                FlowLog.add("System", "Screenshot: setup failed — ${e.javaClass.simpleName}: ${e.message}", LogLevel.ERROR)
            }
        } else {
            FlowLog.add("System", "Screenshot: missing resultCode/data, projection NOT set", LogLevel.ERROR)
        }
        instance = this
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        FlowLog.add("System", "Screenshot: service onDestroy — projection cleared", LogLevel.ERROR)
        tearDownCaptureSurface()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        if (instance == this) instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setUpCaptureSurface(projection: MediaProjection) {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        captureWidth = metrics.widthPixels
        captureHeight = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        val display = projection.createVirtualDisplay(
            "FlowmateScreenshot", captureWidth, captureHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, mainHandler
        )
        imageReader = reader
        virtualDisplay = display
    }

    private fun tearDownCaptureSurface() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
    }

    suspend fun captureScreenshot(): String {
        mediaProjection
            ?: return "Error: screenshot not enabled — tap Enable Screenshot on the flow list screen (instance=${instance != null})"
        val reader = imageReader
            ?: return "Error: capture surface not ready — try tapping Enable Screenshot again"

        val width = captureWidth
        val height = captureHeight

        return try {
            val bitmap = suspendCancellableCoroutine<Bitmap> { cont ->
                reader.setOnImageAvailableListener({ r ->
                    val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * width
                        val bmp = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                        )
                        bmp.copyPixelsFromBuffer(buffer)
                        image.close()
                        r.setOnImageAvailableListener(null, null)
                        if (cont.isActive) cont.resume(bmp)
                    } catch (e: Exception) {
                        image.close()
                        r.setOnImageAvailableListener(null, null)
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }, mainHandler)
            }

            val file = File(filesDir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "flowmate_screenshot"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Flowmate Screenshot", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Flowmate can take screenshots")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2, notification)
        }
    }

    companion object {
        var instance: ScreenCaptureService? = null
            private set
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
    }
}
