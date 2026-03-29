package com.lumina.flow.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.lumina.flow.accessibility.AccessibilityAutomationBridge
import com.lumina.flow.R
import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.model.ActionType
import com.lumina.flow.model.AutomationActionConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun runTaskWindow(
        entity: AutomationEntity,
        actions: List<AutomationActionConfig>,
        logger: (suspend (String) -> Unit)? = null
    ): String {
        if (!entity.repeatUntilWindowEnd) return runActions(actions, logger)

        val endTime = AutomationPlanner.computeWindowEnd(entity, System.currentTimeMillis())
            ?: return "循环窗口无效"

        logger?.invoke("进入循环窗口，结束时间 ${formatClock(entity.windowEndHour, entity.windowEndMinute)}")
        val statuses = mutableListOf<String>()
        var round = 1
        while (System.currentTimeMillis() < endTime) {
            logger?.invoke("开始第 $round 轮动作")
            statuses += runActions(actions, logger)
            round += 1
        }
        logger?.invoke("达到循环截止时间，调试结束")
        return statuses.lastOrNull() ?: "窗口内无动作执行"
    }

    suspend fun runActions(
        actions: List<AutomationActionConfig>,
        logger: (suspend (String) -> Unit)? = null
    ): String {
        if (actions.isEmpty()) return "无动作可执行"

        val statuses = mutableListOf<String>()
        actions.forEachIndexed { index, action ->
            logger?.invoke("动作 ${index + 1}/${actions.size}: ${action.type.label}")
            val status = runCatching { executeAction(action, logger) }
                .fold(
                    onSuccess = { "已执行${action.type.label}" },
                    onFailure = { error ->
                        logger?.invoke("动作失败: ${error.message ?: action.type.label}")
                        "失败:${action.type.label}"
                    }
                )
            statuses += status
        }
        return statuses.joinToString("，")
    }

    private suspend fun executeAction(
        action: AutomationActionConfig,
        logger: (suspend (String) -> Unit)? = null
    ) {
        when (action.type) {
            ActionType.NOTIFICATION -> {
                ensureChannels()
                showActionNotification(action)
                logger?.invoke("已发送通知: ${action.title.ifBlank { "LuminaFlow 自动化" }}")
            }
            ActionType.OPEN_URL -> {
                openUri(action.target)
                logger?.invoke("已打开链接: ${action.target}")
            }
            ActionType.OPEN_APP -> {
                openApp(action.target)
                logger?.invoke("已尝试启动应用: ${action.target}")
            }
            ActionType.GO_HOME -> {
                val success = if (AccessibilityAutomationBridge.isEnabled(context)) {
                    logger?.invoke("无障碍 HOME 已启用，执行全局返回桌面")
                    AccessibilityAutomationBridge.goHome()
                } else {
                    logger?.invoke("无障碍未启用，回退到普通版回桌面")
                    goHome()
                    true
                }
                logger?.invoke(if (success) "已回到桌面" else "回到桌面失败")
            }
            ActionType.CLOSE_APP -> {
                val success = if (AccessibilityAutomationBridge.isEnabled(context)) {
                    logger?.invoke("无障碍已启用，尝试强行停止应用: ${action.target}")
                    AccessibilityAutomationBridge.forceStopPackage(action.target)
                } else {
                    logger?.invoke("无障碍未启用，回退到普通版关闭后台进程")
                    closeApp(action.target)
                    true
                }
                logger?.invoke(if (success) "关闭应用动作执行完成" else "关闭应用失败，请检查无障碍权限")
            }
            ActionType.RANDOM_DELAY -> {
                randomDelay(action, logger)
            }
            ActionType.CLIPBOARD -> {
                val text = action.message.ifBlank { action.target }
                copyToClipboard(text)
                logger?.invoke("已复制文本到剪贴板")
            }
            ActionType.VIBRATE -> {
                vibrate(action.durationMs)
                logger?.invoke("已振动 ${action.durationMs} ms")
            }
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
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
        val manager = context.getSystemService<NotificationManager>() ?: return
        val title = action.title.ifBlank { "LuminaFlow 自动化" }
        val message = action.message.ifBlank { "任务已触发" }
        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(context, AutomationScheduler.ACTION_CHANNEL_ID)
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
        context.startActivity(intent)
    }

    private fun openApp(packageName: String) {
        if (packageName.isBlank()) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        context.startActivity(launchIntent)
    }

    private fun goHome() {
        runCatching {
            context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        }

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val launcherIntent = resolveHomeIntent(intent) ?: intent
        context.startActivity(launcherIntent)
    }

    private fun closeApp(packageName: String) {
        if (packageName.isBlank()) return
        val activityManager = context.getSystemService<ActivityManager>() ?: return
        activityManager.killBackgroundProcesses(packageName)
    }

    private fun resolveHomeIntent(fallback: Intent): Intent? {
        val packageManager = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                fallback,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(fallback, PackageManager.MATCH_DEFAULT_ONLY)
        } ?: return null

        val activityInfo = resolved.activityInfo ?: return null
        if (activityInfo.packageName == "android") return fallback

        return Intent(fallback).apply {
            setClassName(activityInfo.packageName, activityInfo.name)
        }
    }

    private suspend fun randomDelay(
        action: AutomationActionConfig,
        logger: (suspend (String) -> Unit)? = null
    ) {
        val start = action.rangeStart.coerceAtLeast(0L)
        val end = action.rangeEnd.coerceAtLeast(start)
        val seconds = if (end <= start) start else Random.nextLong(start, end + 1)
        logger?.invoke("随机延迟命中 ${seconds} 秒")
        for (remaining in seconds downTo 1) {
            logger?.invoke("延迟倒计时: ${remaining} 秒")
            delay(1000L)
        }
        if (seconds == 0L) {
            logger?.invoke("随机延迟为 0 秒，直接继续")
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("LuminaFlow", text))
    }

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService<VibratorManager>() ?: return
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(durationMs.coerceAtLeast(100L), VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()?.vibrate(durationMs.coerceAtLeast(100L))
        }
    }

    private fun formatClock(hour: Int?, minute: Int?): String =
        "%02d:%02d".format(hour ?: 0, minute ?: 0)
}
