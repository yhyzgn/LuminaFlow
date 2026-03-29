package com.lumina.flow.scheduler

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.lumina.flow.R
import com.lumina.flow.automation.AutomationActionExecutor
import com.lumina.flow.automation.AutomationJsonCodec
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.automation.AutomationScheduler
import com.lumina.flow.data.AutomationDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AutomationExecutionService : Service() {
    private val tag = "LuminaFlowExec"

    @Inject
    lateinit var dao: AutomationDao

    @Inject
    lateinit var scheduler: AutomationScheduler

    @Inject
    lateinit var executor: AutomationActionExecutor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runningJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_AUTOMATION_ID, -1L) ?: -1L
        val manual = intent?.getBooleanExtra(EXTRA_MANUAL, false) ?: false
        Log.d(tag, "onStartCommand id=$id manual=$manual")
        if (id <= 0L) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForegroundCompat(buildNotification("准备执行任务"))
        runningJob?.cancel()
        runningJob = serviceScope.launch {
            runCatching {
                runAutomation(id, manual)
            }.onFailure { error ->
                Log.e(tag, "runAutomation failed", error)
                updateNotification("执行失败: ${error.message ?: "未知错误"}")
            }
            stopForegroundCompat()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runningJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runAutomation(id: Long, manual: Boolean) = withContext(Dispatchers.IO) {
        Log.d(tag, "runAutomation start id=$id manual=$manual")
        val entity = dao.getById(id) ?: return@withContext
        Log.d(tag, "entity loaded id=${entity.id} name=${entity.name}")
        if (!manual && !entity.enabled) return@withContext

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
            Log.d(tag, "conditions not met id=$id")
            updateNotification("条件未满足，已跳过")
            return@withContext
        }

        updateNotification("正在执行: ${entity.name.ifBlank { "未命名任务" }}")
        val actions = AutomationJsonCodec.decodeActions(entity.actionsJson)
        val resultLabel = executor.runTaskWindow(entity, actions)
        val now = System.currentTimeMillis()
        val nextRunAt = if (manual) entity.nextRunAt else AutomationPlanner.computeNextRun(entity, now)
        val updated = entity.copy(
            lastRunAt = now,
            nextRunAt = nextRunAt,
            lastResult = resultLabel,
            updatedAt = now
        )
        dao.upsert(updated)
        Log.d(tag, "automation finished id=$id result=$resultLabel nextRunAt=$nextRunAt")
        if (!manual) {
            if (updated.enabled && nextRunAt != null) scheduler.schedule(updated) else scheduler.cancel(updated.id)
        }
        updateNotification(resultLabel)
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

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification =
        NotificationCompat.Builder(this, AutomationScheduler.STATUS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LuminaFlow 正在执行")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 3014
        private const val EXTRA_AUTOMATION_ID = "automation_id"
        private const val EXTRA_MANUAL = "manual"

        fun start(context: Context, id: Long, manual: Boolean) {
            Log.d("LuminaFlowExec", "request start id=$id manual=$manual")
            val intent = Intent(context, AutomationExecutionService::class.java).apply {
                putExtra(EXTRA_AUTOMATION_ID, id)
                putExtra(EXTRA_MANUAL, manual)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
