package com.lumina.flow.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.BatteryManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumina.flow.R
import com.lumina.flow.automation.AutomationJsonCodec
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.automation.AutomationScheduler
import com.lumina.flow.data.AutomationDao
import com.lumina.flow.model.ActionType
import com.lumina.flow.model.AutomationActionConfig
import kotlinx.coroutines.delay
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: AutomationDao,
    private val scheduler: AutomationScheduler
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

        ensureChannels()
        val actions = AutomationJsonCodec.decodeActions(entity.actionsJson)
        val resultLabel = runTaskWindow(entity, actions)
        val now = System.currentTimeMillis()
        val nextRunAt = if (manual) entity.nextRunAt else AutomationPlanner.computeNextRun(entity, now)

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

    private suspend fun runTaskWindow(
        entity: com.lumina.flow.data.AutomationEntity,
        actions: List<AutomationActionConfig>
    ): String {
        if (!entity.repeatUntilWindowEnd) return runActions(actions)

        val endTime = AutomationPlanner.computeWindowEnd(entity, System.currentTimeMillis())
            ?: return "循环窗口无效"
        val statuses = mutableListOf<String>()
        while (System.currentTimeMillis() < endTime) {
            statuses += runActions(actions)
        }
        return statuses.lastOrNull() ?: "窗口内无动作执行"
    }

    private suspend fun runActions(actions: List<AutomationActionConfig>): String {
        if (actions.isEmpty()) return "无动作可执行"

        val statuses = actions.map { action ->
            runCatching { executeAction(action) }
                .fold(
                    onSuccess = { "已执行${action.type.label}" },
                    onFailure = { "失败:${action.type.label}" }
                )
        }
        return statuses.joinToString("，")
    }

    private suspend fun executeAction(action: AutomationActionConfig) {
        when (action.type) {
            ActionType.NOTIFICATION -> showActionNotification(action)
            ActionType.OPEN_URL -> openUri(action.target)
            ActionType.OPEN_APP -> openApp(action.target)
            ActionType.CLOSE_APP -> goHome()
            ActionType.RANDOM_DELAY -> randomDelay(action)
            ActionType.CLIPBOARD -> copyToClipboard(action.message.ifBlank { action.target })
            ActionType.VIBRATE -> vibrate(action.durationMs)
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService<NotificationManager>() ?: return
        val channels = listOf(
            NotificationChannel(
                AutomationScheduler.ACTION_CHANNEL_ID,
                "自动化动作",
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                AutomationScheduler.STATUS_CHANNEL_ID,
                "自动化状态",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannels(channels)
    }

    private fun showActionNotification(action: AutomationActionConfig) {
        val manager = applicationContext.getSystemService<NotificationManager>() ?: return
        val title = action.title.ifBlank { "LuminaFlow 自动化" }
        val message = action.message.ifBlank { "任务已触发" }
        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(applicationContext, AutomationScheduler.ACTION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
        )
    }

    private fun openUri(target: String) {
        if (target.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    private fun openApp(packageName: String) {
        if (packageName.isBlank()) return
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        applicationContext.startActivity(launchIntent)
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    private suspend fun randomDelay(action: AutomationActionConfig) {
        val start = action.rangeStart.coerceAtLeast(0L)
        val end = action.rangeEnd.coerceAtLeast(start)
        val seconds = if (end <= start) start else Random.nextLong(start, end + 1)
        delay(seconds * 1000L)
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = applicationContext.getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("LuminaFlow", text))
    }

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = applicationContext.getSystemService<VibratorManager>() ?: return
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(durationMs.coerceAtLeast(100L), VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService<Vibrator>()?.vibrate(durationMs.coerceAtLeast(100L))
        }
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
