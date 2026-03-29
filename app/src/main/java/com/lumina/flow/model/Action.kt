package com.lumina.flow.model

sealed class Action(val type: String) {
    data class Notification(val message: String) : Action("NOTIFICATION")
    data class Vibrate(val durationMs: Long = 500) : Action("VIBRATE")
    data class OpenApp(val packageName: String) : Action("OPEN_APP")
}