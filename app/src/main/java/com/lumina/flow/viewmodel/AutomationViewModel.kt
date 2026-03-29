package com.lumina.flow.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.flow.automation.AutomationJsonCodec
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.automation.AutomationScheduler
import com.lumina.flow.data.AutomationDao
import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.debug.DebugSessionRepository
import com.lumina.flow.debug.DebugSessionService
import com.lumina.flow.model.AutomationConditions
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.yaml.snakeyaml.Yaml
import java.io.StringWriter
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val dao: AutomationDao,
    private val scheduler: AutomationScheduler,
    private val debugRepository: DebugSessionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val automations =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val debugState = debugRepository.state

    fun save(entity: AutomationEntity, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val prepared = entity.copy(
                updatedAt = now,
                nextRunAt = AutomationPlanner.computeNextRun(entity, now)
            )
            val id = dao.upsert(prepared)
            val saved = prepared.copy(id = if (prepared.id == 0L) id else prepared.id)
            if (saved.enabled && saved.nextRunAt != null) {
                scheduler.schedule(saved)
                onComplete("任务已保存并加入调度")
            } else {
                scheduler.cancel(saved.id)
                onComplete(
                    when (saved.triggerType) {
                        "LOCATION" -> "任务已保存，地理围栏待后续系统接入"
                        else -> "任务已保存，但当前配置还不会自动触发"
                    }
                )
            }
        }
    }

    fun toggleEnabled(entity: AutomationEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = entity.copy(
                enabled = enabled,
                updatedAt = System.currentTimeMillis(),
                nextRunAt = if (enabled) {
                    AutomationPlanner.computeNextRun(entity.copy(enabled = true))
                } else {
                    null
                },
                lastResult = if (enabled) entity.lastResult else "已暂停"
            )
            dao.upsert(updated)
            if (enabled && updated.nextRunAt != null) scheduler.schedule(updated) else scheduler.cancel(updated.id)
        }
    }

    fun runNow(entity: AutomationEntity, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (entity.id <= 0) {
                onComplete("请先保存任务")
                return@launch
            }
            scheduler.enqueueImmediate(entity.id)
            onComplete("已加入立即执行队列")
        }
    }

    fun startDebug(entity: AutomationEntity) {
        DebugSessionService.start(appContext, entity)
    }

    fun stopDebug() {
        DebugSessionService.stop(appContext)
    }

    fun dismissDebug() {
        if (debugState.value.running) {
            DebugSessionService.stop(appContext)
        }
        debugRepository.clear()
    }

    fun delete(entity: AutomationEntity) {
        viewModelScope.launch {
            scheduler.cancel(entity.id)
            dao.delete(entity)
        }
    }

    fun importFromUrl(url: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val yamlContent = response.body?.string().orEmpty()
                    require(yamlContent.isNotBlank()) { "下载内容为空" }

                    val yaml = Yaml()
                    val list = yaml.load<List<Map<String, Any?>>>(yamlContent).orEmpty()
                    var imported = 0

                    list.forEach { map ->
                        val actions = map["actions"] as? List<Map<String, Any?>>
                        val actionsJson = if (actions.isNullOrEmpty()) {
                            AutomationJsonCodec.encodeActions(defaultActions())
                        } else {
                            AutomationJsonCodec.encodeActions(
                                actions.map { action ->
                                    com.lumina.flow.model.AutomationActionConfig(
                                        type = com.lumina.flow.model.ActionType.fromValue(
                                            action["type"] as? String ?: "NOTIFICATION"
                                        ),
                                        title = action["title"] as? String ?: "",
                                        message = action["message"] as? String ?: "",
                                        target = action["target"] as? String ?: "",
                                        durationMs = (action["durationMs"] as? Number)?.toLong() ?: 600L,
                                        rangeStart = (action["rangeStart"] as? Number)?.toLong() ?: 0L,
                                        rangeEnd = (action["rangeEnd"] as? Number)?.toLong() ?: 0L
                                    )
                                }
                            )
                        }

                        val conditionsMap = map["conditions"] as? Map<String, Any?>
                        val conditions = AutomationConditions(
                            requireCharging = conditionsMap?.get("requireCharging") as? Boolean ?: false,
                            wifiOnly = conditionsMap?.get("wifiOnly") as? Boolean ?: false,
                            minimumBattery = (conditionsMap?.get("minimumBattery") as? Number)?.toInt()
                        )

                        val entity = AutomationEntity(
                            name = map["name"] as? String ?: "导入任务",
                            description = map["description"] as? String ?: "",
                            triggerType = map["triggerType"] as? String ?: "TIME",
                            hour = (map["hour"] as? Number)?.toInt(),
                            minute = (map["minute"] as? Number)?.toInt(),
                            daysOfWeek = map["daysOfWeek"] as? String ?: "",
                            repeatUntilWindowEnd = map["repeatUntilWindowEnd"] as? Boolean ?: false,
                            windowEndHour = (map["windowEndHour"] as? Number)?.toInt(),
                            windowEndMinute = (map["windowEndMinute"] as? Number)?.toInt(),
                            intervalMinutes = (map["intervalMinutes"] as? Number)?.toInt(),
                            latitude = (map["latitude"] as? Number)?.toDouble(),
                            longitude = (map["longitude"] as? Number)?.toDouble(),
                            radius = (map["radius"] as? Number)?.toFloat() ?: 100f,
                            enabled = map["enabled"] as? Boolean ?: true,
                            actionsJson = actionsJson,
                            conditionsJson = AutomationJsonCodec.encodeConditions(conditions)
                        )
                        save(entity)
                        imported += 1
                    }
                    imported
                }
            }.onSuccess { imported ->
                onComplete(if (imported > 0) "成功导入 $imported 个任务" else "未发现可导入任务")
            }.onFailure { error ->
                onComplete(error.message ?: "导入失败")
            }
        }
    }

    fun exportToYaml(onComplete: (String, String?) -> Unit) {
        viewModelScope.launch {
            val list = automations.value
            if (list.isEmpty()) {
                onComplete("当前没有可导出的任务", null)
                return@launch
            }

            runCatching {
                val yaml = Yaml()
                val exportList = list.map { entity ->
                    mapOf(
                        "name" to entity.name,
                        "description" to entity.description,
                        "triggerType" to entity.triggerType,
                                "hour" to entity.hour,
                                "minute" to entity.minute,
                                "daysOfWeek" to entity.daysOfWeek,
                                "repeatUntilWindowEnd" to entity.repeatUntilWindowEnd,
                                "windowEndHour" to entity.windowEndHour,
                                "windowEndMinute" to entity.windowEndMinute,
                                "intervalMinutes" to entity.intervalMinutes,
                                "latitude" to entity.latitude,
                                "longitude" to entity.longitude,
                        "radius" to entity.radius,
                        "enabled" to entity.enabled,
                        "actions" to AutomationJsonCodec.decodeActions(entity.actionsJson).map { action ->
                            mapOf(
                                "type" to action.type.value,
                                "title" to action.title,
                                "message" to action.message,
                                "target" to action.target,
                                "durationMs" to action.durationMs,
                                "rangeStart" to action.rangeStart,
                                "rangeEnd" to action.rangeEnd
                            )
                        },
                        "conditions" to AutomationJsonCodec.decodeConditions(entity.conditionsJson).let { conditions ->
                            mapOf(
                                "requireCharging" to conditions.requireCharging,
                                "wifiOnly" to conditions.wifiOnly,
                                "minimumBattery" to conditions.minimumBattery
                            )
                        }
                    )
                }
                StringWriter().use { writer ->
                    yaml.dump(exportList, writer)
                    writer.toString()
                }
            }.onSuccess { yaml ->
                onComplete("导出内容已生成，可直接复制保存", yaml)
            }.onFailure { error ->
                onComplete(error.message ?: "导出失败", null)
            }
        }
    }

    private fun defaultActions() = listOf(
        com.lumina.flow.model.AutomationActionConfig(
            type = com.lumina.flow.model.ActionType.NOTIFICATION,
            title = "LuminaFlow",
            message = "任务已触发"
        )
    )

}
