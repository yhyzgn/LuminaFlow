package com.lumina.flow.scheduler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.automation.AutomationJsonCodec
import com.lumina.flow.automation.AutomationActionExecutor
import com.lumina.flow.automation.AutomationScheduler
import com.lumina.flow.data.AutomationDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: AutomationDao,
    private val scheduler: AutomationScheduler,
    private val executor: AutomationActionExecutor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getLong(AutomationScheduler.AUTOMATION_ID_KEY, -1L)
        val manual = inputData.getBoolean(AutomationScheduler.MANUAL_KEY, false)
        if (id <= 0) return@withContext Result.failure()

        val entity = dao.getById(id) ?: return@withContext Result.failure()
        if (!manual && !entity.enabled) return@withContext Result.success()

        val conditions = AutomationJsonCodec.decodeConditions(entity.conditionsJson)
        if (!conditionsSatisfied(conditions)) {
            val nextRunAt = AutomationPlanner.computeNextRun(entity)
            dao.upsert(
                entity.copy(
                    nextRunAt = nextRunAt,
                    lastResult = "条件未满足，已跳过",
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (!manual && nextRunAt != null) scheduler.schedule(entity.copy(nextRunAt = nextRunAt))
            return@withContext Result.success()
        }

        val actions = AutomationJsonCodec.decodeActions(entity.actionsJson)
        val resultLabel = executor.runTaskWindow(entity, actions)
        val now = System.currentTimeMillis()
        val nextRunAt: Long? = if (manual) entity.nextRunAt else AutomationPlanner.computeNextRun(entity, now)

        val updated = entity.copy(
            lastRunAt = now,
            nextRunAt = nextRunAt,
            lastResult = resultLabel,
            updatedAt = now
        )
        dao.upsert(updated)

        if (!manual) {
            if (updated.enabled && nextRunAt != null) scheduler.schedule(updated) else scheduler.cancel(updated.id)
        }
        Result.success()
    }

    private fun conditionsSatisfied(conditions: com.lumina.flow.model.AutomationConditions): Boolean {
        if (conditions.requireCharging && !isCharging()) return false
        if (conditions.wifiOnly && !isOnWifi()) return false
        if ((conditions.minimumBattery ?: 0) > currentBatteryLevel()) return false
        return true
    }

    private fun isCharging(): Boolean {
        val batteryManager = applicationContext.getSystemService<BatteryManager>() ?: return false
        return batteryManager.isCharging
    }

    private fun currentBatteryLevel(): Int {
        val batteryManager = applicationContext.getSystemService<BatteryManager>() ?: return 0
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isOnWifi(): Boolean {
        val connectivityManager = applicationContext.getSystemService<ConnectivityManager>() ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
