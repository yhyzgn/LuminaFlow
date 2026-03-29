package com.lumina.flow.model

enum class TriggerType(val value: String) {
    TIME("TIME"),
    INTERVAL("INTERVAL"),
    LOCATION("LOCATION");

    companion object {
        fun fromValue(value: String): TriggerType =
            entries.firstOrNull { it.value == value } ?: TIME
    }
}

enum class ActionType(val value: String, val label: String) {
    NOTIFICATION("NOTIFICATION", "通知"),
    OPEN_URL("OPEN_URL", "打开链接"),
    OPEN_APP("OPEN_APP", "启动应用"),
    GO_HOME("GO_HOME", "回到桌面"),
    CLOSE_APP("CLOSE_APP", "关闭应用"),
    RANDOM_DELAY("RANDOM_DELAY", "随机延迟"),
    CLIPBOARD("CLIPBOARD", "复制文本"),
    VIBRATE("VIBRATE", "振动");

    companion object {
        fun fromValue(value: String): ActionType =
            entries.firstOrNull { it.value == value } ?: NOTIFICATION
    }
}

data class AutomationActionConfig(
    val type: ActionType,
    val title: String = "",
    val message: String = "",
    val target: String = "",
    val durationMs: Long = 600L,
    val rangeStart: Long = 0L,
    val rangeEnd: Long = 0L
)

data class AutomationConditions(
    val requireCharging: Boolean = false,
    val wifiOnly: Boolean = false,
    val minimumBattery: Int? = null,
    val strictExactTime: Boolean = false
)
