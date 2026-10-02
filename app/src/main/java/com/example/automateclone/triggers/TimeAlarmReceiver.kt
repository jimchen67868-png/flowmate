package com.example.automateclone.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.automateclone.engine.FlowEngine
import com.example.automateclone.model.BlockType
import com.example.automateclone.model.FlowRepository
import java.util.Calendar

class TimeAlarmReceiver : BroadcastReceiver() {

    private val dayCodes = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    override fun onReceive(context: Context, intent: Intent) {
        val repo = FlowRepository(context)
        val engine = FlowEngine(context)
        val now = Calendar.getInstance()

        val hour = intent.getIntExtra(TimeTriggerScheduler.EXTRA_TARGET_HOUR, now.get(Calendar.HOUR_OF_DAY))
        val minute = intent.getIntExtra(TimeTriggerScheduler.EXTRA_TARGET_MINUTE, now.get(Calendar.MINUTE))
        val dayCode = dayCodes[now.get(Calendar.DAY_OF_WEEK) - 1]

        repo.loadAll().filter { it.enabled }.forEach { flow ->
            flow.triggerBlocks()
                .filter { it.type == BlockType.TIME_SCHEDULE }
                .filter { trigger ->
                    val h = trigger.config["hour"]?.toIntOrNull() ?: return@filter false
                    val m = trigger.config["minute"]?.toIntOrNull() ?: return@filter false
                    val days = trigger.config["repeatDays"].orEmpty()
                    h == hour && m == minute && (days.isBlank() || days.contains(dayCode))
                }
                .forEach { trigger -> engine.runFrom(flow, trigger) }
        }

        TimeTriggerScheduler.rescheduleNextAlarm(context)
    }
}
