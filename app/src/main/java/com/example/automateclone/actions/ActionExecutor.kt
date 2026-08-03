package com.example.automateclone.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockType

object ActionExecutor {

    private const val CHANNEL_ID = "flowmate_actions"

    fun execute(context: Context, block: Block) {
        when (block.type) {
            BlockType.SHOW_NOTIFICATION -> showNotification(
                context,
                title = block.config["title"] ?: "Flowmate",
                text = block.config["text"].orEmpty(),
                colorHex = block.config["colorHex"]
            )
            BlockType.SHOW_TOAST -> Toast.makeText(
                context, block.config["text"].orEmpty(), Toast.LENGTH_SHORT
            ).show()
            BlockType.VIBRATE -> vibrate(
                context,
                durationMs = block.config["durationMs"]?.toLongOrNull() ?: 300L
            )
            BlockType.LAUNCH_APP -> launchApp(context, block.config["packageName"].orEmpty())
            BlockType.SET_VOLUME -> setVolume(
                context,
                level = block.config["level"]?.toIntOrNull() ?: 5
            )
            BlockType.SIMULATED_TAP -> simulatedTap(
                context,
                x = block.config["x"]?.toFloatOrNull() ?: 0f,
                y = block.config["y"]?.toFloatOrNull() ?: 0f
            )
            BlockType.SET_WALLPAPER -> setWallpaperColor(context, block.config["colorHex"].orEmpty())
            BlockType.COPY_TO_CLIPBOARD -> copyToClipboard(context, block.config["text"].orEmpty())
            else -> { /* not an action block */ }
        }
    }

    private fun showNotification(context: Context, title: String, text: String, colorHex: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Flowmate Actions", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
        if (!colorHex.isNullOrBlank()) {
            try {
                builder.color = Color.parseColor(colorHex)
                builder.setColorized(true)
            } catch (e: IllegalArgumentException) {
                Log.w("ActionExecutor", "Invalid color hex for notification: $colorHex")
            }
        }
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun vibrate(context: Context, durationMs: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun setVolume(context: Context, level: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }

    private fun simulatedTap(context: Context, x: Float, y: Float) {
        val service = TapAccessibilityService.instance
        if (service == null) {
            Toast.makeText(
                context,
                "Enable Flowmate's Accessibility Service (Settings > Accessibility) to use Simulated Tap",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        service.performTap(x, y)
    }

    private fun setWallpaperColor(context: Context, colorHex: String) {
        if (colorHex.isBlank()) return
        try {
            val color = Color.parseColor(colorHex)
            val metrics = context.resources.displayMetrics
            val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            WallpaperManager.getInstance(context).setBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Failed to set wallpaper color: $colorHex", e)
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Flowmate", text))
    }
}
