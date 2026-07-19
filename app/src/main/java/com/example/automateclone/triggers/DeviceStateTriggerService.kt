package com.example.automateclone.triggers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.example.automateclone.engine.FlowEngine
import com.example.automateclone.model.BlockType
import com.example.automateclone.model.FlowRepository

/**
 * Long-running foreground service that listens for battery/charging/screen
 * broadcasts and fires matching trigger blocks across all saved flows.
 * Runs as a foreground service so Android doesn't kill it in the background.
 */
class DeviceStateTriggerService : Service() {

    private lateinit var repo: FlowRepository
    private lateinit var engine: FlowEngine

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBattery(intent)
                Intent.ACTION_POWER_CONNECTED -> handleCharging(true)
                Intent.ACTION_POWER_DISCONNECTED -> handleCharging(false)
                Intent.ACTION_SCREEN_ON -> handleScreen(true)
                Intent.ACTION_SCREEN_OFF -> handleScreen(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = FlowRepository(applicationContext)
        engine = FlowEngine(applicationContext)
        startForegroundWithNotification()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(receiver, filter)
    }

    private fun handleBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = (level * 100) / scale

        repo.loadAll().filter { it.enabled }.forEach { flow ->
            flow.triggerBlocks()
                .filter { it.type == BlockType.BATTERY_LEVEL }
                .filter { trigger ->
                    val threshold = trigger.config["threshold"]?.toIntOrNull() ?: return@filter false
                    when (trigger.config["direction"]) {
                        "below" -> percent <= threshold
                        else -> percent >= threshold // default "above"
                    }
                }
                .forEach { trigger -> engine.runFrom(flow, trigger) }
        }
    }

    private fun handleCharging(isCharging: Boolean) {
        val wanted = if (isCharging) "charging" else "not_charging"
        repo.loadAll().filter { it.enabled }.forEach { flow ->
            flow.triggerBlocks()
                .filter { it.type == BlockType.DEVICE_CHARGING && it.config["state"] == wanted }
                .forEach { trigger -> engine.runFrom(flow, trigger) }
        }
    }

    private fun handleScreen(isOn: Boolean) {
        val wanted = if (isOn) "on" else "off"
        repo.loadAll().filter { it.enabled }.forEach { flow ->
            flow.triggerBlocks()
                .filter { it.type == BlockType.SCREEN_STATE && it.config["state"] == wanted }
                .forEach { trigger -> engine.runFrom(flow, trigger) }
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "flowmate_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Flowmate Monitoring", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Flowmate is watching for triggers")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
