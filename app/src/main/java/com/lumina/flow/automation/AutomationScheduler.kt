package com.lumina.flow.automation

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lumina.flow.MainActivity
import com.lumina.flow.automation.AutomationJsonCodec.decodeConditions
import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.model.TriggerType
import com.lumina.flow.scheduler.AutomationAlarmReceiver
import com.lumina.flow.scheduler.AutomationExecutionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "LuminaFlowSched"
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(entity: AutomationEntity) {
        cancel(entity.id)
        val nextRunAt = entity.nextRunAt ?: return
        val triggerType = TriggerType.fromValue(entity.triggerType)
        if (triggerType == TriggerType.LOCATION) return

        val pendingIntent = scheduledPendingIntent(entity.id)
        val manager = alarmManager ?: return
        val conditions = decodeConditions(entity.conditionsJson)
        if (triggerType == TriggerType.TIME && conditions.strictExactTime) {
            Log.d(tag, "schedule strict alarmClock id=${entity.id} nextRunAt=$nextRunAt")
            manager.setAlarmClock(
                AlarmClockInfo(nextRunAt, launcherPendingIntent()),
                pendingIntent
            )
            return
        }
        Log.d(tag, "schedule normal id=${entity.id} triggerType=$triggerType nextRunAt=$nextRunAt strict=${conditions.strictExactTime}")
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canScheduleExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunAt, pendingIntent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunAt, pendingIntent)
        }
    }

    fun enqueueImmediate(id: Long) {
        AutomationExecutionService.start(context, id, manual = true)
    }

    fun cancel(id: Long) {
        if (id <= 0) return
        alarmManager?.cancel(scheduledPendingIntent(id))
        WorkManager.getInstance(context).cancelUniqueWork(scheduledWorkName(id))
    }

    private fun baseData(id: Long, manual: Boolean): Data =
        Data.Builder()
            .putLong(AUTOMATION_ID_KEY, id)
            .putBoolean(MANUAL_KEY, manual)
            .build()

    private fun scheduledWorkName(id: Long) = "automation-scheduled-$id"

    private fun scheduledPendingIntent(id: Long): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            action = ACTION_RUN_AUTOMATION
            putExtra(AUTOMATION_ID_KEY, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launcherPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val AUTOMATION_ID_KEY = "automation_id"
        const val MANUAL_KEY = "manual"
        const val ACTION_CHANNEL_ID = "lumina_flow_actions"
        const val STATUS_CHANNEL_ID = "lumina_flow_status"
        const val ACTION_RUN_AUTOMATION = "com.lumina.flow.action.RUN_AUTOMATION"
    }
}
