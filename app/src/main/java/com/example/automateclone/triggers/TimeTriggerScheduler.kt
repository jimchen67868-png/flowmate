package com.example.automateclone.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.automateclone.model.BlockType
import com.example.automateclone.model.FlowRepository
import java.util.Calendar

object TimeTriggerScheduler {
    private const val REQUEST_CODE = 9001
    private val DAY_CODES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    fun rescheduleNextAlarm(context: Context) {
        val repo = FlowRepository(context)
        var earliest: Calendar? = null

        repo.loadAll().filter { it.enabled }.forEach { flow ->
            flow.triggerBlocks().filter { it.type == BlockType.TIME_SCHEDULE }.forEach { trigger ->
                val hour = trigger.config["hour"]?.toIntOrNull() ?: return@forEach
                val minute = trigger.config["minute"]?.toIntOrNull() ?: return@forEach
                val repeatDays = trigger.config["repeatDays"].orEmpty()
                val next = nextOccurrence(hour, minute, repeatDays)
                if (earliest == null || next.before(earliest)) earliest = next
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimeAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val target = earliest
        if (target == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
    }

    private fun nextOccurrence(hour: Int, minute: Int, repeatDays: String): Calendar {
        val now = Calendar.getInstance()
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (offset in 0..7) {
            val candidate = base.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            val dayCode = DAY_CODES[candidate.get(Calendar.DAY_OF_WEEK) - 1]
            val dayMatches = repeatDays.isBlank() || repeatDays.contains(dayCode)
            if (dayMatches && candidate.after(now)) return candidate
        }
        return base.apply { add(Calendar.DAY_OF_YEAR, 1) }
    }
}
