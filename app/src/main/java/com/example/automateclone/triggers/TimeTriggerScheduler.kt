package com.example.automateclone.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.automateclone.engine.FlowLog
import com.example.automateclone.engine.LogLevel
import com.example.automateclone.model.BlockType
import com.example.automateclone.model.FlowRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TimeTriggerScheduler {
    private const val REQUEST_CODE = 9001
    const val EXTRA_TARGET_HOUR = "targetHour"
    const val EXTRA_TARGET_MINUTE = "targetMinute"
    private val DAY_CODES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    fun rescheduleNextAlarm(context: Context) {
        val repo = FlowRepository(context)
        var earliest: Calendar? = null
        var triggerCount = 0

        val enabledFlows = repo.loadAll().filter { it.enabled }
        enabledFlows.forEach { flow ->
            flow.triggerBlocks().filter { it.type == BlockType.TIME_SCHEDULE }.forEach { trigger ->
                val hour = trigger.config["hour"]?.toIntOrNull()
                val minute = trigger.config["minute"]?.toIntOrNull()
                if (hour == null || minute == null) {
                    FlowLog.add(
                        "System",
                        "Scheduler: Time Schedule in '${flow.name}' has invalid hour/minute (hour=${trigger.config["hour"]}, minute=${trigger.config["minute"]}) — skipped",
                        LogLevel.ERROR
                    )
                    return@forEach
                }
                triggerCount++
                val repeatDays = trigger.config["repeatDays"].orEmpty()
                val next = nextOccurrence(hour, minute, repeatDays)
                FlowLog.add(
                    "System",
                    "Scheduler: '${flow.name}' Time Schedule $hour:${minute.toString().padStart(2, '0')} (days='$repeatDays') -> next occurrence ${formatCal(next)}"
                )
                if (earliest == null || next.before(earliest)) earliest = next
            }
        }

        FlowLog.add("System", "Scheduler: ${enabledFlows.size} enabled flow(s), $triggerCount Time Schedule trigger(s) found")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val target = earliest

        val intent = Intent(context, TimeAlarmReceiver::class.java).apply {
            if (target != null) {
                putExtra(EXTRA_TARGET_HOUR, target.get(Calendar.HOUR_OF_DAY))
                putExtra(EXTRA_TARGET_MINUTE, target.get(Calendar.MINUTE))
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (target == null) {
            FlowLog.add("System", "Scheduler: no Time Schedule triggers to arm — alarm cancelled")
            alarmManager.cancel(pendingIntent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            FlowLog.add(
                "System",
                "Scheduler: EXACT ALARM PERMISSION NOT GRANTED — alarm was NOT armed. Go to Settings > Apps > Flowmate > Alarms & reminders and enable it.",
                LogLevel.ERROR
            )
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        FlowLog.add("System", "Scheduler: alarm ARMED for ${formatCal(target)}")
    }

    private fun formatCal(cal: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEE", Locale.getDefault()).format(cal.time)

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
