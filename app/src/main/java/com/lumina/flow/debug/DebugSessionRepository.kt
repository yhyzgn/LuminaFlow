package com.lumina.flow.debug

import com.lumina.flow.model.DebugLogEntry
import com.lumina.flow.model.DebugSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSessionRepository @Inject constructor() {
    private val _state = MutableStateFlow(DebugSessionState())
    val state: StateFlow<DebugSessionState> = _state.asStateFlow()

    fun start(title: String) {
        _state.value = DebugSessionState(
            visible = true,
            title = title,
            running = true,
            logs = listOf(log("调试开始"))
        )
    }

    fun append(message: String) {
        _state.value = _state.value.copy(logs = _state.value.logs + log(message))
    }

    fun finish(message: String? = null) {
        val logs = if (message.isNullOrBlank()) {
            _state.value.logs
        } else {
            _state.value.logs + log(message)
        }
        _state.value = _state.value.copy(running = false, logs = logs)
    }

    fun clear() {
        _state.value = DebugSessionState()
    }

    private fun log(message: String) = DebugLogEntry(
        id = System.nanoTime(),
        message = message
    )
}
