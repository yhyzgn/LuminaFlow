package com.lumina.flow.model

data class DebugLogEntry(
    val id: Long,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DebugSessionState(
    val visible: Boolean = false,
    val title: String = "",
    val running: Boolean = false,
    val logs: List<DebugLogEntry> = emptyList()
)
