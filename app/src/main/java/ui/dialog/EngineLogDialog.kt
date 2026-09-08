package ui.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.libopenmw.openmw.R
import utils.EngineLogger

class EngineLogDialog(private val context: Context) {
    private val dialog = Dialog(context, R.style.MyTheme)
    private lateinit var txtLogContent: TextView
    private lateinit var scrollLogs: ScrollView

    private val logListener: (EngineLogger.LogEntry) -> Unit = {
        // Run on UI thread if possible
        try {
            (context as? android.app.Activity)?.runOnUiThread {
                updateLogsText()
            }
        } catch (_: Exception) {}
    }

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_engine_logs, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        txtLogContent = view.findViewById(R.id.txt_log_content)
        scrollLogs = view.findViewById(R.id.scroll_logs)

        val btnClear = view.findViewById<Button>(R.id.btn_clear_logs)
        val btnClose = view.findViewById<Button>(R.id.btn_close_logs)

        btnClear.setOnClickListener {
            EngineLogger.clearLogs()
            updateLogsText()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }
    }

    fun show() {
        EngineLogger.addListener(logListener)
        updateLogsText()
        dialog.setOnDismissListener {
            EngineLogger.removeListener(logListener)
        }
        dialog.show()
    }

    private fun updateLogsText() {
        val logs = EngineLogger.getLogs()
        val sb = StringBuilder()
        if (logs.isEmpty()) {
            sb.append("No log entries yet.")
        } else {
            for (entry in logs) {
                val prefix = when (entry.level) {
                    "E" -> "[ERROR]"
                    "W" -> "[WARN]"
                    "I" -> "[INFO]"
                    "N" -> "[NATIVE]"
                    else -> "[DEBUG]"
                }
                sb.append("${entry.timestamp} $prefix ${entry.tag}: ${entry.message}\n")
            }
        }
        txtLogContent.text = sb.toString()
        scrollLogs.post {
            scrollLogs.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
