package utils

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EngineLogger {
    private const val TAG = "OpenMWEngine"
    private val logBuffer = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()
    private var isInitialized = false

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        // Capture uncaught exceptions to diagnose crashes or infinite loading hangs
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            e(TAG, "FATAL EXCEPTION on thread [${thread.name}]:\n$stackTrace")
            defaultHandler?.uncaughtException(thread, throwable)
        }
        i(TAG, "EngineLogger initialized. Tracking initialization and native startup flow.")
    }

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = LogEntry(timeStr, level, tag, message)
        logBuffer.add(entry)
        if (logBuffer.size > 800) {
            logBuffer.removeAt(0)
        }
        for (listener in listeners) {
            try {
                listener(entry)
            } catch (_: Exception) {}
        }
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message)
            "E" -> Log.e(tag, message)
            else -> Log.v(tag, message)
        }
    }

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)

    @Synchronized
    fun getLogs(): List<LogEntry> = ArrayList(logBuffer)

    @Synchronized
    fun clearLogs() {
        logBuffer.clear()
    }

    @Synchronized
    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }
}
