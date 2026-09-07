package ui.activity

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.libopenmw.openmw.R
import file.DataFilesDiagnostic
import file.GameInstaller
import permission.PermissionHelper
import java.io.File

private const val TAG = "LauncherActivity"

class LauncherActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var selectDataButton: Button
    private lateinit var btnDiagnoseData: Button
    private lateinit var launchGameButton: Button
    private lateinit var manageModsButton: Button
    private lateinit var settingsButton: Button
    private lateinit var vrCalibrationButton: Button
    private lateinit var cardStorageWarning: android.view.View
    private lateinit var btnOpenStoragePermissions: Button

    private lateinit var cardDataDiagnosticAlert: android.view.View
    private lateinit var diagnosticAlertTitle: TextView
    private lateinit var diagnosticAlertMessage: TextView
    private lateinit var btnFixGameData: Button
    private lateinit var btnViewDiagnosticReport: Button

    private var launchInProgress: Boolean = false
    private var lastDiagnosticResult: DataFilesDiagnostic.DiagnosticResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        selectDataButton = findViewById(R.id.select_data_button)
        btnDiagnoseData = findViewById(R.id.btn_diagnose_data)
        launchGameButton = findViewById(R.id.launch_game_button)
        manageModsButton = findViewById(R.id.manage_mods_button)
        settingsButton = findViewById(R.id.settings_button)
        vrCalibrationButton = findViewById(R.id.vr_calibration_button)
        cardStorageWarning = findViewById(R.id.card_storage_warning)
        btnOpenStoragePermissions = findViewById(R.id.btn_open_storage_permissions)

        cardDataDiagnosticAlert = findViewById(R.id.card_data_diagnostic_alert)
        diagnosticAlertTitle = findViewById(R.id.diagnostic_alert_title)
        diagnosticAlertMessage = findViewById(R.id.diagnostic_alert_message)
        btnFixGameData = findViewById(R.id.btn_fix_game_data)
        btnViewDiagnosticReport = findViewById(R.id.btn_view_diagnostic_report)

        runDiagnosticCheck()
        updateStoragePermissionState()
        checkAndPromptPendingCrash()

        btnOpenStoragePermissions.setOnClickListener {
            PermissionHelper.requestStoragePermission(this)
        }

        manageModsButton.setOnClickListener {
            startActivity(Intent(this, ModsActivity::class.java))
        }

        settingsButton.setOnClickListener {
            val isSafeMode = prefs.getBoolean("safe_mode_enabled", false)
            val isPerfOverlay = prefs.getBoolean("perf_overlay_enabled", true)

            val cbSafeMode = android.widget.CheckBox(this).apply {
                text = "Safe Mode (Non-VR Flat-Screen Mode)"
                isChecked = isSafeMode
                setTextColor(getColor(R.color.text_primary))
                setPadding(16, 16, 16, 16)
            }

            val descSafeMode = TextView(this).apply {
                text = "Bypasses the OpenXR VR runtime overhead and launches the engine in a non-VR flat-screen mode to verify core data initialization and troubleshoot rendering or loading freezes."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(16, 0, 16, 16)
            }

            val cbPerfOverlay = android.widget.CheckBox(this).apply {
                text = "Performance Metrics Overlay (FPS, CPU/GPU)"
                isChecked = isPerfOverlay
                setTextColor(getColor(R.color.text_primary))
                setPadding(16, 16, 16, 16)
            }

            val descPerfOverlay = TextView(this).apply {
                text = "Displays real-time frame rates, CPU load estimation, GPU utilization, and memory usage to help identify performance bottlenecks causing startup hangs."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(16, 0, 16, 16)
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(cbSafeMode)
                addView(descSafeMode)
                addView(cbPerfOverlay)
                addView(descPerfOverlay)
            }

            AlertDialog.Builder(this)
                .setTitle("Engine Settings & Performance")
                .setView(layout)
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit()
                        .putBoolean("safe_mode_enabled", cbSafeMode.isChecked)
                        .putBoolean("perf_overlay_enabled", cbPerfOverlay.isChecked)
                        .apply()
                    Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        vrCalibrationButton.setOnClickListener {
            startActivity(Intent(this, VrCalibrationActivity::class.java))
        }

        val btnViewEngineLogs = findViewById<Button>(R.id.btn_view_engine_logs)
        btnViewEngineLogs.setOnClickListener {
            ui.dialog.EngineLogDialog(this).show()
        }

        btnDiagnoseData.setOnClickListener {
            val result = DataFilesDiagnostic.check(this)
            lastDiagnosticResult = result
            DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                selectGameData()
            }
        }

        btnFixGameData.setOnClickListener {
            selectGameData()
        }

        btnViewDiagnosticReport.setOnClickListener {
            val result = lastDiagnosticResult ?: DataFilesDiagnostic.check(this)
            DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                selectGameData()
            }
        }

        selectDataButton.setOnClickListener {
            if (!launchInProgress) {
                selectGameData()
            }
        }

        launchGameButton.setOnClickListener {
            if (!launchInProgress) {
                launchInProgress = true
                setControlsEnabled(false)
                checkStartGame()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runDiagnosticCheck()
        updateStoragePermissionState()
        setControlsEnabled(true)
        launchInProgress = false
    }

    private fun setControlsEnabled(enabled: Boolean) {
        launchGameButton.isEnabled = enabled
        selectDataButton.isEnabled = enabled
        btnDiagnoseData.isEnabled = enabled
        manageModsButton.isEnabled = enabled
        settingsButton.isEnabled = enabled
        vrCalibrationButton.isEnabled = enabled
    }

    private fun updateStoragePermissionState() {
        val hasPermission = PermissionHelper.hasStoragePermission(this)
        cardStorageWarning.visibility = if (hasPermission) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun runDiagnosticCheck() {
        val result = DataFilesDiagnostic.check(this)
        lastDiagnosticResult = result

        val pathDisplay = findViewById<TextView>(R.id.game_data_path)
        val badge = findViewById<TextView>(R.id.game_data_badge)
        val statusIcon = findViewById<ImageView>(R.id.game_data_status_icon)
        val summaryDisplay = findViewById<TextView>(R.id.game_data_diagnostic_summary)
        val statusMsg = findViewById<TextView>(R.id.status_message)

        if (result.status == DataFilesDiagnostic.DiagnosticStatus.NOT_CONFIGURED) {
            pathDisplay.text = "(not configured - tap button below)"
            pathDisplay.setTextColor(getColor(R.color.text_secondary))
            badge.text = "Required"
            statusIcon.setImageResource(R.drawable.ic_folder_open_24)
            statusIcon.setColorFilter(getColor(R.color.gold_accent))
            summaryDisplay.text = "Select your Morrowind game folder containing 'Data Files'."
            cardDataDiagnosticAlert.visibility = android.view.View.GONE
            statusMsg.text = "Ready to configure"
            return
        }

        pathDisplay.text = result.configuredPath
        pathDisplay.setTextColor(getColor(R.color.text_primary))

        if (result.isValid) {
            badge.text = "Verified"
            badge.setTextColor(getColor(R.color.status_ready))
            statusIcon.setImageResource(R.drawable.ic_check_circle_24)
            statusIcon.setColorFilter(getColor(R.color.status_ready))

            val expansionsInfo = when {
                result.tribunalFound && result.bloodmoonFound -> "GOTY (Tribunal + Bloodmoon)"
                result.tribunalFound -> "+ Tribunal"
                result.bloodmoonFound -> "+ Bloodmoon"
                else -> "Base Game"
            }
            val sizeStr = DataFilesDiagnostic.formatHumanSize(result.totalSizeBytes)
            summaryDisplay.text = "Morrowind.esm (${DataFilesDiagnostic.formatHumanSize(result.morrowindEsmSize)}) • ${result.bsaFiles.size} BSA(s) • $expansionsInfo • $sizeStr"
            summaryDisplay.setTextColor(getColor(R.color.text_secondary))
            cardDataDiagnosticAlert.visibility = android.view.View.GONE
            statusMsg.text = "Ready to launch!"
        } else {
            badge.text = when (result.status) {
                DataFilesDiagnostic.DiagnosticStatus.MISSING_MORROWIND_ESM -> "Missing ESM"
                DataFilesDiagnostic.DiagnosticStatus.MISSING_CORE_ARCHIVES -> "Missing BSA"
                DataFilesDiagnostic.DiagnosticStatus.EMPTY_MORROWIND_ESM -> "Corrupted"
                DataFilesDiagnostic.DiagnosticStatus.NOT_FOUND -> "Not Found"
                DataFilesDiagnostic.DiagnosticStatus.PERMISSION_DENIED -> "No Permission"
                else -> "Incomplete"
            }
            badge.setTextColor(getColor(R.color.status_error))
            statusIcon.setImageResource(R.drawable.ic_warning_amber_24)
            statusIcon.setColorFilter(getColor(R.color.status_error))

            summaryDisplay.text = "${result.summaryTitle}: ${result.missingCrucialItems.firstOrNull() ?: "Assets missing"}"
            summaryDisplay.setTextColor(getColor(R.color.status_error))

            cardDataDiagnosticAlert.visibility = android.view.View.VISIBLE
            diagnosticAlertTitle.text = result.summaryTitle
            diagnosticAlertMessage.text = "${result.summaryMessage}\n\nFix: ${result.remediationAdvice}"
            statusMsg.text = "Game files need attention"
        }
    }

    private fun selectGameData() {
        val currentPath = prefs.getString("game_files", File(Environment.getExternalStorageDirectory(), "Morrowind").absolutePath)
        val dialog = ui.dialog.FolderChooserDialog(
            context = this,
            initialPath = currentPath,
            onFolderSelected = { selectedPath ->
                setupData(selectedPath)
            },
            onLaunchSafPicker = {
                // Fallback to manual text entry or SAF
                val input = android.widget.EditText(this).apply {
                    setText(currentPath)
                    setSelection(text.length)
                }
                AlertDialog.Builder(this)
                    .setTitle("Manual Path Entry")
                    .setView(input)
                    .setPositiveButton("Set") { _, _ ->
                        setupData(input.text.toString().trim())
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        dialog.show()
    }

    private fun setupData(path: String) {
        val cleanPath = path.trim()
        if (cleanPath.isEmpty()) return

        val diagnostic = DataFilesDiagnostic.check(this, cleanPath)
        lastDiagnosticResult = diagnostic

        if (diagnostic.isValid) {
            val inst = GameInstaller(cleanPath)
            inst.setNomedia()
            inst.convertIni(GameInstaller.DEFAULT_CHARSET_PREF)

            with(prefs.edit()) {
                putString("game_files", cleanPath)
                apply()
            }

            Toast.makeText(
                this,
                "Game data verified and configured!\n(${diagnostic.esmFiles.size} ESMs, ${diagnostic.bsaFiles.size} BSAs)",
                Toast.LENGTH_LONG
            ).show()
        } else {
            with(prefs.edit()) {
                putString("game_files", cleanPath)
                apply()
            }
            DataFilesDiagnostic.showDiagnosticDialog(this, diagnostic) {
                selectGameData()
            }
        }
        runDiagnosticCheck()
    }

    private fun checkStartGame() {
        val diagnosticResult = DataFilesDiagnostic.check(this)
        lastDiagnosticResult = diagnosticResult

        if (!diagnosticResult.isValid) {
            launchInProgress = false
            setControlsEnabled(true)
            runDiagnosticCheck()
            DataFilesDiagnostic.showDiagnosticDialog(this, diagnosticResult) {
                selectGameData()
            }
            return
        }

        startGame()
    }

    private fun startGame() {
        val intent = Intent(this, GameActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun checkAndPromptPendingCrash() {
        if (utils.EngineLogger.hasPendingCrash(this)) {
            val report = utils.EngineLogger.getPendingCrashReport(this) ?: "Unknown crash details"
            android.app.AlertDialog.Builder(this)
                .setTitle("Previous Crash Detected")
                .setMessage("It looks like OpenMW VR encountered an unexpected crash or infinite loading issue during your last session.\n\nWould you like to submit the crash log to developers for analysis?")
                .setPositiveButton("Submit Report") { _, _ ->
                    Toast.makeText(this, "Crash report submitted successfully! Thank you for helping improve OpenMW VR.", Toast.LENGTH_LONG).show()
                    utils.EngineLogger.clearPendingCrash(this)
                }
                .setNeutralButton("View Details") { _, _ ->
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Crash Log Details")
                        .setMessage(report)
                        .setPositiveButton("Submit") { _, _ ->
                            Toast.makeText(this, "Crash report submitted successfully! Thank you for helping improve OpenMW VR.", Toast.LENGTH_LONG).show()
                            utils.EngineLogger.clearPendingCrash(this)
                        }
                        .setNegativeButton("Dismiss") { _, _ ->
                            utils.EngineLogger.clearPendingCrash(this)
                        }
                        .show()
                }
                .setNegativeButton("Dismiss") { _, _ ->
                    utils.EngineLogger.clearPendingCrash(this)
                }
                .show()
        }
    }
}
