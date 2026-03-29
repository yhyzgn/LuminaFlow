package com.lumina.flow.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lumina.flow.R
import com.lumina.flow.automation.AutomationActionExecutor
import com.lumina.flow.automation.AutomationJsonCodec
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DebugSessionService : Service() {
    private val tag = "LuminaFlowDebug"

    @Inject
    lateinit var executor: AutomationActionExecutor

    @Inject
    lateinit var repository: DebugSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debugJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> stopDebug("调试已停止")
            ACTION_START -> startDebug(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        debugJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDebug(intent: Intent) {
        val payload = DebugPayload.fromIntent(intent) ?: run {
            stopSelf()
            return
        }

        debugJob?.cancel()
        repository.start(payload.title)
        Log.d(tag, "startDebug title=${payload.title}")
        startForegroundCompat(buildNotification(payload.title, "正在准备调试"))

        debugJob = serviceScope.launch {
            runCatching {
                val actions = AutomationJsonCodec.decodeActions(payload.actionsJson)
                val conditions = AutomationJsonCodec.decodeConditions(payload.conditionsJson)
                append(payload.title, "触发器: ${payload.triggerType}")
                append(payload.title, "条件: ${describeConditions(conditions)}")
                if (payload.repeatUntilWindowEnd) {
                    append(
                        payload.title,
                        "调试模式忽略定时窗口 %02d:%02d -> %02d:%02d，仅演示一轮动作序列".format(
                            payload.hour ?: 0,
                            payload.minute ?: 0,
                            payload.windowEndHour ?: 0,
                            payload.windowEndMinute ?: 0
                        )
                    )
                }
                val result = executor.runActions(actions) { message ->
                    append(payload.title, message)
                }
                repository.finish("调试完成: $result")
                updateNotification(payload.title, "调试完成")
            }.onFailure { error ->
                repository.finish("调试失败: ${error.message ?: "未知错误"}")
                updateNotification(payload.title, "调试失败")
            }
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopDebug(message: String) {
        debugJob?.cancel()
        debugJob = null
        repository.finish(message)
        updateNotification(repository.state.value.title.ifBlank { "调试控制台" }, "调试已停止")
        stopForegroundCompat()
        stopSelf()
    }

    private fun append(title: String, message: String) {
        repository.append(message)
        updateNotification(title, message)
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun buildNotification(title: String, content: String): Notification =
        NotificationCompat.Builder(this, DEBUG_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (title.isBlank()) "LuminaFlow 调试中" else "调试中: $title")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            "调试执行",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
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
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    private fun describeConditions(conditions: com.lumina.flow.model.AutomationConditions): String {
        val items = buildList {
            if (conditions.requireCharging) add("充电中")
            if (conditions.wifiOnly) add("仅 Wi-Fi")
            conditions.minimumBattery?.let { add("电量 >= $it%") }
        }
        return if (items.isEmpty()) "无附加条件" else items.joinToString(" / ")
    }

    companion object {
        const val DEBUG_CHANNEL_ID = "lumina_flow_debug"
        private const val NOTIFICATION_ID = 3013
        private const val ACTION_START = "com.lumina.flow.debug.START"
        private const val ACTION_STOP = "com.lumina.flow.debug.STOP"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TRIGGER_TYPE = "trigger_type"
        private const val EXTRA_ACTIONS_JSON = "actions_json"
        private const val EXTRA_CONDITIONS_JSON = "conditions_json"
        private const val EXTRA_REPEAT_UNTIL_WINDOW_END = "repeat_until_window_end"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_WINDOW_END_HOUR = "window_end_hour"
        private const val EXTRA_WINDOW_END_MINUTE = "window_end_minute"

        fun start(context: Context, entity: com.lumina.flow.data.AutomationEntity) {
            val intent = Intent(context, DebugSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, entity.name.ifBlank { "未命名任务" })
                putExtra(EXTRA_TRIGGER_TYPE, entity.triggerType)
                putExtra(EXTRA_ACTIONS_JSON, entity.actionsJson)
                putExtra(EXTRA_CONDITIONS_JSON, entity.conditionsJson)
                putExtra(EXTRA_REPEAT_UNTIL_WINDOW_END, entity.repeatUntilWindowEnd)
                putExtra(EXTRA_HOUR, entity.hour)
                putExtra(EXTRA_MINUTE, entity.minute)
                putExtra(EXTRA_WINDOW_END_HOUR, entity.windowEndHour)
                putExtra(EXTRA_WINDOW_END_MINUTE, entity.windowEndMinute)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DebugSessionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private data class DebugPayload(
        val title: String,
        val triggerType: String,
        val actionsJson: String,
        val conditionsJson: String,
        val repeatUntilWindowEnd: Boolean,
        val hour: Int?,
        val minute: Int?,
        val windowEndHour: Int?,
        val windowEndMinute: Int?
    ) {
        companion object {
            fun fromIntent(intent: Intent): DebugPayload? {
                val actionsJson = intent.getStringExtra(EXTRA_ACTIONS_JSON) ?: return null
                val conditionsJson = intent.getStringExtra(EXTRA_CONDITIONS_JSON) ?: ""
                return DebugPayload(
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE).orEmpty(),
                    actionsJson = actionsJson,
                    conditionsJson = conditionsJson,
                    repeatUntilWindowEnd = intent.getBooleanExtra(EXTRA_REPEAT_UNTIL_WINDOW_END, false),
                    hour = intent.takeIf { it.hasExtra(EXTRA_HOUR) }?.getIntExtra(EXTRA_HOUR, 0),
                    minute = intent.takeIf { it.hasExtra(EXTRA_MINUTE) }?.getIntExtra(EXTRA_MINUTE, 0),
                    windowEndHour = intent.takeIf { it.hasExtra(EXTRA_WINDOW_END_HOUR) }?.getIntExtra(EXTRA_WINDOW_END_HOUR, 0),
                    windowEndMinute = intent.takeIf { it.hasExtra(EXTRA_WINDOW_END_MINUTE) }?.getIntExtra(EXTRA_WINDOW_END_MINUTE, 0)
                )
            }
        }
    }
}
