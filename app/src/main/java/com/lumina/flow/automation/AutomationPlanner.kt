package com.lumina.flow.automation

import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.model.TriggerType
import java.util.Calendar
import kotlin.math.max

object AutomationPlanner {
    fun computeNextRun(entity: AutomationEntity, now: Long = System.currentTimeMillis()): Long? {
        if (!entity.enabled) return null

        return when (TriggerType.fromValue(entity.triggerType)) {
            TriggerType.TIME -> computeNextTimeRun(
                hour = entity.hour,
                minute = entity.minute,
                days = decodeDays(entity.daysOfWeek),
                now = now
            )

            TriggerType.INTERVAL -> entity.intervalMinutes
                ?.takeIf { it > 0 }
                ?.let { now + max(it, 1) * 60_000L }

            TriggerType.LOCATION -> null
        }
    }

    fun decodeDays(raw: String): Set<Int> =
        raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    fun encodeDays(days: Set<Int>): String =
        days.sorted().joinToString(",")

    fun computeWindowEnd(entity: AutomationEntity, referenceTime: Long): Long? {
        if (!entity.repeatUntilWindowEnd) return null
        val endHour = entity.windowEndHour ?: return null
        val endMinute = entity.windowEndMinute ?: return null
        val calendar = Calendar.getInstance().apply {
            timeInMillis = referenceTime
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
        }
        return calendar.timeInMillis.takeIf { it > referenceTime }
    }

    private fun computeNextTimeRun(
        hour: Int?,
        minute: Int?,
        days: Set<Int>,
        now: Long
    ): Long? {
        if (hour == null || minute == null) return null

        val selectedDays = if (days.isEmpty()) (1..7).toSet() else days
        val threshold = now + 30_000L

        repeat(8) { offset ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in selectedDays && calendar.timeInMillis >= threshold) {
                return calendar.timeInMillis
            }
        }
        return null
    }
}
