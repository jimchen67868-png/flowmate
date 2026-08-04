package com.example.automateclone.engine

import androidx.compose.runtime.mutableStateListOf

enum class LogLevel { INFO, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val flowName: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

object FlowLog {
    private const val MAX_ENTRIES = 300
    val entries = mutableStateListOf<LogEntry>()

    fun add(flowName: String, message: String, level: LogLevel = LogLevel.INFO) {
        entries.add(0, LogEntry(flowName = flowName, message = message, level = level))
        if (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
    }

    fun clear() {
        entries.clear()
    }
}
