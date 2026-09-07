package utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EngineLogger {
    private const val TAG = "OpenMWEngine"
    private val logBuffer = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()
    private var isInitialized = false
    private var appContext: Context? = null

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext

        // Capture uncaught exceptions to diagnose crashes or infinite loading hangs
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            e(TAG, "FATAL EXCEPTION on thread [${thread.name}]:\n$stackTrace")
            
            // Persist crash report for analysis upon restart
            appContext?.let { ctx ->
                try {
                    val crashFile = File(ctx.filesDir, "crash_report_pending.txt")
                    val sb = StringBuilder()
                    sb.append("CRASH REPORT - Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                    sb.append("Thread: ${thread.name}\n\nStack Trace:\n$stackTrace\n\nRecent Log Entries:\n")
                    for (entry in logBuffer) {
                        sb.append("${entry.timestamp} [${entry.level}] ${entry.tag}: ${entry.message}\n")
                    }
                    crashFile.writeText(sb.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write pending crash report", e)
                }
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
        i(TAG, "EngineLogger initialized. Tracking initialization and native startup flow.")
    }

    fun hasPendingCrash(context: Context): Boolean {
        return File(context.filesDir, "crash_report_pending.txt").exists()
    }

    fun getPendingCrashReport(context: Context): String? {
        val file = File(context.filesDir, "crash_report_pending.txt")
        return if (file.exists()) {
            try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearPendingCrash(context: Context) {
        val file = File(context.filesDir, "crash_report_pending.txt")
        if (file.exists()) {
            try {
                file.delete()
            } catch (_: Exception) {}
        }
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
