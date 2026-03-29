package com.lumina.flow.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lumina.flow.data.AutomationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(entity: AutomationEntity) {
        cancel(entity.id)
        val nextRunAt = entity.nextRunAt ?: return

        val workRequest = OneTimeWorkRequestBuilder<com.lumina.flow.scheduler.AutomationWorker>()
            .setInitialDelay((nextRunAt - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(baseData(entity.id, false))
            .addTag("automation-${entity.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            scheduledWorkName(entity.id),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun enqueueImmediate(id: Long) {
        val request = OneTimeWorkRequestBuilder<com.lumina.flow.scheduler.AutomationWorker>()
            .setInputData(baseData(id, true))
            .addTag("automation-manual-$id")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(id: Long) {
        if (id <= 0) return
        WorkManager.getInstance(context).cancelUniqueWork(scheduledWorkName(id))
    }

    private fun baseData(id: Long, manual: Boolean): Data =
        Data.Builder()
            .putLong(AUTOMATION_ID_KEY, id)
            .putBoolean(MANUAL_KEY, manual)
            .build()

    private fun scheduledWorkName(id: Long) = "automation-scheduled-$id"

    companion object {
        const val AUTOMATION_ID_KEY = "automation_id"
        const val MANUAL_KEY = "manual"
        const val ACTION_CHANNEL_ID = "lumina_flow_actions"
        const val STATUS_CHANNEL_ID = "lumina_flow_status"
    }
}
