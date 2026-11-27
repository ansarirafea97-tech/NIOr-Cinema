package com.example.util

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class SeekLogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val message: String
)

object SeekDiagnosticsLogger {
    private const val MAX_LOGS = 400
    private val idCounter = AtomicLong(1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    private val _logs = MutableStateFlow<List<SeekLogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    @Synchronized
    fun log(tag: String, message: String) {
        // Always write to standard Android Logcat as well
        Log.i(tag, message)

        val timeStr = timeFormat.format(Date())
        val entry = SeekLogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = timeStr,
            tag = tag,
            message = message
        )
        val currentList = _logs.value
        val updated = if (currentList.size >= MAX_LOGS) {
            currentList.drop(currentList.size - MAX_LOGS + 1) + entry
        } else {
            currentList + entry
        }
        _logs.value = updated
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
        Log.i("LOCAL_SEEK_ROOT_CAUSE", "[SEEK_DIAGNOSTICS_CLEARED]")
    }

    fun getFormattedText(): String {
        val sb = StringBuilder()
        sb.append("=== LOCAL SEEK ROOT CAUSE DIAGNOSTIC REPORT ===\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android SDK: ${Build.VERSION.SDK_INT} (OS: ${Build.VERSION.RELEASE})\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("Total Events: ${_logs.value.size}\n")
        sb.append("===============================================\n\n")

        val currentList = _logs.value
        if (currentList.isEmpty()) {
            sb.append("(No seek diagnostic events recorded yet. Play a video and perform a seek first.)\n")
        } else {
            for (entry in currentList) {
                sb.append("[${entry.timestamp}] ${entry.message}\n")
            }
        }
        sb.append("\n=== END OF REPORT ===\n")
        return sb.toString()
    }
}
