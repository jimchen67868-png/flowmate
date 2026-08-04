package com.example.automateclone

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.automateclone.model.AutomationFlow
import com.example.automateclone.triggers.DeviceStateTriggerService
import com.example.automateclone.triggers.TimeTriggerScheduler
import com.example.automateclone.ui.FlowEditorScreen
import com.example.automateclone.ui.FlowListScreen
import com.example.automateclone.ui.theme.AutomateCloneTheme

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRuntimePermissions()
        requestExactAlarmPermissionIfNeeded()
        TimeTriggerScheduler.rescheduleNextAlarm(this)
        ContextCompat.startForegroundService(this, Intent(this, DeviceStateTriggerService::class.java))

        setContent {
            AutomateCloneTheme {
                var openFlow by remember { mutableStateOf<AutomationFlow?>(null) }
                val current = openFlow
                if (current == null) {
                    FlowListScreen(onOpenFlow = { openFlow = it })
                } else {
                    FlowEditorScreen(initialFlow = current, onBack = { openFlow = null })
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.VIBRATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        requestPermissions.launch(perms.toTypedArray())
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
        }
    }
}
