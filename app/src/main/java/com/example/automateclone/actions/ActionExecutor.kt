package com.example.automateclone.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.automateclone.model.Block
import com.example.automateclone.model.BlockType

/**
 * Runs the side effect for a single ACTION block. Each `when` branch reads
 * its params from Block.config (populated by the flow editor's config sheet).
 *
 * Note: SEND_SMS, POST_NOTIFICATIONS etc. require the corresponding runtime
 * permission to already be granted; permission requests are handled in the
 * UI layer (MainActivity), not here.
 */
object ActionExecutor {

    private const val CHANNEL_ID = "flowmate_actions"

    fun execute(context: Context, block: Block) {
        when (block.type) {
            BlockType.SEND_SMS -> sendSms(
                phoneNumber = block.config["phoneNumber"].orEmpty(),
                message = block.config["message"].orEmpty()
            )
            BlockType.SHOW_NOTIFICATION -> showNotification(
                context,
                title = block.config["title"] ?: "Flowmate",
                text = block.config["text"].orEmpty()
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
            else -> { /* not an action block; engine shouldn't call execute() for these */ }
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        if (phoneNumber.isBlank() || message.isBlank()) return
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
    }

    private fun showNotification(context: Context, title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Flowmate Actions", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
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
}
