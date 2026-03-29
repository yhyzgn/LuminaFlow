package com.lumina.flow.automation

import com.lumina.flow.model.ActionType
import com.lumina.flow.model.AutomationActionConfig
import com.lumina.flow.model.AutomationConditions
import org.json.JSONArray
import org.json.JSONObject

object AutomationJsonCodec {
    fun decodeActions(raw: String): List<AutomationActionConfig> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        AutomationActionConfig(
                            type = ActionType.fromValue(item.optString("type")),
                            title = item.optString("title"),
                            message = item.optString("message"),
                            target = item.optString("target"),
                            durationMs = item.optLong("durationMs", 600L),
                            rangeStart = item.optLong("rangeStart", 0L),
                            rangeEnd = item.optLong("rangeEnd", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeActions(actions: List<AutomationActionConfig>): String {
        val array = JSONArray()
        actions.forEach { action ->
            array.put(
                JSONObject()
                    .put("type", action.type.value)
                    .put("title", action.title)
                    .put("message", action.message)
                    .put("target", action.target)
                    .put("durationMs", action.durationMs)
                    .put("rangeStart", action.rangeStart)
                    .put("rangeEnd", action.rangeEnd)
            )
        }
        return array.toString()
    }

    fun decodeConditions(raw: String): AutomationConditions {
        if (raw.isBlank()) return AutomationConditions()
        return runCatching {
            val json = JSONObject(raw)
            AutomationConditions(
                requireCharging = json.optBoolean("requireCharging"),
                wifiOnly = json.optBoolean("wifiOnly"),
                minimumBattery = json.optInt("minimumBattery").takeIf { it > 0 },
                strictExactTime = json.optBoolean("strictExactTime")
            )
        }.getOrDefault(AutomationConditions())
    }

    fun encodeConditions(conditions: AutomationConditions): String =
        JSONObject()
            .put("requireCharging", conditions.requireCharging)
            .put("wifiOnly", conditions.wifiOnly)
            .put("minimumBattery", conditions.minimumBattery)
            .put("strictExactTime", conditions.strictExactTime)
            .toString()
}
