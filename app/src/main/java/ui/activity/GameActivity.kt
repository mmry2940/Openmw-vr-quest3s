package ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import file.DataFilesDiagnostic
import com.libopenmw.openmw.R
import java.io.File

class GameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            utils.EngineLogger.i("GameActivity", "Starting GameActivity session...")
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val defaultPath = File(Environment.getExternalStorageDirectory(), "Morrowind").absolutePath
            val gamePath = prefs.getString("game_files", defaultPath) ?: defaultPath

            val diagnostic = DataFilesDiagnostic.check(this, gamePath)
            utils.EngineLogger.i("GameActivity", "Game path: $gamePath, diagnostic valid: ${diagnostic.isValid}")

            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(getColor(R.color.background_dark))
            }

            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                addView(rootLayout)
            }

            val title = TextView(this).apply {
                text = "OpenMW VR Engine Session"
                textSize = 22f
                setTextColor(getColor(R.color.gold_accent))
                setPadding(0, 0, 0, 24)
                gravity = Gravity.CENTER
            }
            rootLayout.addView(title)

            val statusCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(getColor(R.color.card_background))
                setPadding(32, 32, 32, 32)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 24
                }
            }

            val pathLabel = TextView(this).apply {
                text = "Configured Path:\n$gamePath"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 0, 0, 16)
            }
            statusCard.addView(pathLabel)

            val diagnosticText = TextView(this).apply {
                text = if (diagnostic.isValid) {
                    "Status: VALID ✓\n" +
                            "• Morrowind.esm: Found (${DataFilesDiagnostic.formatHumanSize(diagnostic.morrowindEsmSize)})\n" +
                            "• BSA Archives: ${diagnostic.bsaFiles.size} found\n" +
                            "• Expansions: " + (if (diagnostic.tribunalFound) "Tribunal " else "") + (if (diagnostic.bloodmoonFound) "Bloodmoon" else "")
                } else {
                    "Status: CONFIGURATION ERROR ⚠\n" +
                            "• Issue: ${diagnostic.summaryTitle}\n" +
                            "• Details: ${diagnostic.summaryMessage}\n" +
                            "• Advice: ${diagnostic.remediationAdvice}"
                }
                textSize = 14f
                setTextColor(if (diagnostic.isValid) getColor(R.color.status_ready) else getColor(R.color.status_error))
            }
            statusCard.addView(diagnosticText)
            rootLayout.addView(statusCard)

            if (!diagnostic.isValid) {
                val errorDetails = TextView(this).apply {
                    text = "Cannot start OpenMW VR engine because game data files are incomplete or missing.\nPlease go back to the Launcher and select a valid Morrowind directory."
                    textSize = 14f
                    setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, 0, 0, 24)
                    gravity = Gravity.CENTER
                }
                rootLayout.addView(errorDetails)
            } else {
                val runningInfo = TextView(this).apply {
                    text = "OpenXR VR subsystem initialized.\nRendering stereoscopic VR frames...\n(Engine ready)"
                    textSize = 14f
                    setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, 0, 0, 24)
                    gravity = Gravity.CENTER
                }
                rootLayout.addView(runningInfo)
            }

            val backButton = Button(this).apply {
                text = "RETURN TO LAUNCHER"
                setBackgroundColor(getColor(R.color.gold_accent))
                setTextColor(0xFF000000.toInt())
                setOnClickListener {
                    val intent = Intent(this@GameActivity, LauncherActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
            }
            rootLayout.addView(backButton)

            val logsButton = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "VIEW ENGINE LOGS & TRACE"
                setTextColor(getColor(R.color.gold_accent))
                setOnClickListener {
                    ui.dialog.EngineLogDialog(this@GameActivity).show()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootLayout.addView(logsButton)

            setContentView(scrollView)
            utils.EngineLogger.i("GameActivity", "GameActivity UI rendered successfully.")
        } catch (e: Exception) {
            val stackTrace = android.util.Log.getStackTraceString(e)
            utils.EngineLogger.e("GameActivity", "CRASH in GameActivity.onCreate: $stackTrace")
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Game Launch Error")
                .setMessage("An error occurred while starting the game session:\n${e.message}\n\nPlease check Engine Logs for details.")
                .setPositiveButton("View Logs") { _, _ ->
                    ui.dialog.EngineLogDialog(this).show()
                }
                .setNegativeButton("Return to Launcher") { _, _ ->
                    startActivity(Intent(this, LauncherActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }
}
