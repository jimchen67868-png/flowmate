package com.example.automateclone.triggers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.automateclone.engine.FlowEngine
import com.example.automateclone.model.BlockType
import com.example.automateclone.model.FlowRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules a recurring WorkManager job that checks every 15 minutes
 * (WorkManager's minimum periodic interval) whether any TIME_SCHEDULE
 * trigger block matches the current hour/minute, and fires it if so.
 *
 * For minute-precision alarms, swap this for AlarmManager.setExactAndAllowWhileIdle
 * per-flow; this polling approach is simpler and battery-friendlier as a default.
 */
object TimeTriggerScheduler {
    private const val WORK_NAME = "flowmate_time_trigger_check"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<TimeCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class TimeCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val dayCode = "SUN,MON,TUE,WED,THU,FRI,SAT".split(",")[now.get(Calendar.DAY_OF_WEEK) - 1]

        val repo = FlowRepository(applicationContext)
        val engine = FlowEngine(applicationContext)

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
        return Result.success()
    }
}
